package it.cpqia.web;

import it.cpqia.domain.AppUser;
import it.cpqia.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalAdvice {

    private final CurrentUserService current;
    private final UiPrefsController uiPrefs;

    private final String version;
    private final String build;

    public GlobalAdvice(CurrentUserService current, UiPrefsController uiPrefs, @org.springframework.beans.factory.annotation.Value("${cpqia.version:}") String version,
                        @org.springframework.beans.factory.annotation.Value("${cpqia.build:}") String build) {
        this.current = current;
        this.uiPrefs = uiPrefs;
        this.build = build == null || build.isBlank() || build.startsWith("@") ? "sviluppo" : build.replace('T', ' ').replace("Z", " UTC");
        // versione del pom (filtrata in application.properties); fuori dal build Maven resta il segnaposto
        this.version = version == null || version.isBlank() || version.startsWith("@") ? "dev" : version;
    }

    /** versione dell'applicazione, mostrata nella barra superiore come ${appVersion} */
    @ModelAttribute("appVersion")
    public String appVersion() {
        return version;
    }

    /** data e ora di compilazione del WAR (tooltip della versione): permette di riconoscere quale WAR e' in esecuzione */
    @ModelAttribute("appBuild")
    public String appBuild() {
        return build;
    }

    /** utente corrente disponibile in tutti i template come ${me} */
    @ModelAttribute("me")
    public AppUser me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return current.get(auth);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** preferenze di aspetto salvate dell'utente (tema e palette), per applicarle subito al caricamento della pagina; null senza utente */
    @ModelAttribute("uiPrefs")
    public UiPrefsController.Prefs uiPrefs(Authentication auth) {
        AppUser u = me(auth);
        return u == null ? null : uiPrefs.load(u.id());
    }

    /** chiamate JSON del wizard (/api/) o richieste che accettano JSON: risposta {message}; altrimenti pagina di errore */
    private static boolean wantsJson(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return req.getRequestURI().contains("/api/") || (accept != null && accept.contains("application/json"));
    }

    private static Object respond(HttpServletRequest req, HttpStatusCode status, String message) {
        if (wantsJson(req)) {
            return ResponseEntity.status(status).body(Map.of("message", message == null ? "Errore" : message));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(status);
        mv.addObject("status", status.value());
        mv.addObject("message", message);
        return mv;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object badRequest(IllegalArgumentException e, HttpServletRequest req) {
        return respond(req, HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public Object notFound(NoSuchElementException e, HttpServletRequest req) {
        return respond(req, HttpStatus.NOT_FOUND, "Elemento non trovato");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Object status(ResponseStatusException e, HttpServletRequest req) {
        return respond(req, e.getStatusCode(), e.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Dati non validi", "errors", errors));
    }
}
