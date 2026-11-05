package it.cpqia.power;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** Caricamento e scarico del documento Word di un modello Power System (permesso: gestione catalogo). */
@RestController
@PreAuthorize("hasAuthority('P_MANAGE_CATALOG')")
public class PowerDocumentController {

    private final PowerDocumentService docs;

    public PowerDocumentController(PowerDocumentService docs) {
        this.docs = docs;
    }

    @PostMapping("/admin/power-models/{id}/document")
    public Map<String, Object> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) throws IOException {
        String name = docs.upload(id, file);
        return Map.of("ok", true, "file", name);
    }

    @GetMapping("/admin/power-models/{id}/document")
    public ResponseEntity<byte[]> download(@PathVariable long id) throws IOException {
        String name = docs.currentFile(id);
        Optional<InputStream> in = docs.open(name);
        if (in.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes;
        try (InputStream s = in.get()) {
            bytes = s.readAllBytes();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }
}
