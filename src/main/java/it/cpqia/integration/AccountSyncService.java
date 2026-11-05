package it.cpqia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agente di sincronizzazione clienti: legge da IBM Power System (file CGANA01J, libreria INF90DAT) i record con CLFOCP = 'C'
 * inseriti (DT01CA) o modificati (DTMNCA) dall'ultima importazione fino alla data di esecuzione, e li importa in "account"
 * come CLIENTE (codice = CONTCA).
 */
@Service
public class AccountSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_$#@]{1,10}$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern NUMBER_AT_END = Pattern.compile(
            "^(.*?)[,\\s]+((?:\\d+[A-Za-z]?(?:\\s*[/\\-]\\s*\\w+)?)|SNC|S\\.N\\.C\\.?)$", Pattern.CASE_INSENSITIVE);
    /** abbreviazioni comuni dei toponimi nei gestionali */
    private static final Map<String, String> ABBR = Map.ofEntries(
            Map.entry("V", "Via"), Map.entry("V.", "Via"), Map.entry("VLE", "Viale"), Map.entry("V.LE", "Viale"), Map.entry("VIALE", "Viale"),
            Map.entry("P.ZZA", "Piazza"), Map.entry("P.ZA", "Piazza"), Map.entry("PZA", "Piazza"), Map.entry("PIAZZA", "Piazza"), Map.entry("P.LE", "Piazzale"),
            Map.entry("C.SO", "Corso"), Map.entry("CSO", "Corso"), Map.entry("L.GO", "Largo"), Map.entry("LGO", "Largo"),
            Map.entry("STR.", "Strada"), Map.entry("STR", "Strada"), Map.entry("LOC.", "Località"), Map.entry("LOC", "Località"),
            Map.entry("FRAZ.", "Frazione"), Map.entry("FRAZ", "Frazione"), Map.entry("V.LO", "Vicolo"), Map.entry("C.DA", "Contrada"));

    public record Config(boolean enabled, String mode, int intervalMin, String dailyTime, String sourceLibrary, String sourceFile,
                         LocalDate lastSyncDate, LocalDateTime lastRunAt) {
    }

    public record Run(Long id, LocalDateTime startedAt, LocalDateTime finishedAt, String triggerType, Boolean ok, int readCount,
                      int inserted, int updated, int unchanged, int skipped, String message) {
    }

    private record Existing(long id, String code, String type, String vat, String company, String streetType, String streetName,
                            String streetNumber, String cap, String city, String province, String taxCode, String agentCode, boolean deleted) {
    }

    record Src(String code, String company, String indica, String cap, String city, String province, String vat, String tax,
                       LocalDate dtInsert, LocalDate dtModify, String agent) {
        Src(String code, String company, String indica, String cap, String city, String province, String vat, String tax,
            LocalDate dtInsert, LocalDate dtModify) {
            this(code, company, indica, cap, city, province, vat, tax, dtInsert, dtModify, "");
        }
    }

    private record Mapped(String code, String company, String streetType, String streetName, String streetNumber, String cap,
                          String city, String province, String vat, String tax, String agent) {
    }

    private enum Outcome { INSERTED, UPDATED, UNCHANGED, SKIPPED }

    private final JdbcClient jdbc;
    private final IbmiService ibmi;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopReq = new AtomicBoolean(false);

    public AccountSyncService(JdbcClient jdbc, IbmiService ibmi) {
        this.jdbc = jdbc;
        this.ibmi = ibmi;
    }

    public boolean running() {
        return running.get();
    }

    /**
     * «Ferma»: se il job e' in esecuzione ne chiede l'arresto (si ferma al prossimo record); altrimenti chiude le righe rimaste «in corso»
     * (esecuzione interrotta da un riavvio dell'applicazione).
     */
    public String stop() {
        if (running.get()) {
            stopReq.set(true);
            return "Arresto richiesto: il job si ferma al termine del record in elaborazione";
        }
        int n = jdbc.sql("update %s set finished_at = now(), ok = false, message = 'Interrotta (esecuzione non terminata)' where finished_at is null".formatted("ibmi_sync_run")).update();
        if (n > 0) {
            jdbc.sql("update ibmi_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        }
        return n > 0 ? "Esecuzione chiusa come interrotta" : "Nessun job in corso";
    }

    // ------------------------------------------------------------------ configurazione
    public Config config() {
        return jdbc.sql("select enabled, mode, interval_min, daily_time, source_library, source_file, last_sync_date, last_run_at from ibmi_sync_config where id = 1")
                .query(Config.class).single();
    }

    public void saveConfig(Config c) {
        if (!"INTERVAL".equals(c.mode()) && !"DAILY".equals(c.mode())) {
            throw new IllegalArgumentException("Tipo di pianificazione non valido");
        }
        if (c.intervalMin() < 5 || c.intervalMin() > 1440) {
            throw new IllegalArgumentException("L'intervallo deve essere tra 5 e 1440 minuti");
        }
        if (c.dailyTime() == null || !TIME.matcher(c.dailyTime()).matches()) {
            throw new IllegalArgumentException("Orario non valido (formato HH:MM)");
        }
        if (c.sourceLibrary() == null || !IDENT.matcher(c.sourceLibrary().trim()).matches()
                || c.sourceFile() == null || !IDENT.matcher(c.sourceFile().trim()).matches()) {
            throw new IllegalArgumentException("Libreria e file sorgente: fino a 10 caratteri (lettere, cifre, _ $ # @)");
        }
        jdbc.sql("update ibmi_sync_config set enabled = :e, mode = :m, interval_min = :i, daily_time = :t, source_library = :l, source_file = :f, last_sync_date = :d where id = 1")
                .param("d", c.lastSyncDate()).param("e", c.enabled()).param("m", c.mode()).param("i", c.intervalMin()).param("t", c.dailyTime())
                .param("l", c.sourceLibrary().trim().toUpperCase()).param("f", c.sourceFile().trim().toUpperCase()).update();
    }

    /** true se la sincronizzazione pianificata e' da eseguire adesso */
    public boolean due(LocalDateTime now) {
        Config c = config();
        if (!c.enabled() || running.get()) {
            return false;
        }
        if (c.lastRunAt() == null) {
            return "INTERVAL".equals(c.mode()) || now.toLocalTime().isAfter(LocalTime.parse(c.dailyTime()));
        }
        if ("INTERVAL".equals(c.mode())) {
            return !now.isBefore(c.lastRunAt().plusMinutes(c.intervalMin()));
        }
        LocalDateTime scheduled = now.toLocalDate().atTime(LocalTime.parse(c.dailyTime()));
        return !now.isBefore(scheduled) && c.lastRunAt().isBefore(scheduled);
    }

    public String nextRunText() {
        Config c = config();
        if (!c.enabled()) {
            return "pianificazione disattivata";
        }
        LocalDateTime now = LocalDateTime.now();
        if ("INTERVAL".equals(c.mode())) {
            LocalDateTime n = c.lastRunAt() == null ? now : c.lastRunAt().plusMinutes(c.intervalMin());
            return (n.isBefore(now) ? now : n).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        LocalDateTime at = now.toLocalDate().atTime(LocalTime.parse(c.dailyTime()));
        boolean doneToday = c.lastRunAt() != null && !c.lastRunAt().isBefore(at);
        LocalDateTime n = doneToday ? at.plusDays(1) : (now.isBefore(at) ? at : now);
        return n.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public List<Run> lastRuns(int n) {
        return jdbc.sql("select id, started_at, finished_at, trigger_type, ok, read_count, inserted, updated, unchanged, skipped, message "
                + "from ibmi_sync_run order by id desc limit :n").param("n", n).query(Run.class).list();
    }

    public String runDetails(long id) {
        return jdbc.sql("select details from ibmi_sync_run where id = :id").param("id", id).query(String.class).optional().orElse("");
    }

    // ------------------------------------------------------------------ esecuzione
    /** esegue la sincronizzazione (una alla volta); restituisce l'esito */
    public Run run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        stopReq.set(false);
        long runId = jdbc.sql("insert into ibmi_sync_run (trigger_type) values (:t) returning id").param("t", triggerType).query(Long.class).single();
        int read = 0, ins = 0, upd = 0, unch = 0, skip = 0;
        List<String> problems = new ArrayList<>();
        boolean ok = false;
        String message;
        try {
            IbmiService.Settings st = ibmi.stored();
            if (st.host() == null || st.host().isBlank() || st.user() == null || st.user().isBlank()) {
                throw new IllegalStateException("Connessione a IBM Power System non configurata");
            }
            Config cfg = config();
            LocalDate today = LocalDate.now();
            LocalDate since = cfg.lastSyncDate();
            List<String> streetTypes = jdbc.sql("select name from address_type").query(String.class).list();
            List<Src> selected = new ArrayList<>();
            String sql = "select CONTCA, DSCOCP, INDICA, CAPOCA, LOCACA, PROVCA, PIVACA, CDFICA, DT01CA, DTMNCA, CDAGCA from "
                    + cfg.sourceLibrary() + "." + cfg.sourceFile() + " where CLFOCP = 'C'";
            try (Connection c = ibmi.connect(st); Statement stm = c.createStatement(); ResultSet rs = stm.executeQuery(sql)) {
                while (rs.next()) {
                    if (stopReq.get()) {
                        throw new JobStopped();
                    }
                    read++;
                    Src s = new Src(txt(rs.getString(1)), txt(rs.getString(2)), txt(rs.getString(3)), txt(rs.getString(4)), txt(rs.getString(5)),
                            txt(rs.getString(6)), txt(rs.getString(7)), txt(rs.getString(8)), ymd(rs.getString(9)), ymd(rs.getString(10)), txt(rs.getString(11)));
                    if (inScope(s, since, today)) {
                        selected.add(s);
                    }
                }
            }
            for (Src s : selected) {
                if (stopReq.get()) {
                    throw new JobStopped();
                }
                try {
                    Object[] r = process(s, streetTypes);
                    switch ((Outcome) r[0]) {
                        case INSERTED -> ins++;
                        case UPDATED -> upd++;
                        case UNCHANGED -> unch++;
                        default -> {
                            skip++;
                            if (problems.size() < 200) {
                                problems.add(s.code() + " – " + r[1]);
                            }
                        }
                    }
                } catch (Exception e) {
                    skip++;
                    if (problems.size() < 200) {
                        problems.add(s.code() + " – errore: " + e.getMessage());
                    }
                }
            }
            jdbc.sql("update ibmi_sync_config set last_sync_date = :d where id = 1").param("d", today).update();
            ok = true;
            message = "Letti " + read + " clienti, da importare " + selected.size() + ": " + ins + " nuovi, " + upd + " aggiornati, "
                    + unch + " invariati, " + skip + " saltati";
        } catch (JobStopped e) {
            message = "Interrotta dall'utente: " + read + " clienti letti, " + ins + " nuovi, " + upd + " aggiornati, " + unch + " invariati, " + skip + " saltati (data ultima importazione non aggiornata)";
        } catch (Exception e) {
            Throwable c = e.getCause() != null && e.getMessage() == null ? e.getCause() : e;
            message = "Sincronizzazione non riuscita: " + c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
            log.warn(message, e);
        } finally {
            running.set(false);
        }
        String details = String.join("\n", problems);
        jdbc.sql("update ibmi_sync_run set finished_at = now(), ok = :ok, read_count = :r, inserted = :i, updated = :u, unchanged = :n, skipped = :s, message = :m, details = :d where id = :id")
                .param("ok", ok).param("r", read).param("i", ins).param("u", upd).param("n", unch).param("s", skip)
                .param("m", message.length() > 290 ? message.substring(0, 290) : message).param("d", details).param("id", runId).update();
        // data, ora e secondo di fine esecuzione (job terminato, fermato a mano o chiuso con errore)
        jdbc.sql("update ibmi_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        return lastRuns(1).get(0);
    }

    /**
     * Record da importare: inseriti o modificati tra la data dell'ultima importazione (inclusa: i dati hanno solo la data) e la data
     * di esecuzione; alla prima esecuzione tutti (anche senza date valide).
     */
    static boolean inScope(Src s, LocalDate since, LocalDate today) {
        boolean ins = inWindow(s.dtInsert(), since, today), mod = inWindow(s.dtModify(), since, today);
        return ins || mod || (since == null && s.dtInsert() == null && s.dtModify() == null);
    }

    private static boolean inWindow(LocalDate d, LocalDate since, LocalDate today) {
        return d != null && (since == null || !d.isBefore(since)) && !d.isAfter(today);
    }

    // ------------------------------------------------------------------ trasformazione e scrittura
    private static String txt(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').trim();
    }

    /** data in formato YYYYMMDD (testo o numero, anche "20240115.0"); null se vuota/non valida */
    static LocalDate ymd(String s) {
        if (s == null) {
            return null;
        }
        String d = s.trim();
        int dot = d.indexOf('.');
        if (dot >= 0) {
            d = d.substring(0, dot);
        }
        if (!d.matches("\\d{8}") || d.equals("00000000")) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)), Integer.parseInt(d.substring(6, 8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String cut(String s, int n) {
        return s == null ? null : (s.length() > n ? s.substring(0, n) : s);
    }

    private static String noAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** INDICA ("VIA ROMA 12/A") -> toponimo (tabella Tipo Indirizzo), indirizzo e numero civico */
    static String[] parseAddress(String indica, List<String> streetTypes) {
        String street = indica == null ? "" : indica.trim().replaceAll("\\s+", " ");
        String number = "SNC";
        Matcher m = NUMBER_AT_END.matcher(street);
        if (m.matches() && !m.group(1).isBlank()) {
            street = m.group(1).trim().replaceAll("[,\\s]+$", "");
            number = m.group(2).trim();
        }
        String type = "Via";
        int sp = street.indexOf(' ');
        if (sp > 0) {
            String first = street.substring(0, sp);
            String key = noAccents(first).toUpperCase(Locale.ROOT);
            String found = null;
            for (String t : streetTypes) {
                if (noAccents(t).toUpperCase(Locale.ROOT).equals(key)) {
                    found = t;
                }
            }
            if (found == null && ABBR.containsKey(key)) {
                found = ABBR.get(key);
                for (String t : streetTypes) {
                    if (t.equalsIgnoreCase(found)) {
                        found = t;
                    }
                }
                if (!streetTypes.contains(found)) {
                    found = null;
                }
            }
            if (found != null) {
                type = found;
                street = street.substring(sp + 1).trim();
            }
        }
        if (!streetTypes.contains(type)) {
            type = streetTypes.isEmpty() ? "Via" : streetTypes.get(0);
        }
        return new String[]{type, street, number};
    }

    private static Mapped map(Src s, List<String> streetTypes) {
        String[] a = parseAddress(s.indica(), streetTypes);
        String cap = s.cap().replaceAll("\\s", "");
        if (cap.matches("\\d{1,4}")) {
            cap = String.format("%05d", Integer.parseInt(cap));       // CAP numerico senza zeri iniziali
        }
        String vat = s.vat().replaceAll("[\\s.\\-]", "");
        if (vat.matches("\\d{1,10}")) {
            vat = String.format("%011d", Long.parseLong(vat));        // partita IVA italiana: 11 cifre
        }
        if (vat.isEmpty()) {
            vat = s.tax().replaceAll("\\s", "");                       // privati senza P.IVA: si usa il codice fiscale
        }
        return new Mapped(cut(s.code(), 20), cut(s.company(), 200), a[0], cut(a[1].isEmpty() ? "-" : a[1], 200), cut(a[2], 20),
                cut(cap.isEmpty() ? "-" : cap, 10), cut(s.city().isEmpty() ? "-" : s.city(), 100),
                s.province().isEmpty() ? null : cut(s.province().toUpperCase(Locale.ROOT), 5), cut(vat, 20), s.tax().isEmpty() ? null : cut(s.tax(), 20),
                s.agent() == null || s.agent().isEmpty() ? null : cut(s.agent(), 20));
    }

    private Existing find(String where, Object param) {
        return jdbc.sql("select id, code, type, vat_number as vat, company_name as company, street_type, street_name, street_number, "
                + "postal_code as cap, city, province, tax_code, agent_code, (deleted_at is not null) as deleted from account where " + where)
                .param("p", param).query(Existing.class).optional().orElse(null);
    }

    private static boolean same(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    /** scrive un cliente; restituisce {esito, motivo} */
    private Object[] process(Src s, List<String> streetTypes) {
        if (s.code().isEmpty()) {
            return new Object[]{Outcome.SKIPPED, "codice (CONTCA) vuoto"};
        }
        if (s.company().isEmpty()) {
            return new Object[]{Outcome.SKIPPED, "ragione sociale (DSCOCP) vuota"};
        }
        Mapped m = map(s, streetTypes);
        if (m.vat().isEmpty()) {
            return new Object[]{Outcome.SKIPPED, "né partita IVA né codice fiscale"};
        }
        Existing byCode = find("code = :p", m.code());
        if (byCode != null && byCode.deleted()) {
            return new Object[]{Outcome.SKIPPED, "il codice è nel Cestino (ripristinalo o eliminalo definitivamente)"};
        }
        Existing byVat = find("vat_number = :p and deleted_at is null", m.vat());
        Existing target = byCode;
        if (byCode != null && byVat != null && byVat.id() != byCode.id()) {
            return new Object[]{Outcome.SKIPPED, "partita IVA " + m.vat() + " già usata dall'account " + byVat.code()};
        }
        if (byCode == null && byVat != null) {
            if (!"PROSPECT".equals(byVat.type())) {
                return new Object[]{Outcome.SKIPPED, "partita IVA " + m.vat() + " già presente nel cliente " + byVat.code()};
            }
            target = byVat;                                               // prospect diventato cliente: lo si promuove
        }
        if (target == null) {
            jdbc.sql("""
                    insert into account (code, type, company_name, street_type, street_name, street_number, postal_code, city, province, vat_number, tax_code, agent_code)
                    values (:code, 'CLIENTE', :co, :st, :sn, :num, :cap, :city, :prov, :vat, :tax, :ag)""")
                    .param("code", m.code()).param("co", m.company()).param("st", m.streetType()).param("sn", m.streetName()).param("num", m.streetNumber())
                    .param("cap", m.cap()).param("city", m.city()).param("prov", m.province()).param("vat", m.vat()).param("tax", m.tax()).param("ag", m.agent()).update();
            return new Object[]{Outcome.INSERTED, ""};
        }
        boolean addrSame = same(target.streetType(), m.streetType()) && same(target.streetName(), m.streetName()) && same(target.streetNumber(), m.streetNumber())
                && same(target.cap(), m.cap()) && same(target.city(), m.city()) && same(target.province(), m.province());
        boolean unchanged = addrSame && same(target.code(), m.code()) && "CLIENTE".equals(target.type()) && same(target.company(), m.company())
                && same(target.vat(), m.vat()) && same(target.taxCode(), m.tax()) && same(target.agentCode(), m.agent());
        if (unchanged) {
            return new Object[]{Outcome.UNCHANGED, ""};
        }
        jdbc.sql("""
                update account set code = :code, type = 'CLIENTE', company_name = :co, street_type = :st, street_name = :sn, street_number = :num,
                    postal_code = :cap, city = :city, province = :prov, vat_number = :vat, tax_code = :tax, agent_code = :ag,
                    latitude = case when :same then latitude else null end, longitude = case when :same then longitude else null end,
                    geo_key = case when :same then geo_key else null end
                where id = :id""")
                .param("code", m.code()).param("co", m.company()).param("st", m.streetType()).param("sn", m.streetName()).param("num", m.streetNumber())
                .param("cap", m.cap()).param("city", m.city()).param("prov", m.province()).param("vat", m.vat()).param("tax", m.tax())
                .param("ag", m.agent()).param("same", addrSame).param("id", target.id()).update();
        return new Object[]{Outcome.UPDATED, ""};
    }
}
