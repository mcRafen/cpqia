-- CPQIA - schema iniziale (PostgreSQL)

-- =========================================================== utenti applicativi
CREATE TABLE app_user (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100),
    last_name             VARCHAR(100),
    full_name             VARCHAR(200),
    initials              VARCHAR(10)  NOT NULL DEFAULT '',
    picture_url           VARCHAR(500),
    auth_provider         VARCHAR(20),               -- GOOGLE | APPLE
    provider_subject      VARCHAR(255),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    -- flag di abilitazione alle funzioni
    administrator         BOOLEAN      NOT NULL DEFAULT FALSE,   -- tutte le funzioni + gestione utenti
    can_create_offers     BOOLEAN      NOT NULL DEFAULT TRUE,
    can_edit_offers       BOOLEAN      NOT NULL DEFAULT TRUE,    -- modifica le proprie offerte
    can_view_all_offers   BOOLEAN      NOT NULL DEFAULT FALSE,   -- vede/modifica le offerte di tutti
    can_delete_offers     BOOLEAN      NOT NULL DEFAULT FALSE,
    can_export_docx       BOOLEAN      NOT NULL DEFAULT TRUE,
    can_manage_customers  BOOLEAN      NOT NULL DEFAULT TRUE,
    can_manage_catalog    BOOLEAN      NOT NULL DEFAULT FALSE,   -- anagrafica applicazioni, moduli, prezzi
    can_view_prices       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    last_login_at         TIMESTAMP
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- =========================================================== anagrafica clienti / prospect
CREATE SEQUENCE customer_code_seq START 1;

CREATE TABLE customer (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL UNIQUE,                -- auto, non modificabile
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('CLIENTE', 'PROSPECT')),
    company_name    VARCHAR(200) NOT NULL,                       -- ragione sociale
    street_type     VARCHAR(30)  NOT NULL,                       -- via (Via, Viale, Piazza, ...)
    street_name     VARCHAR(200) NOT NULL,                       -- indirizzo
    street_number   VARCHAR(20)  NOT NULL,                       -- numero civico
    postal_code     VARCHAR(10)  NOT NULL,                       -- CAP
    city            VARCHAR(100) NOT NULL,
    province        VARCHAR(5),
    vat_number      VARCHAR(20)  NOT NULL,                       -- partita IVA
    tax_code        VARCHAR(20),                                 -- codice fiscale
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES app_user (id)
);
CREATE UNIQUE INDEX ux_customer_vat ON customer (vat_number);
CREATE INDEX ix_customer_name ON customer (lower(company_name));

-- =========================================================== catalogo
CREATE TABLE application (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(20)  NOT NULL UNIQUE,
    name             VARCHAR(150) NOT NULL,
    family           VARCHAR(60),
    description      TEXT,
    maintenance_pct  NUMERIC(6,2) NOT NULL DEFAULT 18,
    needs_review     BOOLEAN      NOT NULL DEFAULT FALSE,   -- logica del listino da verificare
    notes            TEXT,
    source_sheet     VARCHAR(100),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0
);

-- modalita' di prezzo (nuova installazione / migrazione / ...)
CREATE TABLE app_mode (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    code            VARCHAR(20) NOT NULL,
    label           VARCHAR(100) NOT NULL,
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order      INT         NOT NULL DEFAULT 0,
    UNIQUE (application_id, code)
);

-- grandezza che moltiplica il prezzo (utenti, macchine, visualizzatori)
CREATE TABLE app_driver (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT       NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    code            VARCHAR(30)  NOT NULL,
    label           VARCHAR(100) NOT NULL,
    license_code    VARCHAR(40),
    unit_label      VARCHAR(30)  NOT NULL DEFAULT 'utenti',
    sort_order      INT          NOT NULL DEFAULT 0,
    UNIQUE (application_id, code)
);

-- gruppo di prezzo: i moduli del gruppo sommano la base su cui si applicano le fasce
CREATE TABLE price_group (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT       NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    driver_id       BIGINT       NOT NULL REFERENCES app_driver (id) ON DELETE CASCADE,
    code            VARCHAR(30)  NOT NULL,
    label           VARCHAR(150) NOT NULL,
    base_mode       VARCHAR(10)  NOT NULL DEFAULT 'TIER1' CHECK (base_mode IN ('TIER1', 'LINEAR')),
    sort_order      INT          NOT NULL DEFAULT 0,
    UNIQUE (application_id, code)
);

-- base fissa del gruppo (es. JMES macchine 5.000) per modalita'
CREATE TABLE price_group_base (
    group_id   BIGINT       NOT NULL REFERENCES price_group (id) ON DELETE CASCADE,
    mode_code  VARCHAR(20)  NOT NULL DEFAULT 'STD',
    amount     NUMERIC(14,2) NOT NULL,
    PRIMARY KEY (group_id, mode_code)
);

-- fasce di posti di lavoro
CREATE TABLE pricing_tier (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL REFERENCES price_group (id) ON DELETE CASCADE,
    from_qty   BIGINT      NOT NULL,
    to_qty     BIGINT,                              -- NULL = illimitato
    label      VARCHAR(100) NOT NULL,
    pct        NUMERIC(10,5),                       -- % del valore moduli per utente aggiuntivo
    sort_order INT         NOT NULL DEFAULT 0
);
CREATE TABLE pricing_tier_price (                   -- prezzo unitario esplicito per modalita' (alternativo a pct)
    tier_id     BIGINT      NOT NULL REFERENCES pricing_tier (id) ON DELETE CASCADE,
    mode_code   VARCHAR(20) NOT NULL DEFAULT 'STD',
    unit_price  NUMERIC(14,4) NOT NULL,
    PRIMARY KEY (tier_id, mode_code)
);

CREATE TABLE module (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    group_id         BIGINT       REFERENCES price_group (id) ON DELETE SET NULL,   -- NULL = prezzo fisso
    code             VARCHAR(40)  NOT NULL,
    print_code       VARCHAR(40),                                                   -- codice da stampare in offerta
    description      VARCHAR(500) NOT NULL,
    section          VARCHAR(10)  NOT NULL DEFAULT 'FIXED' CHECK (section IN ('SCALED', 'FIXED', 'LINK')),
    kind             VARCHAR(10)  NOT NULL DEFAULT 'LICENSE' CHECK (kind IN ('LICENSE', 'ANNUAL', 'ONETIME')),
    qty_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    excl_group       VARCHAR(40),                                                   -- alternativi: uno solo del gruppo
    default_selected BOOLEAN      NOT NULL DEFAULT FALSE,
    included         BOOLEAN      NOT NULL DEFAULT FALSE,                           -- incluso nel modulo base (prezzo 0)
    maintenance_pct  NUMERIC(6,2),                                                  -- NULL = % dell'applicazione
    onetime_price    NUMERIC(14,2) NOT NULL DEFAULT 0,                              -- una tantum aggiuntiva
    link_text        VARCHAR(300),
    note             TEXT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_module_app ON module (application_id, sort_order);

CREATE TABLE module_price (
    module_id  BIGINT        NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    mode_code  VARCHAR(20)   NOT NULL DEFAULT 'STD',
    price      NUMERIC(14,2) NOT NULL,
    PRIMARY KEY (module_id, mode_code)
);

-- vincoli tra moduli (da commenti del listino)
CREATE TABLE module_rule (
    id            BIGSERIAL PRIMARY KEY,
    module_id     BIGINT       NOT NULL REFERENCES module (id) ON DELETE CASCADE,
    rule_type     VARCHAR(15)  NOT NULL CHECK (rule_type IN ('REQUIRES', 'REQUIRES_ANY', 'EXCLUDES', 'INFO')),
    target_codes  VARCHAR(300),                      -- codici modulo separati da virgola
    severity      VARCHAR(10)  NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR', 'WARNING')),
    message       TEXT         NOT NULL
);

-- =========================================================== offerte
CREATE TABLE offer (
    id                    BIGSERIAL PRIMARY KEY,
    number                VARCHAR(40)  NOT NULL UNIQUE,       -- YYYYMMDD.<progressivo><iniziali>
    offer_date            DATE         NOT NULL,
    progressive           INT          NOT NULL,
    initials              VARCHAR(10)  NOT NULL,
    customer_id           BIGINT       NOT NULL REFERENCES customer (id),
    created_by            BIGINT       NOT NULL REFERENCES app_user (id),
    project_title         VARCHAR(250),
    contact_ref           VARCHAR(150),                       -- "Rif." in copertina
    discount_pct          NUMERIC(6,2) NOT NULL DEFAULT 0,
    status                VARCHAR(10)  NOT NULL DEFAULT 'BOZZA' CHECK (status IN ('BOZZA', 'EMESSA')),
    total_license         NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_maintenance     NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX ix_offer_customer ON offer (customer_id);

CREATE TABLE offer_line (
    id                   BIGSERIAL PRIMARY KEY,
    offer_id             BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    application_id       BIGINT       NOT NULL REFERENCES application (id),
    mode_code            VARCHAR(20),
    sort_order           INT          NOT NULL DEFAULT 0,
    license_amount       NUMERIC(14,2) NOT NULL DEFAULT 0,    -- licenze + una tantum
    maintenance_amount   NUMERIC(14,2) NOT NULL DEFAULT 0
);
CREATE TABLE offer_line_driver (
    line_id    BIGINT NOT NULL REFERENCES offer_line (id) ON DELETE CASCADE,
    driver_id  BIGINT NOT NULL REFERENCES app_driver (id),
    qty        INT    NOT NULL,
    PRIMARY KEY (line_id, driver_id)
);
CREATE TABLE offer_line_module (
    line_id    BIGINT NOT NULL REFERENCES offer_line (id) ON DELETE CASCADE,
    module_id  BIGINT NOT NULL REFERENCES module (id),
    qty        INT    NOT NULL DEFAULT 1,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,               -- prezzo a listino al momento dell'offerta
    PRIMARY KEY (line_id, module_id)
);
