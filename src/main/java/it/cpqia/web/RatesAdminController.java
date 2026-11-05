package it.cpqia.web;

import it.cpqia.rates.RateRepository;
import it.cpqia.rates.RateRepository.RateRow;
import it.cpqia.rates.RateTable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Gestione delle tabelle "Profili professionali" e "Trasferte Italia" (flag "gestione catalogo"). */
@Controller
@RequestMapping("/admin/rates")
public class RatesAdminController {

    public record Section(String path, String title, String nameLabel, String amountLabel, List<RateRow> rows) {
    }

    private final RateRepository rates;
    private final it.cpqia.trash.TrashService trash;
    private final it.cpqia.security.CurrentUserService current;

    public RatesAdminController(RateRepository rates, it.cpqia.trash.TrashService trash, it.cpqia.security.CurrentUserService current) {
        this.rates = rates;
        this.trash = trash;
        this.current = current;
    }

    @GetMapping
    public String list(Model model) {
        List<Section> sections = new ArrayList<>();
        for (RateTable t : RateTable.values()) {
            sections.add(new Section(t.path(), t.title(), t.nameLabel(), t.amountLabel(), rates.list(t, false)));
        }
        model.addAttribute("sections", sections);
        return "admin/rates";
    }

    @PostMapping("/{table}/save")
    public String save(@PathVariable String table, @RequestParam(required = false) Long id, @RequestParam String name,
                       @RequestParam String amount, @RequestParam(required = false) Integer sortOrder,
                       @RequestParam(required = false) String active, RedirectAttributes ra) {
        RateTable t = RateTable.byPath(table);
        try {
            BigDecimal value = parseAmount(amount);
            // nuova voce: attiva di default; modifica: segue la casella
            boolean isActive = id == null || active != null;
            rates.save(t, id, name, value, sortOrder, isActive);
            ra.addFlashAttribute("msg", t.title() + ": voce salvata");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/rates#" + t.path();
    }

    @PostMapping("/{table}/{id}/delete")
    public String delete(@PathVariable String table, @PathVariable long id, RedirectAttributes ra) {
        RateTable t = RateTable.byPath(table);
        trash.delete(t.trashKind(), id, current.get(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()));
        ra.addFlashAttribute("msg", t.title() + ": voce spostata nel Cestino");
        return "redirect:/admin/rates#" + t.path();
    }

    /** accetta "1.250,50" (formato italiano) e "1250.50" */
    static BigDecimal parseAmount(String s) {
        String v = s == null ? "" : s.trim().replace("€", "").trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("L'importo è obbligatorio");
        }
        if (v.contains(",")) {
            v = v.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Importo non valido: " + s);
        }
    }
}
