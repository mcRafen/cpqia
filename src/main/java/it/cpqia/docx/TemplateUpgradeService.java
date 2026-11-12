package it.cpqia.docx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Aggiornamento dei modelli Word all'avvio del WAR. I modelli sono nel WAR (offer-template/, anche Power e Smitech); se l'installazione usa una
 * cartella esterna (cpqia.template-dir) i file li' presenti avrebbero la precedenza su quelli del nuovo WAR e le novita' non si vedrebbero.
 * Per ogni modello del WAR:
 * <ul>
 *   <li>assente nella cartella: viene copiato;</li>
 *   <li>identico: nulla;</li>
 *   <li>diverso ma uguale all'ultima versione distribuita (non modificato dall'utente): sostituito, con copia di sicurezza in _backup/;</li>
 *   <li>diverso e modificato dall'utente (o caricato dalle schede Power / moduli): resta com'e'; la nuova versione e' salvata accanto come «.nuova-versione»;</li>
 *   <li>diverso e senza storico (prima installazione di questa funzione): copia di sicurezza in _backup/ e sostituzione.</li>
 * </ul>
 * I file presenti solo nella cartella (caricati dagli utenti) non vengono mai toccati.
 */
@Component
public class TemplateUpgradeService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateUpgradeService.class);
    private static final String ROOT = "offer-template/";

    private final JdbcClient jdbc;
    private final String templateDir;

    public TemplateUpgradeService(JdbcClient jdbc, @Value("${cpqia.template-dir:}") String templateDir) {
        this.jdbc = jdbc;
        this.templateDir = templateDir == null ? "" : templateDir.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (templateDir.isEmpty()) {
            return;                                  // senza cartella esterna si usano direttamente i modelli del WAR
        }
        try {
            sync();
        } catch (Exception e) {
            log.warn("CPQIA modelli Word: aggiornamento della cartella {} non riuscito: {}", templateDir, e.toString());
        }
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    void sync() throws Exception {
        Path dir = Path.of(templateDir);
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        int copied = 0, updated = 0, kept = 0;
        Resource[] res = new PathMatchingResourcePatternResolver().getResources("classpath*:" + ROOT + "**/*.docx");
        for (Resource r : res) {
            String url = r.getURL().toString();
            String rel = url.substring(url.lastIndexOf(ROOT) + ROOT.length());
            rel = java.net.URLDecoder.decode(rel.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            String name = rel.substring(rel.lastIndexOf('/') + 1);
            if (name.startsWith("~$") || name.toLowerCase().startsWith("old_") || rel.startsWith("_backup/")) {          // file di lavoro Word e vecchie versioni «old_*» non si distribuiscono
                continue;
            }
            byte[] data;
            try (InputStream in = r.getInputStream()) {
                data = in.readAllBytes();
            }
            String warHash = sha(data);
            Path target = dir.resolve(rel).normalize();
            if (!target.startsWith(dir.normalize())) {
                continue;
            }
            String shipped = jdbc.sql("select sha256 from template_shipped where path = :p").param("p", rel).query(String.class).optional().orElse(null);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.write(target, data);
                copied++;
            } else {
                String current = sha(Files.readAllBytes(target));
                if (current.equals(warHash)) {
                    // gia' aggiornato
                } else if (shipped == null || shipped.equals(current)) {
                    backup(dir, target, rel, stamp);                 // non modificato dall'utente (o senza storico): si sostituisce
                    Files.write(target, data);
                    updated++;
                    log.info("CPQIA modelli Word: aggiornato {}", rel);
                } else {
                    Path next = target.resolveSibling(name + ".nuova-versione");           // personalizzato: resta, la nuova versione e' accanto
                    Files.write(next, data);
                    kept++;
                    log.warn("CPQIA modelli Word: {} e' stato modificato e non viene sostituito; la versione del WAR e' in {}", rel, next);
                }
            }
            jdbc.sql("insert into template_shipped (path, sha256, shipped_at) values (:p, :h, now()) "
                    + "on conflict (path) do update set sha256 = :h, shipped_at = now()").param("p", rel).param("h", warHash).update();
        }
        log.info("CPQIA modelli Word ({}): {} copiati, {} aggiornati, {} personalizzati mantenuti", dir, copied, updated, kept);
    }

    private static void backup(Path dir, Path target, String rel, String stamp) throws IOException {
        Path b = dir.resolve("_backup").resolve(stamp).resolve(rel).normalize();
        Files.createDirectories(b.getParent());
        Files.copy(target, b, StandardCopyOption.REPLACE_EXISTING);
    }
}
