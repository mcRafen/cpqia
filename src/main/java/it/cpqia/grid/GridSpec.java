package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import it.cpqia.grid.GridModels.Action;
import it.cpqia.grid.GridModels.Col;
import it.cpqia.grid.GridModels.Meta;
import it.cpqia.grid.GridModels.Opt;
import it.cpqia.trash.TrashKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Definizione lato server di una griglia: query, colonne, permessi, modifica, creazione, azioni. */
public final class GridSpec {

    /** frammento SQL (con parametri) che limita le righe visibili all'utente */
    public record Scope(String sql, Map<String, Object> params) {
        public static Scope none() {
            return new Scope("", Map.of());
        }
    }

    /** controllo/azione prima o dopo la modifica di una cella */
    @FunctionalInterface
    public interface Hook {
        /** @return messaggio di errore oppure null se la modifica e' consentita */
        String check(AppUser user, Map<String, Object> row, ColDef col, Object newValue);
    }

    @FunctionalInterface
    public interface AfterHook {
        void run(AppUser user, long id, ColDef col, Object newValue);
    }

    @FunctionalInterface
    public interface ServerAction {
        String run(AppUser user, String id);
    }

    /** colonna lato server (SQL di selezione, colonna da aggiornare, validazioni) */
    public static final class ColDef {
        final String key;
        final String label;
        String type = "text";
        String sql;
        int width = 140;
        String updateColumn;
        boolean required;
        Integer maxLen;
        Double min;
        Double max;
        String agg;
        List<Opt> options;
        java.util.function.Supplier<List<Opt>> optionsSupplier;      // elenco letto da una tabella di riferimento
        boolean hidden;
        boolean creatable;
        boolean createOnly;                      // valorizzabile solo in creazione
        boolean service;                         // campo di servizio (es. ordine): non compare nelle schede e parte nascosto in griglia
        String upload;                           // indirizzo (con {id}) per caricare un file collegato alla riga
        boolean upper;
        boolean noSpaces;
        Predicate<AppUser> editWho;

        private ColDef(String key, String label, String sql) {
            this.key = key;
            this.label = label;
            this.sql = sql;
        }

        public static ColDef of(String key, String label, String sql) {
            return new ColDef(key, label, sql);
        }

        public ColDef type(String t) {
            this.type = t;
            return this;
        }

        public ColDef width(int w) {
            this.width = w;
            return this;
        }

        /** rende la colonna modificabile in griglia: colonna SQL da aggiornare */
        public ColDef edit(String column) {
            this.updateColumn = column;
            return this;
        }

        public ColDef editOnlyAdmin() {
            this.editWho = AppUser::administrator;
            return this;
        }

        public ColDef required() {
            this.required = true;
            return this;
        }

        public ColDef maxLen(int n) {
            this.maxLen = n;
            return this;
        }

        public ColDef range(Double min, Double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public ColDef agg(String a) {
            this.agg = a;
            return this;
        }

        public ColDef options(String... valueLabelPairs) {
            List<Opt> o = new ArrayList<>();
            for (int i = 0; i + 1 < valueLabelPairs.length; i += 2) {
                o.add(new Opt(valueLabelPairs[i], valueLabelPairs[i + 1]));
            }
            this.options = o;
            this.type = "enum";
            return this;
        }

        /** elenco a tendina letto da una tabella di riferimento (sempre aggiornato) */
        public ColDef optionsFrom(java.util.function.Supplier<List<Opt>> supplier) {
            this.optionsSupplier = supplier;
            this.type = "enum";
            return this;
        }

        List<Opt> opts() {
            return optionsSupplier != null ? optionsSupplier.get() : options;
        }

        /** colonna valorizzabile solo alla creazione del record (non modificabile in seguito) */
        public ColDef createOnly(String column) {
            this.updateColumn = column;
            this.createOnly = true;
            this.creatable = true;
            return this;
        }

        /** scelta multipla: il valore e' un elenco separato da virgole nell'ordine delle opzioni (value, label, value, label...) */
        public ColDef multiOptions(String... valueLabelPairs) {
            options(valueLabelPairs);
            this.type = "multi";
            return this;
        }

        /** numero intero limitato a un elenco di valori (menu a tendina) */
        public ColDef intOptions(int... values) {
            List<Opt> o = new ArrayList<>();
            for (int v : values) {
                o.add(new Opt(String.valueOf(v), String.valueOf(v)));
            }
            this.options = o;
            this.type = "int";
            return this;
        }

        /** colonna con un file collegato: il caricamento avviene nella scheda di modifica (POST multipart su questo indirizzo) */
        public ColDef upload(String path) {
            this.upload = path;
            return this;
        }

        /** campo di servizio: nascosto in griglia (si puo' mostrare dal menu Colonne) e assente dalle schede di modifica / inserimento */
        public ColDef service() {
            this.service = true;
            this.hidden = true;
            return this;
        }

        public ColDef hidden() {
            this.hidden = true;
            return this;
        }

        public ColDef creatable() {
            this.creatable = true;
            return this;
        }

        public ColDef upper() {
            this.upper = true;
            return this;
        }

        public ColDef noSpaces() {
            this.noSpaces = true;
            return this;
        }

        public String key() {
            return key;
        }

        public String updateColumn() {
            return updateColumn;
        }

        Col toCol(boolean editable) {
            return new Col(key, label, type, width, editable, required, maxLen, min, max, agg, opts(), hidden, creatable, upload, service);
        }
    }

    final String key;
    final String title;
    final String idExpr;
    final String from;
    final String baseWhere;
    final String table;
    final List<ColDef> cols = new ArrayList<>();
    final List<Object[]> actionDefs = new ArrayList<>();      // {Action, Predicate<AppUser>}
    final Map<String, ServerAction> serverActions = new LinkedHashMap<>();
    Predicate<AppUser> canView = u -> true;
    Predicate<AppUser> canEdit = u -> false;
    Predicate<AppUser> canCreate = u -> false;
    Predicate<AppUser> canDelete = u -> false;
    Predicate<AppUser> canBulkDelete = u -> false;         // «Elimina» sulle righe selezionate (eliminazione definitiva, con conferma)
    java.util.function.BiConsumer<AppUser, List<Long>> bulkDeleter;
    Function<AppUser, Scope> scope = u -> Scope.none();
    TrashKind trashKind;
    Hook hook;
    AfterHook after;
    UnaryOperator<Map<String, Object>> rowFixer;
    String newHref;
    String newLabel;
    String touch;                           // frammento SQL aggiunto agli UPDATE (es. updated_at = now())
    String insertSql;                       // opzionale: insert personalizzato (parametri nominati = chiavi dei valori)
    java.util.function.Supplier<List<Map<String, Object>>> customRows;   // sorgente dati non SQL (Cestino)
    Function<AppUser, List<Map<String, Object>>> customRowsFor;

    public GridSpec(String key, String title, String table, String idExpr, String from, String baseWhere) {
        this.key = key;
        this.title = title;
        this.table = table;
        this.idExpr = idExpr;
        this.from = from;
        this.baseWhere = baseWhere;
    }

    public GridSpec col(ColDef c) {
        cols.add(c);
        return this;
    }

    public GridSpec view(Predicate<AppUser> p) {
        this.canView = p;
        return this;
    }

    public GridSpec edit(Predicate<AppUser> p) {
        this.canEdit = p;
        return this;
    }

    public GridSpec create(Predicate<AppUser> p) {
        this.canCreate = p;
        return this;
    }

    public GridSpec delete(Predicate<AppUser> p, TrashKind k) {
        this.canDelete = p;
        this.trashKind = k;
        return this;
    }

    public GridSpec scope(Function<AppUser, Scope> f) {
        this.scope = f;
        return this;
    }

    public GridSpec hook(Hook h) {
        this.hook = h;
        return this;
    }

    public GridSpec after(AfterHook h) {
        this.after = h;
        return this;
    }

    /** eliminazione delle righe selezionate (la griglia mostra «Elimina» con conferma); l'handler cancella gli id indicati */
    public GridSpec bulkDelete(Predicate<AppUser> who, java.util.function.BiConsumer<AppUser, List<Long>> handler) {
        this.canBulkDelete = who;
        this.bulkDeleter = handler;
        return this;
    }

    public GridSpec newLink(String href, String label) {
        this.newHref = href;
        this.newLabel = label;
        return this;
    }

    /** azione di riga: link o chiamata al server; visibile solo se il predicato e' vero */
    public GridSpec link(String name, String label, String icon, String href, Predicate<AppUser> who) {
        actionDefs.add(new Object[]{new Action(name, label, icon, href, false, null, null, false), who});
        return this;
    }

    /** azione di riga raccolta nel menu a tre puntini (insieme a «Sposta nel Cestino», se prevista) */
    public GridSpec menuLink(String name, String label, String icon, String href, Predicate<AppUser> who) {
        actionDefs.add(new Object[]{new Action(name, label, icon, href, false, null, null, true), who});
        return this;
    }

    public GridSpec server(String name, String label, String icon, String confirm, String style, Predicate<AppUser> who, ServerAction run) {
        actionDefs.add(new Object[]{new Action(name, label, icon, null, true, confirm, style, false), who});
        serverActions.put(name, run);
        return this;
    }

    /** azione di riga eseguita sul server e raccolta nel menu a tre puntini */
    public GridSpec menuServer(String name, String label, String icon, String confirm, Predicate<AppUser> who, ServerAction run) {
        actionDefs.add(new Object[]{new Action(name, label, icon, null, true, confirm, null, true), who});
        serverActions.put(name, run);
        return this;
    }

    public GridSpec touch(String sql) {
        this.touch = sql;
        return this;
    }

    public GridSpec rowsFrom(Function<AppUser, List<Map<String, Object>>> f) {
        this.customRowsFor = f;
        return this;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public ColDef colDef(String k) {
        for (ColDef c : cols) {
            if (c.key.equals(k)) {
                return c;
            }
        }
        return null;
    }

    /** metadati per l'utente corrente (colonne modificabili, azioni consentite) */
    public Meta meta(AppUser u) {
        boolean ed = canEdit.test(u);
        List<Col> cs = new ArrayList<>();
        for (ColDef c : cols) {
            cs.add(c.toCol(ed && c.updateColumn != null && !c.createOnly && (c.editWho == null || c.editWho.test(u))));
        }
        List<Action> as = new ArrayList<>();
        for (Object[] a : actionDefs) {
            @SuppressWarnings("unchecked")
            Predicate<AppUser> who = (Predicate<AppUser>) a[1];
            if (who.test(u)) {
                as.add((Action) a[0]);
            }
        }
        if (table != null && it.cpqia.audit.AuditService.allowed(u, table)) {      // «Storico modifiche»: sempre nel menu a tre puntini
            as.add(new Action("history", "Storico modifiche", "restore", "audit/" + table + "/{id}", false, null, null, true));
        }
        boolean del = trashKind != null && canDelete.test(u);
        return new Meta(key, title, cs, as, canCreate.test(u), newHref, newLabel, del, GridModels.Behavior.ALL, bulkDeleter != null && canBulkDelete.test(u), false);
    }
}
