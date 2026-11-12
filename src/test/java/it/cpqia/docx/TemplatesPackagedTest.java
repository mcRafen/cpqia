package it.cpqia.docx;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tutti i documenti Word di offer-template (anche Power e Smitech) devono essere nel classpath, e quindi nel WAR. */
class TemplatesPackagedTest {

    private static final List<String> REQUIRED = List.of("offer-template.docx", "licenze.docx", "attivita.docx", "attivita-light.docx", "attivita-appendice.docx", "attivita-conversioni.docx", "attivita-personalizzazioni.docx", "attivita-followup.docx", "licenze-elenco-semplice.docx", "moduli.docx", "manutenzione.docx",
            "servizi-accessori.docx", "riepilogo-finale.docx", "riepilogo-finale-servizi-accessori.docx", "riepilogo-finale-servizi-smitech.docx", "varie.docx", "pagamenti.docx", "condizioni-gdpr.docx",
            "Power/tabelle-power-hardware.docx", "Power/tabelle-power-software.docx", "Power/tabelle-power-manutenzione.docx", "Power/tabelle-power-chiavi di licenza.docx",
            "Power/tabelle-power-solution edition.docx", "Power/tabelle-power-migrazione programmi.docx", "Power/tabelle-power-transfer.docx",
            "Power/tabelle-power-caratteristiche nvme.docx", "Power/tabelle-power-smaltimento.docx",
            "Smitech/SMIDRI.docx", "Smitech/SMISMP.docx", "Smitech/SMISMP-TOTALI-CON-SMIDRI.docx", "Smitech/SMINGF.docx", "Smitech/SMISEP.docx", "Smitech/SMIACR.docx", "Smitech/SMISMP.docx", "Smitech/SMIMGT.docx", "Smitech/SMICAI.docx");

    @Test
    void requiredTemplatesAreOnTheClasspath() throws Exception {
        for (String f : REQUIRED) {
            ClassPathResource r = new ClassPathResource("offer-template/" + f);
            assertTrue(r.exists(), "modello Word mancante: " + f);
            try (InputStream in = r.getInputStream()) {
                assertTrue(in.readAllBytes().length > 1000, "modello Word vuoto: " + f);
            }
        }
    }

    @Test
    void everyTemplateInTheFolderIsPackagedExceptWordLockFiles() throws Exception {
        Resource[] all = new PathMatchingResourcePatternResolver().getResources("classpath*:offer-template/**/*.docx");
        assertTrue(all.length >= REQUIRED.size(), "modelli trovati: " + all.length);
    }
}
