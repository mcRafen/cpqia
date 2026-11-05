package it.cpqia.web;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Documenti Word (template) dei moduli, per esempio i servizi SMITECH: caricamento nella sottocartella «Smitech» della cartella dei
 * modelli Word (cpqia.template-dir; in sviluppo src/main/resources/offer-template) e collegamento al modulo (module.word_file).
 */
@Service
public class ModuleDocumentService {

    private static final String SUBDIR = "Smitech";
    private static final Pattern BAD = Pattern.compile("[^A-Za-z0-9._ \\-()]");
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final JdbcClient jdbc;
    private final String templateDir;

    public ModuleDocumentService(JdbcClient jdbc, @Value("${cpqia.template-dir:}") String templateDir) {
        this.jdbc = jdbc;
        this.templateDir = templateDir == null ? "" : templateDir.trim();
    }

    Path writableDir() {
        if (!templateDir.isEmpty()) {
            return Path.of(templateDir, SUBDIR);
        }
        Path dev = Path.of("src", "main", "resources", "offer-template");
        if (Files.isDirectory(dev)) {
            return dev.resolve(SUBDIR);
        }
        throw new IllegalStateException("Cartella dei modelli Word non scrivibile: imposta cpqia.template-dir (la sottocartella " + SUBDIR + " viene creata automaticamente)");
    }

    static String safeName(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        n = Normalizer.normalize(n, Normalizer.Form.NFC);
        n = BAD.matcher(n).replaceAll("_");
        if (n.startsWith(".")) {
            n = "_" + n.substring(1);
        }
        if (!n.toLowerCase().endsWith(".docx") || n.length() > 150 || n.length() < 6) {
            throw new IllegalArgumentException("Il file deve essere un documento Word .docx (nome fino a 150 caratteri)");
        }
        return n;
    }

    public String currentFile(long moduleId) {
        return jdbc.sql("select word_file from module where id = :id and deleted_at is null").param("id", moduleId).query(String.class).optional().orElse(null);
    }

    /** salva il file caricato e lo collega al modulo; restituisce il nome file */
    public String upload(long moduleId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Seleziona un file Word");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File troppo grande (massimo 25 MB)");
        }
        long exists = jdbc.sql("select count(*) from module where id = :id and deleted_at is null").param("id", moduleId).query(Long.class).single();
        if (exists == 0) {
            throw new IllegalArgumentException("Modulo inesistente");
        }
        String name = safeName(file.getOriginalFilename());
        byte[] data = file.getBytes();
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(data))) {
            d.getBodyElements();
        } catch (Exception e) {
            throw new IllegalArgumentException("Il file non è un documento Word .docx valido");
        }
        String previous = currentFile(moduleId);
        Path dir = writableDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir.normalize())) {
            throw new IllegalArgumentException("Nome file non valido");
        }
        Path tmp = Files.createTempFile(dir, "upload", ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        jdbc.sql("update module set word_file = :f where id = :id").param("f", name).param("id", moduleId).update();
        if (previous != null && !previous.equals(name)) {
            deleteIfUnused(previous, dir);
        }
        return name;
    }

    private void deleteIfUnused(String fileName, Path dir) {
        long uses = jdbc.sql("select count(*) from module where word_file = :f").param("f", fileName).query(Long.class).single();
        if (uses > 0) {
            return;
        }
        try {
            Path old = dir.resolve(fileName).normalize();
            if (old.startsWith(dir.normalize())) {
                Files.deleteIfExists(old);
            }
        } catch (IOException e) {
            // il file resta sul disco; il collegamento e' comunque aggiornato
        }
    }

    /** contenuto del documento collegato (cartella scrivibile, poi classpath offer-template/Smitech) */
    public Optional<InputStream> open(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank() || !fileName.equals(safeName(fileName))) {
            return Optional.empty();
        }
        try {
            Path p = writableDir().resolve(fileName);
            if (Files.isRegularFile(p)) {
                return Optional.of(Files.newInputStream(p));
            }
        } catch (IllegalStateException e) {
            // nessuna cartella scrivibile: si prova il classpath
        }
        ClassPathResource r = new ClassPathResource("offer-template/" + SUBDIR + "/" + fileName);
        return r.exists() ? Optional.of(r.getInputStream()) : Optional.empty();
    }
}
