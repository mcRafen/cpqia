package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import it.cpqia.grid.GridModels.Data;
import it.cpqia.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Esporta in Google Sheets: crea il foglio direttamente sul Drive dell'utente (con autorizzazione al primo uso). */
@Controller
public class GoogleSheetsController {

    private static final String S_TOKEN = "gs.token";
    private static final String S_PENDING = "gs.pending";
    private static final String S_STATE = "gs.state";

    private final GoogleSheetsService sheets;
    private final GridService grids;
    private final CurrentUserService current;

    public GoogleSheetsController(GoogleSheetsService sheets, GridService grids, CurrentUserService current) {
        this.sheets = sheets;
        this.grids = grids;
        this.current = current;
    }

    private static String redirectUri(HttpServletRequest req) {
        return ServletUriComponentsBuilder.fromContextPath(req).path("/google-sheets/callback").toUriString();
    }

    /** Google accetta redirect solo su HTTPS o localhost (mai indirizzi IP di rete in http) */
    private static boolean redirectAllowed(String uri) {
        return uri.startsWith("https://") || uri.startsWith("http://localhost") || uri.startsWith("http://127.0.0.1");
    }

    private static String title(Data d) {
        return d.meta().title() + " – " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Risposte: {mode:"open", url} foglio creato; {mode:"auth", authUrl} serve l'autorizzazione di Google;
     * {mode:"clipboard", message} integrazione non utilizzabile (usare la copia negli appunti).
     */
    @PostMapping("/api/grid/{key}/export/gsheet")
    @ResponseBody
    public Map<String, Object> export(@PathVariable String key, @RequestBody GridController.ExportRequest req, Authentication auth,
                                      HttpServletRequest http, HttpSession session) throws Exception {
        AppUser u = current.get(auth);
        Data data = grids.data(key, u);                              // controlla anche permessi e visibilita'
        Map<String, Object> out = new LinkedHashMap<>();
        String redirect = redirectUri(http);
        if (!sheets.configured()) {
            out.put("mode", "clipboard");
            out.put("message", "Google non è configurato (cpqia.oauth.google.*)");
            return out;
        }
        if (!redirectAllowed(redirect)) {
            out.put("mode", "clipboard");
            out.put("message", "La creazione diretta richiede HTTPS o localhost (indirizzo attuale: " + redirect + ")");
            return out;
        }
        Object t = session.getAttribute(S_TOKEN);
        if (t instanceof GoogleSheetsService.Token tok && tok.valid()) {
            try {
                out.put("mode", "open");
                out.put("url", sheets.create(tok, title(data), data, req.columns(), req.rows()));
                return out;
            } catch (GoogleSheetsService.AuthRequired e) {
                session.removeAttribute(S_TOKEN);
                out.clear();
            }
        }
        byte[] rnd = new byte[18];
        new SecureRandom().nextBytes(rnd);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("key", key);
        pending.put("columns", req.columns() == null ? List.of() : List.copyOf(req.columns()));
        pending.put("rows", req.rows() == null ? List.of() : List.copyOf(req.rows()));
        session.setAttribute(S_PENDING, pending);
        session.setAttribute(S_STATE, state);
        out.put("mode", "auth");
        out.put("authUrl", sheets.authUrl(redirect, state));
        return out;
    }

    /** ritorno da Google: scambio del codice, creazione del foglio e apertura */
    @GetMapping("/google-sheets/callback")
    public ResponseEntity<?> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String error, Authentication auth,
                                      HttpServletRequest http, HttpSession session) {
        try {
            Object expected = session.getAttribute(S_STATE);
            @SuppressWarnings("unchecked")
            Map<String, Object> pending = (Map<String, Object>) session.getAttribute(S_PENDING);
            session.removeAttribute(S_STATE);
            session.removeAttribute(S_PENDING);
            if (error != null) {
                return page("Autorizzazione annullata: il foglio non è stato creato. Puoi chiudere questa scheda.");
            }
            if (code == null || expected == null || !expected.equals(state) || pending == null) {
                return page("Richiesta non valida o scaduta: riprova l'esportazione dalla griglia.");
            }
            GoogleSheetsService.Token tok = sheets.exchange(code, redirectUri(http));
            if ("rfq".equals(pending.get("type"))) {                 // «Importa da RFQ»: token di sola lettura, poi si torna alla pagina che ha aperto la finestra
                session.setAttribute("rfq.token", tok);
                return rfqDone();
            }
            session.setAttribute(S_TOKEN, tok);
            AppUser u = current.get(auth);
            String key = (String) pending.get("key");
            @SuppressWarnings("unchecked")
            List<String> cols = (List<String>) pending.get("columns");
            @SuppressWarnings("unchecked")
            List<String> rows = (List<String>) pending.get("rows");
            Data data = grids.data(key, u);
            String url = sheets.create(tok, title(data), data, cols, rows);
            return ResponseEntity.status(302).header("Location", url).build();
        } catch (Exception e) {
            return page("Esportazione in Google Sheets non riuscita: " + e.getMessage());
        }
    }

    private static ResponseEntity<String> rfqDone() {
        String html = "<!DOCTYPE html><html lang=\"it\"><meta charset=\"utf-8\"><title>Google</title>"
                + "<body style=\"font-family:system-ui,sans-serif;max-width:560px;margin:15vh auto;padding:0 20px\"><h2>Autorizzazione completata</h2>"
                + "<p>Puoi chiudere questa finestra: l'importazione riprende da sola.</p>"
                + "<script>try{window.opener&&window.opener.postMessage('cpqia-rfq-auth',location.origin);}catch(e){}setTimeout(function(){window.close();},400);</script></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private static ResponseEntity<String> page(String msg) {
        String html = "<!DOCTYPE html><html lang=\"it\"><meta charset=\"utf-8\"><title>Google Sheets</title>"
                + "<body style=\"font-family:system-ui,sans-serif;max-width:560px;margin:15vh auto;padding:0 20px\"><h2>Google Sheets</h2><p>"
                + HtmlUtils.htmlEscape(msg) + "</p></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
