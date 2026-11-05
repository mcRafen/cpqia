package it.cpqia.geodata;

import it.cpqia.geodata.GeoDataService.ComuneHit;
import it.cpqia.geodata.GeoDataService.ProvinciaHit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Ricerca comuni/province per i moduli (tutti gli utenti) e pagina di amministrazione (administrator). */
@Controller
public class GeoDataController {

    private final GeoDataService geo;

    public GeoDataController(GeoDataService geo) {
        this.geo = geo;
    }

    @GetMapping("/geo/api/comuni")
    @ResponseBody
    public List<ComuneHit> comuni(@RequestParam String q, @RequestParam(required = false) String prov) {
        return geo.comuni(q, prov, 20);
    }

    @GetMapping("/geo/api/province")
    @ResponseBody
    public List<ProvinciaHit> province(@RequestParam(defaultValue = "") String q) {
        return geo.province(q, 30);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/geodata")
    public String page(Model m) {
        m.addAttribute("active", "geodata");
        m.addAttribute("title", "Comuni");
        return "admin/geodata";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/lookups")
    public String lookups(Model m) {
        m.addAttribute("active", "lookups");
        m.addAttribute("title", "Tipo azienda e tipo indirizzo");
        return "admin/lookups";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/geodata/import")
    public String importNow(RedirectAttributes ra) {
        try {
            var r = geo.importNow();
            ra.addFlashAttribute("msg", r.comuni() + " comuni (" + r.province() + " province) importati da internet");
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Importazione non riuscita: " + e.getMessage() + ". Il server deve poter raggiungere l'indirizzo configurato in cpqia.geodata.url");
        }
        return "redirect:/admin/geodata";
    }
}
