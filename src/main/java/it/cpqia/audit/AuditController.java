package it.cpqia.audit;

import it.cpqia.domain.AppUser;
import it.cpqia.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Pagina «Storico modifiche» di un record (raggiunta dal menu a tre puntini delle griglie). */
@Controller
public class AuditController {

    private final AuditService audit;
    private final CurrentUserService current;

    public AuditController(AuditService audit, CurrentUserService current) {
        this.audit = audit;
        this.current = current;
    }

    @GetMapping("/audit/{table}/{id}")
    public String history(@PathVariable String table, @PathVariable String id, Authentication auth, Model m) {
        return page(table, id, current.get(auth), m);
    }

    /** dal Cestino: l'identificativo della riga e' «TIPO:id» */
    @GetMapping("/audit/trash/{ref}")
    public String trashHistory(@PathVariable String ref, Authentication auth, Model m) {
        String[] ti = audit.fromTrashRef(ref).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return page(ti[0], ti[1], current.get(auth), m);
    }

    private String page(String table, String id, AppUser u, Model m) {
        if (!audit.canSee(u, table, id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorizzato");
        }
        m.addAttribute("h", audit.history(table, id));
        m.addAttribute("title", "Storico modifiche");
        m.addAttribute("active", "");
        return "audit/history";
    }
}
