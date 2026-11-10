package it.cpqia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Sync Schede HW: legge da IBM Power System (file CGYHW00F, libreria INF90DAT) i record inseriti (DT01SH) o modificati (DTMNSH)
 * dall'ultima importazione fino alla data di esecuzione e li importa nella tabella «hw_card» (anagrafica Schede HW).
 * Il codice cliente (CONTSH) si decodifica con join su account.code. Chiave di importazione: CONTSH + TPMASH + MODESH + NRMASH.
 */
@Service
public class HwCardSyncService {

    private static final Logger log = LoggerFactory.getLogger(HwCardSyncService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_$#@]{1,10}$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    /** valori di RIAUSH che significano «rinnovo automatico attivo» */
    private static final Set<String> TRUE_VALUES = Set.of("S", "SI", "Y", "YES", "1", "T", "TRUE", "X");

    public record Config(boolean enabled, String mode, int intervalMin, String dailyTime, String sourceLibrary, String sourceFile,
                         LocalDate lastSyncDate, LocalDateTime lastRunAt) {
    }

    public record Run(Long id, LocalDateTime startedAt, LocalDateTime finishedAt, String triggerType, Boolean ok, int readCount,
                      int inserted, int updated, int unchanged, int skipped, String message) {
    }

    /** scheda HW nella forma della tabella hw_card (i nomi dei componenti seguono le colonne, in camelCase) */
    record Card(String customerCode, String hwType, String machineType, String model, String serialNumber, LocalDate installDate,
                String version, String pefCumulative, String tapeUnit, String processorType,
                LocalDate swMaintenanceExpiry, LocalDate hwMaintenanceExpiry, LocalDate swEffectiveExpiry, LocalDate hwEffectiveExpiry,
                boolean autoRenewal, String swGroup, String swServiceType, String hwServiceType, String notes, Integer licenseCount,
                LocalDate sourceInsertedOn, LocalDate sourceModifiedOn) {
    }

    private enum Outcome { INSERTED, UPDATED, UNCHANGED, SKIPPED }

    private final JdbcClient jdbc;
    private final IbmiService ibmi;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopReq = new AtomicBoolean(false);

    public HwCardSyncService(JdbcClient jdbc, IbmiService ibmi) {
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
        int n = jdbc.sql("update hw_sync_run set finished_at = now(), ok = false, message = 'Interrotta (esecuzione non terminata)' where finished_at is null").update();
        if (n > 0) {
            jdbc.sql("update hw_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        }
        return n > 0 ? "Esecuzione chiusa come interrotta" : "Nessun job in corso";
    }

    // ------------------------------------------------------------------ configurazione
    public Config config() {
        return jdbc.sql("select enabled, mode, interval_min, daily_time, source_library, source_file, last_sync_date, last_run_at from hw_sync_config where id = 1")
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
        jdbc.sql("update hw_sync_config set enabled = :e, mode = :m, interval_min = :i, daily_time = :t, source_library = :l, source_file = :f, last_sync_date = :d where id = 1")
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
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if ("INTERVAL".equals(c.mode())) {
            LocalDateTime n = c.lastRunAt() == null ? now : c.lastRunAt().plusMinutes(c.intervalMin());
            return (n.isBefore(now) ? now : n).format(f);
        }
        LocalDateTime at = now.toLocalDate().atTime(LocalTime.parse(c.dailyTime()));
        boolean doneToday = c.lastRunAt() != null && !c.lastRunAt().isBefore(at);
        LocalDateTime n = doneToday ? at.plusDays(1) : (now.isBefore(at) ? at : now);
        return n.format(f);
    }

    public List<Run> lastRuns(int n) {
        return jdbc.sql("select id, started_at, finished_at, trigger_type, ok, read_count, inserted, updated, unchanged, skipped, message "
                + "from hw_sync_run order by id desc limit :n").param("n", n).query(Run.class).list();
    }

    public String runDetails(long id) {
        return jdbc.sql("select details from hw_sync_run where id = :id").param("id", id).query(String.class).optional().orElse("");
    }

    // ------------------------------------------------------------------ esecuzione
    /** esegue la sincronizzazione (una alla volta); restituisce l'esito */
    public Run run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        stopReq.set(false);
        long runId = jdbc.sql("insert into hw_sync_run (trigger_type) values (:t) returning id").param("t", triggerType).query(Long.class).single();
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
            List<Card> selected = new ArrayList<>();
            String sql = "select CONTSH, TPMASH, MODESH, NRMASH, DTINSH, VERSSH, CUPTSH, UNNASH, PROCSH, DTSWSH, DTHWSH, DTESSH, DTEHSH, "
                    + "RIAUSH, GRSWSH, TSSWSH, TSHWSH, NOTESH, NRLISH, DT01SH, DTMNSH from " + cfg.sourceLibrary() + "." + cfg.sourceFile();
            try (Connection c = ibmi.connect(st); Statement stm = c.createStatement(); ResultSet rs = stm.executeQuery(sql)) {
                while (rs.next()) {
                    if (stopReq.get()) {
                        throw new JobStopped();
                    }
                    read++;
                    Card s = map(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                            rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), rs.getString(19), rs.getString(20), rs.getString(21));
                    if (inScope(s.sourceInsertedOn(), s.sourceModifiedOn(), since, today)) {
                        selected.add(s);
                    }
                }
            }
            for (Card s : selected) {
                if (stopReq.get()) {
                    throw new JobStopped();
                }
                try {
                    Object[] r = process(s);
                    switch ((Outcome) r[0]) {
                        case INSERTED -> ins++;
                        case UPDATED -> upd++;
                        case UNCHANGED -> unch++;
                        default -> {
                            skip++;
                            if (problems.size() < 200) {
                                problems.add(label(s) + " – " + r[1]);
                            }
                        }
                    }
                } catch (Exception e) {
                    skip++;
                    if (problems.size() < 200) {
                        problems.add(label(s) + " – errore: " + e.getMessage());
                    }
                }
            }
            jdbc.sql("update hw_sync_config set last_sync_date = :d where id = 1").param("d", today).update();
            ok = true;
            message = "Letti " + read + " record, da importare " + selected.size() + ": " + ins + " nuovi, " + upd + " aggiornati, "
                    + unch + " invariati, " + skip + " saltati";
        } catch (JobStopped e) {
            message = "Interrotta dall'utente: " + read + " record letti, " + ins + " nuovi, " + upd + " aggiornati, " + unch + " invariati, " + skip
                    + " saltati (data ultima importazione non aggiornata)";
        } catch (Exception e) {
            Throwable c = e.getCause() != null && e.getMessage() == null ? e.getCause() : e;
            message = "Sincronizzazione non riuscita: " + c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
            log.warn(message, e);
        } finally {
            running.set(false);
        }
        jdbc.sql("update hw_sync_run set finished_at = now(), ok = :ok, read_count = :r, inserted = :i, updated = :u, unchanged = :n, skipped = :s, message = :m, details = :d where id = :id")
                .param("ok", ok).param("r", read).param("i", ins).param("u", upd).param("n", unch).param("s", skip)
                .param("m", message.length() > 290 ? message.substring(0, 290) : message).param("d", String.join("\n", problems)).param("id", runId).update();
        // data, ora e secondo di fine esecuzione (job terminato, fermato a mano o chiuso con errore)
        jdbc.sql("update hw_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        return lastRuns(1).get(0);
    }

    /**
     * Record da importare: inseriti (DT01SH) o modificati (DTMNSH) tra la data dell'ultima importazione (inclusa: i dati hanno solo la data)
     * e la data di esecuzione; alla prima esecuzione tutti (anche senza date valide).
     */
    static boolean inScope(LocalDate dtInsert, LocalDate dtModify, LocalDate since, LocalDate today) {
        return inWindow(dtInsert, since, today) || inWindow(dtModify, since, today) || (since == null && dtInsert == null && dtModify == null);
    }

    private static boolean inWindow(LocalDate d, LocalDate since, LocalDate today) {
        return d != null && (since == null || !d.isBefore(since)) && !d.isAfter(today);
    }

    // ------------------------------------------------------------------ trasformazione
    private static String txt(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').trim();
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    /** MODESH -> {tipo macchina, modello}: si divide al primo «-»; senza «-» alimenta solo il tipo macchina */
    static String[] splitModel(String modesh) {
        String m = txt(modesh);
        int dash = m.indexOf('-');
        if (dash < 0) {
            return new String[]{m, ""};
        }
        return new String[]{m.substring(0, dash).trim(), m.substring(dash + 1).trim()};
    }

    /** RIAUSH -> boolean: S / SI / Y / YES / 1 / T / TRUE / X = vero, tutto il resto (anche vuoto) = falso */
    static boolean flag(String s) {
        return TRUE_VALUES.contains(txt(s).toUpperCase(Locale.ROOT));
    }

    /** NRLISH -> intero; null se vuoto o non numerico */
    static Integer integer(String s) {
        String t = txt(s).replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Card map(String contsh, String tpmash, String modesh, String nrmash, String dtinsh, String verssh, String cuptsh, String unnash, String procsh,
                    String dtswsh, String dthwsh, String dtessh, String dtehsh, String riaush, String grswsh, String tsswsh, String tshwsh,
                    String notesh, String nrlish, String dt01sh, String dtmnsh) {
        String[] mm = splitModel(modesh);
        return new Card(cut(txt(contsh), 20), cut(txt(tpmash), 30), cut(mm[0], 20), cut(mm[1], 20), cut(txt(nrmash), 40),
                AccountSyncService.ymd(dtinsh), cut(txt(verssh), 30), cut(txt(cuptsh), 30), cut(txt(unnash), 30), cut(txt(procsh), 30),
                AccountSyncService.ymd(dtswsh), AccountSyncService.ymd(dthwsh), AccountSyncService.ymd(dtessh), AccountSyncService.ymd(dtehsh),
                flag(riaush), cut(txt(grswsh), 30), cut(txt(tsswsh), 30), cut(txt(tshwsh), 30), txt(notesh), integer(nrlish),
                AccountSyncService.ymd(dt01sh), AccountSyncService.ymd(dtmnsh));
    }

    /** chiave di importazione: cliente, tipo hardware, modello (come in MODESH) e matricola */
    static String key(Card c) {
        String model = c.model().isEmpty() ? c.machineType() : c.machineType() + "-" + c.model();
        return cut(String.join("|", c.customerCode(), c.hwType(), model, c.serialNumber()), 200);
    }

    private static String label(Card c) {
        return c.customerCode() + " " + (c.model().isEmpty() ? c.machineType() : c.machineType() + "-" + c.model()) + " " + c.serialNumber();
    }

    // ------------------------------------------------------------------ scrittura
    private StatementSpec bind(StatementSpec sp, Card c) {
        return sp.param("cust", c.customerCode()).param("hwt", c.hwType()).param("mt", c.machineType()).param("mo", c.model())
                .param("sn", c.serialNumber()).param("inst", c.installDate()).param("ver", c.version()).param("pef", c.pefCumulative())
                .param("tape", c.tapeUnit()).param("proc", c.processorType()).param("sws", c.swMaintenanceExpiry()).param("hws", c.hwMaintenanceExpiry())
                .param("swe", c.swEffectiveExpiry()).param("hwe", c.hwEffectiveExpiry()).param("auto", c.autoRenewal()).param("swg", c.swGroup())
                .param("sst", c.swServiceType()).param("hst", c.hwServiceType()).param("notes", c.notes()).param("lic", c.licenseCount())
                .param("di", c.sourceInsertedOn()).param("dm", c.sourceModifiedOn());
    }

    /** scrive una scheda HW; restituisce {esito, motivo} */
    private Object[] process(Card c) {
        if (c.customerCode().isEmpty()) {
            return new Object[]{Outcome.SKIPPED, "codice cliente (CONTSH) vuoto"};
        }
        if (c.machineType().isEmpty() && c.hwType().isEmpty()) {
            return new Object[]{Outcome.SKIPPED, "tipo hardware (TPMASH) e modello (MODESH) vuoti"};
        }
        String key = key(c);
        Card cur = jdbc.sql("select customer_code, hw_type, machine_type, model, serial_number, install_date, version, pef_cumulative, tape_unit, "
                + "processor_type, sw_maintenance_expiry, hw_maintenance_expiry, sw_effective_expiry, hw_effective_expiry, auto_renewal, sw_group, "
                + "sw_service_type, hw_service_type, notes, license_count, source_inserted_on, source_modified_on from hw_card where source_key = :k")
                .param("k", key).query(Card.class).optional().orElse(null);
        if (cur == null) {
            bind(jdbc.sql("""
                    insert into hw_card (source_key, customer_code, hw_type, machine_type, model, serial_number, install_date, version, pef_cumulative,
                        tape_unit, processor_type, sw_maintenance_expiry, hw_maintenance_expiry, sw_effective_expiry, hw_effective_expiry, auto_renewal,
                        sw_group, sw_service_type, hw_service_type, notes, license_count, source_inserted_on, source_modified_on)
                    values (:key, :cust, :hwt, :mt, :mo, :sn, :inst, :ver, :pef, :tape, :proc, :sws, :hws, :swe, :hwe, :auto, :swg, :sst, :hst, :notes,
                        :lic, :di, :dm)""").param("key", key), c).update();
            return new Object[]{Outcome.INSERTED, ""};
        }
        if (cur.equals(c)) {
            return new Object[]{Outcome.UNCHANGED, ""};
        }
        bind(jdbc.sql("""
                update hw_card set customer_code = :cust, hw_type = :hwt, machine_type = :mt, model = :mo, serial_number = :sn, install_date = :inst,
                    version = :ver, pef_cumulative = :pef, tape_unit = :tape, processor_type = :proc, sw_maintenance_expiry = :sws,
                    hw_maintenance_expiry = :hws, sw_effective_expiry = :swe, hw_effective_expiry = :hwe, auto_renewal = :auto, sw_group = :swg,
                    sw_service_type = :sst, hw_service_type = :hst, notes = :notes, license_count = :lic, source_inserted_on = :di,
                    source_modified_on = :dm, updated_at = now()
                where source_key = :key""").param("key", key), c).update();
        return new Object[]{Outcome.UPDATED, ""};
    }
}
