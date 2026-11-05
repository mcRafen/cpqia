package it.cpqia.security;

import it.cpqia.domain.AppUser;
import it.cpqia.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Service
public class CurrentUserService {

    private static final String KEY = "cpqia.currentUser";

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    /** utente applicativo dell'autenticazione corrente (letto una sola volta per richiesta) */
    public AppUser get(Authentication auth) {
        if (auth == null) {
            throw new IllegalStateException("Non autenticato");
        }
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) {
            Object cached = ra.getAttribute(KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof AppUser u) {
                return u;
            }
        }
        String email = auth.getPrincipal() instanceof OidcUser o ? o.getEmail() : auth.getName();
        AppUser u = users.findByEmail(email).orElseThrow(() -> new IllegalStateException("Utente non trovato: " + email));
        if (ra != null) {
            ra.setAttribute(KEY, u, RequestAttributes.SCOPE_REQUEST);
        }
        return u;
    }
}
