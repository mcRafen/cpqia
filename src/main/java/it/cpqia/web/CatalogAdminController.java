package it.cpqia.web;

import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.repo.CatalogAdminRepository;
import it.cpqia.repo.CatalogRepository;
import it.cpqia.security.CurrentUserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Anagrafica applicazioni e sotto-moduli (accessibile con il flag "gestione catalogo"). */
@Controller
@RequestMapping("/admin/applications")
public class CatalogAdminController {

    private final CatalogRepository catalog;
    private final CatalogAdminRepository admin;
    private final CurrentUserService current;
    private final ModuleDocumentService moduleDocs;

    public CatalogAdminController(CatalogRepository catalog, CatalogAdminRepository admin, CurrentUserService current, ModuleDocumentService moduleDocs) {
        this.moduleDocs = moduleDocs;
        this.catalog = catalog;
        this.admin = admin;
        this.current = current;
    }

    private it.cpqia.domain.AppUser me() {
        return current.get(SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("apps", catalog.loadAll(false));
        return "admin/applications";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("app", null);
        return "admin/application-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        model.addAttribute("app", catalog.find(id).orElseThrow());
        model.addAttribute("basePrices", admin.basePrices(id));
        return "admin/application-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> p, RedirectAttributes ra) {
        Long id = p.get("id") == null || p.get("id").isBlank() ? null : Long.valueOf(p.get("id"));
        try {
            long saved = admin.saveApplication(id, p);
            ra.addFlashAttribute("msg", "Applicazione salvata");
            return "redirect:/admin/applications/" + saved;
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
            return id == null ? "redirect:/admin/applications/new" : "redirect:/admin/applications/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes ra) {
        try {
            admin.deleteApplication(id, me());
            ra.addFlashAttribute("msg", "Applicazione spostata nel Cestino");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/applications";
    }

    // ------------------------------------------------------------------ dettaglio
    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        App app = catalog.find(id).orElseThrow();
        Map<Long, String> priceText = new LinkedHashMap<>();
        for (Module m : app.modules()) {
            priceText.put(m.id(), CatalogAdminRepository.formatPrices(m.prices()));
        }
        Map<Long, String> tierText = new LinkedHashMap<>();
        for (it.cpqia.pricing.PricingModel.Group g : app.groups()) {
            for (it.cpqia.pricing.PricingModel.Tier t : g.tiers()) {
                tierText.put(t.id(), CatalogAdminRepository.formatPrices(t.unitByMode()));
            }
        }
        model.addAttribute("app", app);
        model.addAttribute("priceText", priceText);
        model.addAttribute("tierText", tierText);
        model.addAttribute("areas", admin.moduleAreas(id));
        return "admin/application";
    }

    @PostMapping("/{id}/modes/add")
    public String addMode(@PathVariable long id, @RequestParam String code, @RequestParam String label,
                          @RequestParam(required = false) String preferred, RedirectAttributes ra) {
        return run(id, ra, () -> admin.addMode(id, code, label, preferred != null));
    }

    @PostMapping("/{id}/modes/{code}/delete")
    public String deleteMode(@PathVariable long id, @PathVariable String code, RedirectAttributes ra) {
        return run(id, ra, () -> admin.deleteMode(id, code, me()));
    }

    @PostMapping("/{id}/drivers/add")
    public String addDriver(@PathVariable long id, @RequestParam String code, @RequestParam String label,
                            @RequestParam(required = false) String licenseCode, @RequestParam(required = false) String unitLabel,
                            RedirectAttributes ra) {
        return run(id, ra, () -> admin.addDriver(id, code, label, licenseCode, unitLabel));
    }

    @PostMapping("/{id}/drivers/{did}/delete")
    public String deleteDriver(@PathVariable long id, @PathVariable long did, RedirectAttributes ra) {
        return run(id, ra, () -> admin.deleteDriver(did, me()));
    }

    @PostMapping("/{id}/groups/add")
    public String addGroup(@PathVariable long id, @RequestParam long driverId, @RequestParam String code,
                           @RequestParam String label, @RequestParam(defaultValue = "TIER1") String baseMode,
                           @RequestParam(required = false) String fixedBase, RedirectAttributes ra) {
        return run(id, ra, () -> admin.addGroup(id, driverId, code, label, baseMode, fixedBase));
    }

    @PostMapping("/{id}/groups/{gid}/delete")
    public String deleteGroup(@PathVariable long id, @PathVariable long gid, RedirectAttributes ra) {
        return run(id, ra, () -> admin.deleteGroup(gid, me()));
    }

    @PostMapping("/{id}/tiers/save")
    public String saveTier(@PathVariable long id, @RequestParam(required = false) Long tierId, @RequestParam long groupId,
                           @RequestParam long fromQty, @RequestParam(required = false) Long toQty, @RequestParam String label,
                           @RequestParam(required = false) String pct, @RequestParam(required = false) String unitPrices,
                           RedirectAttributes ra) {
        BigDecimal p = pct == null || pct.isBlank() ? null : new BigDecimal(pct.trim().replace(',', '.'));
        return run(id, ra, () -> admin.saveTier(tierId, groupId, fromQty, toQty, label, p, unitPrices));
    }

    @PostMapping("/{id}/tiers/{tid}/delete")
    public String deleteTier(@PathVariable long id, @PathVariable long tid, RedirectAttributes ra) {
        return run(id, ra, () -> admin.deleteTier(tid, me()));
    }

    // ------------------------------------------------------------------ moduli
    @GetMapping("/{id}/modules/new")
    public String newModule(@PathVariable long id, Model model) {
        model.addAttribute("app", catalog.find(id).orElseThrow());
        model.addAttribute("module", null);
        model.addAttribute("priceText", "");
        return "admin/module-form";
    }

    @GetMapping("/{id}/modules/{mid}")
    public String editModule(@PathVariable long id, @PathVariable long mid, Model model) {
        App app = catalog.find(id).orElseThrow();
        Module m = app.module(mid);
        if (m == null) {
            throw new IllegalArgumentException("Modulo inesistente");
        }
        model.addAttribute("app", app);
        model.addAttribute("module", m);
        model.addAttribute("priceText", CatalogAdminRepository.formatPrices(m.prices()));
        model.addAttribute("moduleArea", admin.moduleAreas(id).get(mid));
        model.addAttribute("moduleDoc", moduleDocs.currentFile(mid));
        return "admin/module-form";
    }

    @PostMapping("/{id}/modules/save")
    public String saveModule(@PathVariable long id, @RequestParam Map<String, String> p, RedirectAttributes ra) {
        Long mid = p.get("id") == null || p.get("id").isBlank() ? null : Long.valueOf(p.get("id"));
        try {
            long saved = admin.saveModule(mid, id, p);
            ra.addFlashAttribute("msg", "Modulo salvato");
            return "redirect:/admin/applications/" + id + "/modules/" + saved;
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
            return mid == null ? "redirect:/admin/applications/" + id + "/modules/new"
                    : "redirect:/admin/applications/" + id + "/modules/" + mid;
        }
    }

    /** elimina (nel Cestino) i moduli selezionati */
    @PostMapping("/{id}/modules/delete-many")
    public String deleteModules(@PathVariable long id, @RequestParam("ids") java.util.List<Long> ids, RedirectAttributes ra) {
        try {
            for (Long mid : ids) {
                admin.deleteModule(mid, me());
            }
            ra.addFlashAttribute("msg", ids.size() + (ids.size() == 1 ? " modulo eliminato" : " moduli eliminati"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
        }
        return "redirect:/admin/applications/" + id;
    }

    @PostMapping("/{id}/modules/{mid}/delete")
    public String deleteModule(@PathVariable long id, @PathVariable long mid, RedirectAttributes ra) {
        return run(id, ra, () -> admin.deleteModule(mid, me()));
    }

    @PostMapping("/{id}/modules/{mid}/rules/add")
    public String addRule(@PathVariable long id, @PathVariable long mid, @RequestParam String type,
                          @RequestParam(required = false) String targets, @RequestParam String severity,
                          @RequestParam String message, RedirectAttributes ra) {
        try {
            admin.addRule(mid, type, targets, severity, message);
            ra.addFlashAttribute("msg", "Vincolo aggiunto");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
        }
        return "redirect:/admin/applications/" + id + "/modules/" + mid;
    }

    @PostMapping("/{id}/modules/{mid}/rules/{rid}/update")
    public String updateRule(@PathVariable long id, @PathVariable long mid, @PathVariable long rid, @RequestParam String type,
                             @RequestParam(required = false) String targets, @RequestParam String severity,
                             @RequestParam String message, RedirectAttributes ra) {
        try {
            admin.updateRule(rid, mid, type, targets, severity, message);
            ra.addFlashAttribute("msg", "Vincolo aggiornato");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
        }
        return "redirect:/admin/applications/" + id + "/modules/" + mid;
    }

    @PostMapping("/{id}/modules/{mid}/rules/{rid}/delete")
    public String deleteRule(@PathVariable long id, @PathVariable long mid, @PathVariable long rid, RedirectAttributes ra) {
        admin.deleteRule(rid, me());
        ra.addFlashAttribute("msg", "Vincolo eliminato");
        return "redirect:/admin/applications/" + id + "/modules/" + mid;
    }

    // ------------------------------------------------------------------ helper
    private String run(long appId, RedirectAttributes ra, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("msg", "Modifica salvata");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", message(e));
        }
        return "redirect:/admin/applications/" + appId;
    }

    private static String message(RuntimeException e) {
        if (e instanceof DataIntegrityViolationException d) {
            return "Operazione non consentita: dati duplicati o in uso (" + d.getMostSpecificCause().getMessage() + ")";
        }
        return e.getMessage();
    }
}
