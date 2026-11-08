package it.cpqia.service;

import it.cpqia.domain.AppUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Revisioni dell'offerta. «Nuova revisione» crea una copia completa e collegata dell'offerta: stesso numero, revisione + 1 (progressivo
 * calcolato sul gruppo di revisioni), stato Bozza. Le revisioni precedenti restano com'erano: sono la storia dell'offerta e,
 * superate, non si possono piu' modificare.
 */
@Service
public class OfferRevisionService {

    /** tabelle figlie con colonna offer_id copiate cosi' come sono (le colonne si leggono dal catalogo, per seguire le migrazioni future) */
    private static final List<String> CHILD_TABLES = List.of("offer_activity", "offer_profile_rate", "offer_power_maint", "offer_power_product",
            "offer_extra_service", "offer_extra_option", "offer_galileo", "offer_galileo_module");

    private static final Set<String> NOT_COPIED = Set.of("created_at", "updated_at", "created_by", "status", "revision", "revision_of", "deleted_at", "deleted_by");

    private final JdbcClient jdbc;
    private final OfferSnapshotService snapshots;

    public OfferRevisionService(JdbcClient jdbc, OfferSnapshotService snapshots) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
    }

    public record RevisionRow(long id, int revision, String number, java.time.LocalDate offerDate, String status, java.time.LocalDateTime createdAt, String author, boolean current, int changes) {
    }

    /** colonna del confronto: una revisione scelta */
    public record CompareColumn(long id, int revision, String label) {
    }

    public record CompareRow(String field, List<String> values, boolean changed) {
    }

    public record CompareSection(String name, List<CompareRow> rows) {
    }

    public record Comparison(List<CompareColumn> columns, List<CompareSection> sections) {
    }

    /** modifiche memorizzate di una revisione rispetto alla precedente */
    public record StoredChange(String section, String field, String oldValue, String newValue) {
    }

    /** radice del gruppo di revisioni (l'offerta originale) */
    private long root(long id) {
        return jdbc.sql("select coalesce(revision_of, id) from offer where id = :id").param("id", id).query(Long.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Offerta inesistente"));
    }

    public int revisionOf(long id) {
        return jdbc.sql("select revision from offer where id = :id").param("id", id).query(Integer.class).optional().orElse(0);
    }

    /** storia delle revisioni dell'offerta (dalla piu' recente); «current» = l'offerta richiesta */
    public List<RevisionRow> history(long id) {
        long root = root(id);
        return jdbc.sql("""
                select o.id, o.revision, o.number, o.offer_date, o.status, o.created_at, coalesce(u.full_name, u.email) as author,
                       (select count(*) from offer_revision_change c where c.offer_id = o.id) as changes
                from offer o join app_user u on u.id = o.created_by
                where (o.id = :r or o.revision_of = :r) and o.deleted_at is null order by o.revision desc""")
                .param("r", root).query((rs, n) -> new RevisionRow(rs.getLong("id"), rs.getInt("revision"), rs.getString("number"),
                        rs.getObject("offer_date", java.time.LocalDate.class), rs.getString("status"),
                        rs.getObject("created_at", java.time.LocalDateTime.class), rs.getString("author"), rs.getLong("id") == id, rs.getInt("changes"))).list();
    }

    /** revisione precedente a quella indicata nello stesso gruppo (null per la prima) */
    private Long previousId(long id) {
        long root = root(id);
        return jdbc.sql("select id from offer where (id = :r or revision_of = :r) and deleted_at is null and revision < (select revision from offer where id = :id) order by revision desc limit 1")
                .param("r", root).param("id", id).query(Long.class).optional().orElse(null);
    }

    /** ricalcola e memorizza le modifiche della revisione rispetto alla precedente (a ogni salvataggio); la prima revisione non ha modifiche */
    @Transactional
    public void recordChanges(long id) {
        jdbc.sql("delete from offer_revision_change where offer_id = :o").param("o", id).update();
        Long prev = previousId(id);
        if (prev == null) {
            return;
        }
        int n = 0;
        for (OfferSnapshotService.Change c : OfferSnapshotService.diff(snapshots.snapshot(prev), snapshots.snapshot(id))) {
            jdbc.sql("insert into offer_revision_change (offer_id, prev_id, section, field, old_value, new_value, sort_order) values (:o, :p, :s, :f, :a, :b, :n)")
                    .param("o", id).param("p", prev).param("s", c.section()).param("f", c.field()).param("a", c.oldValue()).param("b", c.newValue()).param("n", n++).update();
        }
    }

    public List<StoredChange> storedChanges(long id) {
        return jdbc.sql("select section, field, old_value, new_value from offer_revision_change where offer_id = :o order by sort_order, id").param("o", id)
                .query((rs, n) -> new StoredChange(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
    }

    /** confronto tra N revisioni dello stesso gruppo: una colonna per revisione, sotto i campi con i rispettivi valori */
    public Comparison compare(long anyId, List<Long> ids) {
        long root = root(anyId);
        List<RevisionRow> hist = history(anyId);
        List<CompareColumn> cols = new ArrayList<>();
        List<Map<String, Map<String, String>>> snaps = new ArrayList<>();
        List<Long> sorted = new ArrayList<>(ids);
        for (RevisionRow r : hist.stream().sorted(java.util.Comparator.comparingInt(RevisionRow::revision)).toList()) {
            if (sorted.contains(r.id())) {
                cols.add(new CompareColumn(r.id(), r.revision(), "Rev. " + r.revision()));
                snaps.add(snapshots.snapshot(r.id()));
            }
        }
        if (cols.size() < 2) {
            throw new IllegalArgumentException("Scegli almeno due revisioni dell'offerta da confrontare");
        }
        java.util.Set<String> sections = new java.util.LinkedHashSet<>();
        snaps.forEach(s -> sections.addAll(s.keySet()));
        List<CompareSection> out = new ArrayList<>();
        for (String sec : sections) {
            java.util.Set<String> fields = new java.util.LinkedHashSet<>();
            snaps.forEach(s -> fields.addAll(s.getOrDefault(sec, Map.of()).keySet()));
            List<CompareRow> rows = new ArrayList<>();
            for (String f : fields) {
                List<String> vals = new ArrayList<>();
                for (Map<String, Map<String, String>> s : snaps) {
                    vals.add(s.getOrDefault(sec, Map.of()).get(f));
                }
                rows.add(new CompareRow(f, vals, vals.stream().distinct().count() > 1));
            }
            out.add(new CompareSection(sec, rows));
        }
        return new Comparison(cols, out);
    }

    /** id della revisione piu' recente (attiva) del gruppo di revisioni dell'offerta */
    public long latestId(long id) {
        long root = root(id);
        return jdbc.sql("select id from offer where (id = :r or revision_of = :r) and deleted_at is null order by revision desc limit 1")
                .param("r", root).query(Long.class).optional().orElse(id);
    }

    /** vero se esiste una revisione piu' recente di questa: l'offerta e' superata e resta di sola consultazione */
    public boolean superseded(long id) {
        long root = root(id);
        return jdbc.sql("select count(*) from offer where (id = :r or revision_of = :r) and deleted_at is null and revision > (select revision from offer where id = :id)")
                .param("r", root).param("id", id).query(Long.class).single() > 0;
    }

    @Transactional
    public long create(AppUser user, long sourceId) {
        long root = root(sourceId);
        int next = jdbc.sql("select coalesce(max(revision), 0) + 1 from offer where id = :r or revision_of = :r").param("r", root).query(Integer.class).single();
        List<String> cols = copyColumns("offer", false);
        String list = String.join(", ", cols);
        long newId = jdbc.sql("insert into offer (" + list + ", created_by, status, revision, revision_of) select " + list
                + ", :by, 'BOZZA', :rev, :root from offer where id = :src returning id")
                .param("by", user.id()).param("rev", next).param("root", root).param("src", sourceId).query(Long.class).single();
        // righe e relativi moduli / grandezze: serve la mappatura dei nuovi id
        List<Long> oldLines = jdbc.sql("select id from offer_line where offer_id = :o order by id").param("o", sourceId).query(Long.class).list();
        for (Long ol : oldLines) {
            long nl = jdbc.sql("insert into offer_line (offer_id, application_id, mode_code, sort_order, license_amount, maintenance_amount) "
                    + "select :n, application_id, mode_code, sort_order, license_amount, maintenance_amount from offer_line where id = :l returning id")
                    .param("n", newId).param("l", ol).query(Long.class).single();
            jdbc.sql("insert into offer_line_driver (line_id, driver_id, qty) select :n, driver_id, qty from offer_line_driver where line_id = :l")
                    .param("n", nl).param("l", ol).update();
            jdbc.sql("insert into offer_line_module (line_id, module_id, qty, unit_price) select :n, module_id, qty, unit_price from offer_line_module where line_id = :l")
                    .param("n", nl).param("l", ol).update();
        }
        for (String t : CHILD_TABLES) {
            List<String> c = copyColumns(t, true);
            if (c.isEmpty()) {
                continue;
            }
            String l = String.join(", ", c);
            jdbc.sql("insert into " + t + " (offer_id, " + l + ") select :n, " + l + " from " + t + " where offer_id = :o")
                    .param("n", newId).param("o", sourceId).update();
        }
        recordChanges(newId);                      // modifiche rispetto alla revisione precedente (es. revisione promossa da una piu' vecchia)
        return newId;
    }

    /** colonne copiabili di una tabella: tutte tranne chiave seriale, offer_id (figlie) e, per «offer», i campi propri della revisione */
    private List<String> copyColumns(String table, boolean child) {
        List<String> out = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.sql("select column_name, column_default from information_schema.columns where table_schema = current_schema() and table_name = :t order by ordinal_position")
                .param("t", table).query().listOfRows();
        for (Map<String, Object> r : rows) {
            String name = (String) r.get("column_name");
            String def = r.get("column_default") == null ? "" : r.get("column_default").toString();
            if (def.startsWith("nextval") || (child && "offer_id".equals(name)) || (!child && NOT_COPIED.contains(name))) {
                continue;
            }
            out.add(name);
        }
        return out;
    }
}
