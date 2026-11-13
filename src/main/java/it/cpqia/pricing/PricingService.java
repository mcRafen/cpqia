package it.cpqia.pricing;

import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Group;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.pricing.PricingModel.Result;
import it.cpqia.pricing.PricingModel.Row;
import it.cpqia.pricing.PricingModel.Tier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Motore di calcolo: replica le formule del listino Excel (verificato contro il ricalcolo LibreOffice, vedi tools/).
 *
 * <pre>
 * per ogni gruppo di prezzo:
 *   base   = somma prezzi dei moduli selezionati del gruppo (oppure base fissa del gruppo)
 *   TIER1  : se qta &gt; 0 e base &gt; 0 -&gt; importo = base (prima fascia inclusa); per ogni fascia successiva
 *            importo += utenti_nella_fascia * unitario, con unitario = prezzo esplicito (per modalita') oppure base*pct/100
 *   LINEAR : importo = qta * unitario
 * moduli a prezzo fisso: LICENSE (manutenzione % dell'applicazione o del modulo), ANNUAL (solo canone annuo), ONETIME
 * </pre>
 */
@Service
public class PricingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FORTY_EIGHT = new BigDecimal("48");
    private static final BigDecimal FOUR = new BigDecimal("4");

    public static BigDecimal priceOf(Module m, String mode) {
        Map<String, BigDecimal> p = m.prices();
        BigDecimal v = null;
        if (mode != null && p.containsKey(mode)) {
            v = p.get(mode);
        } else if (p.containsKey("STD")) {
            v = p.get("STD");
        }
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * @param selected  moduleId -&gt; quantita' (&gt;0 = selezionato)
     * @param driverQty driverId -&gt; quantita' (utenti, macchine, ...)
     */
    public Result calculate(App app, String mode, Map<Long, Integer> selected, Map<Long, Integer> driverQty) {
        return calculate(app, mode, selected, driverQty, Map.of());
    }

    /**
     * come sopra, con prezzi unitari dei moduli diversi dal catalogo (moduleId -&gt; prezzo unitario): sostituiscono quello del listino,
     * sia per i moduli a prezzo fisso sia come base dei gruppi a fasce
     */
    public Result calculate(App app, String mode, Map<Long, Integer> selected, Map<Long, Integer> driverQty, Map<Long, BigDecimal> priceOverride) {
        if (mode == null) {
            mode = app.defaultMode();
        }
        BigDecimal license = BigDecimal.ZERO;
        BigDecimal maint = BigDecimal.ZERO;
        BigDecimal annual = BigDecimal.ZERO;
        BigDecimal onetime = BigDecimal.ZERO;
        List<Row> rows = new ArrayList<>();

        Map<Long, BigDecimal> groupSums = new java.util.HashMap<>();
        Map<Long, String> groupKind = new java.util.HashMap<>();
        for (Module m : app.modules()) {
            Integer q = selected.get(m.id());
            if (q == null || q <= 0 || "LINK".equals(m.section()) || m.included()) {
                continue;
            }
            int qty = m.qtyEnabled() ? q : 1;
            BigDecimal unitPrice = priceOverride != null && priceOverride.get(m.id()) != null ? priceOverride.get(m.id()) : priceOf(m, mode);
            BigDecimal t = unitPrice.multiply(BigDecimal.valueOf(qty));
            if (m.groupId() != null) {
                groupSums.merge(m.groupId(), t, BigDecimal::add);
                groupKind.putIfAbsent(m.groupId(), m.kind());
                continue;
            }
            BigDecimal pct = m.maintenancePct() != null ? m.maintenancePct() : app.maintenancePct();
            switch (m.kind()) {
                case "ANNUAL" -> {
                    annual = annual.add(t);
                    rows.add(new Row(m.code() + " (canone annuo)", t));
                }
                case "MONTHLY" -> {                                          // canone mensile: nel totale annuo vale 12 mensilita'
                    annual = annual.add(t.multiply(BigDecimal.valueOf(12)));
                    rows.add(new Row(m.code() + " (canone mensile)", t));
                }
                case "ONETIME" -> {
                    onetime = onetime.add(t);
                    rows.add(new Row(m.code() + " (una tantum)", t));
                }
                default -> {
                    license = license.add(t);
                    maint = maint.add(t.multiply(pct).divide(HUNDRED));
                    rows.add(new Row(m.code(), t));
                }
            }
            if (m.onetimePrice() != null && m.onetimePrice().signum() != 0) {
                BigDecimal ot = m.onetimePrice().multiply(BigDecimal.valueOf(qty));
                onetime = onetime.add(ot);
                rows.add(new Row(m.code() + " (attivazione una tantum)", ot));
            }
        }

        for (Group g : app.groups()) {
            int qty = driverQty.getOrDefault(g.driverId(), 0);
            BigDecimal base;
            if (g.fixedBase() != null && !g.fixedBase().isEmpty()) {
                base = g.fixedBase().containsKey(mode) ? g.fixedBase().get(mode) : g.fixedBase().getOrDefault("STD", BigDecimal.ZERO);
            } else {
                base = groupSums.getOrDefault(g.id(), BigDecimal.ZERO);
            }
            // gruppo a base fissa (es. JMES «Collegamento macchine»): si applica solo se e' selezionato un suo modulo (se il gruppo ha moduli)
            if (g.fixedBase() != null && !g.fixedBase().isEmpty() && !groupSums.containsKey(g.id())
                    && app.modules().stream().anyMatch(x -> x.groupId() != null && x.groupId().equals(g.id()))) {
                continue;
            }
            boolean step = "STEP".equals(g.baseMode());
            if (step && !groupSums.containsKey(g.id())) {
                continue;                                              // importo per fascia: solo se e' selezionato un modulo del gruppo
            }
            BigDecimal amount = tierAmount(g, base, qty, mode);
            if (step && qty > 0 && amount.signum() == 0) {
                Tier band = stepTier(g, qty);
                rows.add(new Row(g.label() + " – " + (band == null ? "fascia non prevista" : band.label()) + " (" + qty + " " + app.driver(g.driverId()).unitLabel() + ")", amount));
            }
            if (amount.signum() != 0) {
                if ("ANNUAL".equals(groupKind.get(g.id()))) {
                    annual = annual.add(amount);                       // moduli a canone annuo: nessuna manutenzione percentuale
                } else {
                    license = license.add(amount);
                    maint = maint.add(amount.multiply(app.maintenancePct()).divide(HUNDRED));
                }
                rows.add(new Row(g.label() + " (" + qty + " " + app.driver(g.driverId()).unitLabel() + ")", amount));
            }
        }

        BigDecimal totalMaint = maint.add(annual);
        BigDecimal rent = license.add(totalMaint.multiply(FOUR)).divide(FORTY_EIGHT, 2, RoundingMode.HALF_UP);
        return new Result(round(license), round(onetime), round(totalMaint), rent, rows, round(annual));
    }

    /** fascia che contiene la quantita' (da <= qta <= a, a vuoto = senza limite) */
    static Tier stepTier(Group g, int qty) {
        for (Tier t : g.tiers()) {
            if (qty >= t.fromQty() && (t.toQty() == null || qty <= t.toQty())) {
                return t;
            }
        }
        return null;
    }

    static BigDecimal tierAmount(Group g, BigDecimal base, int qty, String mode) {
        if (qty <= 0) {
            return BigDecimal.ZERO;
        }
        List<Tier> tiers = g.tiers();
        if ("STEP".equals(g.baseMode())) {                             // importo unico della fascia in cui cade la quantita' (fascia senza prezzo = da valutare)
            Tier t = stepTier(g, qty);
            BigDecimal u = t == null ? null : unit(t, mode);
            return u == null ? BigDecimal.ZERO : u;
        }
        if ("LINEAR".equals(g.baseMode())) {
            Tier t = tiers.get(0);
            BigDecimal u = unit(t, mode);
            return u == null ? BigDecimal.ZERO : u.multiply(BigDecimal.valueOf(qty));
        }
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = base;
        for (int k = 1; k < tiers.size(); k++) {
            Tier t = tiers.get(k);
            Tier prev = tiers.get(k - 1);
            long to = t.toQty() == null ? Long.MAX_VALUE : t.toQty();
            long prevTo = prev.toQty() == null ? Long.MAX_VALUE : prev.toQty();
            long n;
            if (qty >= to) {
                n = to - prevTo;
            } else if (qty > t.fromQty() - 1) {
                n = qty - t.fromQty() + 1;
            } else {
                n = 0;
            }
            if (n <= 0) {
                continue;
            }
            BigDecimal u = unit(t, mode);
            if (u == null) {
                u = t.pct() == null ? BigDecimal.ZERO : base.multiply(t.pct()).divide(HUNDRED);
            }
            amount = amount.add(u.multiply(BigDecimal.valueOf(n)));
        }
        return amount;
    }

    private static BigDecimal unit(Tier t, String mode) {
        Map<String, BigDecimal> u = t.unitByMode();
        if (u == null || u.isEmpty()) {
            return null;
        }
        if (mode != null && u.containsKey(mode)) {
            return u.get(mode);
        }
        return u.get("STD");
    }

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
