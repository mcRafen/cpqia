package it.cpqia.db;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le migrazioni devono essere V1..Vn consecutive, senza buchi ne' duplicati: cosi' ogni versione precedente si aggiorna a quella nuova. */
class MigrationsTest {

    @Test
    void migrationsAreContiguous() throws Exception {
        URL dir = getClass().getClassLoader().getResource("db/migration");
        assertTrue(dir != null, "cartella db/migration mancante");
        File[] files = new File(dir.toURI()).listFiles((d, n) -> n.endsWith(".sql"));
        assertTrue(files != null && files.length > 0);
        Pattern p = Pattern.compile("^V(\\d+)__[A-Za-z0-9_]+\\.sql$");
        List<Integer> versions = new ArrayList<>();
        for (File f : files) {
            Matcher m = p.matcher(f.getName());
            assertTrue(m.matches(), "nome non valido: " + f.getName());
            versions.add(Integer.parseInt(m.group(1)));
        }
        versions.sort(Integer::compare);
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(i + 1, versions.get(i), "migrazioni non consecutive: atteso V" + (i + 1));
        }
        assertFalse(versions.isEmpty());
    }
}
