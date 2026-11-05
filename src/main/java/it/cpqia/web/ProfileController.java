package it.cpqia.web;

import it.cpqia.domain.AppUser;
import it.cpqia.repo.UserRepository;
import it.cpqia.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Profilo personale: nome, cognome e iniziali (usate nel numero offerta). */
@Controller
public class ProfileController {

    private final CurrentUserService current;
    private final UserRepository users;

    public ProfileController(CurrentUserService current, UserRepository users) {
        this.current = current;
        this.users = users;
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @PostMapping("/profile")
    public String save(@RequestParam(required = false) String firstName, @RequestParam(required = false) String lastName,
                       @RequestParam String initials, Authentication auth, RedirectAttributes ra) {
        AppUser me = current.get(auth);
        String ini = initials == null ? "" : initials.trim().toUpperCase();
        if (ini.isEmpty() || ini.length() > 6) {
            ra.addFlashAttribute("err", "Le iniziali sono obbligatorie (max 6 caratteri)");
            return "redirect:/profile";
        }
        users.updateProfile(me.id(), blank(firstName), blank(lastName), ini);
        ra.addFlashAttribute("msg", "Profilo aggiornato");
        return "redirect:/profile";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
