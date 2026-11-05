package it.cpqia.security;

import it.cpqia.domain.AppUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Dopo il login Google/Apple carica l'utente applicativo e ne imposta le authority dai flag di abilitazione. */
@Component
public class CpqiaOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioning;

    public CpqiaOidcUserService(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidc = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        AppUser u = provisioning.resolve(registrationId, oidc.getSubject(), oidc.getEmail(), oidc.getGivenName(),
                oidc.getFamilyName(), oidc.getFullName(), oidc.getPicture());
        return new DefaultOidcUser(Permissions.of(u), oidc.getIdToken(), oidc.getUserInfo(), "sub");
    }
}
