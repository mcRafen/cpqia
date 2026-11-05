package it.cpqia.rates;

/** Tabelle di tariffe gestite in Amministrazione > Tabelle tariffe (stessa struttura: descrizione + importo). */
public enum RateTable {

    PROFILES("profiles", "professional_profile", "Profili professionali", "Tariffa oraria (€/h)",
            "Profilo professionale"),
    TRAVEL("travel", "travel_rate", "Trasferte Italia", "Prezzo (€)", "Voce di trasferta");

    private final String path;
    private final String sqlTable;
    private final String title;
    private final String amountLabel;
    private final String nameLabel;

    RateTable(String path, String sqlTable, String title, String amountLabel, String nameLabel) {
        this.path = path;
        this.sqlTable = sqlTable;
        this.title = title;
        this.amountLabel = amountLabel;
        this.nameLabel = nameLabel;
    }

    public String path() {
        return path;
    }

    /** nome SQL: viene da questa enum, mai dall'input dell'utente */
    public String sqlTable() {
        return sqlTable;
    }

    public String title() {
        return title;
    }

    public String amountLabel() {
        return amountLabel;
    }

    public String nameLabel() {
        return nameLabel;
    }

    public it.cpqia.trash.TrashKind trashKind() {
        return this == PROFILES ? it.cpqia.trash.TrashKind.PROFILE : it.cpqia.trash.TrashKind.TRAVEL;
    }

    public static RateTable byPath(String path) {
        for (RateTable t : values()) {
            if (t.path.equals(path)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tabella inesistente: " + path);
    }
}
