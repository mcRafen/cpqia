package it.cpqia.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Agente Geo: calcola latitudine e longitudine di account e sedi Sanmarco a partire dall'indirizzo.
 * <ul>
 *   <li>Automatico (AUTO): ogni 15 secondi elabora gli indirizzi appena inseriti o modificati (geo_status = PENDING, impostato dal database
 *       a ogni inserimento / modifica dell'indirizzo, quindi anche per le importazioni da IBM Power System).</li>
 *   <li>Manuale o pianificato: elabora anche gli account presenti prima dell'agente (TODO). Gli indirizzi non trovati (NOT_FOUND) non si riprovano
 *       finche' l'indirizzo non viene modificato: la geolocalizzazione si fa solo all'inserimento / alla modifica.</li>
 * </ul>
 * La distanza usa solo le coordinate gia' calcolate (vedi {@link DistanceService}).
 */
@Service
public class GeoAgentService {

    private static final Logger log = LoggerFactory.getLogger(GeoAgentService.class);
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final int AUTO_BATCH = 200;

    public record Config(boolean enabled, String mode, int intervalMin, String dailyTime, LocalDateTime lastRunAt) {
    }

    public record Run(Long id, LocalDateTime startedAt, LocalDateTime finishedAt, String triggerType, Boolean ok, int readCount,
                      int inserted, int updated, int unchanged, int skipped, String message) {
    }

    private record Addr(String table, long id, String label, String street, String number, String cap, String city, String province) {
    }

    private final JdbcClient jdbc;
    private final GeoService geo;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopReq = new AtomicBoolean(false);

    public GeoAgentService(JdbcClient jdbc, GeoService geo) {
        this.jdbc = jdbc;
        this.geo = geo;
    }

    public boolean running() {
        return running.get();
    }

    /** «Ferma»: arresta l'esecuzione in corso (al termine dell'indirizzo in elaborazione) oppure chiude le righe rimaste «in corso» dopo un riavvio */
    public String stop() {
        if (running.get()) {
            stopReq.set(true);
            return "Arresto richiesto: l'agente si ferma al termine dell'indirizzo in elaborazione";
        }
        int n = jdbc.sql("update geo_agent_run set finished_at = now(), ok = false, message = 'Interrotta (esecuzione non terminata)' where finished_at is null").update();
        if (n > 0) {
            jdbc.sql("update geo_agent_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        }
        return n > 0 ? "Esecuzione chiusa come interrotta" : "Nessun job in corso";
    }

    // ------------------------------------------------------------------ configurazione
    public Config config() {
        return jdbc.sql("select enabled, mode, interval_min, daily_time, last_run_at from geo_agent_config where id = 1").query(Config.class).single();
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
        jdbc.sql("update geo_agent_config set enabled = :e, mode = :m, interval_min = :i, daily_time = :t where id = 1")
                .param("e", c.enabled()).param("m", c.mode()).param("i", c.intervalMin()).param("t", c.dailyTime()).update();
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
                + "from geo_agent_run order by id desc limit :n").param("n", n).query(Run.class).list();
    }

    public String runDetails(long id) {
        return jdbc.sql("select details from geo_agent_run where id = :id").param("id", id).query(String.class).optional().orElse("");
    }

    // ------------------------------------------------------------------ esecuzione
    /** ogni 15 secondi: geolocalizza gli indirizzi appena inseriti o modificati */
    @Scheduled(fixedDelay = 15_000, initialDelay = 45_000)
    public void autoTick() {
        try {
            if (!geo.enabled() || running.get()) {
                return;
            }
            long pending = jdbc.sql("select (select count(*) from account where geo_status = 'PENDING' and deleted_at is null) "
                    + "+ (select count(*) from sede where geo_status = 'PENDING' and deleted_at is null)").query(Long.class).single();
            if (pending > 0) {
                run("AUTO");
            }
        } catch (RuntimeException e) {
            log.warn("Geolocalizzazione automatica non eseguita: {}", e.toString());
        }
    }

    private List<Addr> pending(boolean autoOnly) {
        String cond = autoOnly ? "geo_status = 'PENDING'" : "geo_status in ('PENDING', 'TODO')";
        String limit = autoOnly ? " limit " + AUTO_BATCH : "";
        List<Addr> out = new ArrayList<>(jdbc.sql("select id, code as label, street_type || ' ' || street_name as street, street_number as number, postal_code as cap, city, province "
                + "from account where deleted_at is null and " + cond + " order by id" + limit)
                .query((rs, n) -> new Addr("account", rs.getLong("id"), rs.getString("label"), rs.getString("street"), rs.getString("number"), rs.getString("cap"),
                        rs.getString("city"), rs.getString("province"))).list());
        out.addAll(jdbc.sql("select id, name as label, street, street_number as number, postal_code as cap, coalesce(nullif(city, ''), name) as city, province "
                + "from sede where deleted_at is null and " + cond + " order by id" + limit)
                .query((rs, n) -> new Addr("sede", rs.getLong("id"), rs.getString("label"), rs.getString("street"), rs.getString("number"), rs.getString("cap"),
                        rs.getString("city"), rs.getString("province"))).list());
        return out;
    }

    /**
     * Geolocalizza subito un singolo record (pulsante «Geolocalizza» nella scheda di una sede) e salva le coordinate. Solo tabelle ammesse: sede, account.
     */
    public java.util.Map<String, Object> locateNow(String table, long id) {
        if (!"sede".equals(table) && !"account".equals(table)) {
            throw new IllegalArgumentException("Tabella non valida");
        }
        if (!geo.enabled()) {
            throw new IllegalArgumentException("Geolocalizzazione disattivata (cpqia.geo.enabled=false)");
        }
        String sql = "account".equals(table)
                ? "select id, code as label, street_type || ' ' || street_name as street, street_number as number, postal_code as cap, city, province from account where id = :id and deleted_at is null"
                : "select id, name as label, street, street_number as number, postal_code as cap, coalesce(nullif(city, ''), name) as city, province from sede where id = :id and deleted_at is null";
        Addr a = jdbc.sql(sql).param("id", id).query((rs, n) -> new Addr(table, rs.getLong("id"), rs.getString("label"), rs.getString("street"), rs.getString("number"),
                rs.getString("cap"), rs.getString("city"), rs.getString("province"))).optional().orElseThrow(() -> new IllegalArgumentException("Record inesistente"));
        String key = (a.street() + "|" + a.number() + "|" + a.cap() + "|" + a.city() + "|" + a.province()).toLowerCase();
        String city = a.city() == null ? "" : a.city().replaceAll("\\s*\\(.*?\\)", "").trim();
        GeoService.Geocoded g = geo.geocodeDetailed(a.street(), a.number(), a.cap(), city, a.province());
        if (g.point().isEmpty()) {
            if (g.error() != null) {
                throw new IllegalArgumentException("Servizio di geocodifica non raggiungibile: " + g.error());
            }
            jdbc.sql("update " + table + " set geo_status = 'NOT_FOUND', geo_at = now() where id = :id").param("id", id).update();
            throw new IllegalArgumentException("Indirizzo non trovato sulla mappa: controlla via, civico, CAP e comune");
        }
        BigDecimal lat = BigDecimal.valueOf(g.point().get().lat()).setScale(6, RoundingMode.HALF_UP), lon = BigDecimal.valueOf(g.point().get().lon()).setScale(6, RoundingMode.HALF_UP);
        jdbc.sql("update " + table + " set latitude = :la, longitude = :lo, geo_key = :k, geo_status = 'OK', geo_at = now() where id = :id")
                .param("la", lat).param("lo", lon).param("k", key).param("id", id).update();
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("ok", true);
        out.put("latitude", lat);
        out.put("longitude", lon);
        out.put("message", "Coordinate salvate: " + lat.toPlainString() + ", " + lon.toPlainString());
        return out;
    }

    /** aggiorna i contatori della riga di log durante l'esecuzione (la geocodifica fa circa 1 indirizzo al secondo: un job lungo dura ore) */
    private void progress(long runId, int read, int located, int notFound, int total) {
        try {
            jdbc.sql("update geo_agent_run set read_count = :r, inserted = :i, skipped = :s, message = :m where id = :id")
                    .param("r", read).param("i", located).param("s", notFound).param("m", "In corso: " + read + " di " + total + " indirizzi").param("id", runId).update();
        } catch (RuntimeException e) {
            log.debug("Avanzamento non aggiornato: {}", e.toString());
        }
    }

    /** esegue l'agente; triggerType = AUTO | MANUAL | SCHEDULED. Restituisce null se non c'era nulla da fare (AUTO). */
    public Run run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalArgumentException("L'agente Geo è già in esecuzione");
        }
        stopReq.set(false);
        boolean auto = "AUTO".equals(triggerType);
        Long runId = null;
        int read = 0, located = 0, notFound = 0;
        List<String> problems = new ArrayList<>();
        boolean ok = false;
        String message;
        try {
            if (!geo.enabled()) {
                throw new IllegalStateException("Geolocalizzazione disattivata (cpqia.geo.enabled=false)");
            }
            List<Addr> todo = pending(auto);
            if (auto && todo.isEmpty()) {
                return null;
            }
            runId = jdbc.sql("insert into geo_agent_run (trigger_type) values (:t) returning id").param("t", triggerType).query(Long.class).single();
            boolean stopped = false;
            boolean userStop = false;
            for (Addr a : todo) {
                if (stopReq.get()) {
                    stopped = true;
                    userStop = true;
                    break;
                }
                read++;
                String key = (a.street() + "|" + a.number() + "|" + a.cap() + "|" + a.city() + "|" + a.province()).toLowerCase();
                String city = a.city() == null ? "" : a.city().replaceAll("\\s*\\(.*?\\)", "").trim();      // "Vimercate (MB)" -> "Vimercate"
                GeoService.Geocoded g = geo.geocodeDetailed(a.street(), a.number(), a.cap(), city, a.province());
                if (g.point().isPresent()) {
                    jdbc.sql("update " + a.table() + " set latitude = :la, longitude = :lo, geo_key = :k, geo_status = 'OK', geo_at = now() where id = :id")
                            .param("la", BigDecimal.valueOf(g.point().get().lat()).setScale(6, RoundingMode.HALF_UP))
                            .param("lo", BigDecimal.valueOf(g.point().get().lon()).setScale(6, RoundingMode.HALF_UP))
                            .param("k", key).param("id", a.id()).update();
                    located++;
                } else if (g.error() != null) {                  // servizio non raggiungibile: si ferma, gli indirizzi restano da elaborare
                    problems.add("Servizio di geocodifica non raggiungibile: " + g.error());
                    stopped = true;
                    break;
                } else {
                    jdbc.sql("update " + a.table() + " set geo_status = 'NOT_FOUND', geo_at = now() where id = :id").param("id", a.id()).update();
                    notFound++;
                    if (problems.size() < 300) {
                        problems.add(("account".equals(a.table()) ? "Account " : "Sede ") + a.label() + ": " + a.street() + " " + a.number() + ", " + a.cap() + " " + a.city() + " (" + a.province() + ")");
                    }
                }
                progress(runId, read, located, notFound, todo.size());       // i contatori si vedono nel log mentre il job e' in corso
            }
            ok = !stopped;
            message = userStop ? "Interrotta dall'utente: " + read + " indirizzi elaborati, " + located + " localizzati, " + notFound + " non trovati"
                    : stopped ? "Interrotto: servizio di geocodifica non raggiungibile (il server deve poter collegarsi a internet, cpqia.geo.nominatim-url)"
                    : "Elaborati " + read + " indirizzi: " + located + " localizzati, " + notFound + " non trovati";
        } catch (Exception e) {
            message = "Geolocalizzazione non riuscita: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            log.warn(message, e);
        } finally {
            running.set(false);
        }
        if (runId == null) {
            runId = jdbc.sql("insert into geo_agent_run (trigger_type) values (:t) returning id").param("t", triggerType).query(Long.class).single();
        }
        jdbc.sql("update geo_agent_run set finished_at = now(), ok = :ok, read_count = :r, inserted = :i, skipped = :s, message = :m, details = :d where id = :id")
                .param("ok", ok).param("r", read).param("i", located).param("s", notFound)
                .param("m", message.length() > 290 ? message.substring(0, 290) : message).param("d", String.join("\n", problems)).param("id", runId).update();
        if (!auto) {
            // data, ora e secondo dell'ultima esecuzione: registrati sempre alla chiusura, anche se il job e' stato fermato o e' terminato con un errore
            jdbc.sql("update geo_agent_config set last_run_at = date_trunc('second', now()) where id = 1").update();
        }
        return lastRuns(1).get(0);
    }
}
