package it.cpqia.trash;

/** Tipi di record con cancellazione logica (deleted_at / deleted_by) e relativa descrizione per il Cestino. */
public enum TrashKind {

    OFFER("offer", "Offerta", "number", null),
    CUSTOMER("account", "Account", "code || ' – ' || company_name", null),
    USER("app_user", "Utente", "coalesce(full_name, email)", null),
    APPLICATION("application", "Applicazione", "code || ' – ' || name", null),
    MODULE("module", "Modulo",
            "(select a.code from application a where a.id = module.application_id) || ' / ' || code || ' – ' || left(description, 70)",
            "select count(*) from application a where a.deleted_at is not null and a.id = (select application_id from module where id = :id)"),
    MODE("app_mode", "Modalità di prezzo",
            "(select a.code from application a where a.id = app_mode.application_id) || ' / ' || code || ' – ' || label",
            "select count(*) from application a where a.deleted_at is not null and a.id = (select application_id from app_mode where id = :id)"),
    DRIVER("app_driver", "Grandezza (utenti, macchine…)",
            "(select a.code from application a where a.id = app_driver.application_id) || ' / ' || code || ' – ' || label",
            "select count(*) from application a where a.deleted_at is not null and a.id = (select application_id from app_driver where id = :id)"),
    GROUP("price_group", "Gruppo di prezzo",
            "(select a.code from application a where a.id = price_group.application_id) || ' / ' || code || ' – ' || label",
            "select count(*) from application a where a.deleted_at is not null and a.id = (select application_id from price_group where id = :id)"),
    TIER("pricing_tier", "Fascia utenti",
            "(select a.code from price_group g join application a on a.id = g.application_id where g.id = pricing_tier.group_id) || ' / ' || label",
            "select count(*) from price_group g where g.deleted_at is not null and g.id = (select group_id from pricing_tier where id = :id)"),
    RULE("module_rule", "Vincolo tra moduli",
            "(select a.code || ' / ' || m.code from module m join application a on a.id = m.application_id where m.id = module_rule.module_id) || ': ' || left(message, 70)",
            "select count(*) from module m where m.deleted_at is not null and m.id = (select module_id from module_rule where id = :id)"),
    PROFILE("professional_profile", "Profilo professionale", "name", null),
    TRAVEL("travel_rate", "Trasferta Italia", "name", null),
    SEDE("sede", "Sede Sanmarco", "name", null),
    POWER("power_model", "Modello Power System", "model || ' ' || type || ' – ' || cores || ' core ' || mounting", null),
    IBMI_PRODUCT("ibmi_product", "Prodotto IBM i", "code || ' – ' || description", null);

    private final String table;
    private final String title;
    private final String labelSql;
    private final String deletedParentSql;

    TrashKind(String table, String title, String labelSql, String deletedParentSql) {
        this.table = table;
        this.title = title;
        this.labelSql = labelSql;
        this.deletedParentSql = deletedParentSql;
    }

    /** nome SQL: viene da questa enum, mai dall'input dell'utente */
    public String table() {
        return table;
    }

    public String title() {
        return title;
    }

    public String labelSql() {
        return labelSql;
    }

    /** query che conta i "genitori" eliminati del record (null = nessun genitore): impedisce il ripristino orfano */
    public String deletedParentSql() {
        return deletedParentSql;
    }
}
