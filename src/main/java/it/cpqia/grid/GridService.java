package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import it.cpqia.grid.GridModels.Data;
import it.cpqia.grid.GridModels.Layout;
import it.cpqia.grid.GridSpec.ColDef;
import it.cpqia.grid.GridSpec.Scope;
import it.cpqia.trash.TrashService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Motore delle griglie: lettura, modifica in cella con controlli, creazione, eliminazione logica, viste salvate. */
@Service
public class GridService {

    private final JdbcClient jdbc;
    private final GridRegistry registry;
    private final TrashService trash;
    private final GridBehaviorService behaviors;
    private final GridExportPermissionService exportPerm;

    public GridService(JdbcClient jdbc, GridRegistry registry, TrashService trash, GridBehaviorService behaviors, GridExportPermissionService exportPerm) {
        this.exportPerm = exportPerm;
        this.behaviors = behaviors;
        this.jdbc = jdbc;
        this.registry = registry;
        this.trash = trash;
    }

    public GridSpec spec(String key, AppUser u) {
        GridSpec s = registry.get(key);
        if (s == null) {
            throw new IllegalArgumentException("Griglia sconosciuta: " + key);
        }
        if (!s.canView.test(u)) {
            throw new AccessDeniedException("Non autorizzato");
        }
        return s;
    }

    // ------------------------------------------------------------------ lettura
    private String selectSql(GridSpec s) {
        StringBuilder sb = new StringBuilder("select ").append(s.idExpr).append(" as \"id\"");
        for (ColDef c : s.cols) {
            sb.append(", (").append(c.sql).append(") as \"").append(c.key).append('"');
        }
        sb.append(" from ").append(s.from);
        return sb.toString();
    }

    private List<Map<String, Object>> query(GridSpec s, AppUser u, String extraWhere, Map<String, Object> extraParams) {
        Scope sc = s.scope.apply(u);
        StringBuilder sql = new StringBuilder(selectSql(s)).append(" where ").append(s.baseWhere == null ? "true" : s.baseWhere);
        if (sc.sql() != null && !sc.sql().isBlank()) {
            sql.append(' ').append(sc.sql());
        }
        if (extraWhere != null) {
            sql.append(" and ").append(extraWhere);
        }
        sql.append(" order by ").append(s.orderBy == null || s.orderBy.isBlank() ? s.idExpr : s.orderBy);
        JdbcClient.StatementSpec st = jdbc.sql(sql.toString());
        Map<String, Object> params = new HashMap<>(sc.params());
        params.putAll(extraParams);
        if (!params.isEmpty()) {
            st = st.params(params);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : st.query().listOfRows()) {
            out.add(clean(r));
        }
        return out;
    }

    private static Map<String, Object> clean(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : r.entrySet()) {
            Object v = e.getValue();
            if (v instanceof java.sql.Timestamp ts) {
                v = ts.toLocalDateTime().toString();
            } else if (v instanceof java.sql.Date d) {
                v = d.toLocalDate().toString();
            } else if (v instanceof java.time.temporal.Temporal t) {
                v = t.toString();
            }
            o.put(e.getKey(), v);
        }
        return o;
    }

    public Data data(String key, AppUser u) {
        GridSpec s = spec(key, u);
        List<Map<String, Object>> rows;
        if (s.customRowsFor != null) {
            rows = new ArrayList<>();
            for (Map<String, Object> r : s.customRowsFor.apply(u)) {
                rows.add(clean(r));
            }
        } else {
            rows = query(s, u, null, Map.of());
        }
        return new Data(s.meta(u).withBehavior(behaviors.get(key)).withExport(exportPerm.allowed(u, key)), rows);
    }

    private Map<String, Object> row(GridSpec s, AppUser u, String id) {
        long n;
        try {
            n = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificativo non valido");
        }
        List<Map<String, Object>> r = query(s, u, s.idExpr + " = :__id", Map.of("__id", n));
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Riga non trovata o non accessibile");
        }
        return r.get(0);
    }

    // ------------------------------------------------------------------ validazione per tipo
    static Object coerce(ColDef c, Object raw) {
        String label = c.label;
        switch (c.type) {
            case "bool": {
                if (raw instanceof Boolean b) {
                    return b;
                }
                String t = raw == null ? "" : raw.toString().trim().toLowerCase();
                if (t.equals("true") || t.equals("si") || t.equals("sì") || t.equals("1")) {
                    return Boolean.TRUE;
                }
                if (t.equals("false") || t.equals("no") || t.equals("0")) {
                    return Boolean.FALSE;
                }
                throw new IllegalArgumentException(label + ": valore sì/no non valido");
            }
            case "int": {
                String t = raw == null ? "" : raw.toString().trim();
                if (t.isEmpty()) {
                    return empty(c);
                }
                try {
                    int v = new BigDecimal(t.replace(',', '.')).intValueExact();
                    List<GridModels.Opt> allowedInts = c.opts();
                    if (allowedInts != null && allowedInts.stream().noneMatch(o -> o.value().equals(String.valueOf(v)))) {
                        throw new IllegalArgumentException(label + ": valori ammessi " + String.join(", ", allowedInts.stream().map(GridModels.Opt::value).toList()));
                    }
                    range(c, v);
                    return v;
                } catch (ArithmeticException | NumberFormatException e) {
                    throw new IllegalArgumentException(label + ": inserire un numero intero");
                }
            }
            case "coord": {
                String t = raw == null ? "" : raw.toString().trim();
                if (t.isEmpty()) {
                    return empty(c);
                }
                try {
                    BigDecimal v = new BigDecimal(t.replace(',', '.')).setScale(6, java.math.RoundingMode.HALF_UP);
                    range(c, v.doubleValue());
                    return v;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(c.label + ": inserire una coordinata decimale (es. 45.497500)");
                }
            }
            case "number":
            case "money":
            case "percent": {
                String t = raw == null ? "" : raw.toString().trim();
                if (t.isEmpty()) {
                    return empty(c);
                }
                try {
                    BigDecimal v = new BigDecimal(t.replace("€", "").replace("%", "").replace(" ", "").replace(',', '.'));
                    v = v.setScale(2, java.math.RoundingMode.HALF_UP);
                    range(c, v.doubleValue());
                    return v;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(label + ": inserire un numero valido");
                }
            }
            case "date": {
                String t = raw == null ? "" : raw.toString().trim();
                if (t.isEmpty()) {
                    return empty(c);
                }
                try {
                    return LocalDate.parse(t);
                } catch (Exception e) {
                    throw new IllegalArgumentException(label + ": data non valida (aaaa-mm-gg)");
                }
            }
            case "enum": {
                String t = raw == null ? "" : raw.toString().trim();
                List<GridModels.Opt> allowed = c.opts();
                if (allowed == null || allowed.stream().noneMatch(o -> o.value().equals(t))) {
                    throw new IllegalArgumentException(label + ": valore non ammesso");
                }
                return t.isEmpty() ? null : t;                  // opzione vuota = nessun valore
            }
            case "multi": {
                java.util.Collection<?> parts = raw instanceof java.util.Collection<?> col ? col
                        : java.util.Arrays.asList((raw == null ? "" : raw.toString()).split(","));
                java.util.Set<String> chosen = new java.util.HashSet<>();
                for (Object p : parts) {
                    String t = p == null ? "" : p.toString().trim();
                    if (t.isEmpty()) {
                        continue;
                    }
                    if (c.opts() == null || c.opts().stream().noneMatch(o -> o.value().equals(t))) {
                        throw new IllegalArgumentException(label + ": valore non ammesso (" + t + ")");
                    }
                    chosen.add(t);
                }
                if (c.required && chosen.isEmpty()) {
                    throw new IllegalArgumentException(c.label + " è obbligatorio");
                }
                return c.opts().stream().map(GridModels.Opt::value).filter(chosen::contains).collect(java.util.stream.Collectors.joining(","));
            }
            default: {
                String t = raw == null ? "" : raw.toString().trim();
                if (c.noSpaces) {
                    t = t.replace(" ", "");
                }
                if (c.upper) {
                    t = t.toUpperCase();
                }
                if (t.isEmpty()) {
                    return empty(c);
                }
                if (c.maxLen != null && t.length() > c.maxLen) {
                    throw new IllegalArgumentException(label + ": massimo " + c.maxLen + " caratteri");
                }
                return t;
            }
        }
    }

    private static Object empty(ColDef c) {
        if (c.required) {
            throw new IllegalArgumentException(c.label + " è obbligatorio");
        }
        return c.blankNotNull ? "" : null;
    }

    private static void range(ColDef c, double v) {
        if (c.min != null && v < c.min) {
            throw new IllegalArgumentException(c.label + ": minimo " + c.min.longValue());
        }
        if (c.max != null && v > c.max) {
            throw new IllegalArgumentException(c.label + ": massimo " + c.max.longValue());
        }
    }

    /** Excel delle righe indicate (ordine e colonne come in griglia); le righe non visibili all'utente sono ignorate */
    public byte[] exportXlsx(String key, List<String> columns, List<String> rowIds, AppUser u) {
        Data d = data(key, u);
        if (!d.meta().canExport()) {
            throw new AccessDeniedException("Esportazione dei dati non abilitata su questa griglia");
        }
        try {
            return GridExport.xlsx(d, columns == null ? List.of() : columns, rowIds);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Esportazione non riuscita", e);
        }
    }

    // ------------------------------------------------------------------ modifica / creazione / eliminazione
    public Map<String, Object> update(String key, String id, String field, Object value, AppUser u) {
        GridSpec s = spec(key, u);
        if (!s.canEdit.test(u)) {
            throw new AccessDeniedException("Non hai il permesso di modificare questi dati");
        }
        if (!behaviors.get(key).inlineEdit()) {
            throw new IllegalArgumentException("La modifica in griglia è disattivata per questa griglia (Impostazioni > Comportamento griglie): usa la scheda di modifica");
        }
        ColDef c = s.colDef(field);
        if (c == null || c.updateColumn == null || c.createOnly || (c.editWho != null && !c.editWho.test(u))) {
            throw new IllegalArgumentException("La colonna non è modificabile");
        }
        Map<String, Object> current = row(s, u, id);
        Object v = coerce(c, value);
        if (s.hook != null) {
            String msg = s.hook.check(u, current, c, v);
            if (msg != null) {
                throw new IllegalArgumentException(msg);
            }
        }
        try {
            jdbc.sql("update " + s.table + " set " + c.updateColumn + " = :v" + (s.touch == null ? "" : ", " + s.touch) + " where id = :id")
                    .param("v", v).param("id", Long.parseLong(id)).update();
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(dbMessage(e, c.label + ": esiste già un record con questo valore"));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(dbMessage(e, c.label + ": valore non accettato dal database"));
        }
        if (s.after != null) {
            s.after.run(u, Long.parseLong(id), c, v);
        }
        return row(s, u, id);
    }

    /** modifica di piu' campi della stessa riga (scheda di modifica): validazione completa, un solo aggiornamento */
    public Map<String, Object> updateMany(String key, String id, Map<String, Object> values, AppUser u) {
        GridSpec s = spec(key, u);
        if (!s.canEdit.test(u)) {
            throw new AccessDeniedException("Non hai il permesso di modificare questi dati");
        }
        Map<String, Object> current = row(s, u, id);
        Map<ColDef, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : (values == null ? Map.<String, Object>of() : values).entrySet()) {
            ColDef c = s.colDef(e.getKey());
            if (c == null || c.updateColumn == null || c.createOnly || (c.editWho != null && !c.editWho.test(u))) {
                throw new IllegalArgumentException("La colonna «" + e.getKey() + "» non è modificabile");
            }
            Object v = coerce(c, e.getValue());
            if (s.hook != null) {
                String msg = s.hook.check(u, current, c, v);
                if (msg != null) {
                    throw new IllegalArgumentException(msg);
                }
            }
            changes.put(c, v);
        }
        if (changes.isEmpty()) {
            return current;
        }
        StringBuilder set = new StringBuilder();
        JdbcClient.StatementSpec st;
        int i = 0;
        for (ColDef c : changes.keySet()) {
            set.append(i == 0 ? "" : ", ").append(c.updateColumn).append(" = :p").append(i);
            i++;
        }
        st = jdbc.sql("update " + s.table + " set " + set + (s.touch == null ? "" : ", " + s.touch) + " where id = :id").param("id", Long.parseLong(id));
        i = 0;
        for (Object v : changes.values()) {
            st = st.param("p" + i, v);
            i++;
        }
        try {
            st.update();
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(dbMessage(e, "Esiste già un record con gli stessi valori"));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(dbMessage(e, "Valori non accettati dal database: controllare i campi"));
        }
        if (s.after != null) {
            for (Map.Entry<ColDef, Object> e : changes.entrySet()) {
                s.after.run(u, Long.parseLong(id), e.getKey(), e.getValue());
            }
        }
        return row(s, u, id);
    }

    public Map<String, Object> create(String key, Map<String, Object> values, AppUser u) {
        GridSpec s = spec(key, u);
        if (!s.canCreate.test(u) || s.newHref != null) {
            throw new AccessDeniedException("Non hai il permesso di creare nuovi record");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (ColDef c : s.cols) {
            if (!c.creatable || c.updateColumn == null) {
                continue;
            }
            Object raw = values == null ? null : values.get(c.key);
            if (raw == null && !c.required && !"bool".equals(c.type)) {
                continue;
            }
            if (raw == null && "bool".equals(c.type)) {
                raw = Boolean.TRUE;
            }
            Object v = coerce(c, raw);
            if (s.hook != null) {
                String msg = s.hook.check(u, new HashMap<>(clean), c, v);
                if (msg != null) {
                    throw new IllegalArgumentException(msg);
                }
            }
            clean.put(c.key, v);
        }
        List<String> cols = new ArrayList<>();
        List<String> ph = new ArrayList<>();
        JdbcClient.StatementSpec st;
        int i = 0;
        for (ColDef c : s.cols) {
            if (clean.containsKey(c.key)) {
                cols.add(c.updateColumn);
                ph.add(":p" + i);
                i++;
            }
        }
        st = jdbc.sql("insert into " + s.table + " (" + String.join(", ", cols) + ") values (" + String.join(", ", ph) + ") returning id");
        i = 0;
        for (ColDef c : s.cols) {
            if (clean.containsKey(c.key)) {
                st = st.param("p" + i, clean.get(c.key));
                i++;
            }
        }
        long newId;
        try {
            newId = st.query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(dbMessage(e, "Esiste già un record con questo valore"));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(dbMessage(e, "Dati non accettati dal database: controllare i campi obbligatori"));
        }
        return row(s, u, String.valueOf(newId));
    }

    public void delete(String key, String id, AppUser u) {
        GridSpec s = spec(key, u);
        if (s.trashKind == null || !s.canDelete.test(u)) {
            throw new AccessDeniedException("Non hai il permesso di eliminare");
        }
        row(s, u, id);
        trash.delete(s.trashKind, Long.parseLong(id), u);
    }

    /** elimina definitivamente le righe selezionate (solo griglie che lo prevedono) */
    public int deleteMany(String key, List<String> ids, AppUser u) {
        GridSpec s = spec(key, u);
        if (s.bulkDeleter == null || !s.canBulkDelete.test(u)) {
            throw new AccessDeniedException("Non hai il permesso di eliminare");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Nessuna riga selezionata");
        }
        List<Long> nums = new ArrayList<>();
        for (String id : ids) {
            try {
                nums.add(Long.parseLong(id));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Identificativo non valido: " + id);
            }
        }
        s.bulkDeleter.accept(u, nums);
        return nums.size();
    }

    public String action(String key, String name, String id, AppUser u) {
        GridSpec s = spec(key, u);
        GridSpec.ServerAction a = s.serverActions.get(name);
        boolean allowed = s.actionDefs.stream().anyMatch(d -> ((GridModels.Action) d[0]).name().equals(name) && castPred(d[1]).test(u));
        if (a == null || !allowed) {
            throw new AccessDeniedException("Azione non consentita");
        }
        return a.run(u, id);
    }

    @SuppressWarnings("unchecked")
    private static java.util.function.Predicate<AppUser> castPred(Object o) {
        return (java.util.function.Predicate<AppUser>) o;
    }

    // ------------------------------------------------------------------ impostazioni per utente e per griglia
    public static final List<Integer> PAGE_SIZES = List.of(0, 10, 15, 25, 50, 100, 200, 500);
    public static final int DEFAULT_PAGE_SIZE = 15;

    /** righe per pagina scelte dall'utente per la griglia (0 = tutte; predefinito 15) */
    public int pageSize(String key, AppUser u) {
        spec(key, u);
        return jdbc.sql("select page_size from grid_setting where user_id = :u and grid_key = :k").param("u", u.id()).param("k", key)
                .query(Integer.class).optional().orElse(DEFAULT_PAGE_SIZE);
    }

    public void savePageSize(String key, int size, AppUser u) {
        spec(key, u);
        if (!PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException("Numero di righe per pagina non ammesso");
        }
        jdbc.sql("insert into grid_setting (user_id, grid_key, page_size) values (:u, :k, :s) "
                + "on conflict (user_id, grid_key) do update set page_size = excluded.page_size")
                .param("u", u.id()).param("k", key).param("s", size).update();
    }

    // ------------------------------------------------------------------ viste salvate (ordine colonne, ordinamenti, filtri, raggruppamenti)
    public List<Layout> layouts(String key, AppUser u) {
        spec(key, u);
        return jdbc.sql("select id, name, config, is_default from grid_layout where user_id = :u and grid_key = :k order by lower(name)")
                .param("u", u.id()).param("k", key)
                .query((rs, n) -> new Layout(rs.getLong("id"), rs.getString("name"), rs.getString("config"), rs.getBoolean("is_default"))).list();
    }

    public void saveLayout(String key, String name, String config, boolean makeDefault, AppUser u) {
        spec(key, u);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 80) {
            throw new IllegalArgumentException("Nome della vista obbligatorio (max 80 caratteri)");
        }
        if (config == null || config.length() > 50000 || !config.trim().startsWith("{")) {
            throw new IllegalArgumentException("Configurazione non valida");
        }
        if (makeDefault) {
            jdbc.sql("update grid_layout set is_default = false where user_id = :u and grid_key = :k").param("u", u.id()).param("k", key).update();
        }
        jdbc.sql("insert into grid_layout (user_id, grid_key, name, config, is_default) values (:u, :k, :n, :c, :d) "
                + "on conflict (user_id, grid_key, name) do update set config = excluded.config, is_default = grid_layout.is_default or excluded.is_default")
                .param("u", u.id()).param("k", key).param("n", n).param("c", config).param("d", makeDefault).update();
    }

    public void setDefaultLayout(String key, long id, boolean on, AppUser u) {
        spec(key, u);
        jdbc.sql("update grid_layout set is_default = false where user_id = :u and grid_key = :k").param("u", u.id()).param("k", key).update();
        if (on) {
            jdbc.sql("update grid_layout set is_default = true where id = :id and user_id = :u and grid_key = :k")
                    .param("id", id).param("u", u.id()).param("k", key).update();
        }
    }

    public void deleteLayout(String key, long id, AppUser u) {
        spec(key, u);
        jdbc.sql("delete from grid_layout where id = :id and user_id = :u and grid_key = :k").param("id", id).param("u", u.id()).param("k", key).update();
    }

    /**
     * messaggio specifico per le violazioni note dei vincoli del database; i nomi dei campi sono tra parentesi quadre, così la scheda
     * (grid.js) può evidenziarli. Per i vincoli non noti si riporta il dettaglio del database, se presente.
     */
    static String dbMessage(DataAccessException e, String fallback) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String m = t.getMessage() == null ? "" : t.getMessage();
        if (m.contains("ux_power_model")) {
            return "Esiste già un modello con gli stessi valori di [Tipo macchina, Modello, Tipo, Core, Montaggio]: modificare almeno uno di questi campi";
        }
        if (m.contains("power_model_version_check")) {
            return "Versione non ammessa: [Versione]";
        }
        if (m.contains("power_model_active_complete_check")) {
            return "Per attivare il modello servono [Tipo, Core, Montaggio, CPW, Gruppo processore]";
        }
        // vincolo non noto: si riporta la prima riga dell'errore del database (nome del vincolo o della colonna) per individuare il campo
        String first = m.lines().findFirst().orElse("").replace("ERROR:", "").trim();
        org.slf4j.LoggerFactory.getLogger(GridService.class).warn("Violazione di vincolo non mappata: {}", m);
        if (!first.isEmpty()) {
            return fallback + " (" + first + ")";
        }
        return fallback;
    }
}
