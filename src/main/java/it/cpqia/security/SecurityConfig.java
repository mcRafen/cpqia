package it.cpqia.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CpqiaOidcUserService oidcUserService,
                                           OAuth2AuthorizationRequestResolver resolver) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/dev-login", "/css/**", "/js/**", "/img/**", "/favicon.ico", "/error")
                        .permitAll()
                        .requestMatchers("/admin/users/**", "/admin/sedi/**", "/admin/geodata/**", "/admin/lookups/**", "/admin/integrations/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAuthority(Permissions.MANAGE_CATALOG)
                        .anyRequest().authenticated())
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .authorizationEndpoint(e -> e.authorizationRequestResolver(resolver))
                        .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                        .defaultSuccessUrl("/", true)
                        .failureHandler(this::onFailure))
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                // il callback di Apple e' una POST cross-site (response_mode=form_post)
                .csrf(c -> c.ignoringRequestMatchers("/login/oauth2/code/**"))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    private void onFailure(HttpServletRequest req, HttpServletResponse res,
                           org.springframework.security.core.AuthenticationException ex) throws java.io.IOException {
        String msg = ex.getMessage() == null ? "Accesso non riuscito" : ex.getMessage();
        res.sendRedirect(req.getContextPath() + "/login?error=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
    }
}
