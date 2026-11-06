package it.cpqia.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Collegamento a ATOKA (api.atoka.io, v2): autenticazione con chiave API (token) passata come parametro «token»; ricerca azienda per
 * partita IVA con GET /companies?vatNumbers=...&packages=base. La chiave, ottenuta dall'account ATOKA, si inserisce la prima volta
 * (la richiesta di prova la convalida) e viene salvata cifrata nel database.
 */
@Service
public class AtokaService {

    private final JdbcClient jdbc;
    private final CryptoService crypto;
    private final String baseUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AtokaService(JdbcClient jdbc, CryptoService crypto, @Value("${cpqia.atoka.base-url:https://api.atoka.io/v2}") String baseUrl) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record Status(boolean configured, String lastTestMsg, Boolean lastTestOk, LocalDateTime lastTestAt) {
    }

    /** esito della ricerca: ok, messaggio (se non ok) e JSON della prima azienda trovata (null se nessuna) */
    public record Lookup(boolean ok, int httpStatus, String message, JsonNode company) {
    }

    public Status status() {
        return jdbc.sql("select api_key_enc is not null as c, last_test_msg, last_test_ok, last_test_at from atoka_connection where id = 1")
                .query((rs, n) -> new Status(rs.getBoolean("c"), rs.getString("last_test_msg"), (Boolean) rs.getObject("last_test_ok"),
                        rs.getObject("last_test_at", LocalDateTime.class))).optional().orElse(new Status(false, null, null, null));
    }

    /** partita IVA di 11 cifre (senza prefisso IT, spazi e punteggiatura); null se non valida */
    public static String normalizeVat(String vat) {
        if (vat == null) {
            return null;
        }
        String v = vat.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        if (v.startsWith("IT")) {
            v = v.substring(2);
        }
        return v.matches("\\d{11}") ? v : null;
    }

    /** prima connessione: prova la chiave con una ricerca reale e, se accettata, la salva cifrata */
    public Lookup connect(String apiKey, String vat, long userId) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty() || key.length() > 400) {
            return new Lookup(false, 0, "Inserisci la chiave API (token) del tuo account ATOKA", null);
        }
        Lookup r = query(key, vat);
        boolean accepted = r.httpStatus() >= 200 && r.httpStatus() < 300;
        jdbc.sql("update atoka_connection set last_test_at = now(), last_test_ok = :ok, last_test_msg = :m where id = 1")
                .param("ok", accepted).param("m", accepted ? "Connessione riuscita" : r.message()).update();
        if (accepted) {
            jdbc.sql("update atoka_connection set api_key_enc = :k, updated_at = now(), updated_by = :u where id = 1")
                    .param("k", crypto.encrypt(key)).param("u", userId).update();
        }
        return r;
    }

    /** ricerca per partita IVA con la chiave salvata */
    public Lookup lookup(String vat) {
        String enc = jdbc.sql("select api_key_enc from atoka_connection where id = 1").query(String.class).optional().orElse(null);
        if (enc == null) {
            return new Lookup(false, 0, "ATOKA non è collegato", null);
        }
        return query(crypto.decrypt(enc), vat);
    }

    private Lookup query(String key, String vat) {
        String v = normalizeVat(vat);
        if (v == null) {
            return new Lookup(false, 0, "Partita IVA non valida: servono 11 cifre", null);
        }
        try {
            URI uri = URI.create(baseUrl + "/companies?packages=base&limit=1&vatNumbers=" + URLEncoder.encode(v, StandardCharsets.UTF_8)
                    + "&token=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = res.statusCode();
            if (sc == 401 || sc == 403) {
                return new Lookup(false, sc, "Chiave API ATOKA non valida o senza accesso a questo servizio (" + sc + ")", null);
            }
            if (sc == 429) {
                return new Lookup(false, sc, "Limite di richieste ATOKA raggiunto: riprova tra poco", null);
            }
            if (sc < 200 || sc >= 300) {
                return new Lookup(false, sc, "ATOKA ha risposto con errore " + sc, null);
            }
            JsonNode items = json.readTree(res.body()).path("items");
            JsonNode first = items.isArray() && items.size() > 0 ? items.get(0) : null;
            return new Lookup(true, sc, first == null ? "Nessuna azienda trovata su ATOKA per questa partita IVA" : null, first);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Lookup(false, 0, "Ricerca interrotta", null);
        } catch (Exception e) {
            return new Lookup(false, 0, "ATOKA non raggiungibile: " + e.getClass().getSimpleName(), null);     // nessun dettaglio: l'URL contiene la chiave
        }
    }
}
