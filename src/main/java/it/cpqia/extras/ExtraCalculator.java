package it.cpqia.extras;

import it.cpqia.extras.ExtraModel.Group;
import it.cpqia.extras.ExtraModel.Line;
import it.cpqia.extras.ExtraModel.Option;
import it.cpqia.extras.ExtraModel.OptionQty;
import it.cpqia.extras.ExtraModel.Selection;
import it.cpqia.extras.ExtraModel.Totals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Importi e verifiche dei servizi accessori scelti. Importi in una tantum e canone annuo, senza sconto. */
@Service
public class ExtraCalculator {

    private final ExtraRepository repo;

    public ExtraCalculator(ExtraRepository repo) {
        this.repo = repo;
    }

    /**
     * @param errors riceve i messaggi di scelte incomplete (es. fascia obbligatoria non selezionata)
     */
    public Totals calculate(Selection sel, List<String> errors) {
        List<ExtraModel.Service> catalog = repo.loadAll();
        Set<Long> services = new HashSet<>();
        Map<Long, Integer> options = new HashMap<>();
        if (sel != null) {
            if (sel.services() != null) {
                services.addAll(sel.services());
            }
            if (sel.options() != null) {
                for (OptionQty oq : sel.options()) {
                    if (oq.optionId() != null && (oq.qty() == null || oq.qty() > 0)) {
                        options.put(oq.optionId(), oq.qty() == null ? 1 : oq.qty());
                    }
                }
            }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal totOne = BigDecimal.ZERO;
        BigDecimal totAnn = BigDecimal.ZERO;
        for (ExtraModel.Service s : catalog) {
            if (!services.contains(s.id())) {
                continue;
            }
            BigDecimal one = BigDecimal.ZERO;
            BigDecimal ann = BigDecimal.ZERO;
            boolean adHoc = false;
            String adHocText = null;
            for (Group g : s.groups()) {
                int chosen = 0;
                for (Option o : g.options()) {
                    Integer q = "FIXED".equals(g.selection()) ? Integer.valueOf(1) : options.get(o.id());
                    if (q == null) {
                        continue;
                    }
                    chosen++;
                    int qty = o.qtyEnabled() ? q : 1;
                    if (o.onetime() != null) {
                        one = one.add(o.onetime().multiply(BigDecimal.valueOf(qty)));
                    }
                    if (o.annual() != null) {
                        ann = ann.add(o.annual().multiply(BigDecimal.valueOf(qty)));
                    }
                    if (o.adHoc()) {
                        adHoc = true;
                        adHocText = o.adHocText();
                    }
                }
                if ("ONE".equals(g.selection()) && chosen > 1 && errors != null) {
                    errors.add(s.title() + ": selezionare una sola riga in \"" + g.title() + "\"");
                }
                if (g.required() && chosen == 0 && errors != null) {
                    errors.add(s.title() + ": selezionare una riga in \"" + g.title() + "\"");
                }
            }
            one = one.setScale(2, java.math.RoundingMode.HALF_UP);
            ann = ann.setScale(2, java.math.RoundingMode.HALF_UP);
            lines.add(new Line(s.id(), s.code(), s.summaryName(), s.title(), one, ann, adHoc, adHocText, s.includedText()));
            totOne = totOne.add(one);
            totAnn = totAnn.add(ann);
        }
        if (services.isEmpty() && errors != null) {
            errors.add("Servizi accessori: seleziona almeno un servizio oppure togli la spunta “Aggiungi servizi accessori”");
        }
        return new Totals(totOne, totAnn, lines);
    }
}
