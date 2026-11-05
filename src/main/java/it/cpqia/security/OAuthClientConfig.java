package it.cpqia.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registrazioni OAuth2/OIDC per Google e Apple (configurate per contesto Tomcat, vedi application.properties). */
@Configuration
public class OAuthClientConfig {

    /** repository che ricostruisce il client secret di Apple (JWT) a ogni richiesta */
    public static class DynamicRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

        private final ClientRegistration google;
        private final AppleClientSecretGenerator appleSecret;
        private final String appleClientId;

        DynamicRepository(ClientRegistration google, AppleClientSecretGenerator appleSecret, String appleClientId) {
            this.google = google;
            this.appleSecret = appleSecret;
            this.appleClientId = appleClientId;
        }

        @Override
        public ClientRegistration findByRegistrationId(String id) {
            if ("google".equals(id)) {
                return google;
            }
            if ("apple".equals(id) && appleSecret != null) {
                return apple();
            }
            return null;
        }

        private ClientRegistration apple() {
            return ClientRegistration.withRegistrationId("apple")
                    .clientName("Apple")
                    .clientId(appleClientId)
                    .clientSecret(appleSecret.generate())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "name", "email")
                    .authorizationUri("https://appleid.apple.com/auth/authorize")
                    .tokenUri("https://appleid.apple.com/auth/token")
                    .jwkSetUri("https://appleid.apple.com/auth/keys")
                    .userNameAttributeName("sub")
                    .build();
        }

        public boolean googleEnabled() {
            return google != null;
        }

        public boolean appleEnabled() {
            return appleSecret != null;
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            List<ClientRegistration> l = new ArrayList<>();
            if (google != null) {
                l.add(google);
            }
            if (appleSecret != null) {
                l.add(apple());
            }
            return l.iterator();
        }
    }

    @Bean
    public DynamicRepository clientRegistrationRepository(
            @Value("${cpqia.oauth.google.client-id:}") String googleId,
            @Value("${cpqia.oauth.google.client-secret:}") String googleSecret,
            @Value("${cpqia.oauth.apple.client-id:}") String appleId,
            @Value("${cpqia.oauth.apple.team-id:}") String appleTeam,
            @Value("${cpqia.oauth.apple.key-id:}") String appleKey,
            @Value("${cpqia.oauth.apple.private-key-file:}") String appleFile) {
        ClientRegistration google = null;
        if (!googleId.isBlank()) {
            google = CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(googleId).clientSecret(googleSecret).build();
        }
        AppleClientSecretGenerator apple = null;
        if (!appleId.isBlank() && !appleTeam.isBlank() && !appleKey.isBlank() && !appleFile.isBlank()) {
            apple = new AppleClientSecretGenerator(appleId, appleTeam, appleKey, appleFile);
        }
        return new DynamicRepository(google, apple, appleId);
    }

    /** Apple richiede response_mode=form_post quando si chiedono gli scope name/email */
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver delegate =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return customize(delegate.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return customize(delegate.resolve(request, clientRegistrationId));
            }

            private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req) {
                if (req != null && "apple".equals(req.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))) {
                    Map<String, Object> params = new LinkedHashMap<>(req.getAdditionalParameters());
                    params.put("response_mode", "form_post");
                    return OAuth2AuthorizationRequest.from(req).additionalParameters(params).build();
                }
                return req;
            }
        };
    }
}
