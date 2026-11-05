package it.cpqia.rfq;

import it.cpqia.grid.GoogleSheetsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Importa da RFQ»: legge il primo foglio di un documento Google Sheets con l'account Google dell'utente (OAuth, sola lettura).
 * Risposte: {mode:"auth", authUrl} serve l'autorizzazione (il ritorno passa da /google-sheets/callback), {mode:"ok", items:[…]}.
 */
@Controller
public class RfqController {

    public static final String S_TOKEN = "rfq.token";
    private static final String S_PENDING = "gs.pending";
    private static final String S_STATE = "gs.state";

    public record ImportRequest(String url, Long customerId, Long galileoId) {
    }

    private final GoogleSheetsService sheets;
    private final RfqService rfq;

    public RfqController(GoogleSheetsService sheets, RfqService rfq) {
        this.sheets = sheets;
        this.rfq = rfq;
    }

    @PostMapping("/api/rfq/import")
    @ResponseBody
    public Map<String, Object> importRfq(@RequestBody ImportRequest req, HttpServletRequest http, HttpSession session) throws Exception {
        String id = RfqService.spreadsheetId(req.url());
        Map<String, Object> out = new LinkedHashMap<>();
        if (!sheets.configured()) {
            throw new IllegalArgumentException("Google non è configurato (cpqia.oauth.google.*)");
        }
        String redirect = ServletUriComponentsBuilder.fromContextPath(http).path("/google-sheets/callback").toUriString();
        if (!(redirect.startsWith("https://") || redirect.startsWith("http://localhost") || redirect.startsWith("http://127.0.0.1"))) {
            throw new IllegalArgumentException("L'accesso a Google richiede HTTPS o localhost (indirizzo attuale: " + redirect + ")");
        }
        Object t = session.getAttribute(S_TOKEN);
        if (t instanceof GoogleSheetsService.Token tok && tok.valid()) {
            try {
                RfqService.Result res = rfq.parse(rfq.readFirstSheet(tok, id), req.customerId(), req.galileoId());
                List<RfqService.Item> items = res.items();
                java.math.BigDecimal computed = items.stream().map(i -> i.hours().multiply(i.rate())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                out.put("sheetTotal", res.sheetTotal());
                out.put("computedTotal", computed);
                out.put("mode", "ok");
                out.put("items", items);
                try {                                           // foglio «moduli relativi alle attività»: moduli da attivare (colonna C)
                    List<String> mods = rfq.readModules(tok, id);
                    out.put("modules", mods == null ? List.of() : mods);
                    out.put("modulesSheet", mods != null);
                } catch (GoogleSheetsService.AuthRequired e) {
                    throw e;
                } catch (Exception e) {
                    out.put("modules", List.of());
                    out.put("modulesSheet", false);
                    out.put("modulesError", e.getMessage());
                }
                out.put("notFound", items.stream().filter(i -> i.applicationId() == 0).map(RfqService.Item::applicationName).distinct().toList());
                return out;
            } catch (GoogleSheetsService.AuthRequired e) {
                session.removeAttribute(S_TOKEN);               // token scaduto: nuova autorizzazione
            } catch (RfqService.NoAccess e) {
                // l'utente potrebbe aver autorizzato un altro account Google: si propone di ri-autorizzare
                out.put("mode", "noaccess");
                out.put("message", e.getMessage());
                out.put("authUrl", authUrl(redirect, session));
                return out;
            }
        }
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("type", "rfq");
        session.setAttribute(S_PENDING, pending);
        out.put("mode", "auth");
        out.put("authUrl", authUrl(redirect, session));
        out.put("redirectUri", redirect);
        out.put("clientId", sheets.clientId());
        return out;
    }

    private String authUrl(String redirect, HttpSession session) {
        byte[] rnd = new byte[18];
        new SecureRandom().nextBytes(rnd);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("type", "rfq");
        session.setAttribute(S_PENDING, pending);
        session.setAttribute(S_STATE, state);
        return sheets.authUrl(redirect, state, RfqService.SCOPE);
    }
}
