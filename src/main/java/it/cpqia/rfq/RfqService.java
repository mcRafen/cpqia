package it.cpqia.rfq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.grid.GoogleSheetsService;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.repo.CatalogRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importazione delle attivita' da un foglio RFQ (Google Sheets, primo foglio).
 * Colonne: B applicazione, C titolo del gruppo / descrizione dell'attivita', D giorni (ore x 8, non usata), E ore,
 * F figura professionale, I = "T" sulle righe-titolo. Si prendono solo i gruppi con ore (E) > 0; la colonna G e' ignorata.
 */
@Service
public class RfqService {

    public static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly";

    /** riga importata, pronta per il passo Servizi (tariffa = tariffa dell'account se presente, altrimenti di listino) */
    public record Item(long applicationId, String applicationName, String group, String description,
                       long profileId, String profileName, BigDecimal hours, BigDecimal days, BigDecimal rate) {
    }

    /** il foglio non e' leggibile con l'account Google dell'utente */
    public static class NoAccess extends RuntimeException {
        public NoAccess(String m) {
            super(m);
        }
    }

    private static final Pattern SHEET_ID = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9_-]+)");
    /** applicazione (gruppo) usata quando nella colonna B del foglio non c'è nulla */
    public static final String OTHER = "ALTRO";
    /** descrizione (normalizzata) della riga che chiude l'elenco delle attivita' */
    private static final Pattern END_MARK = Pattern.compile("totale generale (di )?progetto");      // anche senza «di»
    private static final int COL_APP = 1, COL_TITLE = 2, COL_HOURS = 3, COL_DAYS = 4, COL_PROFILE = 5, COL_TOTAL = 7, COL_TYPE = 8;

    private final ObjectMapper mapper;
    private final JdbcClient jdbc;
    private final CatalogRepository catalog;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RfqService(ObjectMapper mapper, JdbcClient jdbc, CatalogRepository catalog) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public static String spreadsheetId(String url) {
        Matcher m = SHEET_ID.matcher(url == null ? "" : url.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("L'indirizzo non è quello di un documento Google Sheets (atteso https://docs.google.com/spreadsheets/d/…)");
        }
        return m.group(1);
    }

    // ------------------------------------------------------------------ lettura del foglio
    private JsonNode get(String url, GoogleSheetsService.Token token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + token.accessToken()).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            throw new GoogleSheetsService.AuthRequired("Autorizzazione scaduta");
        }
        JsonNode n = mapper.readTree(res.body());
        if (res.statusCode() != 200) {
            String msg = n.path("error").path("message").asText("HTTP " + res.statusCode());
            if (res.statusCode() == 403 && msg.toLowerCase(Locale.ROOT).contains("not been used")) {
                throw new IllegalStateException("Abilita «Google Sheets API» nel progetto Google Cloud dell'applicazione");
            }
            if (res.statusCode() == 403 || res.statusCode() == 404) {
                throw new NoAccess("Il tuo account Google non ha accesso a questo foglio, oppure l'indirizzo non è corretto. "
                        + "Chiedi al proprietario di condividerlo con il tuo account e riprova.");
            }
            if (res.statusCode() == 400 && msg.toLowerCase(Locale.ROOT).contains("not supported")) {
                throw new IllegalArgumentException("Il documento non è un foglio Google nativo (es. un file Excel su Drive): aprilo e salvalo come Google Sheets");
            }
            throw new IllegalStateException("Lettura del foglio non riuscita: " + msg);
        }
        return n;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** celle A:I del primo foglio (valori non formattati) */
    public List<List<Object>> readFirstSheet(GoogleSheetsService.Token token, String id) throws Exception {
        JsonNode meta = get("https://sheets.googleapis.com/v4/spreadsheets/" + id + "?fields=sheets.properties(title,index)", token);
        JsonNode sheets = meta.path("sheets");
        if (!sheets.isArray() || sheets.isEmpty()) {
            throw new IllegalArgumentException("Il documento non contiene fogli");
        }
        String title = sheets.get(0).path("properties").path("title").asText();
        String range = "'" + title.replace("'", "''") + "'!A:I";
        JsonNode vals = get("https://sheets.googleapis.com/v4/spreadsheets/" + id + "/values/" + enc(range) + "?valueRenderOption=UNFORMATTED_VALUE", token);
        List<List<Object>> rows = new ArrayList<>();
        for (JsonNode r : vals.path("values")) {
            List<Object> row = new ArrayList<>();
            for (JsonNode c : r) {
                row.add(c.isNumber() ? (Object) c.decimalValue() : c.asText(""));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Foglio «moduli relativi alle attività» (cerca il foglio per nome, senza maiuscole/accenti): elenco dei moduli da attivare nella colonna C.
     * Restituisce null se il foglio non esiste.
     */
    public List<String> readModules(GoogleSheetsService.Token token, String id) throws Exception {
        JsonNode meta = get("https://sheets.googleapis.com/v4/spreadsheets/" + id + "?fields=sheets.properties(title,index)", token);
        String title = null;
        for (JsonNode sh : meta.path("sheets")) {
            String t = sh.path("properties").path("title").asText();
            if (norm(t).contains("moduli relativi")) {
                title = t;
                break;
            }
        }
        if (title == null) {
            return null;
        }
        JsonNode vals = get("https://sheets.googleapis.com/v4/spreadsheets/" + id + "/values/" + enc("'" + title.replace("'", "''") + "'!C:C") + "?valueRenderOption=FORMATTED_VALUE", token);
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode r : vals.path("values")) {
            String v = r.isArray() && r.size() > 0 ? r.get(0).asText("").replace('\u00a0', ' ').trim() : "";
            String n = norm(v);
            if (v.isEmpty() || n.equals("moduli") || n.equals("modulo") || n.startsWith("moduli da attivare") || n.startsWith("elenco moduli")) {
                continue;                                              // righe vuote e intestazione
            }
            if (seen.add(n)) {
                out.add(v);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ interpretazione
    private static String str(List<Object> row, int i) {
        return i < row.size() && row.get(i) != null ? row.get(i).toString().trim() : "";
    }

    private static BigDecimal num(List<Object> row, int i) {
        if (i >= row.size() || row.get(i) == null) {
            return BigDecimal.ZERO;
        }
        Object o = row.get(i);
        if (o instanceof BigDecimal b) {
            return b;
        }
        String s = o.toString().trim().replace(" ", "");
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    static String norm(String s) {
        String t = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** riga «TOTALE GENERALE DI PROGETTO» in una qualunque delle colonne A:I */
    private static boolean isEnd(List<Object> row) {
        for (int i = 0; i < row.size(); i++) {
            if (END_MARK.matcher(norm(str(row, i))).find()) {
                return true;
            }
        }
        return false;
    }

    private record Detail(String desc, String profile, BigDecimal hours, BigDecimal days, int row) {
    }

    private static final class Group {
        final String app;
        final String title;
        final BigDecimal hours;
        final List<Detail> details = new ArrayList<>();

        Group(String app, String title, BigDecimal hours) {
            this.app = app;
            this.title = title;
            this.hours = hours;
        }
    }

    private record Prof(long id, String name, BigDecimal rate) {
    }

    /**
     * Righe del foglio -> attivita'. Ogni riga con "T" in colonna I apre un gruppo (titolo in C, totale giorni in E); le righe successive
     * fino al titolo seguente sono le sue attivita' (descrizione in C, ore in D, giorni in E, figura professionale in F). Le righe «T» sono solo
     * titoli: la figura professionale non si cerca su di esse. Tariffe: quelle dell'account se presenti.
     */
    /** attivita' importate e totale servizi dichiarato nel foglio (colonna H della riga «TOTALE GENERALE PROGETTO», null se assente) */
    public record Result(List<Item> items, BigDecimal sheetTotal) {
    }

    public Result parse(List<List<Object>> rows, Long customerId, Long galileoId) {
        BigDecimal[] sheetTotal = {null};
        List<Group> groups = new ArrayList<>();
        String app = "";
        Group cur = null;
        int rowNo = 0;
        for (List<Object> row : rows) {
            rowNo++;
            if (isEnd(row)) {
                sheetTotal[0] = row.size() > COL_TOTAL && !str(row, COL_TOTAL).isEmpty() ? num(row, COL_TOTAL) : null;
                break;                                           // «TOTALE GENERALE DI PROGETTO»: fine dei dati da importare
            }
            String b = str(row, COL_APP);
            boolean title = "T".equalsIgnoreCase(str(row, COL_TYPE));
            if (!b.isEmpty() && !b.equals(app)) {
                app = b;
                if (!title) {
                    cur = null;
                }
            }
            if (title) {
                cur = new Group(app.isEmpty() ? OTHER : app, str(row, COL_TITLE), num(row, COL_DAYS));      // colonna B vuota: gruppo «ALTRO»
                groups.add(cur);
            } else if (cur != null) {
                BigDecimal h = num(row, COL_HOURS);              // colonna D: ore
                BigDecimal gg = num(row, COL_DAYS);              // colonna E: giorni
                if (h.signum() == 0 && gg.signum() > 0) {
                    h = gg.multiply(new BigDecimal("8"));        // ore mancanti: giorni x 8
                }
                String d = str(row, COL_TITLE);
                if (h.signum() > 0 && !d.isEmpty()) {
                    cur.details.add(new Detail(d, str(row, COL_PROFILE), h, gg, rowNo));
                }
            }
        }
        List<App> apps = catalog.loadAll(true);
        List<Prof> profs = jdbc.sql("select p.id, p.name, coalesce(x.rate, p.amount) as rate from professional_profile p "
                        + "left join account_profile_rate x on x.profile_id = p.id and x.account_id = :acc "
                        + "where p.deleted_at is null and p.active order by p.sort_order, p.id")
                .param("acc", customerId == null ? -1L : customerId).query((rs, n) -> new Prof(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("rate"))).list();
        Set<String> badApps = new LinkedHashSet<>(), badProfiles = new LinkedHashSet<>(), noProfile = new LinkedHashSet<>();
        List<Item> out = new ArrayList<>();
        for (Group g : groups) {
            if (g.hours.signum() <= 0) {
                continue;                                                    // solo i gruppi con ore > 0
            }
            App a = findApp(apps, g.app);
            if (isGalileo(g.app)) {                                  // JGALILEO: si cerca per famiglia GALILEO nell'anagrafica applicazioni
                List<App> fam = apps.stream().filter(x -> "GALILEO".equalsIgnoreCase(x.family())).toList();
                a = fam.size() == 1 ? fam.get(0)
                        : fam.stream().filter(x -> galileoId != null && x.id() == galileoId).findFirst().orElse(null);
                if (a == null) {
                    badApps.add(g.app + (fam.isEmpty() ? " (nessuna applicazione con famiglia GALILEO)"
                            : " (più tipologie Galileo: sceglila nella pagina «Contenuti»)"));
                    continue;
                }
            }
            List<Detail> ds = g.details;             // le righe «T» sono solo titoli: la figura professionale si legge dalle righe di dettaglio
            boolean notFound = a == null && !isGalileo(g.app);       // applicazione non in anagrafica: gruppo «NON TROVATO» in fondo
            if (a == null && !notFound) {
                badApps.add(OTHER.equals(g.app) ? OTHER + " (colonna B vuota: crea in anagrafica un'applicazione chiamata ALTRO)" : g.app);
                continue;
            }
            if (ds.isEmpty()) {
                noProfile.add(g.title);
                continue;
            }
            for (Detail d : ds) {
                Prof p = findProfile(profs, d.profile());
                if (p == null) {
                    badProfiles.add(d.profile().isEmpty()
                            ? "(colonna F vuota alla riga " + d.row() + " «" + d.desc() + "», gruppo «" + g.title + "»)"
                            : d.profile() + " (riga " + d.row() + ")");
                    continue;
                }
                String desc = d.desc().length() > 250 ? d.desc().substring(0, 250) : d.desc();
                String grp = notFound ? g.app + " – " + g.title : g.title;      // il nome dell'applicazione del foglio resta nel titolo
                out.add(new Item(notFound ? 0L : a.id(), notFound ? g.app : ("GALILEO".equalsIgnoreCase(a.family()) ? "GALILEO" : a.name()), grp, desc, p.id(), p.name(), d.hours().setScale(2, RoundingMode.HALF_UP), d.days().setScale(2, RoundingMode.HALF_UP), p.rate()));
            }
        }
        out.sort(java.util.Comparator.comparingInt(i -> i.applicationId() == 0 ? 1 : 0));      // stabile: «NON TROVATO» in fondo
        List<String> err = new ArrayList<>();
        if (!badApps.isEmpty()) {
            err.add("applicazioni non trovate in anagrafica: " + String.join(", ", badApps));
        }
        if (!badProfiles.isEmpty()) {
            err.add("figure professionali non presenti nelle Tabelle Tariffe: " + String.join(", ", badProfiles));
        }
        if (!noProfile.isEmpty()) {
            err.add("gruppi senza attività con ore: " + String.join(", ", noProfile));
        }
        if (!err.isEmpty()) {
            throw new IllegalArgumentException("Importazione annullata – " + String.join("; ", err));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Nel foglio non ci sono gruppi con ore maggiori di zero");
        }
        return new Result(out, sheetTotal[0]);
    }

    /** nel foglio RFQ l'applicazione Galileo si chiama JGALILEO (o GALILEO) */
    private static boolean isGalileo(String name) {
        String n = norm(name);
        return n.equals("jgalileo") || n.equals("galileo");
    }

    /** nomi usati nel foglio RFQ che corrispondono ad altre applicazioni dell'anagrafica (chiave normalizzata -> nome/codice cercato) */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.of("app service", "wat");

    private static App findApp(List<App> apps, String name) {
        String n = norm(name);
        if (n.isEmpty()) {
            return null;
        }
        App found = ALIASES.containsKey(n) ? findAppNorm(apps, ALIASES.get(n)) : null;
        if (found == null) {
            found = findAppNorm(apps, n);
        }
        if (found == null && n.startsWith("app ")) {                  // «App ICM» nel foglio = applicazione «ICM»
            found = findAppNorm(apps, n.substring(4).trim());
        }
        return found;
    }

    private static App findAppNorm(List<App> apps, String n) {
        if (n.isEmpty()) {
            return null;
        }
        for (App a : apps) {
            if (norm(a.name()).equals(n) || norm(a.code()).equals(n)) {
                return a;
            }
        }
        List<App> part = apps.stream().filter(a -> norm(a.name()).startsWith(n) || n.startsWith(norm(a.name()))).toList();
        return part.size() == 1 ? part.get(0) : null;
    }

    private static Prof findProfile(List<Prof> profs, String name) {
        String n = norm(name);
        if (n.isEmpty()) {
            return null;
        }
        for (Prof p : profs) {
            if (norm(p.name()).equals(n)) {
                return p;
            }
        }
        List<Prof> part = profs.stream().filter(p -> norm(p.name()).contains(n) || n.contains(norm(p.name()))).toList();
        return part.size() == 1 ? part.get(0) : null;
    }
}
