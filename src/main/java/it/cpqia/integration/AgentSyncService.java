package it.cpqia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Sync Agenti: legge da IBM Power System (file SMTAB01L, libreria INF90DAT) i record con XTIPTB = '01AG', scartando quelli con «**»
 * alle posizioni 9 e 10 di XDATTB; XCODTB e' il codice dell'agente, la descrizione e' la sottostringa di XDATTB di 20 caratteri a partire dalla posizione 9.
 * Si importano solo i codici interamente numerici che iniziano per 3. La lettura e' completa a ogni esecuzione: gli agenti si inseriscono o si aggiornano per codice.
 */
@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_$#@]{1,10}$");
    private static final Pattern NUMERIC_3 = Pattern.compile("^3\\d*$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public record Config(boolean enabled, String mode, int intervalMin, String dailyTime, String sourceLibrary, String sourceFile,
                         LocalDateTime lastRunAt, java.time.LocalDate lastSyncDate) {
    }

    public record Run(Long id, LocalDateTime startedAt, LocalDateTime finishedAt, String triggerType, Boolean ok, int readCount,
                      int inserted, int updated, int unchanged, int skipped, String message) {
    }

    private final JdbcClient jdbc;
    private final IbmiService ibmi;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopReq = new AtomicBoolean(false);

    public AgentSyncService(JdbcClient jdbc, IbmiService ibmi) {
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
        int n = jdbc.sql("update %s set finished_at = now(), ok = false, message = 'Interrotta (esecuzione non terminata)' where finished_at is null".formatted("agent_sync_run")).update();
        if (n > 0) {
            jdbc.sql("update agent_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        }
        return n > 0 ? "Esecuzione chiusa come interrotta" : "Nessun job in corso";
    }

    // ------------------------------------------------------------------ configurazione
    public Config config() {
        return jdbc.sql("select enabled, mode, interval_min, daily_time, source_library, source_file, last_run_at, last_sync_date from agent_sync_config where id = 1")
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
        jdbc.sql("update agent_sync_config set enabled = :e, mode = :m, interval_min = :i, daily_time = :t, source_library = :l, source_file = :f where id = 1")
                .param("e", c.enabled()).param("m", c.mode()).param("i", c.intervalMin()).param("t", c.dailyTime())
                .param("l", c.sourceLibrary().trim().toUpperCase()).param("f", c.sourceFile().trim().toUpperCase()).update();
    }

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
                + "from agent_sync_run order by id desc limit :n").param("n", n).query(Run.class).list();
    }

    public String runDetails(long id) {
        return jdbc.sql("select details from agent_sync_run where id = :id").param("id", id).query(String.class).optional().orElse("");
    }

    // ------------------------------------------------------------------ esecuzione
    private static String txt(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').trim();
    }

    /** descrizione: 20 caratteri di XDATTB a partire dalla posizione 9 (base 1), cioe' posizioni 9-28 */
    static String description(String xdattb) {
        if (xdattb == null || xdattb.length() < 9) {
            return "";
        }
        return txt(xdattb.substring(8, Math.min(28, xdattb.length())));
    }

    public Run run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        stopReq.set(false);
        long runId = jdbc.sql("insert into agent_sync_run (trigger_type) values (:t) returning id").param("t", triggerType).query(Long.class).single();
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
            String sql = "select XCODTB, XDATTB from " + cfg.sourceLibrary() + "." + cfg.sourceFile() + " where XTIPTB = '01AG'";
            List<String[]> agents = new ArrayList<>();
            try (Connection c = ibmi.connect(st); Statement stm = c.createStatement(); ResultSet rs = stm.executeQuery(sql)) {
                while (rs.next()) {
                    if (stopReq.get()) {
                        throw new JobStopped();
                    }
                    read++;
                    String code = txt(rs.getString(1));
                    String dat = rs.getString(2) == null ? "" : rs.getString(2);
                    if (dat.length() >= 10 && dat.startsWith("**", 8)) {   // record annullati: «**» alle posizioni 9 e 10 di XDATTB
                        skip++;
                        continue;
                    }
                    if (code.isEmpty()) {
                        skip++;
                        if (problems.size() < 200) {
                            problems.add("(codice vuoto) – " + dat.trim());
                        }
                        continue;
                    }
                    if (!NUMERIC_3.matcher(code).matches()) {            // si importano solo i codici interamente numerici che iniziano per 3
                        skip++;
                        continue;
                    }
                    agents.add(new String[]{code.length() > 20 ? code.substring(0, 20) : code, description(dat)});
                }
            }
            for (String[] a : agents) {
                if (stopReq.get()) {
                    throw new JobStopped();
                }
                String cur = jdbc.sql("select description from agent where code = :c").param("c", a[0]).query(String.class).optional().orElse(null);
                if (cur == null) {
                    jdbc.sql("insert into agent (code, description) values (:c, :d)").param("c", a[0]).param("d", a[1]).update();
                    ins++;
                } else if (!cur.equals(a[1])) {
                    jdbc.sql("update agent set description = :d where code = :c").param("c", a[0]).param("d", a[1]).update();
                    upd++;
                } else {
                    unch++;
                }
            }
            jdbc.sql("update agent_sync_config set last_sync_date = current_date where id = 1").update();
            ok = true;
            message = "Letti " + read + " record: " + ins + " nuovi, " + upd + " aggiornati, " + unch + " invariati, " + skip + " scartati";
        } catch (JobStopped e) {
            message = "Interrotta dall'utente: " + read + " record letti, " + ins + " nuovi, " + upd + " aggiornati, " + unch + " invariati, " + skip + " scartati";
        } catch (Exception e) {
            Throwable c = e.getCause() != null && e.getMessage() == null ? e.getCause() : e;
            message = "Sincronizzazione non riuscita: " + c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
            log.warn(message, e);
        } finally {
            running.set(false);
        }
        jdbc.sql("update agent_sync_run set finished_at = now(), ok = :ok, read_count = :r, inserted = :i, updated = :u, unchanged = :n, skipped = :s, message = :m, details = :d where id = :id")
                .param("ok", ok).param("r", read).param("i", ins).param("u", upd).param("n", unch).param("s", skip)
                .param("m", message.length() > 290 ? message.substring(0, 290) : message).param("d", String.join("\n", problems)).param("id", runId).update();
        // data, ora e secondo di fine esecuzione (job terminato, fermato a mano o chiuso con errore)
        jdbc.sql("update agent_sync_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        return lastRuns(1).get(0);
    }
}
