package it.cpqia.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Registra nel thread l'email dell'utente autenticato (il filtro gira dopo la catena di Spring Security). */
@Component
public class AuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
            AuditContext.set(a.getPrincipal() instanceof OidcUser o ? o.getEmail() : a.getName());
        }
        try {
            chain.doFilter(req, res);
        } finally {
            AuditContext.clear();
        }
    }
}
