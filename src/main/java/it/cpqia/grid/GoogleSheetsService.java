package it.cpqia.grid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.grid.GridModels.Col;
import it.cpqia.grid.GridModels.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esportazione diretta in Google Sheets: autorizzazione OAuth (scope drive.file: l'app vede solo i file che crea)
 * e creazione del foglio con titolo, intestazione, dati formattati e riga dei totali. Usa lo stesso client OAuth del login Google.
 */
@Service
public class GoogleSheetsService {

    public static final String SCOPE = "https://www.googleapis.com/auth/drive.file";

    public record Token(String accessToken, long expiresAtMs) {
        public boolean valid() {
            return accessToken != null && System.currentTimeMillis() < expiresAtMs - 30_000;
        }
    }

    /** token rifiutato o scaduto: serve una nuova autorizzazione */
    public static class AuthRequired extends RuntimeException {
        public AuthRequired(String m) {
            super(m);
        }
    }

    private final ObjectMapper mapper;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GoogleSheetsService(ObjectMapper mapper,
                               @Value("${cpqia.oauth.google.client-id:}") String clientId,
                               @Value("${cpqia.oauth.google.client-secret:}") String clientSecret) {
        this.mapper = mapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String clientId() {
        return clientId;
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String authUrl(String redirectUri, String state) {
        return authUrl(redirectUri, state, SCOPE);
    }

    public String authUrl(String redirectUri, String state, String scope) {
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri) + "&scope=" + enc(scope) + "&state=" + enc(state)
                + "&access_type=online&include_granted_scopes=true&prompt=consent";
    }

    public Token exchange(String code, String redirectUri) throws Exception {
        String body = "grant_type=authorization_code&code=" + enc(code) + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret) + "&redirect_uri=" + enc(redirectUri);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode n = mapper.readTree(res.body());
        if (res.statusCode() != 200 || !n.hasNonNull("access_token")) {
            throw new IllegalStateException("Autorizzazione Google non riuscita: " + n.path("error_description").asText(n.path("error").asText("HTTP " + res.statusCode())));
        }
        return new Token(n.get("access_token").asText(), System.currentTimeMillis() + n.path("expires_in").asLong(3600) * 1000);
    }

    // ------------------------------------------------------------------ creazione del foglio
    private static final List<String> NUMERIC = List.of("money", "number", "int", "percent");

    private static String numberPattern(String type) {
        return switch (type) {
            case "money" -> "#,##0.00 \"€\"";
            case "percent" -> "0.00\" %\"";
            case "int" -> "#,##0";
            default -> "#,##0.00";
        };
    }

    private static Map<String, Object> cell(Object value, Map<String, Object> format) {
        Map<String, Object> c = new LinkedHashMap<>();
        if (value instanceof Number n) {
            c.put("userEnteredValue", Map.of("numberValue", n.doubleValue()));
        } else if (value instanceof FormulaValue f) {
            c.put("userEnteredValue", Map.of("formulaValue", f.formula()));
        } else if (value != null) {
            c.put("userEnteredValue", Map.of("stringValue", value.toString()));
        }
        if (format != null) {
            c.put("userEnteredFormat", format);
        }
        return c;
    }

    private record FormulaValue(String formula) {
    }

    private static String colLetter(int i) {
        StringBuilder sb = new StringBuilder();
        for (int n = i; n >= 0; n = n / 26 - 1) {
            sb.insert(0, (char) ('A' + n % 26));
        }
        return sb.toString();
    }

    /** crea il foglio con i dati e restituisce l'indirizzo (spreadsheetUrl) */
    public String create(Token token, String title, Data data, List<String> columnKeys, List<String> rowIds) throws Exception {
        List<Col> cols = GridExport.pickColumns(data, columnKeys);
        List<Map<String, Object>> rows = GridExport.pickRows(data, rowIds);
        Map<String, Object> bold = Map.of("textFormat", Map.of("bold", true));
        Map<String, Object> head = new LinkedHashMap<>(bold);
        head.put("backgroundColor", Map.of("red", 0.89, "green", 0.93, "blue", 1.0));
        List<Map<String, Object>> rowData = new ArrayList<>();
        List<Map<String, Object>> hv = new ArrayList<>();
        for (Col c : cols) {
            hv.add(cell(c.label(), head));
        }
        rowData.add(Map.of("values", hv));
        for (Map<String, Object> r : rows) {
            List<Map<String, Object>> vals = new ArrayList<>();
            for (Col c : cols) {
                Object v = r.get(c.key());
                if (v == null || "".equals(v)) {
                    vals.add(new LinkedHashMap<>());
                    continue;
                }
                switch (c.type()) {
                    case "money", "number", "int", "percent" ->
                            vals.add(cell(((Number) v).doubleValue(), Map.of("numberFormat", Map.of("type", "NUMBER", "pattern", numberPattern(c.type())))));
                    case "date" -> vals.add(cell((double) (LocalDate.parse(v.toString().substring(0, 10)).toEpochDay() + 25569),
                            Map.of("numberFormat", Map.of("type", "DATE", "pattern", "dd/mm/yyyy"))));
                    case "datetime" -> {
                        LocalDateTime t = LocalDateTime.parse(v.toString().length() == 16 ? v + ":00" : v.toString());
                        double serial = t.toLocalDate().toEpochDay() + 25569 + t.toLocalTime().toSecondOfDay() / 86400.0;
                        vals.add(cell(serial, Map.of("numberFormat", Map.of("type", "DATE_TIME", "pattern", "dd/mm/yyyy hh:mm"))));
                    }
                    case "bool" -> vals.add(cell(Boolean.TRUE.equals(v) ? "Sì" : "No", null));
                    case "enum" -> {
                        String label = v.toString();
                        if (c.options() != null) {
                            for (var o : c.options()) {
                                if (o.value().equals(label)) {
                                    label = o.label();
                                }
                            }
                        }
                        vals.add(cell(label, null));
                    }
                    default -> vals.add(cell(v.toString(), null));
                }
            }
            rowData.add(Map.of("values", vals));
        }
        boolean anySum = cols.stream().anyMatch(c -> List.of("money", "number", "int").contains(c.type()));
        if (anySum && !rows.isEmpty()) {
            List<Map<String, Object>> tv = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                Col c = cols.get(i);
                if (i == 0) {
                    tv.add(cell("Totale", bold));
                } else if (List.of("money", "number", "int").contains(c.type())) {
                    String L = colLetter(i);
                    Map<String, Object> f = new LinkedHashMap<>(bold);
                    f.put("numberFormat", Map.of("type", "NUMBER", "pattern", numberPattern(c.type())));
                    tv.add(cell(new FormulaValue("=SUBTOTAL(109," + L + "2:" + L + (rows.size() + 1) + ")"), f));
                } else {
                    tv.add(new LinkedHashMap<>());
                }
            }
            rowData.add(Map.of("values", tv));
        }
        Map<String, Object> sheet = Map.of(
                "properties", Map.of("title", "Dati", "gridProperties", Map.of("frozenRowCount", 1)),
                "data", List.of(Map.of("startRow", 0, "startColumn", 0, "rowData", rowData)));
        Map<String, Object> body = Map.of("properties", Map.of("title", title, "locale", "it_IT"), "sheets", List.of(sheet));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://sheets.googleapis.com/v4/spreadsheets")).timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token.accessToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            throw new AuthRequired("Autorizzazione scaduta");
        }
        JsonNode n = mapper.readTree(res.body());
        if (res.statusCode() != 200) {
            String msg = n.path("error").path("message").asText("HTTP " + res.statusCode());
            if (res.statusCode() == 403 && msg.toLowerCase().contains("not been used")) {
                msg += " (abilita «Google Sheets API» nel progetto Google Cloud)";
            }
            throw new IllegalStateException("Creazione del foglio non riuscita: " + msg);
        }
        return n.path("spreadsheetUrl").asText();
    }
}
