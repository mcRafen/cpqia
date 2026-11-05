package it.cpqia.pricing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.RuleChecker.LineSelection;
import it.cpqia.pricing.RuleChecker.Violation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCheckerTest {

    private final RuleChecker checker = new RuleChecker();

    private List<App> apps() throws Exception {
        try (InputStream a = getClass().getResourceAsStream("/catalog-api.json")) {
            return new ObjectMapper().readValue(a, new TypeReference<List<App>>() { });
        }
    }

    private long id(App app, String code) {
        return app.modules().stream().filter(m -> m.code().equals(code)).findFirst().orElseThrow().id();
    }

    @Test
    void missingPrerequisiteIsAnError() throws Exception {
        App icm = apps().stream().filter(a -> a.code().equals("ICM")).findFirst().orElseThrow();
        Map<Long, Integer> sel = new HashMap<>();
        sel.put(id(icm, "SMIWCP"), 1);                       // richiede SMIWPS4
        List<Violation> v = checker.check(List.of(new LineSelection(icm, null, sel, Map.of(icm.drivers().get(0).id(), 5))));
        assertTrue(v.stream().anyMatch(x -> "ERROR".equals(x.severity()) && x.message().contains("SMIWPS4")));
        sel.put(id(icm, "SMIWPS4"), 1);
        v = checker.check(List.of(new LineSelection(icm, null, sel, Map.of(icm.drivers().get(0).id(), 5))));
        assertEquals(0, v.stream().filter(x -> "ERROR".equals(x.severity())).count());
    }

    @Test
    void usersRequiredForScaledModules() throws Exception {
        App dq = apps().stream().filter(a -> a.code().equals("DQ")).findFirst().orElseThrow();
        List<Violation> v = checker.check(List.of(new LineSelection(dq, null, Map.of(id(dq, "SMIDBAS"), 1), Map.of())));
        assertTrue(v.stream().anyMatch(x -> "ERROR".equals(x.severity()) && x.message().startsWith("Indicare il numero")));
    }

    @Test
    void onlyOneAlternativeModule() throws Exception {
        App aps = apps().stream().filter(a -> a.code().equals("APS")).findFirst().orElseThrow();
        Map<Long, Integer> sel = new HashMap<>();
        sel.put(id(aps, "SMIJAPS"), 1);
        sel.put(id(aps, "SMIJAPSP"), 1);
        List<Violation> v = checker.check(List.of(new LineSelection(aps, null, sel, Map.of(aps.drivers().get(0).id(), 2))));
        assertTrue(v.stream().anyMatch(x -> "ERROR".equals(x.severity()) && x.message().contains("alternative")));
    }
}
