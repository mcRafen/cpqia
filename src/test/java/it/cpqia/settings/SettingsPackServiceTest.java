package it.cpqia.settings;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPackServiceTest {

    @Test
    void tablesAreUniqueAndParentsComeBeforeChildren() {
        var t = SettingsPackService.TABLES;
        assertEquals(t.size(), new HashSet<>(t).size());
        assertTrue(t.indexOf("application") < t.indexOf("module"));
        assertTrue(t.indexOf("module") < t.indexOf("module_price"));
        assertTrue(t.indexOf("price_group") < t.indexOf("module"));
        assertTrue(t.indexOf("pricing_tier") < t.indexOf("pricing_tier_price"));
        assertTrue(t.indexOf("galileo_profile") < t.indexOf("galileo_module"));
        assertFalse(t.contains("offer") || t.contains("account") || t.contains("app_user"));
    }

    @Test
    void hashIsStable() {
        assertEquals(SettingsPackService.sha("a"), SettingsPackService.sha("a"));
        assertFalse(SettingsPackService.sha("a").equals(SettingsPackService.sha("b")));
    }
}
