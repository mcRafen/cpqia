package it.cpqia.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Result;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Confronta il motore Java con gli scenari calcolati dal listino Excel originale (ricalcolo LibreOffice).
 * golden.json / catalog-api.json sono generati da tools/validate_engine.py e tools/api_shape.py.
 */
class PricingGoldenTest {

    record Golden(String app, String mode, Map<String, Integer> selected,
                  @JsonProperty("driver_qty") Map<String, Integer> driverQty,
                  @JsonProperty("expected_license") double expectedLicense,
                  @JsonProperty("expected_maintenance") double expectedMaintenance) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PricingService pricing = new PricingService();

    @Test
    void javaEngineMatchesExcelListino() throws Exception {
        List<App> apps;
        List<Golden> golden;
        try (InputStream a = getClass().getResourceAsStream("/catalog-api.json");
             InputStream g = getClass().getResourceAsStream("/golden.json")) {
            assertNotNull(a);
            assertNotNull(g);
            apps = MAPPER.readValue(a, new TypeReference<List<App>>() { });
            golden = MAPPER.readValue(g, new TypeReference<List<Golden>>() { });
        }
        assertFalse(golden.isEmpty());
        for (Golden s : golden) {
            App app = apps.stream().filter(x -> x.code().equals(s.app())).findFirst().orElseThrow();
            Map<Long, Integer> selected = new HashMap<>();
            s.selected().forEach((idx, q) -> selected.put(app.modules().get(Integer.parseInt(idx)).id(), q));
            Map<Long, Integer> drivers = new HashMap<>();
            s.driverQty().forEach((code, q) -> app.drivers().stream().filter(d -> d.code().equals(code))
                    .findFirst().ifPresent(d -> drivers.put(d.id(), q)));
            Result r = pricing.calculate(app, s.mode(), selected, drivers);
            String msg = s.app() + " " + s.mode() + " " + s.selected() + " " + s.driverQty();
            assertEquals(s.expectedLicense(), r.license().doubleValue(), 0.011, "licenze " + msg);
            assertEquals(s.expectedMaintenance(), r.maintenance().doubleValue(), 0.011, "manutenzione " + msg);
        }
    }

    /** esempio del modello Word: Discovery Quality, 10 utenti, SMIDBAS + NC + Reclami = 8.000 / 1.440 */
    @Test
    void exampleFromOfferTemplate() throws Exception {
        List<App> apps;
        try (InputStream a = getClass().getResourceAsStream("/catalog-api.json")) {
            apps = MAPPER.readValue(a, new TypeReference<List<App>>() { });
        }
        App dq = apps.stream().filter(x -> x.code().equals("DQ")).findFirst().orElseThrow();
        Map<Long, Integer> sel = new HashMap<>();
        for (String code : List.of("SMIDBAS", "SMIDQRNC", "SMIDQRCL")) {
            sel.put(dq.modules().stream().filter(m -> m.code().equals(code)).findFirst().orElseThrow().id(), 1);
        }
        Map<Long, Integer> drv = Map.of(dq.drivers().get(0).id(), 10);
        Result r = pricing.calculate(dq, null, sel, drv);
        assertEquals(8000.0, r.license().doubleValue(), 0.001);
        assertEquals(1440.0, r.maintenance().doubleValue(), 0.001);

        App jpa = apps.stream().filter(x -> x.code().equals("JPA3")).findFirst().orElseThrow();
        Map<Long, Integer> sel2 = Map.of(jpa.modules().stream().filter(m -> m.code().equals("SMIJPA")).findFirst().orElseThrow().id(), 1);
        Result r2 = pricing.calculate(jpa, "NEW", sel2, Map.of(jpa.drivers().get(0).id(), 10));
        assertEquals(10500.0, r2.license().doubleValue(), 0.001);   // "JPA Engine V3 - Full": 10.500 / 1.890
        assertEquals(1890.0, r2.maintenance().doubleValue(), 0.001);
    }
}
