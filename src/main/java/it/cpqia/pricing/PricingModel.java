package it.cpqia.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Modello di calcolo del catalogo (indipendente dal database, usato dal motore e dai test). */
public final class PricingModel {

    private PricingModel() {
    }

    public record Tier(long id, long fromQty, Long toQty, String label, BigDecimal pct, Map<String, BigDecimal> unitByMode) {
    }

    /** Gruppo di prezzo: i moduli del gruppo sommano la base su cui si applicano le fasce del driver. */
    public record Group(long id, String code, String label, long driverId, String baseMode,
                        Map<String, BigDecimal> fixedBase, List<Tier> tiers) {
    }

    public record Driver(long id, String code, String label, String licenseCode, String unitLabel) {
    }

    public record Mode(String code, String label, boolean preferred) {
    }

    public record Rule(long id, String type, String targetCodes, String severity, String message) {
    }

    public record Module(long id, String code, String printCode, String description, Long groupId, String section,
                         String kind, boolean qtyEnabled, String exclGroup, boolean defaultSelected, boolean included,
                         BigDecimal maintenancePct, BigDecimal onetimePrice, String linkText, String note,
                         Map<String, BigDecimal> prices, List<Rule> rules, String area) {

        public String codeForPrint() {
            return printCode != null && !printCode.isBlank() ? printCode : code;
        }
    }

    public record App(long id, String code, String name, String family, BigDecimal maintenancePct, boolean needsReview,
                      boolean active, String notes, List<Mode> modes, List<Driver> drivers, List<Group> groups, List<Module> modules) {

        public Module module(long id) {
            for (Module m : modules) {
                if (m.id() == id) {
                    return m;
                }
            }
            return null;
        }

        public Driver driver(long id) {
            for (Driver d : drivers) {
                if (d.id() == id) {
                    return d;
                }
            }
            return null;
        }

        public String defaultMode() {
            for (Mode m : modes) {
                if (m.preferred()) {
                    return m.code();
                }
            }
            return modes.isEmpty() ? null : modes.get(0).code();
        }
    }

    /** Una riga di riepilogo del calcolo (per UI e per il documento). */
    public record Row(String label, BigDecimal amount) {
    }

    /** maintenance = manutenzione % + canoni annui; annual = solo la parte dei servizi a canone annuo (annuali e mensili x 12) */
    public record Result(BigDecimal license, BigDecimal onetime, BigDecimal maintenance, BigDecimal rentMonthly,
                         List<Row> rows, BigDecimal annual) {

        /** importo "licenze" esposto in offerta: licenze + una tantum */
        public BigDecimal licenseWithOnetime() {
            return license.add(onetime);
        }
    }
}
