package it.cpqia.galileo;

import it.cpqia.galileo.GalileoModel.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** API del wizard per la pagina Galileo: mercati/profili, listino di un profilo (moduli, fasce, vincoli) e calcolo. */
@RestController
@RequestMapping("/offers/api/galileo")
public class GalileoController {

    private final GalileoService service;

    public GalileoController(GalileoService service) {
        this.service = service;
    }

    /** mercati Galileo con i loro profili e informazioni sul listino (versione, scadenza) */
    @GetMapping("/markets")
    public Map<String, Object> markets() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("markets", service.repository().markets());
        service.repository().listino().ifPresent(l -> {
            out.put("listino", l);
            out.put("expired", l.expired());
        });
        return out;
    }

    @GetMapping("/profile/{id}")
    public Map<String, Object> profile(@PathVariable long id) {
        Profile p = service.repository().profile(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profilo Galileo inesistente"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", p);
        out.put("rules", service.repository().rules(p.applicationId()));
        return out;
    }

    @PostMapping("/calc")
    public GalileoService.Calc calc(@RequestBody GalileoService.Request req) {
        return service.calculate(req);
    }
}
