package it.cpqia.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.cpqia.repo.CatalogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pacchetto delle impostazioni: il contenuto delle tabelle di «Impostazioni» del database di sviluppo viaggia dentro il WAR
 * (settings/settings-pack.json) e, all'avvio, viene applicato al database in cui il WAR e' installato. Offerte e account restano dove sono.
 * Esportazione: Impostazioni > Aggiornamento. Applicazione: automatica, una sola volta per ogni versione del pacchetto.
 * Le righe del pacchetto vengono inserite o aggiornate per chiave primaria (stessi id dello sviluppo); le righe eliminate in sviluppo con il Cestino
 * (cancellazione logica) risultano eliminate anche nell'installazione. Non si trasportano utenti, agenti, comuni, integrazioni e preferenze delle griglie,
 * che appartengono all'ambiente.
 */
@Service
public class SettingsPackService {

    private static final Logger log = LoggerFactory.getLogger(SettingsPackService.class);
    public static final String RESOURCE = "settings/settings-pack.json";

    /** tabelle trasportate, in ordine di dipendenza (le tabelle «padre» prima delle «figlie») */
    public static final List<String> TABLES = List.of(
            "company_type", "address_type", "professional_profile", "travel_rate", "sede", "power_model", "ibmi_product",
            "application", "app_mode", "app_driver", "price_group", "price_group_base", "pricing_tier", "pricing_tier_price",
            "module", "module_price", "module_rule",
            "extra_service", "extra_group", "extra_option",
            "galileo_listino", "galileo_profile", "galileo_tier", "galileo_module",
            "grid_behavior");

    public record Applied(String sha256, LocalDateTime appliedAt, String summary) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final CatalogRepository catalog;

    public SettingsPackService(JdbcClient jdbc, ObjectMapper mapper, TransactionTemplate tx, CatalogRepository catalog) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = tx;
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------ metadati
    private List<String> pkColumns(String table) {
        return jdbc.sql("select a.attname from pg_index i join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any(i.indkey) "
                + "where i.indrelid = cast(:t as regclass) and i.indisprimary order by array_position(i.indkey::int2[], a.attnum)")
                .param("t", table).query(String.class).list();
    }

    private List<String> columns(String table) {
        return jdbc.sql("select column_name from information_schema.columns where table_schema = current_schema() and table_name = :t order by ordinal_position")
                .param("t", table).query(String.class).list();
    }

    /** colonne che puntano a tabelle non trasportate (es. deleted_by -> utenti): nel pacchetto valgono NULL */
    private Set<String> foreignOutside(String table) {
        return Set.copyOf(jdbc.sql("""
                select a.attname from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any(c.conkey)
                where c.contype = 'f' and c.conrelid = cast(:t as regclass)
                  and not (c.confrelid::regclass::text = any(string_to_array(:inside, ',')))""")
                .param("t", table).param("inside", String.join(",", TABLES)).query(String.class).list());
    }

    // ------------------------------------------------------------------ esportazione
    /** contenuto attuale delle tabelle delle impostazioni, come JSON */
    public String export() throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("generatedAt", LocalDateTime.now().toString());
        ObjectNode tables = root.putObject("tables");
        for (String t : TABLES) {
            Set<String> nulls = foreignOutside(t);
            List<String> pk = pkColumns(t);
            String order = pk.isEmpty() ? "1" : String.join(", ", pk);
            String json = jdbc.sql("select coalesce(json_agg(x order by " + order + "), '[]'::json)::text from " + t + " x").query(String.class).single();
            JsonNode rows = mapper.readTree(json);
            for (JsonNode row : rows) {
                for (String c : nulls) {
                    if (row.has(c)) {
                        ((ObjectNode) row).putNull(c);
                    }
                }
            }
            tables.set(t, rows);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    /** conteggio delle righe per tabella di un pacchetto */
    public Map<String, Integer> counts(String json) throws IOException {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonNode t = mapper.readTree(json).path("tables");
        for (String name : TABLES) {
            out.put(name, t.path(name).size());
        }
        return out;
    }

    /** scrive il pacchetto nella cartella delle risorse del progetto (solo in sviluppo); restituisce il percorso oppure empty */
    public Optional<Path> writeToProject(String json) throws IOException {
        Path dev = Path.of("src", "main", "resources");
        if (!Files.isDirectory(dev)) {
            return Optional.empty();
        }
        Path target = dev.resolve(RESOURCE);
        Files.createDirectories(target.getParent());
        Files.writeString(target, json, StandardCharsets.UTF_8);
        return Optional.of(target.toAbsolutePath());
    }

    // ------------------------------------------------------------------ applicazione
    public Optional<String> packedJson() throws IOException {
        ClassPathResource r = new ClassPathResource(RESOURCE);
        if (!r.exists()) {
            return Optional.empty();
        }
        try (InputStream in = r.getInputStream()) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Applied> lastApplied() {
        return jdbc.sql("select sha256, applied_at, summary from settings_pack_applied where id = 1").query(Applied.class).optional();
    }

    /** applica il pacchetto del WAR se e' nuovo; restituisce il riepilogo oppure empty se non c'era nulla da fare */
    public Optional<String> applyIfNew() throws IOException {
        Optional<String> json = packedJson();
        if (json.isEmpty()) {
            return Optional.empty();
        }
        String hash = sha(json.get());
        Optional<Applied> last = lastApplied();
        if (last.isPresent() && last.get().sha256().equals(hash)) {
            return Optional.empty();
        }
        String summary = apply(json.get());
        jdbc.sql("insert into settings_pack_applied (id, sha256, applied_at, summary) values (1, :h, now(), :s) "
                + "on conflict (id) do update set sha256 = :h, applied_at = now(), summary = :s").param("h", hash).param("s", summary).update();
        return Optional.of(summary);
    }

    /** inserisce o aggiorna per chiave primaria le righe del pacchetto, in una sola transazione */
    String apply(String json) throws IOException {
        JsonNode tables = mapper.readTree(json).path("tables");
        Map<String, Integer> done = new LinkedHashMap<>();
        tx.executeWithoutResult(st -> {
            jdbc.sql("select set_config('cpqia.settings_import', 'on', true)").query().singleRow();
            for (String t : TABLES) {
                JsonNode rows = tables.path(t);
                if (!rows.isArray() || rows.isEmpty()) {
                    continue;
                }
                List<String> pk = pkColumns(t);
                List<String> cols = columns(t);
                if (pk.isEmpty()) {
                    throw new IllegalStateException("Tabella senza chiave primaria: " + t);
                }
                StringBuilder set = new StringBuilder();
                for (String c : cols) {
                    if (!pk.contains(c)) {
                        set.append(set.length() == 0 ? "" : ", ").append('"').append(c).append("\" = excluded.\"").append(c).append('"');
                    }
                }
                String conflict = pk.stream().map(c -> '"' + c + '"').collect(java.util.stream.Collectors.joining(", "));
                String sql = "insert into " + t + " select * from json_populate_recordset(null::" + t + ", cast(:j as json)) on conflict (" + conflict + ") "
                        + (set.length() == 0 ? "do nothing" : "do update set " + set);
                jdbc.sql(sql).param("j", rows.toString()).update();
                done.put(t, rows.size());
                resetSequence(t, pk);
            }
        });
        catalog.evict();
        StringBuilder sb = new StringBuilder("Impostazioni aggiornate: ");
        List<String> parts = new ArrayList<>();
        done.forEach((k, v) -> parts.add(k + " " + v));
        sb.append(String.join(", ", parts));
        log.info("CPQIA {}", sb);
        return sb.toString();
    }

    private void resetSequence(String table, List<String> pk) {
        if (pk.size() != 1) {
            return;
        }
        String seq = jdbc.sql("select pg_get_serial_sequence(:t, :c)").param("t", table).param("c", pk.get(0)).query(String.class).optional().orElse(null);
        if (seq != null) {
            jdbc.sql("select setval(cast(:s as regclass), greatest(coalesce((select max(\"" + pk.get(0) + "\") from " + table + "), 1), 1))").param("s", seq).query().singleRow();
        }
    }
}
