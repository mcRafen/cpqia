package it.cpqia.integration;

import it.cpqia.integration.AccountSyncService.Src;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logica pura della sincronizzazione clienti da IBM i (senza database né Power System). */
class AccountSyncServiceTest {

    private static final List<String> TYPES = List.of("Via", "Viale", "Piazza", "Piazzale", "Corso", "Largo", "Strada", "Vicolo", "Contrada", "Borgo", "Lungomare", "Frazione", "Località");

    @Test
    void indirizzoSpezzatoInToponimoViaECivico() {
        assertArrayEquals(new String[]{"Via", "VITTORIO VENETO", "153"}, AccountSyncService.parseAddress("VIA VITTORIO VENETO 153", TYPES));
        assertArrayEquals(new String[]{"Viale", "dei Mille", "12/A"}, AccountSyncService.parseAddress("Viale dei Mille, 12/A", TYPES));
        assertArrayEquals(new String[]{"Piazza", "GARIBALDI", "5"}, AccountSyncService.parseAddress("P.ZZA GARIBALDI 5", TYPES));
        assertArrayEquals(new String[]{"Corso", "TORINO", "SNC"}, AccountSyncService.parseAddress("C.SO TORINO", TYPES));
        assertArrayEquals(new String[]{"Località", "ZOCCO", "SNC"}, AccountSyncService.parseAddress("LOCALITA ZOCCO SNC", TYPES));
    }

    @Test
    void toponimoSconosciutoRestaNelNomeConViaPredefinita() {
        String[] a = AccountSyncService.parseAddress("ZONA INDUSTRIALE NORD 7", TYPES);
        assertEquals("Via", a[0]);
        assertEquals("ZONA INDUSTRIALE NORD", a[1]);
        assertEquals("7", a[2]);
    }

    @Test
    void dateYyyymmdd() {
        assertEquals(LocalDate.of(2026, 10, 29), AccountSyncService.ymd("20261029"));
        assertEquals(LocalDate.of(2026, 10, 29), AccountSyncService.ymd("20261029.0"));
        assertNull(AccountSyncService.ymd("0"));
        assertNull(AccountSyncService.ymd("00000000"));
        assertNull(AccountSyncService.ymd("20261345"));
        assertNull(AccountSyncService.ymd(" "));
    }

    private static Src src(LocalDate ins, LocalDate mod) {
        return new Src("1", "ACME", "VIA ROMA 1", "36010", "ZANE", "VI", "01234567890", "", ins, mod);
    }

    @Test
    void finestraDiImportazione() {
        LocalDate today = LocalDate.of(2026, 10, 29), since = LocalDate.of(2026, 10, 20);
        // inserito o modificato dall'ultima importazione (inclusa) alla data di esecuzione
        assertTrue(AccountSyncService.inScope(src(LocalDate.of(2026, 10, 25), null), since, today));
        assertTrue(AccountSyncService.inScope(src(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 20)), since, today));
        assertTrue(AccountSyncService.inScope(src(LocalDate.of(2020, 1, 1), today), since, today));
        // piu' vecchio dell'ultima importazione, oppure successivo alla data di esecuzione
        assertFalse(AccountSyncService.inScope(src(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 19)), since, today));
        assertFalse(AccountSyncService.inScope(src(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), since, today));
        assertFalse(AccountSyncService.inScope(src(null, null), since, today));
        // prima esecuzione: tutti i record non datati nel futuro
        assertTrue(AccountSyncService.inScope(src(LocalDate.of(2001, 5, 5), null), null, today));
        assertTrue(AccountSyncService.inScope(src(null, null), null, today));
        assertFalse(AccountSyncService.inScope(src(LocalDate.of(2027, 1, 1), null), null, today));
    }
}
