package it.cpqia.security;

import it.cpqia.domain.AppUser;
import it.cpqia.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Accesso SOLO per sviluppo (cpqia.dev-login=true), senza Google/Apple. Disattivato di default. */
@Controller
public class DevLoginController {

    private final UserRepository users;
    private final boolean enabled;
    private final String email;

    public DevLoginController(UserRepository users,
                              @Value("${cpqia.dev-login:false}") boolean enabled,
                              @Value("${cpqia.dev-login-email:admin@example.com}") String email) {
        this.users = users;
        this.enabled = enabled;
        this.email = email;
    }

    @GetMapping("/dev-login")
    public String login(HttpServletRequest request, HttpServletResponse response) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        AppUser u = users.findByEmail(email).orElseGet(() -> {
            long id = users.insert(email, "Dev", "Admin", "Dev Admin", "DA", null, "DEV", "dev", true, true);
            return users.findById(id).orElseThrow();
        });
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(u.email(), null, Permissions.of(u));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return "redirect:/";
    }
}
