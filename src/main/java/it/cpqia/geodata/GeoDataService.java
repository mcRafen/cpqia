package it.cpqia.geodata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Comuni d'Italia (con sigla, provincia e regione): ricerca per la selezione nei moduli e importazione da internet (elenco open data in JSON). */
@Service
public class GeoDataService {

    private static final Logger log = LoggerFactory.getLogger(GeoDataService.class);

    public record ComuneHit(long id, String nome, String cap, String capList, String sigla, String provincia) {
    }

    public record ProvinciaHit(String sigla, String nome, String regione) {
    }

    public record ImportResult(int province, int comuni, String source) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final ObjectMapper mapper;
    private final boolean autoImport;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public GeoDataService(JdbcClient jdbc, JdbcTemplate template, ObjectMapper mapper,
                          @Value("${cpqia.geodata.auto-import:true}") boolean autoImport,
                          @Value("${cpqia.geodata.url:https://raw.githubusercontent.com/matteocontrini/comuni-json/master/comuni.json}") String url) {
        this.jdbc = jdbc;
        this.template = template;
        this.mapper = mapper;
        this.autoImport = autoImport;
        this.url = url;
    }

    // ------------------------------------------------------------------ ricerca
    private static String like(String q) {
        return q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** comuni il cui nome inizia (poi contiene) il testo; con sigla, solo quella provincia */
    public List<ComuneHit> comuni(String q, String sigla, int limit) {
        String t = q == null ? "" : q.trim();
        if (t.length() < 1) {
            return List.of();
        }
        String sql = """
                select c.id, c.nome, c.cap, c.cap_list, c.sigla, c.provincia
                from comuni c
                where lower(c.nome) like :contains escape '\\'""" + (sigla == null || sigla.isBlank() ? "" : " and c.sigla = :sigla") + """
                
                order by (lower(c.nome) like :starts escape '\\') desc, c.nome limit :lim""";
        JdbcClient.StatementSpec st = jdbc.sql(sql).param("contains", "%" + like(t) + "%").param("starts", like(t) + "%").param("lim", Math.min(Math.max(limit, 1), 50));
        if (sigla != null && !sigla.isBlank()) {
            st = st.param("sigla", sigla.trim().toUpperCase());
        }
        return st.query((rs, n) -> new ComuneHit(rs.getLong("id"), rs.getString("nome"), rs.getString("cap"), rs.getString("cap_list"),
                rs.getString("sigla"), rs.getString("provincia"))).list();
    }

    /** province (ricavate dall'elenco dei comuni) per sigla o nome */
    public List<ProvinciaHit> province(String q, int limit) {
        String t = q == null ? "" : q.trim();
        String sql = """
                select sigla, provincia as nome, max(regione) as regione from comuni
                where :t = '' or lower(provincia) like :contains escape '\\' or lower(sigla) like :starts escape '\\'
                group by sigla, provincia
                order by (lower(sigla) = :exact) desc, (lower(provincia) like :starts escape '\\') desc, provincia limit :lim""";
        return jdbc.sql(sql).param("t", t).param("contains", "%" + like(t) + "%").param("starts", like(t) + "%").param("exact", t.toLowerCase())
                .param("lim", Math.min(Math.max(limit, 1), 120))
                .query((rs, n) -> new ProvinciaHit(rs.getString("sigla"), rs.getString("nome"), rs.getString("regione"))).list();
    }

    public long countComuni() {
        return jdbc.sql("select count(*) from comuni").query(Long.class).single();
    }

    // ------------------------------------------------------------------ importazione
    /** all'avvio, se la tabella dei comuni e' vuota, scarica l'elenco (in background, senza bloccare l'avvio) */
    @EventListener(ApplicationReadyEvent.class)
    public void importIfEmpty() {
        if (!autoImport) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                if (countComuni() == 0) {
                    ImportResult r = importNow();
                    log.info("Comuni importati: {} comuni, {} province da {}", r.comuni(), r.province(), r.source());
                }
            } catch (Exception e) {
                log.warn("Importazione automatica dei comuni non riuscita ({}). Si può rilanciare da Impostazioni > Comuni.", e.toString());
            }
        }, "geodata-import");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Scarica il JSON dei comuni (nome, codice ISTAT, sigla e nome provincia, regione, codice catastale, elenco CAP),
     * inserisce/aggiorna i comuni (con sigla, provincia e regione) per codice ISTAT. Ripetibile.
     */
    public synchronized ImportResult importNow() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Download non riuscito: HTTP " + res.statusCode() + " da " + url);
        }
        JsonNode root = mapper.readTree(res.body());
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalStateException("Formato inatteso dell'elenco dei comuni");
        }
        Map<String, String[]> prov = new HashMap<>();            // sigla -> {nome, regione}
        List<Object[]> comuni = new ArrayList<>();               // istat, nome, sigla, cap, capList, catastale
        for (JsonNode n : root) {
            JsonNode p = n.path("provincia");
            String sigla = text(n, "sigla");
            if (sigla.isEmpty()) {
                sigla = text(p, "sigla");
            }
            String nomeProv = p.isTextual() ? p.asText() : text(p, "nome");
            String regione = n.path("regione").isTextual() ? n.path("regione").asText() : text(n.path("regione"), "nome");
            String nome = text(n, "nome");
            if (sigla.length() != 2 || nome.isEmpty()) {
                continue;
            }
            prov.putIfAbsent(sigla.toUpperCase(), new String[]{nomeProv.isEmpty() ? sigla : nomeProv, regione});
            List<String> caps = new ArrayList<>();
            JsonNode cc = n.path("cap");
            if (cc.isArray()) {
                cc.forEach(x -> caps.add(x.asText()));
            } else if (cc.isTextual()) {
                caps.add(cc.asText());
            }
            String istat = text(n, "codice");
            comuni.add(new Object[]{istat.isEmpty() ? null : istat, nome, sigla.toUpperCase(),
                    caps.isEmpty() ? null : caps.get(0), caps.isEmpty() ? null : String.join(",", caps), blankNull(text(n, "codiceCatastale"))});
        }
        if (comuni.isEmpty()) {
            throw new IllegalStateException("Nessun comune riconosciuto nell'elenco scaricato");
        }
        List<Object[]> withIstat = new ArrayList<>();
        for (Object[] c : comuni) {
            if (c[0] != null) {
                String[] pv = prov.get((String) c[2]);
                withIstat.add(new Object[]{c[0], c[1], c[2], pv[0], pv[1], c[3], c[4], c[5]});
            }
        }
        template.batchUpdate("insert into comuni (istat_code, nome, sigla, provincia, regione, cap, cap_list, catastale) values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (istat_code) where istat_code is not null do update set nome = excluded.nome, sigla = excluded.sigla, "
                + "provincia = excluded.provincia, regione = excluded.regione, cap = excluded.cap, cap_list = excluded.cap_list, catastale = excluded.catastale", withIstat);
        return new ImportResult(prov.size(), withIstat.size(), url);
    }

    private static String text(JsonNode n, String f) {
        return n == null ? "" : n.path(f).asText("").trim();
    }

    private static String blankNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
