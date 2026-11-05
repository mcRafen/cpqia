package it.cpqia.galileo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Modello dei profili di calcolo JGalileo (indipendente dal database: usato dal motore, dal servizio e dai test). */
public final class GalileoModel {

    private GalileoModel() {
    }

    /** fascia utenti: to == null = senza limite; pct == null = prima fascia (inclusa nel prezzo base) */
    public record Tier(int from, Integer to, BigDecimal pct) {
    }

    /**
     * Modulo di un profilo. eRule: regola di addebito tra i «moduli in comune/migrati» (D = se in comune, F = se nuovo,
     * DFA = D solo per le funzioni aggiuntive, G1 = D salvo i moduli base in nuova configurazione, vuoto = mai).
     * ePrice/gPrice: prezzo realmente addebitato quando il foglio Excel fa riferimento a un'altra riga.
     */
    public record Mod(long moduleId, String code, String description, String area, String flag, String eRule, boolean gCounted,
                      BigDecimal price, BigDecimal ePrice, BigDecimal gPrice, int order) {

        public BigDecimal eAmount() {
            return ePrice != null ? ePrice : (price == null ? BigDecimal.ZERO : price);
        }

        public BigDecimal gAmount() {
            return gPrice != null ? gPrice : (price == null ? BigDecimal.ZERO : price);
        }
    }

    public record Profile(long id, long applicationId, String kind, String label, String sheet, BigDecimal cfgV9, BigDecimal cfgV11,
                          BigDecimal discountPct, BigDecimal newDiscountPct, boolean v11Discount, BigDecimal consDivisor,
                          BigDecimal migCarattere, BigDecimal migVisuale, BigDecimal migV9Flat, Integer maxUsers,
                          boolean aggiuntaSupported, boolean proRataBelowFirst, BigDecimal maintenancePct,
                          Map<String, List<Tier>> tiers, List<Mod> modules) {

        public boolean hasV11() {
            return cfgV11 != null;
        }

        public Mod module(String code) {
            for (Mod m : modules) {
                if (m.code().equals(code)) {
                    return m;
                }
            }
            return null;
        }
    }

    /** selezioni: d = moduli selezionati/in comune/migrati, f = moduli nuovi (codici modulo) */
    public record Input(int users, int addUsers, String release, int source, boolean aggiunta, Set<String> d, Set<String> f) {
    }

    public record Line(String label, BigDecimal amount) {
    }

    /** valid = false: combinazione non consentita (message spiega il motivo) */
    public record Result(boolean valid, String message, BigDecimal list, BigDecimal maintenance, BigDecimal rentMonthly,
                         BigDecimal rentAnnual, List<Line> lines) {
    }
}
