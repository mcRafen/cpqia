-- Listino JGalileo: profili di calcolo per mercato (applicazioni della famiglia GALILEO) e scenario
-- (nuova configurazione, consociata, migrazione, migrazione consociata, immobiliare), fasce utenti e moduli per profilo.
ALTER TABLE module ADD COLUMN area VARCHAR(120);                 -- area applicativa (es. FLUSSO ATTIVO)

CREATE TABLE galileo_listino (
    id          INT PRIMARY KEY CHECK (id = 1),
    name        VARCHAR(60)  NOT NULL,
    version     VARCHAR(20)  NOT NULL,
    valid_until DATE         NOT NULL
);

CREATE TABLE galileo_profile (
    id                  BIGSERIAL PRIMARY KEY,
    application_id      BIGINT       NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    kind                VARCHAR(8)   NOT NULL CHECK (kind IN ('NEW', 'CONS', 'MIG', 'MIGCONS', 'IMMOB')),
    label               VARCHAR(100) NOT NULL,
    source_sheet        VARCHAR(100),
    cfg_v9              NUMERIC(14,2),                           -- prezzo della configurazione (release V9 / foglio senza V11)
    cfg_v11             NUMERIC(14,2),                           -- prezzo della configurazione con release V11 (NULL = non prevista)
    module_discount_pct NUMERIC(6,2) NOT NULL DEFAULT 30,        -- sconto sulla somma dei moduli
    new_discount_pct    NUMERIC(6,2) NOT NULL DEFAULT 30,        -- sconto sui moduli nuovi (consociata, migrazione)
    v11_discount        BOOLEAN      NOT NULL DEFAULT TRUE,      -- consociata: sconto moduli anche con V11 (FALSE in COMMERCIALE e HOLDING, come nel foglio)
    cons_divisor        NUMERIC(4,2) NOT NULL DEFAULT 1,         -- consociata = prezzo / 2
    mig_carattere_pct   NUMERIC(6,2),                            -- migrazione da carattere: % del prezzo configurazione
    mig_visuale_pct     NUMERIC(6,2),                            -- migrazione da visuale
    mig_v9_flat         NUMERIC(14,2),                           -- migrazione da V9: importo fisso
    max_users           INT,                                     -- immobiliare: utenti massimi
    aggiunta_supported  BOOLEAN      NOT NULL DEFAULT FALSE,     -- opzione «aggiunta moduli» (senza prezzo di configurazione)
    pro_rata_below_first BOOLEAN     NOT NULL DEFAULT FALSE,     -- MODA: sotto i 6 utenti il prezzo scende in proporzione
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INT          NOT NULL DEFAULT 0,
    UNIQUE (application_id, kind)
);

CREATE TABLE galileo_tier (
    profile_id  BIGINT       NOT NULL REFERENCES galileo_profile (id) ON DELETE CASCADE,
    tier_set    VARCHAR(8)   NOT NULL CHECK (tier_set IN ('MAIN', 'NEWMOD', 'ADDUSR')),   -- configurazione / moduli nuovi / utenti aggiuntivi
    seq         INT          NOT NULL,
    from_users  INT          NOT NULL,
    to_users    INT,                                              -- NULL = senza limite
    pct         NUMERIC(6,2),                                     -- % della base per ogni utente della fascia (NULL = prima fascia, inclusa)
    PRIMARY KEY (profile_id, tier_set, seq)
);

CREATE TABLE galileo_module (
    profile_id  BIGINT       NOT NULL REFERENCES galileo_profile (id) ON DELETE CASCADE,
    module_id   BIGINT       NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    flag        VARCHAR(3)   NOT NULL CHECK (flag IN ('CB', 'FA', 'V11')),   -- CB = configurazione base, FA = funzione aggiuntiva, V11 = attivazione V11
    e_rule      VARCHAR(4)   NOT NULL DEFAULT '',                -- regola di addebito in «moduli in comune/migrati» (D, F, DFA, G1 o vuota = mai)
    g_counted   BOOLEAN      NOT NULL DEFAULT FALSE,             -- addebitato tra i «moduli nuovi»
    price       NUMERIC(14,2),                                   -- NULL = prezzo STD del modulo
    e_price     NUMERIC(14,2),                                   -- prezzo effettivamente addebitato in colonna E (se diverso, come nel foglio)
    g_price     NUMERIC(14,2),
    sheet_order INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, module_id)
);

-- configurazione Galileo dell'offerta (una per offerta)
CREATE TABLE offer_galileo (
    offer_id    BIGINT PRIMARY KEY REFERENCES offer (id) ON DELETE CASCADE,
    profile_id  BIGINT       NOT NULL REFERENCES galileo_profile (id),
    release     VARCHAR(3)   NOT NULL DEFAULT 'V9' CHECK (release IN ('V9', 'V11')),
    users       INT          NOT NULL DEFAULT 0,
    add_users   INT          NOT NULL DEFAULT 0,
    source      SMALLINT     NOT NULL DEFAULT 0,                  -- migrazione da: 1 carattere, 2 visuale, 3 V9
    aggiunta    BOOLEAN      NOT NULL DEFAULT FALSE,
    list_total  NUMERIC(14,2),                                   -- listino di vendita calcolato al salvataggio
    maintenance NUMERIC(14,2)
);
CREATE TABLE offer_galileo_module (
    offer_id    BIGINT       NOT NULL REFERENCES offer_galileo (offer_id) ON DELETE CASCADE,
    module_id   BIGINT       NOT NULL REFERENCES module (id),
    state       CHAR(1)      NOT NULL CHECK (state IN ('D', 'F')),   -- D = in comune/migrato/selezionato, F = nuovo
    PRIMARY KEY (offer_id, module_id)
);
