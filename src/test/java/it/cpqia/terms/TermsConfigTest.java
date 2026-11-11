package it.cpqia.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.terms.TermsModel.PaymentTerms;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TermsConfigTest {

    @Test
    void defaultsAndValidation() throws Exception {
        TermsConfig cfg = new TermsConfig(new ObjectMapper());
        PaymentTerms prospect = cfg.defaultsFor("PROSPECT");
        assertEquals("RIBA30", prospect.payLic());
        assertEquals("USUAL", cfg.defaultsFor("CLIENTE").payFee());
        assertTrue(cfg.validate(prospect, "PROSPECT").isEmpty());
        assertTrue(cfg.validate(cfg.defaultsFor("CLIENTE"), "CLIENTE").isEmpty());

        // "Solito in uso" non e' ammesso per un prospect (ne' come default ne' come scelta)
        assertTrue(cfg.validate(cfg.defaultsFor("CLIENTE"), "PROSPECT").stream().anyMatch(m -> m.contains("opzione non valida")));

        // "Data" richiede la data; "Altro" richiede il testo; a pacchetto RIBA30 non e' ammesso
        PaymentTerms bad = new PaymentTerms("DATE", null, "DATE", null, true, "PACCHETTO", "OTHER", " ", "USUAL", null, "RIBA30", null, "INSTALL", null, "RIBA30", null);
        List<String> errors = cfg.validate(bad, "PROSPECT");
        // 5 errori: data licenze, data canoni, testo "altro" licenze, "solito in uso" canoni (prospect), RIBA30 a pacchetto
        assertEquals(5, errors.size(), errors.toString());
        assertTrue(errors.contains("Fatturazione licenze: indicare la data"));
        assertTrue(errors.contains("Fatturazione canoni: indicare la data"));
        assertTrue(errors.contains("Pagamento licenze: scrivere la modalità"));
        assertTrue(errors.contains("Pagamento canoni: opzione non valida"));
        assertTrue(errors.contains("Pagamento attività sistemistiche: opzione non valida"));
        // per un cliente "solito in uso" e' valido: restano 4 errori
        assertEquals(4, cfg.validate(bad, "CLIENTE").size());

        PaymentTerms ok = new PaymentTerms("DATE", LocalDate.of(2026, 12, 15), "DATE", LocalDate.of(2027, 1, 31), true, "PACCHETTO",
                "OTHER", "60 gg data fattura", "IVA_60_90", null, "IVA_30_60_90", null, "INSTALL", null, "RIBA30", null);
        // canone SMIDRI/SMISMP: solo se presente in offerta; «Data» richiede la data
        PaymentTerms driBad = new PaymentTerms("DAYS30", null, "NEXT_MONTH", null, false, "CONSUNTIVO", "RIBA30", null, "RIBA30", null, "RIBA30", null, "DATE", null, "RIBA30", null);
        assertTrue(cfg.validate(driBad, "PROSPECT").isEmpty());
        assertTrue(cfg.validate(driBad, "PROSPECT", true).contains("Fatturazione canone SMIDRI/SMISMP: indicare la data"));
        assertEquals("Mese di installazione", cfg.billDriText(ok));
        assertTrue(cfg.validate(ok, "PROSPECT").isEmpty());
        assertEquals("In data 15/12/2026", cfg.billLicText(ok));
        assertEquals("In data 31/01/2027 – fatturazione semestrale", cfg.billFeeText(ok));
        assertEquals("60 gg data fattura", cfg.payText("OTHER", "60 gg data fattura"));
        assertEquals("A pacchetto", cfg.billActText(ok));
    }
}
