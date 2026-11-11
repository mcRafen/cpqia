package it.cpqia.terms;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Modalita' di fatturazione e pagamento dell'offerta e relativo elenco di opzioni (offer-terms.json). */
public final class TermsModel {

    private TermsModel() {
    }

    public record BillOption(String code, String label, boolean date) {
    }

    public record PayOption(String code, String label, boolean free) {
    }

    public record Config(List<BillOption> licBill, List<BillOption> feeBill, List<BillOption> actBill, List<PayOption> pay,
                         List<String> payLic, List<String> payFee, Map<String, List<String>> payAct, Map<String, String> defaults,
                         List<BillOption> driBill, List<String> payDri) {
    }

    /** nomi dei componenti = colonne della tabella offer (snake_case) */
    public record PaymentTerms(String billLic, LocalDate billLicDate, String billFee, LocalDate billFeeDate,
                               boolean feeSemiannual, String billAct, String payLic, String payLicOther,
                               String payFee, String payFeeOther, String payAct, String payActOther,
                               String billDri, LocalDate billDriDate, String payDri, String payDriOther) {
    }
}
