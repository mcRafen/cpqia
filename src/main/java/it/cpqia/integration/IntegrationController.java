package it.cpqia.integration;

import it.cpqia.integration.IbmiService.Settings;
import it.cpqia.security.CurrentUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/** Configurazione delle integrazioni (solo administrator): IBM Power System / IBM i via JDBC. */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationController {

    /** dati del modulo; password vuota = invariata */
    public record IbmiForm(String host, String user, String password, String library, Boolean secure) {
        Settings toSettings() {
            return new Settings(host == null ? "" : host, user == null ? "" : user, password, library, Boolean.TRUE.equals(secure));
        }
    }

    /** pianificazione e sorgente dell'agente di sincronizzazione clienti */
    public record SyncForm(Boolean enabled, String mode, Integer intervalMin, String dailyTime, String sourceLibrary, String sourceFile, String lastSyncDate) {
    }

    private final IbmiService ibmi;
    private final CurrentUserService current;
    private final AccountSyncService sync;
    private final AgentSyncService agents;
    private final HwCardSyncService hwCards;
    private final it.cpqia.geo.GeoAgentService geoAgent;

    public IntegrationController(IbmiService ibmi, CurrentUserService current, AccountSyncService sync, AgentSyncService agents,
                                 HwCardSyncService hwCards, it.cpqia.geo.GeoAgentService geoAgent) {
        this.hwCards = hwCards;
        this.geoAgent = geoAgent;
        this.ibmi = ibmi;
        this.current = current;
        this.sync = sync;
        this.agents = agents;
    }

    @GetMapping("/admin/integrations")
    public String page(Model m) {
        m.addAttribute("active", "integrations");
        m.addAttribute("title", "Integrazioni");
        m.addAttribute("ibmi", ibmi.view());
        m.addAttribute("sync", sync.config());
        m.addAttribute("nextRun", sync.nextRunText());
        m.addAttribute("agSync", agents.config());
        m.addAttribute("agNextRun", agents.nextRunText());
        m.addAttribute("hwSync", hwCards.config());
        m.addAttribute("hwNextRun", hwCards.nextRunText());
        m.addAttribute("geoCfg", geoAgent.config());
        m.addAttribute("geoNextRun", geoAgent.nextRunText());
        return "admin/integrations";
    }

    @PostMapping("/admin/integrations/ibmi/save")
    @ResponseBody
    public Map<String, Object> save(@RequestBody IbmiForm f, Authentication auth) {
        ibmi.save(f.toSettings(), current.get(auth).id());
        return Map.of("ok", true, "message", "Configurazione IBM Power System salvata");
    }

    /** data dell'ultima importazione dal modulo: non indicata = invariata, vuota = nessuna (importazione totale) */
    private static java.time.LocalDate sinceDate(String s, java.time.LocalDate old) {
        if (s == null) {
            return old;
        }
        if (s.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Data ultima importazione non valida");
        }
    }

    @PostMapping("/admin/integrations/sync/save")
    @ResponseBody
    public Map<String, Object> saveSync(@RequestBody SyncForm f) {
        AccountSyncService.Config old = sync.config();
        sync.saveConfig(new AccountSyncService.Config(Boolean.TRUE.equals(f.enabled()), f.mode() == null ? "DAILY" : f.mode(),
                f.intervalMin() == null ? 60 : f.intervalMin(), f.dailyTime() == null ? "02:00" : f.dailyTime(),
                f.sourceLibrary() == null ? "" : f.sourceLibrary(), f.sourceFile() == null ? "" : f.sourceFile(), sinceDate(f.lastSyncDate(), old.lastSyncDate()), old.lastRunAt()));
        return Map.of("ok", true, "message", "Pianificazione salvata", "nextRun", sync.nextRunText());
    }

    /** avvia subito una sincronizzazione in background (l'esito si legge da /sync/status) */
    @PostMapping("/admin/integrations/sync/run")
    @ResponseBody
    public Map<String, Object> runSync() {
        if (sync.running()) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        Thread t = new Thread(() -> {
            try {
                sync.run("MANUAL");
            } catch (RuntimeException e) {
                // già registrato nello storico
            }
        }, "ibmi-sync-manual");
        t.setDaemon(true);
        t.start();
        return Map.of("ok", true, "message", "Sincronizzazione avviata");
    }

    @GetMapping("/admin/integrations/sync/status")
    @ResponseBody
    public Map<String, Object> syncStatus() {
        AccountSyncService.Config c = sync.config();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("running", sync.running());
        out.put("lastSyncDate", c.lastSyncDate() == null ? null : c.lastSyncDate().toString());
        out.put("lastRunAt", c.lastRunAt() == null ? null : c.lastRunAt().toString());
        out.put("nextRun", sync.nextRunText());
        out.put("runs", sync.lastRuns(10));
        return out;
    }

    @GetMapping("/admin/integrations/sync/run/{id}/details")
    @ResponseBody
    public Map<String, Object> runDetails(@org.springframework.web.bind.annotation.PathVariable long id) {
        return Map.of("details", sync.runDetails(id));
    }

    @PostMapping("/admin/integrations/ibmi/test")
    @ResponseBody
    public Map<String, Object> test(@RequestBody IbmiForm f) {
        IbmiService.TestResult r = ibmi.test(f.toSettings());
        return Map.of("ok", r.ok(), "message", r.message(), "millis", r.millis());
    }

    // ------------------------------------------------------------------ Sync Agenti
    @PostMapping("/admin/integrations/agents/save")
    @ResponseBody
    public Map<String, Object> saveAgents(@RequestBody SyncForm f) {
        AgentSyncService.Config old = agents.config();
        agents.saveConfig(new AgentSyncService.Config(Boolean.TRUE.equals(f.enabled()), f.mode() == null ? "DAILY" : f.mode(),
                f.intervalMin() == null ? 60 : f.intervalMin(), f.dailyTime() == null ? "02:30" : f.dailyTime(),
                f.sourceLibrary() == null ? "" : f.sourceLibrary(), f.sourceFile() == null ? "" : f.sourceFile(), old.lastRunAt(), old.lastSyncDate()));
        return Map.of("ok", true, "message", "Pianificazione salvata", "nextRun", agents.nextRunText());
    }

    @PostMapping("/admin/integrations/agents/run")
    @ResponseBody
    public Map<String, Object> runAgents() {
        if (agents.running()) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        Thread t = new Thread(() -> {
            try {
                agents.run("MANUAL");
            } catch (RuntimeException e) {
                // già registrato nello storico
            }
        }, "ibmi-agents-manual");
        t.setDaemon(true);
        t.start();
        return Map.of("ok", true, "message", "Sincronizzazione avviata");
    }

    @GetMapping("/admin/integrations/agents/status")
    @ResponseBody
    public Map<String, Object> agentsStatus() {
        AgentSyncService.Config c = agents.config();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("running", agents.running());
        out.put("lastSyncDate", c.lastSyncDate() == null ? null : c.lastSyncDate().toString());
        out.put("lastRunAt", c.lastRunAt() == null ? null : c.lastRunAt().toString());
        out.put("nextRun", agents.nextRunText());
        out.put("runs", agents.lastRuns(10));
        return out;
    }

    @GetMapping("/admin/integrations/agents/run/{id}/details")
    @ResponseBody
    public Map<String, Object> agentsRunDetails(@org.springframework.web.bind.annotation.PathVariable long id) {
        return Map.of("details", agents.runDetails(id));
    }

    // ------------------------------------------------------------------ Sync Schede HW
    @PostMapping("/admin/integrations/hw/save")
    @ResponseBody
    public Map<String, Object> saveHw(@RequestBody SyncForm f) {
        HwCardSyncService.Config old = hwCards.config();
        hwCards.saveConfig(new HwCardSyncService.Config(Boolean.TRUE.equals(f.enabled()), f.mode() == null ? "DAILY" : f.mode(),
                f.intervalMin() == null ? 60 : f.intervalMin(), f.dailyTime() == null ? "03:30" : f.dailyTime(),
                f.sourceLibrary() == null ? "" : f.sourceLibrary(), f.sourceFile() == null ? "" : f.sourceFile(), sinceDate(f.lastSyncDate(), old.lastSyncDate()), old.lastRunAt()));
        return Map.of("ok", true, "message", "Pianificazione salvata", "nextRun", hwCards.nextRunText());
    }

    @PostMapping("/admin/integrations/hw/run")
    @ResponseBody
    public Map<String, Object> runHw() {
        if (hwCards.running()) {
            throw new IllegalArgumentException("Una sincronizzazione è già in corso");
        }
        Thread t = new Thread(() -> {
            try {
                hwCards.run("MANUAL");
            } catch (RuntimeException e) {
                // già registrato nello storico
            }
        }, "ibmi-hw-manual");
        t.setDaemon(true);
        t.start();
        return Map.of("ok", true, "message", "Sincronizzazione avviata");
    }

    @GetMapping("/admin/integrations/hw/status")
    @ResponseBody
    public Map<String, Object> hwStatus() {
        HwCardSyncService.Config c = hwCards.config();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("running", hwCards.running());
        out.put("lastSyncDate", c.lastSyncDate() == null ? null : c.lastSyncDate().toString());
        out.put("lastRunAt", c.lastRunAt() == null ? null : c.lastRunAt().toString());
        out.put("nextRun", hwCards.nextRunText());
        out.put("runs", hwCards.lastRuns(10));
        return out;
    }

    @GetMapping("/admin/integrations/hw/run/{id}/details")
    @ResponseBody
    public Map<String, Object> hwRunDetails(@org.springframework.web.bind.annotation.PathVariable long id) {
        return Map.of("details", hwCards.runDetails(id));
    }

    @PostMapping("/admin/integrations/hw/stop")
    @ResponseBody
    public Map<String, Object> stopHw() {
        return Map.of("ok", true, "message", hwCards.stop());
    }

    // ------------------------------------------------------------------ agente Geo
    public record GeoForm(Boolean enabled, String mode, Integer intervalMin, String dailyTime) {
    }

    @PostMapping("/admin/integrations/geo/save")
    @ResponseBody
    public Map<String, Object> saveGeo(@RequestBody GeoForm f) {
        it.cpqia.geo.GeoAgentService.Config old = geoAgent.config();
        geoAgent.saveConfig(new it.cpqia.geo.GeoAgentService.Config(Boolean.TRUE.equals(f.enabled()), f.mode() == null ? "DAILY" : f.mode(),
                f.intervalMin() == null ? 60 : f.intervalMin(), f.dailyTime() == null ? "03:00" : f.dailyTime(), old.lastRunAt()));
        return Map.of("ok", true, "message", "Pianificazione salvata", "nextRun", geoAgent.nextRunText());
    }

    @PostMapping("/admin/integrations/geo/run")
    @ResponseBody
    public Map<String, Object> runGeo() {
        if (geoAgent.running()) {
            throw new IllegalArgumentException("L'agente Geo è già in esecuzione");
        }
        Thread t = new Thread(() -> {
            try {
                geoAgent.run("MANUAL");
            } catch (RuntimeException e) {
                // già registrato nello storico
            }
        }, "geo-agent-manual");
        t.setDaemon(true);
        t.start();
        return Map.of("ok", true, "message", "Agente Geo avviato");
    }

    @GetMapping("/admin/integrations/geo/status")
    @ResponseBody
    public Map<String, Object> geoStatus() {
        it.cpqia.geo.GeoAgentService.Config c = geoAgent.config();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("running", geoAgent.running());
        out.put("lastRunAt", c.lastRunAt() == null ? null : c.lastRunAt().toString());
        out.put("nextRun", geoAgent.nextRunText());
        out.put("runs", geoAgent.lastRuns(10));
        return out;
    }

    @GetMapping("/admin/integrations/geo/run/{id}/details")
    @ResponseBody
    public Map<String, Object> geoRunDetails(@org.springframework.web.bind.annotation.PathVariable long id) {
        return Map.of("details", geoAgent.runDetails(id));
    }

    // ------------------------------------------------------------------ «Ferma» sui job in corso
    @PostMapping("/admin/integrations/sync/stop")
    @ResponseBody
    public Map<String, Object> stopSync() {
        return Map.of("ok", true, "message", sync.stop());
    }

    @PostMapping("/admin/integrations/agents/stop")
    @ResponseBody
    public Map<String, Object> stopAgents() {
        return Map.of("ok", true, "message", agents.stop());
    }

    @PostMapping("/admin/integrations/geo/stop")
    @ResponseBody
    public Map<String, Object> stopGeo() {
        return Map.of("ok", true, "message", geoAgent.stop());
    }
}
