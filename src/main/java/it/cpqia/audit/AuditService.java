package it.cpqia.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.cpqia.domain.AppUser;
import it.cpqia.trash.TrashKind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Storico delle azioni (tabella audit_log, alimentata dai trigger del database). */
@Service
public class AuditService {

    /** tabelle con journaling (vedi V61__audit_log.sql) */
    public static final Set<String> TABLES = Set.of("account", "address_type", "agent", "app_driver", "app_mode", "app_user", "application", "company_type",
            "comuni", "extra_group", "extra_option", "extra_service", "ibmi_product", "module", "module_rule", "offer", "power_model", "price_group",
            "pricing_tier", "professional_profile", "sede", "travel_rate", "agent_sync_config", "ibmi_sync_config", "account_profile_rate", "module_price",
            "price_group_base", "pricing_tier_price", "grid_behavior");

    public record Change(String field, String oldValue, String newValue) {
    }

    public record Entry(long id, LocalDateTime at, String action, String actionLabel, String userName, String label, List<Change> changes) {
    }

    public record History(String table, String tableTitle, String recordId, String label, List<Entry> entries) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** lo storico e' consultabile solo dagli amministratori */
    public static boolean allowed(AppUser u, String table) {
        return u != null && u.administrator() && TABLES.contains(table);
    }

    public boolean canSee(AppUser u, String table, String id) {
        return allowed(u, table);
    }

    /** tabella e id dalla riga del Cestino («KIND:id») */
    public Optional<String[]> fromTrashRef(String ref) {
        try {
            String[] p = ref.split(":");
            return Optional.of(new String[]{TrashKind.valueOf(p[0]).table(), p[1]});
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String tableTitle(String table) {
        for (TrashKind k : TrashKind.values()) {
            if (k.table().equals(table)) {
                return k.title();
            }
        }
        return switch (table) {
            case "agent" -> "Agente";
            case "comuni" -> "Comune";
            case "company_type" -> "Tipo azienda";
            case "address_type" -> "Tipo indirizzo";
            case "module_price" -> "Prezzo modulo";
            default -> table;
        };
    }

    private static String actionLabel(String a) {
        return switch (a) {
            case "INSERT" -> "Inserimento";
            case "UPDATE" -> "Modifica";
            case "DELETE" -> "Eliminazione (Cestino)";
            case "RESTORE" -> "Ripristino";
            case "PURGE" -> "Eliminazione definitiva";
            default -> a;
        };
    }

    private static String fieldLabel(String f) {
        String s = f.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String show(Object v) {
        if (v == null) {
            return "—";
        }
        String s = v.toString();
        return s.isEmpty() ? "(vuoto)" : s;
    }

    @SuppressWarnings("unchecked")
    public History history(String table, String id) {
        List<Entry> entries = new ArrayList<>();
        String[] label = {null};
        jdbc.sql("select id, at, action, user_name, record_label, changes::text as changes from audit_log where table_name = :t and record_id = :r order by at desc, id desc limit 500")
                .param("t", table).param("r", id).query((rs, n) -> {
                    List<Change> ch = new ArrayList<>();
                    String action = rs.getString("action");
                    String raw = rs.getString("changes");
                    if (raw != null) {
                        try {
                            Map<String, Object> m = mapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
                            });
                            for (Map.Entry<String, Object> e : m.entrySet()) {
                                if ("UPDATE".equals(action) && e.getValue() instanceof Map<?, ?> d) {
                                    ch.add(new Change(fieldLabel(e.getKey()), show(d.get("old")), show(d.get("new"))));
                                } else if (e.getValue() != null) {
                                    ch.add(new Change(fieldLabel(e.getKey()), null, show(e.getValue())));
                                }
                            }
                        } catch (Exception ex) {
                            // dettaglio non leggibile: resta l'azione
                        }
                    }
                    if (label[0] == null && rs.getString("record_label") != null) {
                        label[0] = rs.getString("record_label");
                    }
                    entries.add(new Entry(rs.getLong("id"), rs.getTimestamp("at").toLocalDateTime(), action, actionLabel(action), rs.getString("user_name"),
                            rs.getString("record_label"), ch));
                    return null;
                }).list();
        return new History(table, tableTitle(table), id, label[0], entries);
    }
}
