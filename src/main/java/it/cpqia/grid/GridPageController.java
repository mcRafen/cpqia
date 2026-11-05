package it.cpqia.grid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pagine che ospitano solo una griglia: Cestino (tutti gli utenti) e Sedi Sanmarco (solo administrator). */
@Controller
public class GridPageController {

    @GetMapping("/trash")
    public String trash(Model m) {
        m.addAttribute("title", "Cestino");
        m.addAttribute("active", "trash");
        m.addAttribute("gridKey", "trash");
        m.addAttribute("hint", "Qui trovi gli elementi che hai eliminato. Puoi ripristinarli; solo un amministratore può eliminarli definitivamente e vede gli elementi eliminati da tutti gli utenti.");
        return "grid/page";
    }

    @GetMapping("/admin/ibmi-products")
    public String ibmiProducts(Model m) {
        m.addAttribute("title", "Prodotti IBM i");
        m.addAttribute("active", "ibmi-products");
        m.addAttribute("gridKey", "ibmi-products");
        m.addAttribute("hint", "Prodotti software IBM i (codice e descrizione). Doppio clic su una cella per modificarla; i prodotti eliminati vanno nel Cestino.");
        return "grid/page";
    }

    @GetMapping("/admin/power-models")
    public String powerModels(Model m) {
        m.addAttribute("title", "Modelli IBM Power System");
        m.addAttribute("active", "power");
        m.addAttribute("gridKey", "power-models");
        m.addAttribute("hint", "Anagrafica dei modelli IBM Power System (modello, tipo, core, montaggio, versione, CPW). Doppio clic su una cella per modificarla; i modelli eliminati vanno nel Cestino.");
        return "grid/page";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/sedi")
    public String sedi(Model m) {
        m.addAttribute("title", "Sedi Sanmarco");
        m.addAttribute("active", "sedi");
        m.addAttribute("gridKey", "sedi");
        m.addAttribute("hint", "Doppio clic su una cella per modificarla (Invio conferma, Esc annulla). Le sedi eliminate vanno nel Cestino.");
        return "grid/page";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/agents")
    public String agents(Model m) {
        m.addAttribute("title", "Agenti");
        m.addAttribute("active", "agents");
        m.addAttribute("gridKey", "agents");
        m.addAttribute("hint", "Elenco alimentato dalla «Sync Agenti» (Impostazioni > Integrazioni): da IBM Power System, file SMTAB01L, tipo tabella 01AG.");
        return "grid/page";
    }
}
