package it.cpqia.security;

import it.cpqia.domain.AppUser;
import it.cpqia.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Collega l'identita' Google/Apple all'anagrafica utenti applicativi (che governa le abilitazioni). */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final String bootstrapAdmin;
    private final boolean autoRegister;

    public UserProvisioningService(UserRepository users,
                                   @Value("${cpqia.bootstrap-admin-email:}") String bootstrapAdmin,
                                   @Value("${cpqia.auto-register:true}") boolean autoRegister) {
        this.users = users;
        this.bootstrapAdmin = bootstrapAdmin == null ? "" : bootstrapAdmin.trim();
        this.autoRegister = autoRegister;
    }

    public AppUser resolve(String provider, String subject, String email, String givenName, String familyName,
                           String fullName, String picture) {
        if (email == null || email.isBlank()) {
            throw denied("Il provider di accesso non ha fornito un indirizzo email.");
        }
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            AppUser u = existing.get();
            if (!u.active()) {
                throw denied("Utente non abilitato: contatta un amministratore.");
            }
            users.touchLogin(u.id(), provider.toUpperCase(), subject, picture);
            if ((u.initials() == null || u.initials().isBlank()) || u.fullName() == null) {
                String fn = u.firstName() != null ? u.firstName() : givenName;
                String ln = u.lastName() != null ? u.lastName() : familyName;
                String ini = u.initials() != null && !u.initials().isBlank() ? u.initials() : initialsOf(givenName, familyName, email);
                users.updateProfile(u.id(), fn, ln, ini);
            }
            return users.findByEmail(email).orElse(u);
        }
        boolean isBootstrap = !bootstrapAdmin.isEmpty() && bootstrapAdmin.equalsIgnoreCase(email);
        if (!isBootstrap && !autoRegister) {
            throw denied("Utente non censito: contatta un amministratore.");
        }
        String name = fullName != null ? fullName : ((givenName == null ? "" : givenName) + " " + (familyName == null ? "" : familyName)).trim();
        long id = users.insert(email, givenName, familyName, name.isBlank() ? null : name,
                initialsOf(givenName, familyName, email), picture, provider.toUpperCase(), subject, isBootstrap, isBootstrap);
        if (!isBootstrap) {
            throw denied("Registrazione ricevuta: un amministratore deve abilitare il tuo utente.");
        }
        users.touchLogin(id, provider.toUpperCase(), subject, picture);
        return users.findById(id).orElseThrow();
    }

    /** iniziali di nome e cognome (es. Enrico Rossi -> ER); fallback sulla parte locale dell'email */
    public static String initialsOf(String given, String family, String email) {
        StringBuilder sb = new StringBuilder();
        if (given != null && !given.isBlank()) {
            sb.append(Character.toUpperCase(given.trim().charAt(0)));
        }
        if (family != null && !family.isBlank()) {
            sb.append(Character.toUpperCase(family.trim().charAt(0)));
        }
        if (sb.length() == 0 && email != null) {
            String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            for (String part : local.split("[._\\-]+")) {
                if (!part.isBlank() && sb.length() < 2) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                }
            }
        }
        return sb.toString();
    }

    private static OAuth2AuthenticationException denied(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", message, null), message);
    }
}
