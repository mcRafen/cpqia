package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import it.cpqia.grid.GridSpec.ColDef;
import it.cpqia.grid.GridSpec.Scope;
import it.cpqia.trash.TrashKind;
import it.cpqia.trash.TrashService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Elenco delle griglie dell'applicazione: colonne, permessi, regole di modifica. */
@Component
public class GridRegistry {

    private static final Pattern CAP = Pattern.compile("\\d{5}");
    private static final Pattern PROV = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern MAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, GridSpec> specs = new LinkedHashMap<>();
    private final JdbcClient jdbc;
    private final TrashService trash;
    private final it.cpqia.repo.CatalogRepository catalog;
    private final it.cpqia.lookup.LookupRepository lookups;

    public GridRegistry(JdbcClient jdbc, TrashService trash, it.cpqia.repo.CatalogRepository catalog, it.cpqia.lookup.LookupRepository lookups) {
        this.catalog = catalog;
        this.lookups = lookups;
        this.jdbc = jdbc;
        this.trash = trash;
        offers();
        customers();
        users();
        applications();
        rates("rates-profiles", "Profili professionali", "professional_profile", TrashKind.PROFILE);
        rates("rates-travel", "Trasferte Italia", "travel_rate", TrashKind.TRAVEL);
        sedi();
        geodata();
        agents();
        lookupTables();
        powerModels();
        ibmiProducts();
        trashGrid();
    }

    /** tutte le griglie registrate (per la pagina Comportamento griglie) */
    public java.util.Collection<GridSpec> all() {
        return specs.values();
    }

    public GridSpec get(String key) {
        return specs.get(key);
    }

    private void add(GridSpec s) {
        specs.put(s.key(), s);
    }

    private static boolean admin(AppUser u) {
        return u.administrator();
    }

    private static boolean catalog(AppUser u) {
        return u.administrator() || u.canManageCatalog();
    }

    private static ColDef c(String key, String label, String sql) {
        return ColDef.of(key, label, sql);
    }

    // ------------------------------------------------------------------ offerte
    private void offers() {
        GridSpec s = new GridSpec("offers", "Offerte", "offer", "o.id",
                "offer o join account cu on cu.id = o.customer_id join app_user ou on ou.id = o.created_by", "o.deleted_at is null")
                .col(c("number", "Numero", "o.number").width(130))
                .col(c("offerDate", "Data", "o.offer_date").type("date").width(110))
                .col(c("customer", "Cliente", "cu.company_name").width(220))
                .col(c("customerCode", "Cod. cliente", "cu.code").width(100))
                .col(c("projectTitle", "Progetto", "coalesce(o.project_title, '')").edit("project_title").maxLen(250).width(260))
                .col(c("contactRef", "Rif.", "coalesce(o.contact_ref, '')").edit("contact_ref").maxLen(150).width(150))
                .col(c("owner", "Creata da", "coalesce(ou.full_name, ou.email)").width(150))
                .col(c("status", "Stato", "o.status").options("BOZZA", "Bozza", "EMESSA", "Emessa").edit("status").width(100))
                .col(c("totalLicense", "Licenze", "o.total_license").type("money").agg("sum").width(120))
                .col(c("totalMaintenance", "Manutenzione", "o.total_maintenance").type("money").agg("sum").width(120))
                .col(c("discountPct", "Sconto %", "o.discount_pct").type("percent").edit("discount_pct").range(0d, 100d).agg("avg").width(90))
                .col(c("margin", "Margine comm.", "round(o.total_license * (1 - o.discount_pct / 100), 2) + case when o.maint_on_net then round(o.total_maintenance * (1 - o.discount_pct / 100), 2) else o.total_maintenance end").type("money").agg("sum").width(130))
                .col(c("updatedAt", "Aggiornata", "o.updated_at").type("datetime").width(140).hidden())
                .scope(u -> (u.administrator() || u.canViewAllOffers()) ? Scope.none()
                        : new Scope("and o.created_by = :scopeUser", Map.of("scopeUser", u.id())))
                .edit(u -> u.administrator() || u.canEditOffers())
                .delete(u -> u.administrator() || u.canDeleteOffers(), TrashKind.OFFER)
                .touch("updated_at = now()")
                .newLink("offers/new", "Nuova offerta")
                .create(u -> u.administrator() || u.canCreateOffers())
                .hook((u, row, col, v) -> {
                    if ("EMESSA".equals(row.get("status")) && !"status".equals(col.key())) {
                        return "Offerta emessa: riportarla a Bozza per modificarla";
                    }
                    return null;
                })
                .menuLink("docx", "Scarica Word", "download", "offers/{id}/docx", u -> u.administrator() || u.canExportDocx())
                .link("edit", "Apri e modifica nel configuratore", "edit", "offers/{id}/edit", u -> u.administrator() || u.canEditOffers());
        add(s);
    }

    // ------------------------------------------------------------------ account
    private void customers() {
        GridSpec s = new GridSpec("customers", "Account", "account", "c.id", "account c", "c.deleted_at is null")
                .col(c("code", "Codice", "c.code").width(100))
                .col(c("type", "Tipo", "c.type").optionsFrom(() -> lookups.companyTypes().stream().map(t -> new GridModels.Opt(t.code(), t.label())).toList()).width(100))
                .col(c("companyName", "Ragione sociale", "c.company_name").edit("company_name").required().maxLen(200).width(260))
                .col(c("address", "Indirizzo", "c.street_type || ' ' || c.street_name || ', ' || c.street_number").width(260))
                .col(c("streetType", "Via", "c.street_type").optionsFrom(() -> lookups.streetTypes().stream().map(v -> new GridModels.Opt(v, v)).toList()).edit("street_type").required().width(80).hidden())
                .col(c("streetName", "Indirizzo (nome)", "c.street_name").edit("street_name").required().maxLen(200).width(200).hidden())
                .col(c("streetNumber", "Civico", "c.street_number").edit("street_number").required().maxLen(20).width(80).hidden())
                .col(c("postalCode", "CAP", "c.postal_code").edit("postal_code").required().maxLen(10).width(80))
                .col(c("city", "Città", "c.city").edit("city").required().maxLen(100).width(150))
                .col(c("province", "Prov.", "coalesce(c.province, '')").edit("province").upper().maxLen(5).width(70))
                .col(c("vatNumber", "Partita IVA", "c.vat_number").width(130))
                .col(c("taxCode", "Codice fiscale", "coalesce(c.tax_code, '')").edit("tax_code").upper().maxLen(20).width(150))
                .col(c("latitude", "Latitudine", "c.latitude").type("coord").width(110).hidden())
                .col(c("longitude", "Longitudine", "c.longitude").type("coord").width(110).hidden())
                .col(c("agentCode", "Cod. agente", "coalesce(c.agent_code, '')").width(100))
                .col(c("agent", "Agente", "coalesce((select a.description from agent a where a.code = c.agent_code), '')").width(180))
                // utente associato a un agente: solo i propri prospect e i clienti con lo stesso codice agente
                .scope(u -> !u.agentRestricted() ? Scope.none()
                        : new Scope("and ((c.type = 'PROSPECT' and c.created_by = :scopeUser) or (c.type = 'CLIENTE' and c.agent_code = :scopeAgent))",
                                Map.of("scopeUser", u.id(), "scopeAgent", u.agentCode().trim())))
                .edit(u -> u.administrator() || u.canManageCustomers())
                .delete(u -> u.administrator() || u.canManageCustomers(), TrashKind.CUSTOMER)
                .newLink("customers/new", "Nuovo account")
                .create(u -> u.administrator() || u.canManageCustomers())
                .hook((u, row, col, v) -> {
                    // account Cliente (anagrafica da IBM Power System): si modificano solo tariffe e diritto di trasferta, dalla scheda completa
                    Object id = row.get("id");
                    if (id != null && "CLIENTE".equals(jdbc.sql("select type from account where id = :id").param("id", Long.parseLong(id.toString()))
                            .query(String.class).optional().orElse(null))) {
                        return "Account di tipo Cliente: l'anagrafica non è modificabile (solo tariffe e diritto di trasferta, dalla scheda completa)";
                    }
                    if ("province".equals(col.key()) && v != null && !PROV.matcher(v.toString()).matches()) {
                        return "Provincia: due lettere (es. VI)";
                    }
                    return null;
                })
                .link("newoffer", "Nuova offerta per questo account", "add", "offers/new?customerId={id}", u -> u.administrator() || u.canCreateOffers())
                .link("edit", "Apri scheda completa", "edit", "customers/{id}/edit", u -> u.administrator() || u.canManageCustomers());
        add(s);
    }

    // ------------------------------------------------------------------ utenti (solo administrator)
    private void users() {
        GridSpec s = new GridSpec("users", "Utenti e permessi", "app_user", "u.id", "app_user u", "u.deleted_at is null")
                .col(c("name", "Utente", "coalesce(u.full_name, u.email)").width(180))
                .col(c("email", "Email", "u.email").edit("email").required().maxLen(255).width(230))
                .col(c("initials", "Iniz.", "u.initials").edit("initials").upper().maxLen(10).width(70))
                .col(c("authProvider", "Accesso", "coalesce(u.auth_provider, '')").width(90))
                .col(c("active", "Abilitato", "u.active").type("bool").edit("active").width(90))
                .col(c("administrator", "Administrator", "u.administrator").type("bool").edit("administrator").width(110))
                .col(c("canCreateOffers", "Crea offerte", "u.can_create_offers").type("bool").edit("can_create_offers").width(100))
                .col(c("canEditOffers", "Modifica offerte", "u.can_edit_offers").type("bool").edit("can_edit_offers").width(110))
                .col(c("canViewAllOffers", "Vede tutte", "u.can_view_all_offers").type("bool").edit("can_view_all_offers").width(100))
                .col(c("canDeleteOffers", "Elimina offerte", "u.can_delete_offers").type("bool").edit("can_delete_offers").width(110))
                .col(c("canExportDocx", "Word", "u.can_export_docx").type("bool").edit("can_export_docx").width(70))
                .col(c("canManageCustomers", "Anagrafiche", "u.can_manage_customers").type("bool").edit("can_manage_customers").width(100))
                .col(c("canManageCatalog", "Catalogo", "u.can_manage_catalog").type("bool").edit("can_manage_catalog").width(90))
                .col(c("canViewPrices", "Prezzi", "u.can_view_prices").type("bool").edit("can_view_prices").width(80))
                .col(c("agentCode", "Cod. agente", "coalesce(u.agent_code, '')").width(100))
                .col(c("agent", "Agente", "coalesce((select a.description from agent a where a.code = u.agent_code), '')").width(180))
                .col(c("lastLoginAt", "Ultimo accesso", "u.last_login_at").type("datetime").width(140))
                .view(GridRegistry::admin).edit(GridRegistry::admin).delete(GridRegistry::admin, TrashKind.USER)
                .newLink("admin/users/new", "Nuovo utente")
                .hook((u, row, col, v) -> {
                    Long target = row.get("id") instanceof Number n ? n.longValue() : null;
                    boolean self = target != null && target.equals(u.id());
                    if (self && ("active".equals(col.key()) || "administrator".equals(col.key())) && Boolean.FALSE.equals(v)) {
                        return "Non puoi togliere a te stesso l'abilitazione o il ruolo di administrator";
                    }
                    if ("administrator".equals(col.key()) && Boolean.FALSE.equals(v)) {
                        long others = jdbc.sql("select count(*) from app_user where administrator and active and deleted_at is null and id <> :id")
                                .param("id", target).query(Long.class).single();
                        if (others == 0) {
                            return "Deve rimanere almeno un administrator attivo";
                        }
                    }
                    if ("email".equals(col.key()) && v != null && !MAIL.matcher(v.toString()).matches()) {
                        return "Email non valida";
                    }
                    return null;
                })
                .link("edit", "Apri scheda utente", "edit", "admin/users/{id}", GridRegistry::admin);
        add(s);
    }

    // ------------------------------------------------------------------ applicazioni
    private void applications() {
        GridSpec s = new GridSpec("applications", "Applicazioni", "application", "a.id", "application a", "a.deleted_at is null")
                .col(c("code", "Codice", "a.code").edit("code").required().maxLen(20).upper().noSpaces().width(100))
                .col(c("name", "Applicazione", "a.name").edit("name").required().maxLen(150).width(240))
                .col(c("family", "Famiglia", "coalesce(a.family, '')").edit("family").maxLen(60).width(140))
                .col(c("modules", "Moduli", "(select count(*) from module m where m.application_id = a.id and m.deleted_at is null)").type("int").agg("sum").width(90))
                .col(c("maintenancePct", "Manut. %", "a.maintenance_pct").type("percent").edit("maintenance_pct").range(0d, 100d).width(90))
                .col(c("additionalReductionPct", "Riduz. moduli agg. %", "a.additional_reduction_pct").type("percent").edit("additional_reduction_pct").range(0d, 100d).width(130))
                .col(c("needsReview", "Da verificare", "a.needs_review").type("bool").width(100))
                .col(c("active", "Attiva", "a.active").type("bool").edit("active").width(80))
                .col(c("sortOrder", "Ordine", "a.sort_order").type("int").edit("sort_order").width(80).service())
                .view(GridRegistry::catalog).edit(GridRegistry::catalog).delete(GridRegistry::catalog, TrashKind.APPLICATION)
                .after((u, id, col, v) -> catalog.evict())
                .newLink("admin/applications/new", "Nuova applicazione")
                .link("open", "Apri applicazione e moduli", "chevron", "admin/applications/{id}", GridRegistry::catalog);
        add(s);
    }

    // ------------------------------------------------------------------ tabelle tariffe
    private void rates(String key, String title, String table, TrashKind kind) {
        GridSpec s = new GridSpec(key, title, table, "id", table, "deleted_at is null")
                .col(c("name", "Descrizione", "name").edit("name").required().maxLen(150).creatable().width(300))
                .col(c("amount", "Importo €", "amount").type("money").edit("amount").range(0d, 10_000_000d).required().creatable().width(130))
                .col(c("sortOrder", "Ordine", "sort_order").type("int").edit("sort_order").creatable().width(90).service())
                .col(c("active", "Attiva", "active").type("bool").edit("active").creatable().width(90))
                .view(GridRegistry::catalog).edit(GridRegistry::catalog).create(GridRegistry::catalog).delete(GridRegistry::catalog, kind);
        add(s);
    }

    // ------------------------------------------------------------------ sedi Sanmarco (solo administrator)
    private void sedi() {
        GridSpec s = new GridSpec("sedi", "Sedi Sanmarco", "sede", "id", "sede", "deleted_at is null")
                .col(c("sortOrder", "Progressivo", "sort_order").type("int").edit("sort_order").range(0d, 100000d).creatable().width(110))
                .col(c("name", "Paese", "name").edit("name").required().maxLen(100).creatable().width(200))
                .col(c("street", "Indirizzo", "street").edit("street").required().maxLen(150).creatable().width(240))
                .col(c("streetNumber", "Nr. civico", "street_number").edit("street_number").required().maxLen(20).creatable().width(100))
                .col(c("postalCode", "CAP", "postal_code").edit("postal_code").required().maxLen(10).creatable().width(90))
                .col(c("city", "Comune", "coalesce(city, '')").edit("city").maxLen(100).creatable().width(180))
                .col(c("province", "Provincia", "province").edit("province").required().upper().noSpaces().maxLen(5).creatable().width(90))
                .col(c("latitude", "Latitudine", "latitude").type("coord").width(110))
                .col(c("longitude", "Longitudine", "longitude").type("coord").width(110))
                .view(GridRegistry::admin).edit(GridRegistry::admin).create(GridRegistry::admin).delete(GridRegistry::admin, TrashKind.SEDE)
                // latitudine e longitudine sono in sola lettura: le calcola l'agente Geo a ogni inserimento / modifica dell'indirizzo
                .hook((u, row, col, v) -> {
                    if ("postalCode".equals(col.key()) && v != null && !CAP.matcher(v.toString()).matches()) {
                        return "CAP: cinque cifre";
                    }
                    if ("province".equals(col.key()) && v != null && !PROV.matcher(v.toString()).matches()) {
                        return "Provincia: due lettere (es. VI)";
                    }
                    return null;
                });
        add(s);
    }

    // ------------------------------------------------------------------ comuni con provincia e regione (sola lettura, administrator)
    private void geodata() {
        add(new GridSpec("comuni", "Comuni", "comuni", "c.id", "comuni c", "true")
                .col(c("nome", "Comune", "c.nome").width(240))
                .col(c("sigla", "Prov.", "c.sigla").width(70))
                .col(c("provincia", "Provincia", "c.provincia").width(200))
                .col(c("regione", "Regione", "coalesce(c.regione, '')").width(160))
                .col(c("cap", "CAP", "coalesce(c.cap, '')").width(80))
                .col(c("capList", "Tutti i CAP", "coalesce(c.cap_list, '')").width(200).hidden())
                .col(c("istat", "Codice ISTAT", "coalesce(c.istat_code, '')").width(110).hidden())
                .view(GridRegistry::admin));
    }

    // ------------------------------------------------------------------ agenti (alimentati dalla Sync Agenti, sola lettura)
    private void agents() {
        add(new GridSpec("agents", "Agenti", "agent", "id", "agent", "true")
                .col(c("code", "Codice", "code").width(140))
                .col(c("description", "Descrizione", "description").width(320))
                .view(GridRegistry::admin)
                .bulkDelete(GridRegistry::admin, (u, ids) -> {
                    for (Long id : ids) {
                        jdbc.sql("delete from agent where id = :id").param("id", id).update();
                    }
                }));
    }

    // ------------------------------------------------------------------ prodotti IBM i (software dei sistemi Power)
    private void ibmiProducts() {
        add(new GridSpec("ibmi-products", "Prodotti IBM i", "ibmi_product", "id", "ibmi_product", "deleted_at is null")
                .col(c("code", "Codice", "code").edit("code").required().maxLen(20).upper().noSpaces().creatable().width(140))
                .col(c("description", "Descrizione", "description").edit("description").required().maxLen(200).creatable().width(420))
                .col(c("active", "Attivo", "active").type("bool").edit("active").creatable().width(80))
                .col(c("sortOrder", "Ordine", "sort_order").type("int").edit("sort_order").creatable().width(80).service())
                .view(GridRegistry::catalog).edit(GridRegistry::catalog).create(GridRegistry::catalog).delete(GridRegistry::catalog, TrashKind.IBMI_PRODUCT));
    }

    // ------------------------------------------------------------------ anagrafica modelli IBM Power System
    private void powerModels() {
        add(new GridSpec("power-models", "Modelli IBM Power System", "power_model", "id", "power_model", "deleted_at is null")
                .col(c("model", "Modello", "model").edit("model").required().maxLen(20).creatable().width(130))
                .col(c("type", "Tipo", "type").edit("type").required().maxLen(20).creatable().width(100))
                .col(c("cores", "Core", "cores").intOptions(1, 4, 8).edit("cores").required().creatable().width(80))
                .col(c("mounting", "Montaggio", "mounting").options("DESK", "Desk", "RACK", "Rack").edit("mounting").required().creatable().width(110))
                .col(c("description", "Descrizione", "coalesce(nullif(description, ''), 'Model ' || model || ' ' || cores || ' core ' || mounting)").edit("description").maxLen(200).creatable().width(260))
                .col(c("processorGroup", "Gruppo processore", "coalesce(processor_group, '')").options("P05", "P05", "P10", "P10").edit("processor_group").required().creatable().width(150))
                .col(c("ramOptions", "RAM supportata", "ram_options").multiOptions("32", "32 GB", "64", "64 GB", "96", "96 GB", "128", "128 GB", "256", "256 GB", "384", "384 GB", "512", "512 GB").edit("ram_options").creatable().width(240))
                .col(c("diskOptions", "Dischi supportati", "disk_options").multiOptions("1.6TB", "1.6 TB", "3.2TB", "3.2 TB", "6.4TB", "6.4 TB").edit("disk_options").creatable().width(200))
                .col(c("version", "Versione", "version").options("POWER 11", "POWER 11", "POWER 10", "POWER 10").edit("version").required().creatable().width(130))
                .col(c("ghz", "GHz", "coalesce(ghz, '')").edit("ghz").maxLen(200).creatable().width(156))
                .col(c("cpw", "CPW", "cpw").type("int").edit("cpw").required().range(0d, 10_000_000d).creatable().width(100))
                .col(c("wordFile", "Documento Word", "coalesce(word_file, '')").upload("admin/power-models/{id}/document").width(240))
                .col(c("active", "Attivo", "active").type("bool").edit("active").creatable().width(80))
                .col(c("sortOrder", "Ordine", "sort_order").type("int").edit("sort_order").creatable().width(80).service())
                .view(GridRegistry::catalog).edit(GridRegistry::catalog).create(GridRegistry::catalog).delete(GridRegistry::catalog, TrashKind.POWER));
    }

    // ------------------------------------------------------------------ tabelle di riferimento dell'account (solo administrator)
    private void lookupTables() {
        add(new GridSpec("company-types", "Tipo Azienda", "company_type", "id", "company_type", "true")
                .col(c("code", "Codice", "code").maxLen(10).upper().noSpaces().required().createOnly("code").width(120))
                .col(c("label", "Descrizione", "label").edit("label").required().maxLen(60).creatable().width(220))
                .col(c("sortOrder", "Ordine", "sort_order").type("int").edit("sort_order").creatable().width(90).service())
                .view(GridRegistry::admin).edit(GridRegistry::admin).create(GridRegistry::admin));
        add(new GridSpec("address-types", "Tipo Indirizzo", "address_type", "id", "address_type", "true")
                .col(c("name", "Tipo indirizzo", "name").edit("name").required().maxLen(30).creatable().width(220))
                .col(c("sortOrder", "Ordine", "sort_order").type("int").edit("sort_order").creatable().width(90).service())
                .col(c("used", "Account che lo usano", "(select count(*) from account a where a.street_type = address_type.name and a.deleted_at is null)").type("int").width(160))
                .view(GridRegistry::admin).edit(GridRegistry::admin).create(GridRegistry::admin));
    }

    // ------------------------------------------------------------------ cestino
    private void trashGrid() {
        GridSpec s = new GridSpec("trash", "Cestino", null, "'0'", "dual", "true")
                .col(c("kindTitle", "Tipo", "''").width(170))
                .col(c("label", "Elemento", "''").width(420))
                .col(c("deletedAt", "Eliminato il", "''").type("datetime").width(150))
                .col(c("deletedByName", "Eliminato da", "''").width(180))
                .rowsFrom(u -> {
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Map<String, Object> r : trash.list(u)) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", r.get("id"));
                        m.put("kindTitle", r.get("kind_title"));
                        m.put("label", r.get("label"));
                        m.put("deletedAt", r.get("deleted_at"));
                        m.put("deletedByName", r.get("deleted_by_name"));
                        out.add(m);
                    }
                    return out;
                })
                .menuLink("history", "Storico modifiche", "restore", "audit/trash/{id}", GridRegistry::admin)
                .server("restore", "Ripristina", "restore", null, null, u -> true, (u, id) -> {
                    String[] p = id.split(":");
                    trash.restore(TrashKind.valueOf(p[0]), Long.parseLong(p[1]), u);
                    return "Elemento ripristinato";
                })
                .server("purge", "Elimina definitivamente", "delete", "Eliminare definitivamente l'elemento? L'operazione non è reversibile.", "danger",
                        GridRegistry::admin, (u, id) -> {
                            String[] p = id.split(":");
                            trash.purge(TrashKind.valueOf(p[0]), Long.parseLong(p[1]), u);
                            return "Elemento eliminato definitivamente";
                        });
        add(s);
    }
}
