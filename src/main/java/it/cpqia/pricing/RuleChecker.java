package it.cpqia.pricing;

import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Group;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.pricing.PricingModel.Rule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifica i vincoli tra moduli (prerequisiti, alternative) sull'intera offerta. */
@Service
public class RuleChecker {

    public record LineSelection(App app, String mode, Map<Long, Integer> selected, Map<Long, Integer> driverQty) {
    }

    public record Violation(long applicationId, Long moduleId, String severity, String message) {
    }

    public List<Violation> check(List<LineSelection> lines) {
        List<Violation> out = new ArrayList<>();
        // codici modulo selezionati in tutta l'offerta (i prerequisiti possono stare anche in un'altra applicazione)
        Set<String> codes = new HashSet<>();
        for (LineSelection l : lines) {
            for (Module m : l.app().modules()) {
                if (isSelected(l, m)) {
                    codes.add(m.code());
                }
            }
        }
        for (LineSelection l : lines) {
            App app = l.app();
            Map<String, List<Module>> byExcl = new HashMap<>();
            boolean anyScaledSelected = false;
            for (Module m : app.modules()) {
                if (!isSelected(l, m)) {
                    continue;
                }
                if (m.exclGroup() != null) {
                    byExcl.computeIfAbsent(m.exclGroup(), k -> new ArrayList<>()).add(m);
                }
                for (Rule r : m.rules()) {
                    String text = m.code() + ": " + r.message();
                    switch (r.type()) {
                        case "REQUIRES" -> {
                            if (!containsAll(codes, r.targetCodes())) {
                                out.add(new Violation(app.id(), m.id(), r.severity(), text));
                            }
                        }
                        case "REQUIRES_ANY" -> {
                            if (!containsAny(codes, r.targetCodes())) {
                                out.add(new Violation(app.id(), m.id(), r.severity(), text));
                            }
                        }
                        case "EXCLUDES" -> {
                            if (containsAny(codes, r.targetCodes())) {
                                out.add(new Violation(app.id(), m.id(), r.severity(), text));
                            }
                        }
                        default -> out.add(new Violation(app.id(), m.id(), "INFO", text));
                    }
                }
                if (m.groupId() != null && !m.included()) {
                    anyScaledSelected = true;
                }
            }
            for (Map.Entry<String, List<Module>> e : byExcl.entrySet()) {
                if (e.getValue().size() > 1) {
                    out.add(new Violation(app.id(), null, "ERROR",
                            "Selezionare un solo modulo tra le alternative: " + joinCodes(e.getValue())));
                }
            }
            // moduli a utente senza numero utenti; con piu' tipi di utenti (es. JPM Enterprise / User) basta indicarne uno: un solo messaggio con tutti i tipi
            boolean multiDriver = app.drivers().size() > 1;
            boolean anyDriver = l.driverQty().values().stream().anyMatch(q -> q != null && q > 0);
            boolean multiReported = false;
            for (Group g : app.groups()) {
                int qty = l.driverQty().getOrDefault(g.driverId(), 0);
                boolean groupHasSelection = false;
                for (Module m : app.modules()) {
                    if (m.groupId() != null && m.groupId() == g.id() && isSelected(l, m)) {
                        groupHasSelection = true;
                        break;
                    }
                }
                boolean fixedBase = g.fixedBase() != null && !g.fixedBase().isEmpty();
                if ((groupHasSelection || (fixedBase && qty > 0)) && qty < 1 && !fixedBase) {
                    if (multiDriver) {
                        if (!anyDriver && !multiReported) {
                            out.add(new Violation(app.id(), null, "ERROR", "Indicare il numero di " + app.driver(g.driverId()).unitLabel() + " (" + driverKinds(app) + ")"));
                            multiReported = true;
                        }
                    } else {
                        out.add(new Violation(app.id(), null, "ERROR",
                                "Indicare il numero di " + app.driver(g.driverId()).unitLabel() + " ("
                                        + app.driver(g.driverId()).label() + ")"));
                    }
                }
            }
            if (!anyScaledSelected && allFixedEmpty(l)) {
                out.add(new Violation(app.id(), null, "ERROR", "Nessun modulo selezionato per " + app.name()));
            }
        }
        return out;
    }

    /** tipi di utenti dell'applicazione senza la parola comune iniziale: «Utenti Enterprise», «Utenti User» → «Enterprise/User» */
    static String driverKinds(App app) {
        List<String> labels = new ArrayList<>();
        for (PricingModel.Driver d : app.drivers()) {
            labels.add(d.label() == null ? "" : d.label().trim());
        }
        String first = labels.isEmpty() ? "" : labels.get(0).split("\\s+")[0];
        boolean common = !first.isEmpty() && labels.stream().allMatch(x -> x.split("\\s+").length > 1 && x.split("\\s+")[0].equalsIgnoreCase(first));
        List<String> out = new ArrayList<>();
        for (String x : labels) {
            out.add(common ? x.substring(x.indexOf(' ') + 1).trim() : x);
        }
        return String.join("/", out);
    }

    private static boolean allFixedEmpty(LineSelection l) {
        for (Module m : l.app().modules()) {
            if (isSelected(l, m)) {
                return false;
            }
        }
        return true;
    }

    static boolean isSelected(LineSelection l, Module m) {
        Integer q = l.selected().get(m.id());
        return q != null && q > 0 && !"LINK".equals(m.section());
    }

    private static boolean containsAll(Set<String> codes, String targets) {
        if (targets == null) {
            return true;
        }
        for (String t : targets.split(",")) {
            if (!t.isBlank() && !codes.contains(t.trim())) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAny(Set<String> codes, String targets) {
        if (targets == null) {
            return false;
        }
        for (String t : targets.split(",")) {
            if (codes.contains(t.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String joinCodes(List<Module> mods) {
        StringBuilder sb = new StringBuilder();
        for (Module m : mods) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(m.code());
        }
        return sb.toString();
    }
}
