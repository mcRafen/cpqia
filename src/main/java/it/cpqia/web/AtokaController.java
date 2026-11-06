package it.cpqia.web;

import it.cpqia.integration.AtokaService;
import it.cpqia.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dettagli azienda da ATOKA per partita IVA (cliccando sulla P.IVA in anagrafica account e nella testata offerta). */
@RestController
@RequestMapping("/api/atoka")
public class AtokaController {

    public record ConnectForm(String token, String vat) {
    }

    private final AtokaService atoka;
    private final CurrentUserService current;

    public AtokaController(AtokaService atoka, CurrentUserService current) {
        this.atoka = atoka;
        this.current = current;
    }

    /** collegato o no; solo l'administrator puo' (ri)collegare */
    @GetMapping("/status")
    public Map<String, Object> status(Authentication auth) {
        AtokaService.Status s = atoka.status();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", s.configured());
        m.put("canConfigure", current.get(auth).administrator());
        m.put("lastTestMsg", s.lastTestMsg());
        return m;
    }

    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestBody ConnectForm f, Authentication auth) {
        var u = current.get(auth);
        if (!u.administrator()) {
            throw new org.springframework.security.access.AccessDeniedException("Solo l'amministratore può collegare ATOKA");
        }
        return result(atoka.connect(f.token(), f.vat(), u.id()));
    }

    @GetMapping("/company")
    public Map<String, Object> company(@RequestParam String vat, Authentication auth) {
        current.get(auth);                                       // utente autenticato
        return result(atoka.lookup(vat));
    }

    private static Map<String, Object> result(AtokaService.Lookup r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", r.ok());
        m.put("message", r.message());
        m.put("company", r.company());
        return m;
    }
}
