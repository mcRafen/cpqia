package it.cpqia.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.terms.TermsModel.BillOption;
import it.cpqia.terms.TermsModel.Config;
import it.cpqia.terms.TermsModel.PayOption;
import it.cpqia.terms.TermsModel.PaymentTerms;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Opzioni, valori predefiniti, validazione e testi (per il Word) delle modalita' di fatturazione e pagamento. */
@Component
public class TermsConfig {

    private static final DateTimeFormatter IT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Config config;

    public TermsConfig(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("offer-terms.json").getInputStream()) {
            this.config = mapper.readValue(in, Config.class);
        }
    }

    public Config config() {
        return config;
    }

    /** predefiniti: pagamento RI.BA. 30 gg dffm per un prospect, "solito in uso" per un cliente */
    public PaymentTerms defaultsFor(String customerType) {
        String pay = "CLIENTE".equals(customerType) ? config.defaults().get("payCustomer") : config.defaults().get("payProspect");
        return new PaymentTerms(config.defaults().get("billLic"), null, config.defaults().get("billFee"), null, false,
                config.defaults().get("billAct"), pay, null, pay, null, pay, null,
                config.defaults().get("billDri"), null, pay, null);
    }

    /** errori di compilazione (vuota = valido); "Solito in uso" e' ammesso solo per i clienti, mai per i prospect */
    public List<String> validate(PaymentTerms t, String customerType) {
        return validate(t, customerType, false);
    }

    /** dri = l'offerta comprende SMIDRI e/o SMISMP: serve anche la modalita' del relativo canone */
    public List<String> validate(PaymentTerms t, String customerType, boolean dri) {
        List<String> e = new ArrayList<>();
        BillOption lic = find(config.licBill(), t.billLic());
        if (lic == null) {
            e.add("Modalità di fatturazione delle licenze non valida");
        } else if (lic.date() && t.billLicDate() == null) {
            e.add("Fatturazione licenze: indicare la data");
        }
        BillOption fee = find(config.feeBill(), t.billFee());
        if (fee == null) {
            e.add("Modalità di fatturazione dei canoni non valida");
        } else if (fee.date() && t.billFeeDate() == null) {
            e.add("Fatturazione canoni: indicare la data");
        }
        if (find(config.actBill(), t.billAct()) == null) {
            e.add("Modalità di fatturazione delle attività sistemistiche non valida");
        }
        checkPay(e, "licenze", t.payLic(), t.payLicOther(), allowedFor(config.payLic(), customerType));
        checkPay(e, "canoni", t.payFee(), t.payFeeOther(), allowedFor(config.payFee(), customerType));
        List<String> actAllowed = config.payAct().get(t.billAct() == null ? "" : t.billAct());
        checkPay(e, "attività sistemistiche", t.payAct(), t.payActOther(), allowedFor(actAllowed == null ? List.of() : actAllowed, customerType));
        if (dri) {
            BillOption d = find(config.driBill(), t.billDri());
            if (d == null) {
                e.add("Modalità di fatturazione del canone SMIDRI/SMISMP non valida");
            } else if (d.date() && t.billDriDate() == null) {
                e.add("Fatturazione canone SMIDRI/SMISMP: indicare la data");
            }
            checkPay(e, "canone SMIDRI/SMISMP", t.payDri(), t.payDriOther(), allowedFor(config.payDri(), customerType));
        }
        return e;
    }

    /** opzioni di pagamento ammesse per il tipo di account: senza "Solito in uso" per i prospect */
    static List<String> allowedFor(List<String> codes, String customerType) {
        if ("CLIENTE".equals(customerType)) {
            return codes;
        }
        List<String> out = new ArrayList<>(codes);
        out.remove("USUAL");
        return out;
    }

    private void checkPay(List<String> e, String what, String code, String other, List<String> allowed) {
        if (code == null || !allowed.contains(code)) {
            e.add("Pagamento " + what + ": opzione non valida");
            return;
        }
        PayOption o = pay(code);
        if (o != null && o.free() && (other == null || other.isBlank())) {
            e.add("Pagamento " + what + ": scrivere la modalità");
        }
    }

    // ------------------------------------------------------------------ testi per il documento
    public String billLicText(PaymentTerms t) {
        return billText(config.licBill(), t.billLic(), t.billLicDate());
    }

    /** testo della cella FATTURAZIONE dei canoni, con "Semestrale" se richiesto */
    public String billFeeText(PaymentTerms t) {
        String base = billText(config.feeBill(), t.billFee(), t.billFeeDate());
        return t.feeSemiannual() ? base + " – fatturazione semestrale" : base;
    }

    /** cella FATTURAZIONE del canone SMIDRI/SMISMP: «Mese di installazione» o «In data gg/mm/aaaa» */
    public String billDriText(PaymentTerms t) {
        return billText(config.driBill(), t.billDri(), t.billDriDate());
    }

    public String billActText(PaymentTerms t) {
        BillOption o = find(config.actBill(), t.billAct());
        return o == null ? "" : o.label();
    }

    public String payText(String code, String other) {
        PayOption o = pay(code);
        if (o == null) {
            return "";
        }
        return o.free() ? (other == null ? "" : other.trim()) : o.label();
    }

    private String billText(List<BillOption> options, String code, LocalDate date) {
        BillOption o = find(options, code);
        if (o == null) {
            return "";
        }
        return o.date() ? "In data " + (date == null ? "" : date.format(IT_DATE)) : o.label();
    }

    private static BillOption find(List<BillOption> list, String code) {
        for (BillOption o : list) {
            if (o.code().equals(code)) {
                return o;
            }
        }
        return null;
    }

    private PayOption pay(String code) {
        for (PayOption o : config.pay()) {
            if (o.code().equals(code)) {
                return o;
            }
        }
        return null;
    }
}
