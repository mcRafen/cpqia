package it.cpqia.web;

import it.cpqia.domain.AppUser;
import it.cpqia.repo.UserRepository;
import it.cpqia.security.CurrentUserService;
import it.cpqia.security.UserProvisioningService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Gestione utenti applicativi e flag di abilitazione (solo administrator). */
@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserRepository users;
    private final CurrentUserService current;
    private final it.cpqia.trash.TrashService trash;
    private final it.cpqia.grid.GridRegistry gridRegistry;
    private final it.cpqia.grid.GridExportPermissionService exportPerm;

    public record GridItem(String key, String title) {
    }

    public UserAdminController(UserRepository users, CurrentUserService current, it.cpqia.trash.TrashService trash,
                               it.cpqia.grid.GridRegistry gridRegistry, it.cpqia.grid.GridExportPermissionService exportPerm) {
        this.gridRegistry = gridRegistry;
        this.exportPerm = exportPerm;
        this.users = users;
        this.current = current;
        this.trash = trash;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", users.findAll());
        return "admin/users";
    }

    /** griglie dell'applicazione (per l'abilitazione all'esportazione), in ordine di titolo */
    private java.util.List<GridItem> gridItems() {
        return gridRegistry.all().stream().map(s -> new GridItem(s.key(), s.title()))
                .sorted(java.util.Comparator.comparing(GridItem::title, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("u", null);
        model.addAttribute("agents", users.agents());
        model.addAttribute("grids", gridItems());
        model.addAttribute("exportKeys", java.util.Set.of());
        return "admin/user-form";
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable long id, Model model) {
        model.addAttribute("u", users.findById(id).orElseThrow());
        model.addAttribute("agents", users.agents());
        model.addAttribute("grids", gridItems());
        model.addAttribute("exportKeys", exportPerm.keys(id));
        return "admin/user-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        try {
            String email = p.getOrDefault("email", "").trim();
            String first = nz(p.get("firstName"));
            String last = nz(p.get("lastName"));
            String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            String initials = nz(p.get("initials"));
            if (initials == null) {
                initials = UserProvisioningService.initialsOf(first, last, email);
            }
            String idStr = p.get("id");
            long id;
            if (idStr == null || idStr.isBlank()) {
                if (email.isEmpty() || !email.contains("@")) {
                    throw new IllegalArgumentException("Email non valida");
                }
                id = users.insert(email, first, last, full.isEmpty() ? null : full, initials.toUpperCase(), null, null, null,
                        true, false);
            } else {
                id = Long.parseLong(idStr);
            }
            String agent = nz(p.get("agentCode"));
            if (agent != null && !users.agentExists(agent)) {
                throw new IllegalArgumentException("Codice agente «" + agent + "» non presente nella tabella Agenti");
            }
            AppUser old = users.findById(id).orElseThrow();
            boolean adm = flag(p, "administrator");
            boolean active = flag(p, "active");
            if (old.administrator() && (!adm || !active) && countAdmins() <= 1) {
                throw new IllegalArgumentException("Deve rimanere almeno un amministratore attivo");
            }
            AppUser u = new AppUser(id, old.email(), first, last, full.isEmpty() ? null : full, initials.toUpperCase(),
                    old.pictureUrl(), old.authProvider(), old.providerSubject(), active, adm, flag(p, "canCreateOffers"),
                    flag(p, "canEditOffers"), flag(p, "canViewAllOffers"), flag(p, "canDeleteOffers"), flag(p, "canExportDocx"),
                    flag(p, "canManageCustomers"), flag(p, "canManageCatalog"), flag(p, "canViewPrices"), old.createdAt(), old.lastLoginAt(), agent);
            users.updateAdmin(u);
            // griglie su cui l'utente puo' esportare i dati: «tutte» oppure le singole griglie spuntate
            java.util.List<String> exp = new java.util.ArrayList<>();
            if (flag(p, "exportAll")) {
                exp.add(it.cpqia.grid.GridExportPermissionService.ALL);
            } else {
                for (GridItem g : gridItems()) {
                    if (flag(p, "exp_" + g.key())) {
                        exp.add(g.key());
                    }
                }
            }
            exportPerm.save(id, exp);
            ra.addFlashAttribute("msg", "Utente salvato (le modifiche ai permessi valgono dal prossimo accesso)");
            return "redirect:/admin/users";
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("err", "Esiste gia' un utente con questa email");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        String id = p.get("id");
        return id == null || id.isBlank() ? "redirect:/admin/users/new" : "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, Authentication auth, RedirectAttributes ra) {
        try {
            trash.delete(it.cpqia.trash.TrashKind.USER, id, current.get(auth));
            ra.addFlashAttribute("msg", "Utente spostato nel Cestino");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    private long countAdmins() {
        return users.findAll().stream().filter(u -> u.administrator() && u.active()).count();
    }

    private static boolean flag(Map<String, String> p, String k) {
        String v = p.get(k);
        return v != null && (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true"));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
