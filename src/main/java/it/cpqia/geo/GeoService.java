package it.cpqia.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Geocodifica indirizzi (Nominatim) e distanza stradale (OSRM). Servizi pubblici gratuiti: uso moderato, risultati in cache nel database. */
@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

    public record Point(double lat, double lon) {
    }

    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String nominatim;
    private final String osrm;
    private final String userAgent;
    private final int timeout;
    private final HttpClient http;
    private long lastCall = 0;

    public GeoService(ObjectMapper mapper,
                      @Value("${cpqia.geo.enabled:true}") boolean enabled,
                      @Value("${cpqia.geo.nominatim-url:https://nominatim.openstreetmap.org/search}") String nominatim,
                      @Value("${cpqia.geo.osrm-url:https://router.project-osrm.org/route/v1/driving}") String osrm,
                      @Value("${cpqia.geo.user-agent:CPQIA/1.0}") String userAgent,
                      @Value("${cpqia.geo.timeout-seconds:10}") int timeout) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.nominatim = nominatim;
        this.osrm = osrm;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public boolean enabled() {
        return enabled;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Nominatim chiede al massimo 1 richiesta al secondo */
    private synchronized JsonNode get(String url) throws Exception {
        long wait = 1100 - (System.currentTimeMillis() - lastCall);
        if (wait > 0 && url.startsWith(nominatim)) {
            Thread.sleep(wait);
        }
        lastCall = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                .header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 403) {
            throw new IllegalStateException("HTTP 403: richiesta rifiutata dal servizio. Imposta cpqia.geo.user-agent con un tuo recapito reale (nome applicazione + e-mail), richiesto dalla policy di Nominatim/OSRM");
        }
        if (res.statusCode() == 429) {
            throw new IllegalStateException("HTTP 429: troppe richieste al servizio, riprova tra poco");
        }
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        return mapper.readTree(res.body());
    }

    /** esito di una geocodifica: punto trovato oppure motivo (error = servizio non raggiungibile, null = indirizzo non trovato) */
    public record Geocoded(Optional<Point> point, String error) {
    }

    private Point firstHit(String url) throws Exception {
        JsonNode n = get(url);
        if (n.isArray() && n.size() > 0) {
            return new Point(n.get(0).path("lat").asDouble(), n.get(0).path("lon").asDouble());
        }
        return null;
    }

    /**
     * Coordinate dell'indirizzo, con ricerche via via piu' larghe: via e civico con citta'/CAP, via senza civico, testo libero
     * completo e infine solo CAP e citta' (centro del comune). Il tentativo si ferma al primo risultato.
     */
    public Geocoded geocodeDetailed(String street, String number, String postalCode, String city, String province) {
        if (!enabled) {
            return new Geocoded(Optional.empty(), "calcolo distanze disattivato (cpqia.geo.enabled)");
        }
        String st = street == null ? "" : street.trim();
        String no = number == null ? "" : number.trim();
        String ct = city == null ? "" : city.trim();
        String cap = postalCode == null ? "" : postalCode.trim();
        String pv = province == null ? "" : province.trim();
        String base = nominatim + "?format=jsonv2&limit=1&countrycodes=it";
        List<String> urls = new ArrayList<>();
        if (!st.isEmpty()) {
            // ricerca strutturata: Nominatim vuole «civico via»; la provincia (sigla) non e' una regione, quindi non si passa
            urls.add(base + "&street=" + enc((no + " " + st).trim()) + (ct.isEmpty() ? "" : "&city=" + enc(ct)) + (cap.isEmpty() ? "" : "&postalcode=" + enc(cap)));
            urls.add(base + "&street=" + enc(st) + (ct.isEmpty() ? "" : "&city=" + enc(ct)));
            urls.add(base + "&q=" + enc(String.join(", ", java.util.stream.Stream.of((st + " " + no).trim(), (cap + " " + ct).trim(), pv, "Italia")
                    .filter(x -> !x.isBlank()).toList())));
        }
        if (!ct.isEmpty()) {
            urls.add(base + "&q=" + enc(String.join(", ", java.util.stream.Stream.of((cap + " " + ct).trim(), pv, "Italia").filter(x -> !x.isBlank()).toList())));
            urls.add(base + "&city=" + enc(ct) + (cap.isEmpty() ? "" : "&postalcode=" + enc(cap)));
        }
        String error = null;
        for (String u : urls) {
            try {
                Point p = firstHit(u);
                if (p != null) {
                    return new Geocoded(Optional.of(p), null);
                }
                error = null;                       // risposta valida ma senza risultati: si prova la ricerca successiva
            } catch (Exception e) {
                log.warn("Geocodifica non riuscita ({}): {}", u, e.toString());
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        return new Geocoded(Optional.empty(), error);
    }

    public Optional<Point> geocode(String street, String number, String postalCode, String city, String province) {
        return geocodeDetailed(street, number, postalCode, city, province).point();
    }

    /** distanza stradale in km (percorso piu' breve per auto) */
    public Optional<Double> roadKm(Point a, Point b) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String url = osrm + "/" + String.format(Locale.ROOT, "%f,%f;%f,%f", a.lon(), a.lat(), b.lon(), b.lat()) + "?overview=false";
            JsonNode n = get(url);
            JsonNode routes = n.path("routes");
            if (routes.isArray() && routes.size() > 0) {
                return Optional.of(routes.get(0).path("distance").asDouble() / 1000.0);
            }
        } catch (Exception e) {
            log.warn("Calcolo percorso non riuscito: {}", e.toString());
        }
        return Optional.empty();
    }

    /** distanza in linea d'aria (km) */
    public static double haversineKm(Point a, Point b) {
        double r = 6371.0088;
        double dLat = Math.toRadians(b.lat() - a.lat()), dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }
}
