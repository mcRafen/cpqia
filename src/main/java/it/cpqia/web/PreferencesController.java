package it.cpqia.web;

import it.cpqia.security.CurrentUserService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Preferenze personali dell'utente (menu in alto a destra › Preferenze). */
@Controller
public class PreferencesController {

    public static final int DEFAULT_RECENT = 5;
    public static final int MAX_RECENT = 20;

    private final JdbcClient jdbc;
    private final CurrentUserService current;

    public PreferencesController(JdbcClient jdbc, CurrentUserService current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    /** numero di «Ultime offerte gestite» da mostrare nel menu laterale (0 = nascoste) */
    public int recentCount(long userId) {
        Integer n = jdbc.sql("select recent_offers from user_preference where user_id = :u").param("u", userId)
                .query((rs, i) -> (Integer) rs.getObject(1)).optional().orElse(null);
        return n == null ? DEFAULT_RECENT : n;
    }

    @GetMapping("/preferences")
    public String page(Authentication auth, Model model) {
        model.addAttribute("recentCount", recentCount(current.get(auth).id()));
        model.addAttribute("maxRecent", MAX_RECENT);
        return "preferences";
    }

    @PostMapping("/preferences")
    public String save(@RequestParam(required = false) String recentOffers, Authentication auth, RedirectAttributes ra) {
        int n;
        try {
            n = Integer.parseInt(recentOffers == null ? "" : recentOffers.trim());
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("err", "Indica un numero intero tra 0 e " + MAX_RECENT);
            return "redirect:/preferences";
        }
        if (n < 0 || n > MAX_RECENT) {
            ra.addFlashAttribute("err", "Il numero di ultime offerte deve essere tra 0 e " + MAX_RECENT);
            return "redirect:/preferences";
        }
        jdbc.sql("""
                insert into user_preference (user_id, recent_offers) values (:u, :n)
                on conflict (user_id) do update set recent_offers = :n, updated_at = now()""")
                .param("u", current.get(auth).id()).param("n", n).update();
        ra.addFlashAttribute("msg", "Preferenze salvate");
        return "redirect:/preferences";
    }
}
