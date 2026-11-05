package it.cpqia.extras;

import java.math.BigDecimal;
import java.util.List;

/** Servizi accessori (Audit, Discovery Xchange, Firmae, CRC): servizi, gruppi di scelta e righe (opzioni) con i relativi importi. */
public final class ExtraModel {

    private ExtraModel() {
    }

    public record Option(long id, long groupId, String rowKey, String articleCode, String label, BigDecimal onetime,
                         BigDecimal annual, BigDecimal extraUnit, boolean adHoc, String adHocText, boolean qtyEnabled) {
    }

    /** selection: FIXED = tutte le righe incluse; ONE = una sola riga da scegliere; MANY = zero o piu' righe */
    public record Group(long id, long serviceId, String code, String title, String selection, boolean required,
                        List<Option> options) {
    }

    public record Service(long id, String code, String title, String summaryName, String includedText, List<Group> groups) {
    }

    /** scelta dell'utente nel configuratore */
    public record OptionQty(Long optionId, Integer qty) {
    }

    public record Selection(List<Long> services, List<OptionQty> options) {
        public static Selection empty() {
            return new Selection(List.of(), List.of());
        }
    }

    /** riga calcolata di un servizio selezionato */
    public record Line(long serviceId, String serviceCode, String name, String title, BigDecimal onetime, BigDecimal annual,
                       boolean adHoc, String adHocText, String includedText) {
    }

    public record Totals(BigDecimal onetime, BigDecimal annual, List<Line> lines) {
    }
}
