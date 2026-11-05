package it.cpqia.galileo;

import it.cpqia.galileo.GalileoModel.Input;
import it.cpqia.galileo.GalileoModel.Line;
import it.cpqia.galileo.GalileoModel.Mod;
import it.cpqia.galileo.GalileoModel.Profile;
import it.cpqia.galileo.GalileoModel.Result;
import it.cpqia.galileo.GalileoModel.Tier;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Motore di calcolo del listino JGalileo: replica le formule dei fogli Excel (nuova configurazione, consociata, migrazione,
 * migrazione consociata, immobiliare). Verificato contro il ricalcolo LibreOffice degli stessi fogli (tools/galileo/validate.py,
 * scenari in src/test/resources/galileo).
 */
public final class GalileoPricing {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal SEVEN_TENTHS = new BigDecimal("0.7");
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal FORTY_EIGHT = new BigDecimal("48");

    private GalileoPricing() {
    }

    // ------------------------------------------------------------------ fasce utenti
    /** prima fascia inclusa nella base; ogni utente delle fasce successive costa base * pct / 100 */
    static BigDecimal tierTotal(BigDecimal base, int users, List<Tier> tiers, boolean proRata) {
        if (users <= 0 || base.signum() <= 0 || tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = base;
        long prevTo = tiers.get(0).to() == null ? Long.MAX_VALUE : tiers.get(0).to();
        for (int k = 1; k < tiers.size(); k++) {
            Tier t = tiers.get(k);
            long to = t.to() == null ? Long.MAX_VALUE : t.to();
            long n;
            if (users >= to) {
                n = to - prevTo;
            } else if (users > t.from() - 1L || (proRata && k == 1)) {
                n = users - t.from() + 1L;
            } else {
                n = 0;
            }
            if (n > 0 || (proRata && k == 1)) {
                BigDecimal pct = t.pct() == null ? BigDecimal.ZERO : t.pct();
                amount = amount.add(base.multiply(pct).divide(HUNDRED, MC).multiply(BigDecimal.valueOf(n)));
            }
            prevTo = to;
        }
        return amount;
    }

    // ------------------------------------------------------------------ somme dei moduli
    private static BigDecimal eSum(Profile p, Input in, boolean aggiunta) {
        BigDecimal s = BigDecimal.ZERO;
        boolean n5IsOne = p.aggiuntaSupported() && !aggiunta;        // N5 = 1: nuova configurazione
        for (Mod m : p.modules()) {
            String e = m.eRule();
            if (e == null || e.isEmpty()) {
                continue;
            }
            boolean inD = in.d().contains(m.code());
            boolean inF = in.f().contains(m.code());
            boolean fa = "FA".equals(m.flag());
            boolean charge = switch (e) {
                case "D" -> inD;
                case "F" -> inF;
                case "G1" -> inD && (fa || !n5IsOne);
                case "DFA" -> fa && inD;
                default -> false;
            };
            if (charge) {
                s = s.add(m.eAmount());
            }
        }
        return s;
    }

    private static BigDecimal gSum(Profile p, Input in) {
        BigDecimal s = BigDecimal.ZERO;
        for (Mod m : p.modules()) {
            if (m.gCounted() && in.f().contains(m.code())) {
                s = s.add(m.gAmount());
            }
        }
        return s;
    }

    private static Result invalid(String message) {
        return new Result(false, message, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private static Result result(Profile p, BigDecimal raw, List<Line> lines) {
        BigDecimal list = raw.setScale(2, RoundingMode.HALF_UP);
        BigDecimal maint = list.multiply(p.maintenancePct()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal rent = list.add(maint.multiply(FOUR)).divide(FORTY_EIGHT, 2, RoundingMode.HALF_UP);
        return new Result(true, null, list, maint, rent, rent.multiply(new BigDecimal("12")), lines);
    }

    private static BigDecimal r2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ calcolo
    public static Result calculate(Profile p, Input in) {
        if (p.hasV11() && (in.release() == null || in.release().isBlank())) {
            return invalid("Seleziona la release (V9 o V11)");
        }
        String rel = "V11".equals(in.release()) && p.hasV11() ? "V11" : "V9";
        BigDecimal cfg = "V11".equals(rel) ? p.cfgV11() : p.cfgV9();
        int users = in.users();
        boolean aggiunta = in.aggiunta() && p.aggiuntaSupported();
        List<Line> lines = new ArrayList<>();
        switch (p.kind()) {
            case "IMMOB" -> {
                if (users < 1 || users > (p.maxUsers() == null ? 3 : p.maxUsers())) {
                    return invalid(users < 1 ? "Indica il numero di utenti" : "Oltre " + p.maxUsers() + " utenti non è prevista la configurazione immobiliare: usa la configurazione Holding amministrativa");
                }
                lines.add(new Line("Configurazione immobiliare (fino a " + p.maxUsers() + " utenti)", cfg));
                return result(p, cfg, lines);
            }
            case "NEW" -> {
                if (users <= 0) {
                    return invalid("Indica il numero di utenti");
                }
                BigDecimal sum = eSum(p, in, false);
                BigDecimal base = sum.add(cfg);
                BigDecimal total = tierTotal(base, users, p.tiers().get("MAIN"), p.proRataBelowFirst());
                // dettaglio: configurazione (con gli utenti inclusi), moduli aggiuntivi, utenti aggiuntivi rispetto al minimo previsto dallo scenario
                String included = firstTierUsers(p, "MAIN");
                int inc = "—".equals(included) ? 0 : Integer.parseInt(included);
                lines.add(new Line("Configurazione " + rel + (inc > 0 ? " (configurazione base a " + inc + " utenti)" : ""), r2(cfg)));
                lines.add(new Line("Moduli aggiuntivi selezionati", r2(sum)));
                int extra = inc > 0 ? users - inc : 0;
                lines.add(new Line(extra < 0 ? "Utenti sotto il minimo previsto (" + (-extra) + ")" : "Utenti aggiuntivi (" + Math.max(0, extra) + ")", r2(total.subtract(base))));
                return result(p, total, lines);
            }
            case "CONS" -> {
                if (users <= 0) {
                    return invalid("Indica il numero di utenti");
                }
                BigDecimal sum = eSum(p, in, aggiunta);
                BigDecimal base = sum.add(aggiunta ? BigDecimal.ZERO : cfg);
                BigDecimal c124 = tierTotal(base, users, p.tiers().get("MAIN"), false);
                BigDecimal gs = gSum(p, in);
                BigDecimal r44 = tierTotal(gs, users, p.tiers().get("NEWMOD"), false);
                BigDecimal part = c124.divide(p.consDivisor(), MC);
                lines.add(new Line("Configurazione consociata (moduli in comune e utenti)", r2(c124)));
                lines.add(new Line("Consociata: prezzo diviso " + p.consDivisor().stripTrailingZeros().toPlainString(), r2(part)));
                lines.add(new Line("Moduli nuovi per la consociata (con utenti)", r2(r44)));
                return result(p, part.add(r44), lines);
            }
            default -> {          // MIG / MIGCONS
                int k5 = in.source();
                int i5 = "V9".equals(rel) ? 1 : 4;
                if (k5 < 1 || k5 > 3) {
                    return invalid("Indica da quale versione migra il cliente (carattere, visuale, V9)");
                }
                if (i5 + k5 == 4) {
                    return invalid("Migrazione non valida: da V9 si può migrare solo alla V11");
                }
                if (users <= 0) {
                    return invalid("Indica il numero di utenti");
                }
                BigDecimal sum = eSum(p, in, aggiunta);
                BigDecimal o14 = sum.add(aggiunta ? BigDecimal.ZERO : cfg);
                BigDecimal c124 = tierTotal(o14, users, p.tiers().get("MAIN"), false);
                BigDecimal n24 = switch (k5) {
                    case 1 -> c124.multiply(p.migCarattere()).divide(HUNDRED, MC);
                    case 2 -> c124.multiply(p.migVisuale()).divide(HUNDRED, MC);
                    default -> p.migV9Flat();
                };
                BigDecimal o37 = gSum(p, in).multiply(SEVEN_TENTHS);
                BigDecimal n25 = tierTotal(o37, users, p.tiers().get("NEWMOD"), false);
                BigDecimal n26 = addUsersTotal(o14, in.addUsers(), p.tiers().get("ADDUSR"));
                BigDecimal total = n24.add(n25).add(n26).divide(p.consDivisor(), MC);
                lines.add(new Line("Configurazione " + rel + " (moduli migrati e utenti)", r2(c124)));
                lines.add(new Line("Listino di vendita migrazione (" + (k5 == 1 ? "da carattere" : k5 == 2 ? "da visuale" : "da V9") + ")", r2(n24)));
                lines.add(new Line("Listino di vendita nuovi moduli", r2(n25)));
                lines.add(new Line("Aggiunta posti di lavoro", r2(n26)));
                if (p.consDivisor().compareTo(BigDecimal.ONE) != 0) {
                    lines.add(new Line("Migrazione consociata: diviso " + p.consDivisor().stripTrailingZeros().toPlainString(), r2(total)));
                }
                return result(p, total, lines);
            }
        }
    }

    private static String firstTierUsers(Profile p, String set) {
        List<Tier> t = p.tiers().get(set);
        return t == null || t.isEmpty() || t.get(0).to() == null ? "—" : String.valueOf(t.get(0).to());
    }

    /** utenti aggiuntivi della migrazione: i primi (resto) a base/prima fascia * 0,7, gli altri per fascia a pct della base */
    private static BigDecimal addUsersTotal(BigDecimal o14, int u2, List<Tier> t) {
        if (u2 <= 0 || t == null || t.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long[] cnt = new long[t.size()];
        long prev = t.get(0).to() == null ? Long.MAX_VALUE : t.get(0).to();
        long others = 0;
        for (int k = 1; k < t.size(); k++) {
            long to = t.get(k).to() == null ? Long.MAX_VALUE : t.get(k).to();
            if (u2 >= to) {
                cnt[k] = to - prev;
            } else if (u2 > t.get(k).from() - 1L) {
                cnt[k] = u2 - t.get(k).from() + 1L;
            }
            others += cnt[k];
            prev = to;
        }
        cnt[0] = u2 - others;
        BigDecimal total = o14.divide(BigDecimal.valueOf(t.get(0).to()), MC).multiply(BigDecimal.valueOf(cnt[0])).multiply(SEVEN_TENTHS);
        for (int k = 1; k < t.size(); k++) {
            BigDecimal pct = t.get(k).pct() == null ? BigDecimal.ZERO : t.get(k).pct();
            total = total.add(o14.multiply(pct).divide(HUNDRED, MC).multiply(BigDecimal.valueOf(cnt[k])));
        }
        return total;
    }
}
