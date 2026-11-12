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

    /** il profilo non si modifica da qui: nome, cognome e iniziali sono quelli della configurazione utente gestita dall'amministratore */
    @PostMapping("/profile")
    public String save(RedirectAttributes ra) {
        ra.addFlashAttribute("err", "Il profilo non è modificabile: i dati sono gestiti dall'amministratore");
        return "redirect:/profile";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
