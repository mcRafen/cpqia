package it.cpqia.repo;

import it.cpqia.domain.AppUser;
import it.cpqia.trash.TrashKind;
import it.cpqia.trash.TrashService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scrittura dell'anagrafica applicazioni / moduli / fasce / vincoli. Ogni modifica invalida la cache del catalogo. */
@Repository
public class CatalogAdminRepository {

    private final JdbcClient jdbc;
    private final CatalogRepository catalog;
    private final TrashService trash;

    public CatalogAdminRepository(JdbcClient jdbc, CatalogRepository catalog, TrashService trash) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.trash = trash;
    }

    // ------------------------------------------------------------------ applicazioni
    /** prezzi base impostati sui gruppi di prezzo dell'applicazione: {descrizione, importo formattato} per gruppo e modalita' */
    public java.util.List<String[]> basePrices(long appId) {
        java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.ITALY);
        return jdbc.sql("""
                select g.label, b.mode_code, b.amount from price_group_base b join price_group g on g.id = b.group_id
                where g.application_id = :a and g.deleted_at is null order by g.sort_order, g.id, b.mode_code""")
                .param("a", appId).query((rs, n) -> new String[]{rs.getString(1) + " (" + rs.getString(2) + ")", nf.format(rs.getBigDecimal(3))}).list();
    }

    @Transactional
    public long saveApplication(Long id, Map<String, String> p) {
        BigDecimal pct = dec(p.get("maintenancePct"), new BigDecimal("18"));
        boolean active = flag(p, "active");
        boolean review = flag(p, "needsReview");
        BigDecimal reduction = decOrNull(p.get("additionalReductionPct"));            // facoltativa: vuoto = nessuna riduzione
        if (reduction != null && (reduction.signum() < 0 || reduction.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Riduzione su moduli aggiuntivi: valore tra 0 e 100");
        }
        long out;
        if (id == null) {
            out = jdbc.sql("""
                    insert into application (code, name, family, description, maintenance_pct, needs_review, notes, active, sort_order, additional_reduction_pct)
                    values (:code, :name, :fam, :descr, :pct, :rev, :notes, :act, :sort, :red) returning id""")
                    .param("code", req(p, "code")).param("name", req(p, "name")).param("fam", nz(p.get("family")))
                    .param("descr", nz(p.get("description"))).param("pct", pct).param("rev", review)
                    .param("notes", nz(p.get("notes"))).param("act", active).param("sort", intv(p.get("sortOrder"), 1000)).param("red", reduction)
                    .query(Long.class).single();
        } else {
            jdbc.sql("""
                    update application set code = :code, name = :name, family = :fam, description = :descr,
                        maintenance_pct = :pct, needs_review = :rev, notes = :notes, active = :act,
                        sort_order = coalesce(:sort, sort_order), additional_reduction_pct = :red
                    where id = :id""")
                    .param("code", req(p, "code")).param("name", req(p, "name")).param("fam", nz(p.get("family")))
                    .param("descr", nz(p.get("description"))).param("pct", pct).param("rev", review)
                    .param("notes", nz(p.get("notes"))).param("act", active).param("sort", intOrNull(p.get("sortOrder"))).param("red", reduction)
                    .param("id", id).update();
            out = id;
        }
        catalog.evict();
        return out;
    }

    public void deleteApplication(long id, AppUser user) {
        trash.delete(TrashKind.APPLICATION, id, user);
        catalog.evict();
    }

    // ------------------------------------------------------------------ modalita' e driver
    public void addMode(long appId, String code, String label, boolean preferred) {
        if (preferred) {
            jdbc.sql("update app_mode set is_default = false where application_id = :a").param("a", appId).update();
        }
        jdbc.sql("insert into app_mode (application_id, code, label, is_default, sort_order) values (:a, :c, :l, :d, (select coalesce(max(sort_order), 0) + 10 from app_mode where application_id = :a))")
                .param("a", appId).param("c", code.trim().toUpperCase()).param("l", label).param("d", preferred).update();
        catalog.evict();
    }

    public void deleteMode(long appId, String code, AppUser user) {
        Long id = jdbc.sql("select id from app_mode where application_id = :a and code = :c and deleted_at is null")
                .param("a", appId).param("c", code).query(Long.class).optional().orElseThrow(() -> new IllegalArgumentException("Modalità inesistente"));
        trash.delete(TrashKind.MODE, id, user);
        catalog.evict();
    }

    public void addDriver(long appId, String code, String label, String licenseCode, String unitLabel) {
        jdbc.sql("insert into app_driver (application_id, code, label, license_code, unit_label, sort_order) values (:a, :c, :l, :lc, :u, (select coalesce(max(sort_order), 0) + 10 from app_driver where application_id = :a))")
                .param("a", appId).param("c", code.trim().toUpperCase()).param("l", label).param("lc", nz(licenseCode))
                .param("u", unitLabel == null || unitLabel.isBlank() ? "utenti" : unitLabel).update();
        catalog.evict();
    }

    public void deleteDriver(long id, AppUser user) {
        trash.delete(TrashKind.DRIVER, id, user);
        catalog.evict();
    }

    // ------------------------------------------------------------------ gruppi e fasce
    @Transactional
    public void addGroup(long appId, long driverId, String code, String label, String baseMode, String fixedBase) {
        long gid = jdbc.sql("insert into price_group (application_id, driver_id, code, label, base_mode, sort_order) values (:a, :d, :c, :l, :m, (select coalesce(max(sort_order), 0) + 10 from price_group where application_id = :a)) returning id")
                .param("a", appId).param("d", driverId).param("c", code.trim().toUpperCase()).param("l", label)
                .param("m", "LINEAR".equals(baseMode) ? "LINEAR" : ("STEP".equals(baseMode) ? "STEP" : "TIER1")).query(Long.class).single();
        for (Map.Entry<String, BigDecimal> e : parsePrices(fixedBase).entrySet()) {
            jdbc.sql("insert into price_group_base (group_id, mode_code, amount) values (:g, :m, :a)")
                    .param("g", gid).param("m", e.getKey()).param("a", e.getValue()).update();
        }
        catalog.evict();
    }

    public void deleteGroup(long id, AppUser user) {
        trash.delete(TrashKind.GROUP, id, user);
        catalog.evict();
    }

    @Transactional
    public void saveTier(Long id, long groupId, long from, Long to, String label, BigDecimal pct, String unitPrices) {
        long tid;
        if (id == null) {
            tid = jdbc.sql("insert into pricing_tier (group_id, from_qty, to_qty, label, pct, sort_order) values (:g, :f, :t, :l, :p, :f) returning id")
                    .param("g", groupId).param("f", from).param("t", to).param("l", label).param("p", pct).query(Long.class).single();
        } else {
            jdbc.sql("update pricing_tier set from_qty = :f, to_qty = :t, label = :l, pct = :p, sort_order = :f where id = :id")
                    .param("f", from).param("t", to).param("l", label).param("p", pct).param("id", id).update();
            tid = id;
        }
        jdbc.sql("delete from pricing_tier_price where tier_id = :t").param("t", tid).update();
        for (Map.Entry<String, BigDecimal> e : parsePrices(unitPrices).entrySet()) {
            jdbc.sql("insert into pricing_tier_price (tier_id, mode_code, unit_price) values (:t, :m, :p)")
                    .param("t", tid).param("m", e.getKey()).param("p", e.getValue()).update();
        }
        catalog.evict();
    }

    public void deleteTier(long id, AppUser user) {
        trash.delete(TrashKind.TIER, id, user);
        catalog.evict();
    }

    // ------------------------------------------------------------------ moduli
    @Transactional
    public long saveModule(Long id, long appId, Map<String, String> p) {
        Long groupId = p.get("groupId") == null || p.get("groupId").isBlank() ? null : Long.valueOf(p.get("groupId"));
        String section = p.getOrDefault("section", "FIXED");
        long mid;
        if (id == null) {
            mid = jdbc.sql("""
                    insert into module (application_id, group_id, code, print_code, description, section, kind, qty_enabled, excl_group,
                        default_selected, included, maintenance_pct, onetime_price, link_text, note, active, sort_order)
                    values (:a, :g, :code, :pc, :descr, :sec, :kind, :qty, :excl, :def, :inc, :mp, :ot, :link, :note, :act,
                        coalesce(:sort, (select coalesce(max(sort_order), 0) + 10 from module where application_id = :a)))
                    returning id""")
                    .param("a", appId).param("g", groupId).param("code", req(p, "code")).param("pc", nz(p.get("printCode")))
                    .param("descr", req(p, "description")).param("sec", section).param("kind", p.getOrDefault("kind", "LICENSE"))
                    .param("qty", flag(p, "qtyEnabled")).param("excl", nz(p.get("exclGroup"))).param("def", flag(p, "defaultSelected"))
                    .param("inc", flag(p, "included")).param("mp", decOrNull(p.get("maintenancePct")))
                    .param("ot", dec(p.get("onetimePrice"), BigDecimal.ZERO)).param("link", nz(p.get("linkText")))
                    .param("note", nz(p.get("note"))).param("act", true).param("sort", intOrNull(p.get("sortOrder")))
                    .query(Long.class).single();
        } else {
            jdbc.sql("""
                    update module set group_id = :g, code = :code, print_code = :pc, description = :descr, section = :sec,
                        kind = :kind, qty_enabled = :qty, excl_group = :excl, default_selected = :def, included = :inc,
                        maintenance_pct = :mp, onetime_price = :ot, link_text = :link, note = :note, active = :act,
                        sort_order = coalesce(:sort, sort_order) where id = :id""")
                    .param("g", groupId).param("code", req(p, "code")).param("pc", nz(p.get("printCode")))
                    .param("descr", req(p, "description")).param("sec", section).param("kind", p.getOrDefault("kind", "LICENSE"))
                    .param("qty", flag(p, "qtyEnabled")).param("excl", nz(p.get("exclGroup"))).param("def", flag(p, "defaultSelected"))
                    .param("inc", flag(p, "included")).param("mp", decOrNull(p.get("maintenancePct")))
                    .param("ot", dec(p.get("onetimePrice"), BigDecimal.ZERO)).param("link", nz(p.get("linkText")))
                    .param("note", nz(p.get("note"))).param("act", true)
                    .param("sort", intOrNull(p.get("sortOrder"))).param("id", id).update();
            mid = id;
        }
        if (p.containsKey("area")) {
            jdbc.sql("update module set area = :ar where id = :id").param("ar", nz(p.get("area"))).param("id", mid).update();
        }
        jdbc.sql("delete from module_price where module_id = :m").param("m", mid).update();
        Map<String, BigDecimal> prices = parsePrices(p.get("prices"));
        if (prices.isEmpty()) {
            prices.put("STD", BigDecimal.ZERO);
        }
        for (Map.Entry<String, BigDecimal> e : prices.entrySet()) {
            jdbc.sql("insert into module_price (module_id, mode_code, price) values (:m, :c, :p)")
                    .param("m", mid).param("c", e.getKey()).param("p", e.getValue()).update();
        }
        catalog.evict();
        return mid;
    }

    /** area applicativa dei moduli dell'applicazione (moduleId -> area) */
    public Map<Long, String> moduleAreas(long appId) {
        Map<Long, String> out = new java.util.HashMap<>();
        jdbc.sql("select id, area from module where application_id = :a and area is not null and deleted_at is null").param("a", appId)
                .query((rs, n) -> {
                    out.put(rs.getLong(1), rs.getString(2));
                    return null;
                }).list();
        return out;
    }

    public void deleteModule(long id, AppUser user) {
        trash.delete(TrashKind.MODULE, id, user);
        catalog.evict();
    }

    public void addRule(long moduleId, String type, String targets, String severity, String message) {
        jdbc.sql("insert into module_rule (module_id, rule_type, target_codes, severity, message) values (:m, :t, :c, :s, :msg)")
                .param("m", moduleId).param("t", type).param("c", nz(targets))
                .param("s", "WARNING".equals(severity) ? "WARNING" : "ERROR").param("msg", message).update();
        catalog.evict();
    }

    public void updateRule(long ruleId, long moduleId, String type, String targets, String severity, String message) {
        if (!java.util.List.of("REQUIRES", "REQUIRES_ANY", "EXCLUDES", "INFO").contains(type)) {
            throw new IllegalArgumentException("Tipo di vincolo non valido");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Il messaggio del vincolo è obbligatorio");
        }
        int n = jdbc.sql("update module_rule set rule_type = :t, target_codes = :c, severity = :s, message = :msg where id = :id and module_id = :m")
                .param("t", type).param("c", nz(targets)).param("s", "WARNING".equals(severity) ? "WARNING" : "ERROR")
                .param("msg", message.trim()).param("id", ruleId).param("m", moduleId).update();
        if (n == 0) {
            throw new IllegalArgumentException("Vincolo inesistente");
        }
        catalog.evict();
    }

    public void deleteRule(long id, AppUser user) {
        trash.delete(TrashKind.RULE, id, user);
        catalog.evict();
    }

    // ------------------------------------------------------------------ helper
    /** "1000" -> {STD=1000};  "NEW=6000;MIGR=3000" -> {NEW=6000, MIGR=3000} */
    public static Map<String, BigDecimal> parsePrices(String text) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return m;
        }
        for (String part : text.split("[;\\n]")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            int eq = s.indexOf('=');
            String mode = eq < 0 ? "STD" : s.substring(0, eq).trim().toUpperCase();
            String num = (eq < 0 ? s : s.substring(eq + 1)).trim();
            // formato italiano (1.234,50) se c'e' la virgola, altrimenti il punto e' il separatore decimale
            if (num.contains(",")) {
                num = num.replace(".", "").replace(',', '.');
            }
            m.put(mode, new BigDecimal(num));
        }
        return m;
    }

    /** formatta i prezzi per la modifica: {STD=1000} -> "1000" ; modalita' -> "NEW=6000;MIGR=3000" */
    public static String formatPrices(Map<String, BigDecimal> prices) {
        if (prices == null || prices.isEmpty()) {
            return "";
        }
        if (prices.size() == 1 && prices.containsKey("STD")) {
            return prices.get("STD").stripTrailingZeros().toPlainString();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BigDecimal> e : prices.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('=').append(e.getValue().stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    private static String req(Map<String, String> p, String k) {
        String v = p.get(k);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Campo obbligatorio: " + k);
        }
        return v.trim();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean flag(Map<String, String> p, String k) {
        String v = p.get(k);
        return v != null && (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equals("1"));
    }

    private static int intv(String s, int def) {
        try {
            return s == null || s.isBlank() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Integer intOrNull(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal dec(String s, BigDecimal def) {
        return s == null || s.isBlank() ? def : new BigDecimal(s.trim().replace(',', '.'));
    }

    private static BigDecimal decOrNull(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s.trim().replace(',', '.'));
    }

    /** «Riduzione su moduli aggiuntivi» (%) dell'applicazione, null se non impostata */
    public BigDecimal additionalReduction(long appId) {
        return jdbc.sql("select additional_reduction_pct from application where id = :id").param("id", appId).query(BigDecimal.class).optional().orElse(null);
    }
}
