package it.cpqia.galileo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.galileo.GalileoModel.Input;
import it.cpqia.galileo.GalileoModel.Mod;
import it.cpqia.galileo.GalileoModel.Profile;
import it.cpqia.galileo.GalileoModel.Result;
import it.cpqia.galileo.GalileoModel.Tier;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confronta il motore Java con il ricalcolo LibreOffice dei fogli del listino JGalileo.
 * galileo/profiles.json (estratto da tools/galileo/extract.py) e galileo/golden.json (tools/galileo/validate.py) sono generati dal listino.
 */
class GalileoPricingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BigDecimal dec(JsonNode n) {
        return n == null || n.isNull() ? null : n.decimalValue();
    }

    static Map<String, Profile> profiles() throws Exception {
        Map<String, Profile> out = new LinkedHashMap<>();
        try (InputStream in = GalileoPricingTest.class.getResourceAsStream("/galileo/profiles.json")) {
            assertNotNull(in);
            for (JsonNode p : MAPPER.readTree(in).get("profiles")) {
                Map<String, List<Tier>> tiers = new LinkedHashMap<>();
                if (p.has("tiers")) {
                    p.get("tiers").fields().forEachRemaining(e -> {
                        List<Tier> l = new ArrayList<>();
                        for (JsonNode t : e.getValue()) {
                            l.add(new Tier(t.get("from").asInt(), t.get("to").isNull() ? null : t.get("to").asInt(), dec(t.get("pct"))));
                        }
                        tiers.put(e.getKey(), l);
                    });
                }
                List<Mod> mods = new ArrayList<>();
                for (JsonNode m : p.get("modules")) {
                    mods.add(new Mod(0, m.get("code").asText(), m.get("desc").asText(), m.path("area").asText(null), m.get("flag").asText(), m.get("e").asText(),
                            m.get("g").asBoolean(), dec(m.get("price")), dec(m.get("ePrice")), dec(m.get("gPrice")), m.get("row").asInt()));
                }
                BigDecimal d = p.get("discount").decimalValue().multiply(BigDecimal.valueOf(100));
                BigDecimal dn = (p.has("discountNew") ? p.get("discountNew").decimalValue() : new BigDecimal("0.3")).multiply(BigDecimal.valueOf(100));
                String kind = p.get("kind").asText();
                BigDecimal cons = p.has("consDivisor") ? p.get("consDivisor").decimalValue() : BigDecimal.ONE;
                JsonNode cfg = p.get("cfg");
                out.put(p.get("sheet").asText(), new Profile(0, 0, kind, p.get("label").asText(), p.get("sheet").asText(), dec(cfg.get("V9")), dec(cfg.get("V11")),
                        d, dn, !p.has("v11Discount") || p.get("v11Discount").asBoolean(), cons,
                        dec(p.get("migCaratterePct")), dec(p.get("migVisualePct")), dec(p.get("migV9Flat")), p.has("maxUsers") ? p.get("maxUsers").asInt() : null,
                        p.has("aggiuntaSupported") && p.get("aggiuntaSupported").asBoolean(), p.has("proRataBelowFirst") && p.get("proRataBelowFirst").asBoolean(),
                        new BigDecimal("18"), tiers, mods));
            }
        }
        return out;
    }

    @Test
    void javaEngineMatchesExcelListino() throws Exception {
        Map<String, Profile> profiles = profiles();
        assertEquals(17, profiles.size());
        int n = 0;
        try (InputStream in = getClass().getResourceAsStream("/galileo/golden.json")) {
            assertNotNull(in);
            for (JsonNode g : MAPPER.readTree(in)) {
                Profile p = profiles.get(g.get("sheet").asText());
                assertNotNull(p, g.get("sheet").asText());
                JsonNode i = g.get("inputs");
                Set<String> d = new HashSet<>();
                Set<String> f = new HashSet<>();
                i.get("D").forEach(x -> d.add(x.asText()));
                i.get("F").forEach(x -> f.add(x.asText()));
                Input input = new Input(i.get("users").asInt(), i.get("addUsers").asInt(), i.get("release").asText(), i.get("source").asInt(),
                        i.get("aggiunta").asBoolean(), d, f);
                Result r = GalileoPricing.calculate(p, input);
                String where = p.sheet() + " " + i;
                if (g.get("expected").isNull()) {
                    assertFalse(r.valid(), where);
                } else {
                    assertTrue(r.valid(), where + " " + r.message());
                    assertEquals(g.get("expected").doubleValue(), r.list().doubleValue(), 0.011, where);
                }
                n++;
            }
        }
        assertTrue(n >= 200, "scenari verificati: " + n);
    }

    @Test
    void immobiliareAcceptsUpToThreeUsers() throws Exception {
        Profile p = profiles().get("IMMOBILIARE");
        Result ok = GalileoPricing.calculate(p, new Input(3, 0, "V9", 0, false, new HashSet<>(), new HashSet<>()));
        assertTrue(ok.valid());
        assertEquals(0, new BigDecimal("2000.00").compareTo(ok.list()));
        assertFalse(GalileoPricing.calculate(p, new Input(4, 0, "V9", 0, false, new HashSet<>(), new HashSet<>())).valid());
    }

    @Test
    void migrationFromV9ToV9IsRejected() throws Exception {
        Profile p = profiles().get("MIGRAZIONE AZ. COMMERCIALE");
        Result r = GalileoPricing.calculate(p, new Input(10, 0, "V9", 3, false, new HashSet<>(), new HashSet<>()));
        assertFalse(r.valid());
    }
}
