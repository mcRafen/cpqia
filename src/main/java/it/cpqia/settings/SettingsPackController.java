package it.cpqia.settings;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Impostazioni > Aggiornamento: esportazione del pacchetto delle impostazioni da includere nel WAR (solo administrator). */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class SettingsPackController {

    private final SettingsPackService service;

    public SettingsPackController(SettingsPackService service) {
        this.service = service;
    }

    @GetMapping("/admin/settings-pack")
    public String page(Model m) throws IOException {
        m.addAttribute("active", "settings-pack");
        m.addAttribute("title", "Aggiornamento impostazioni");
        m.addAttribute("tables", SettingsPackService.TABLES);
        m.addAttribute("last", service.lastApplied().orElse(null));
        Optional<String> packed = service.packedJson();
        m.addAttribute("packed", packed.isPresent());
        m.addAttribute("packedCounts", packed.isPresent() ? service.counts(packed.get()) : Map.of());
        return "admin/settings-pack";
    }

    /** genera il pacchetto dal database corrente e, in sviluppo, lo scrive in src/main/resources/settings/settings-pack.json */
    @PostMapping("/admin/settings-pack/export")
    @ResponseBody
    public Map<String, Object> export() throws IOException {
        String json = service.export();
        Optional<Path> written = service.writeToProject(json);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("counts", service.counts(json));
        out.put("path", written.map(Path::toString).orElse(null));
        out.put("message", written.isPresent() ? "Pacchetto scritto nel progetto: ricompila il WAR" : "Pacchetto generato: scaricalo e copialo in src/main/resources/settings/settings-pack.json");
        return out;
    }

    @GetMapping("/admin/settings-pack/download")
    public ResponseEntity<byte[]> download() throws IOException {
        byte[] data = service.export().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("settings-pack.json", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_JSON).body(data);
    }
}
