package it.cpqia.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RatesAdminControllerTest {

    @Test
    void parsesItalianAndPlainAmounts() {
        assertEquals(new BigDecimal("125.00"), RatesAdminController.parseAmount("125,00"));
        assertEquals(new BigDecimal("1250.50"), RatesAdminController.parseAmount("1.250,50"));
        assertEquals(new BigDecimal("95.5"), RatesAdminController.parseAmount("95.5"));
        assertEquals(new BigDecimal("350"), RatesAdminController.parseAmount("€ 350"));
        assertThrows(IllegalArgumentException.class, () -> RatesAdminController.parseAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> RatesAdminController.parseAmount(" "));
    }
}
