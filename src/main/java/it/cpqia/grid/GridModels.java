package it.cpqia.grid;

import java.util.List;
import java.util.Map;

/** Strutture JSON scambiate con il componente griglia (grid.js). */
public final class GridModels {

    private GridModels() {
    }

    public record Opt(String value, String label) {
    }

    /**
     * type: text | number | money | percent | int | date | datetime | bool | enum
     * agg: funzione di totalizzazione predefinita (sum | avg | min | max | count) o null
     */
    public record Col(String key, String label, String type, int width, boolean editable, boolean required, Integer maxLen,
                      Double min, Double max, String agg, List<Opt> options, boolean hidden, boolean creatable, String upload, boolean service) {
    }

    /** azione di riga: link (href con segnaposto {id} e {campo}) oppure chiamata al server (server = true) */
    public record Action(String name, String label, String icon, String href, boolean server, String confirm, String style, boolean menu) {
    }

    /** funzioni della griglia: se non configurabile, tutte le altre sono disattivate */
    public record Behavior(boolean configurable, boolean moveColumns, boolean sortColumns, boolean pivot, boolean totals, boolean subtotals, boolean inlineEdit) {
        public static final Behavior ALL = new Behavior(true, true, true, true, true, true, true);

        /** «Modifica in griglia» e' indipendente da «Configurabile» */
        public static Behavior of(boolean configurable, boolean move, boolean sort, boolean pivot, boolean totals, boolean subtotals, boolean inlineEdit) {
            return configurable ? new Behavior(true, move, sort, pivot, totals, subtotals, inlineEdit) : new Behavior(false, false, false, false, false, false, inlineEdit);
        }
    }

    public record Meta(String key, String title, List<Col> columns, List<Action> actions, boolean canCreate,
                       String newHref, String newLabel, boolean canDelete, Behavior behavior, boolean bulkDelete, boolean canExport) {
        public Meta withBehavior(Behavior b) {
            return new Meta(key, title, columns, actions, canCreate, newHref, newLabel, canDelete, b, bulkDelete, canExport);
        }

        /** esportazione dei dati (Excel, CSV, Google Sheets) consentita all'utente su questa griglia */
        public Meta withExport(boolean e) {
            return new Meta(key, title, columns, actions, canCreate, newHref, newLabel, canDelete, behavior, bulkDelete, e);
        }
    }

    public record Data(Meta meta, List<Map<String, Object>> rows) {
    }

    public record IdsRequest(List<String> ids) {
    }

    public record UpdateRequest(String field, Object value) {
    }

    public record CreateRequest(Map<String, Object> values) {
    }

    public record LayoutRequest(String name, String config, Boolean makeDefault) {
    }

    public record Layout(Long id, String name, String config, boolean isDefault) {
    }
}
