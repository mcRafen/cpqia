package it.cpqia.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fotografia leggibile di un'offerta (sezioni → campi → valori in testo), usata per confrontare le revisioni e per memorizzare
 * le modifiche tra una revisione e la precedente. Legge direttamente i dati salvati, quindi vale per qualsiasi revisione.
 */
@Service
public class OfferSnapshotService {

    public record Change(String section, String field, String oldValue, String newValue) {
    }

    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.ITALY);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static {
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
    }

    private final JdbcClient jdbc;

    public OfferSnapshotService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String money(Object o) {
        return o == null ? "" : "€ " + MONEY.format(o instanceof BigDecimal b ? b : new BigDecimal(o.toString()));
    }

    private static String yn(Object o) {
        return Boolean.TRUE.equals(o) ? "Sì" : "No";
    }

    private static String s(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate().format(DATE);
        }
        if (o instanceof LocalDate d) {
            return d.format(DATE);
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        if (o instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        return o.toString();
    }

    private static void put(Map<String, Map<String, String>> snap, String section, String field, String value) {
        snap.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(field, value == null ? "" : value);
    }

    private static String num(Map<String, Object> m, String col) {
        return s(m.get(col));
    }

    public Map<String, Map<String, String>> snapshot(long id) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Map<String, Object> o = jdbc.sql("""
                select o.*, c.company_name, c.code as customer_code, pm.model as pm_model, pm.type as pm_type, pm.description as pm_desc
                from offer o join account c on c.id = o.customer_id left join power_model pm on pm.id = o.power_model_id where o.id = :id""")
                .param("id", id).query().singleRow();
        String T = "Testata";
        put(out, T, "Data offerta", s(o.get("offer_date")));
        put(out, T, "Cliente", o.get("company_name") + " (" + o.get("customer_code") + ")");
        put(out, T, "Titolo progetto", s(o.get("project_title")));
        put(out, T, "Rif.", s(o.get("contact_ref")));
        put(out, T, "Sconto %", num(o, "discount_pct"));
        put(out, T, "Stato", s(o.get("status")));
        put(out, T, "Totale licenze", money(o.get("total_license")));
        put(out, T, "Totale manutenzione annua", money(o.get("total_maintenance")));
        put(out, T, "Manutenzione su netto scontato", yn(o.get("maint_on_net")));
        put(out, T, "Modello offerta Light", yn(o.get("opt_final_summary")));
        put(out, T, "Dipartimentali", yn(o.get("opt_departmental")));
        put(out, T, "Servizi accessori", yn(o.get("opt_extra_services")));
        put(out, T, "Manutenzione in aggiunta all'attuale", yn(o.get("opt_maintenance_additional")));
        put(out, T, "Servizi SMITECH", yn(o.get("opt_smitech_services")));
        put(out, T, "Gestione servizi", s(o.get("services_mode")));
        put(out, T, "Servizi espressi in gg", yn(o.get("opt_services_days")));
        put(out, T, "Dettaglio importi attività", yn(o.get("opt_activity_detail")));
        put(out, T, "Dettaglio ore per singola attività", yn(o.get("opt_hours_detail")));
        put(out, T, "Sconto servizi %", num(o, "activity_discount_pct"));
        put(out, T, "Distanza (km)", num(o, "distance_km"));
        put(out, T, "Sede più vicina", s(o.get("nearest_sede")));

        // Dipartimentali (e SMITECH): una voce per applicazione
        for (Map<String, Object> l : jdbc.sql("""
                select l.id, a.name, l.mode_code, l.license_amount, l.maintenance_amount from offer_line l
                join application a on a.id = l.application_id where l.offer_id = :o order by l.sort_order, l.id""").param("o", id).query().listOfRows()) {
            long lid = ((Number) l.get("id")).longValue();
            String sec = "Applicazione " + l.get("name");
            put(out, sec, "Modalità", s(l.get("mode_code")));
            List<String> drv = jdbc.sql("select d.label || ': ' || x.qty from offer_line_driver x join app_driver d on d.id = x.driver_id where x.line_id = :l order by d.sort_order, d.id")
                    .param("l", lid).query(String.class).list();
            put(out, sec, "Utenti", String.join(" · ", drv));
            List<String> mods = jdbc.sql("select m.code || case when x.qty > 1 then ' × ' || x.qty else '' end from offer_line_module x join module m on m.id = x.module_id where x.line_id = :l order by m.sort_order, m.id")
                    .param("l", lid).query(String.class).list();
            put(out, sec, "Moduli", String.join(", ", mods));
            put(out, sec, "Licenze", money(l.get("license_amount")));
            put(out, sec, "Manutenzione annua", money(l.get("maintenance_amount")));
        }

        // Galileo
        List<Map<String, Object>> g = jdbc.sql("""
                select a.name, gp.label, og.release, og.users, og.add_users, og.list_total, og.maintenance, og.profile_id from offer_galileo og
                join galileo_profile gp on gp.id = og.profile_id join application a on a.id = gp.application_id where og.offer_id = :o""").param("o", id).query().listOfRows();
        if (!g.isEmpty()) {
            Map<String, Object> r = g.get(0);
            String G = "Galileo";
            put(out, G, "Tipologia", s(r.get("name")));
            put(out, G, "Configurazione", s(r.get("label")));
            put(out, G, "Release", s(r.get("release")));
            put(out, G, "Utenti", s(r.get("users")));
            put(out, G, "Utenti aggiuntivi", s(r.get("add_users")));
            put(out, G, "Moduli selezionati", String.join(", ", jdbc.sql("select m.code || ' (' || x.state || ')' from offer_galileo_module x join module m on m.id = x.module_id where x.offer_id = :o order by m.code")
                    .param("o", id).query(String.class).list()));
            put(out, G, "Listino di vendita", money(r.get("list_total")));
            put(out, G, "Manutenzione annua", money(r.get("maintenance")));
        }

        // Power System
        if (Boolean.TRUE.equals(o.get("opt_power_system"))) {
            String P = "Power System";
            put(out, P, "Modello", o.get("pm_model") == null ? "" : o.get("pm_model") + " " + s(o.get("pm_type")) + (o.get("pm_desc") == null || o.get("pm_desc").toString().isBlank() ? "" : " – " + o.get("pm_desc")));
            put(out, P, "RAM (GB)", s(o.get("power_ram_gb")));
            put(out, P, "Disco", s(o.get("power_disk")));
            put(out, P, "Numero dischi", s(o.get("power_disk_count")));
            put(out, P, "Utenti IBM i", Boolean.TRUE.equals(o.get("power_ibmi_unlimited")) ? "ILLIMITATI" : s(o.get("power_ibmi_users")));
            put(out, P, "LTO8", yn(o.get("power_lto8")));
            put(out, P, "Ethernet 10G", yn(o.get("power_eth10")));
            put(out, P, "Cavo", s(o.get("power_cable")));
            put(out, P, "Transfer / Conversion", s(o.get("power_transfer")));
            put(out, P, "Edizione", s(o.get("power_edition")));
            put(out, P, "Sistema operativo", s(o.get("power_os")));
            for (Map<String, Object> m : jdbc.sql("select months, kind, list_price, net_price from offer_power_maint where offer_id = :o order by months, kind desc").param("o", id).query().listOfRows()) {
                String k = "Manutenzione " + m.get("months") + " mesi – " + ("LTO8".equals(m.get("kind")) ? "LTO8" : "Power");
                put(out, P, k + " (listino)", money(m.get("list_price")));
                put(out, P, k + " (netto scontato)", money(m.get("net_price")));
            }
        }

        // Servizi: attività sistemistiche
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Map<String, Object> a : jdbc.sql("""
                select coalesce(ap.name, '') as app, coalesce(x.rfq_group, '') as grp, x.description, x.profile_name, x.hours, x.rate from offer_activity x
                left join application ap on ap.id = x.application_id where x.offer_id = :o order by x.sort_order, x.id""").param("o", id).query().listOfRows()) {
            String base = (a.get("app").toString().isBlank() ? "" : a.get("app") + " › ") + (a.get("grp").toString().isBlank() ? "" : a.get("grp") + " › ") + a.get("description");
            int n = seen.merge(base, 1, Integer::sum);
            put(out, "Servizi", n > 1 ? base + " (" + n + ")" : base, s(a.get("hours")) + " ore · " + s(a.get("profile_name")) + " · " + money(a.get("rate")) + "/h");
        }

        // Fatturazione e pagamento
        String F = "Fatturazione e pagamento";
        put(out, F, "Licenze – fatturazione", s(o.get("bill_lic")) + (o.get("bill_lic_date") == null ? "" : " " + s(o.get("bill_lic_date"))));
        put(out, F, "Licenze – pagamento", s(o.get("pay_lic")) + (o.get("pay_lic_other") == null ? "" : " " + s(o.get("pay_lic_other"))));
        put(out, F, "Canoni – fatturazione", s(o.get("bill_fee")) + (o.get("bill_fee_date") == null ? "" : " " + s(o.get("bill_fee_date"))));
        put(out, F, "Canoni – pagamento", s(o.get("pay_fee")) + (o.get("pay_fee_other") == null ? "" : " " + s(o.get("pay_fee_other"))));
        put(out, F, "Attività – fatturazione", s(o.get("bill_act")));
        put(out, F, "Attività – pagamento", s(o.get("pay_act")) + (o.get("pay_act_other") == null ? "" : " " + s(o.get("pay_act_other"))));
        return out;
    }

    /** differenze tra due fotografie (campi aggiunti, tolti o cambiati), nell'ordine delle sezioni */
    public static List<Change> diff(Map<String, Map<String, String>> before, Map<String, Map<String, String>> after) {
        List<Change> out = new ArrayList<>();
        java.util.Set<String> sections = new java.util.LinkedHashSet<>(after.keySet());
        sections.addAll(before.keySet());
        for (String sec : sections) {
            Map<String, String> b = before.getOrDefault(sec, Map.of()), a = after.getOrDefault(sec, Map.of());
            java.util.Set<String> fields = new java.util.LinkedHashSet<>(a.keySet());
            fields.addAll(b.keySet());
            for (String f : fields) {
                String vb = b.get(f), va = a.get(f);
                if (!Objects.equals(vb, va)) {
                    out.add(new Change(sec, f, vb, va));
                }
            }
        }
        return out;
    }
}
