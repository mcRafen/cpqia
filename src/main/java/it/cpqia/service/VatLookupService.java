package it.cpqia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recupera i dati anagrafici da una partita IVA interrogando il servizio VIES della Commissione Europea.
 * Per le imprese italiane VIES restituisce ragione sociale e indirizzo (quando l'impresa non ha negato la diffusione dei dati);
 * se i dati non sono disponibili si conferma solo la validita' della partita IVA.
 */
@Service
public class VatLookupService {

    public record Result(boolean valid, boolean dataAvailable, String vat, String name, String streetType, String streetName,
                         String streetNumber, String postalCode, String city, String province, String message) {
    }

    private static final Pattern STREET = Pattern.compile(
            "^(?i)(via|viale|piazza|piazzale|corso|largo|strada|vicolo|contrada|borgo|lungomare|localit[aà]'?|frazione)\\s+(.*)$");
    private static final Pattern NUMBER = Pattern.compile("^(.*?)[,\\s]+((?:\\d+[A-Za-z]?(?:[/\\-]\\w+)?)|SNC|S\\.N\\.C\\.?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAP_CITY = Pattern.compile("^(\\d{5})\\s+(.+?)(?:\\s+\\(?([A-Za-z]{2})\\)?)?$");

    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String urlTemplate;
    private final int timeout;
    private final HttpClient http;

    public VatLookupService(ObjectMapper mapper,
                            @Value("${cpqia.vat-lookup.enabled:true}") boolean enabled,
                            @Value("${cpqia.vat-lookup.url:https://ec.europa.eu/taxation_customs/vies/rest-api/ms/{cc}/vat/{vat}}") String urlTemplate,
                            @Value("${cpqia.vat-lookup.timeout-seconds:10}") int timeout) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.urlTemplate = urlTemplate;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public Result lookup(String input) {
        String raw = input == null ? "" : input.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
        String cc = "IT";
        String vat = raw;
        if (raw.length() > 2 && Character.isLetter(raw.charAt(0)) && Character.isLetter(raw.charAt(1))) {
            cc = raw.substring(0, 2);
            vat = raw.substring(2);
        }
        if (vat.isEmpty()) {
            return fail(raw, "Inserire la partita IVA");
        }
        if ("IT".equals(cc) && !validItalianVat(vat)) {
            return fail(vat, "Partita IVA italiana non valida (11 cifre con cifra di controllo corretta)");
        }
        if (!enabled) {
            return fail(vat, "Recupero automatico dei dati disattivato (cpqia.vat-lookup.enabled)");
        }
        try {
            String url = urlTemplate.replace("{cc}", cc).replace("{vat}", vat);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 500 || res.statusCode() == 429) {
                return fail(vat, "Servizio VIES momentaneamente non disponibile: riprova tra poco o compila a mano");
            }
            JsonNode n = mapper.readTree(res.body());
            boolean valid = n.path("isValid").asBoolean(n.path("valid").asBoolean(false));
            if (!valid) {
                String err = n.path("userError").asText("");
                if (err.contains("UNAVAILABLE") || err.contains("TIMEOUT") || err.contains("BUSY")) {
                    return fail(vat, "Servizio VIES momentaneamente non disponibile: riprova tra poco o compila a mano");
                }
                return fail(vat, "Partita IVA non risultante attiva in VIES");
            }
            String name = clean(n.path("name").asText(""));
            String address = n.path("address").asText("");
            if (name.isEmpty() && clean(address).isEmpty()) {
                return new Result(true, false, vat, null, null, null, null, null, null, null,
                        "Partita IVA valida, ma l'impresa non rende pubblici i dati in VIES: compila i campi a mano");
            }
            return parse(vat, name, address);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(vat, "Richiesta interrotta");
        } catch (Exception e) {
            return fail(vat, "Impossibile contattare il servizio online (" + e.getClass().getSimpleName() + "): compila i campi a mano");
        }
    }

    // ------------------------------------------------------------------ parsing indirizzo
    static Result parse(String vat, String name, String address) {
        String addr = address == null ? "" : address.replace("\r", "").trim();
        String[] lines = addr.split("\n");
        String streetLine = "";
        String capLine = "";
        if (lines.length >= 2) {
            capLine = lines[lines.length - 1].trim();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length - 1; i++) {
                sb.append(sb.length() > 0 ? " " : "").append(lines[i].trim());
            }
            streetLine = sb.toString();
        } else {
            Matcher m = Pattern.compile("^(.*?)[,\\s]+(\\d{5}\\s+.+)$").matcher(addr);
            if (m.matches()) {
                streetLine = m.group(1).trim();
                capLine = m.group(2).trim();
            } else {
                streetLine = addr;
            }
        }
        String postal = null;
        String city = null;
        String province = null;
        Matcher cm = CAP_CITY.matcher(capLine);
        if (cm.matches()) {
            postal = cm.group(1);
            city = title(cm.group(2));
            province = cm.group(3) == null ? null : cm.group(3).toUpperCase(Locale.ROOT);
        } else if (!capLine.isEmpty()) {
            city = title(capLine);
        }
        String number = null;
        Matcher nm = NUMBER.matcher(streetLine);
        if (nm.matches()) {
            streetLine = nm.group(1).trim();
            number = nm.group(2).toUpperCase(Locale.ROOT);
        }
        String type = "Via";
        String street = streetLine;
        Matcher sm = STREET.matcher(streetLine);
        if (sm.matches()) {
            type = title(sm.group(1));
            if (type.toLowerCase(Locale.ROOT).startsWith("localit")) {
                type = "Località";
            }
            street = sm.group(2);
        }
        return new Result(true, true, vat, name.isEmpty() ? null : name, type, title(street), number, postal, city, province, "Dati recuperati da VIES: verificali prima di salvare");
    }

    private static final java.util.Set<String> SMALL = java.util.Set.of("di", "del", "della", "dei", "delle", "da", "de", "e", "il", "la", "lo", "in", "al");

    static String title(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        String[] words = s.trim().toLowerCase(Locale.ITALIAN).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0 && SMALL.contains(w)) {
                sb.append(' ').append(w);
                continue;
            }
            // dopo apostrofo la parola riparte maiuscola: dell'elettronica -> dell'Elettronica
            StringBuilder t = new StringBuilder();
            boolean up = true;
            for (char c : w.toCharArray()) {
                t.append(up ? Character.toUpperCase(c) : c);
                up = (c == '\'' || c == '’' || c == '-' || c == '/');
            }
            sb.append(i > 0 ? " " : "").append(t);
        }
        return sb.toString();
    }

    private static String clean(String s) {
        String t = s == null ? "" : s.trim();
        return t.equals("---") || t.equals("-") ? "" : t;
    }

    /** controllo della cifra di controllo della partita IVA italiana (algoritmo di Luhn) */
    static boolean validItalianVat(String v) {
        if (v == null || !v.matches("\\d{11}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 11; i++) {
            int d = v.charAt(i) - '0';
            if (i % 2 == 1) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return sum % 10 == 0;
    }

    private static Result fail(String vat, String message) {
        return new Result(false, false, vat, null, null, null, null, null, null, null, message);
    }
}
