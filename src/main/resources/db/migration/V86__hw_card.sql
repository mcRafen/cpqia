-- Schede HW: anagrafica dell'hardware dei clienti (da IBM Power System, file CGYHW00F) e relativo agente di sincronizzazione
CREATE TABLE hw_card (
    id                       BIGSERIAL PRIMARY KEY,
    source_key               VARCHAR(200) NOT NULL UNIQUE,         -- chiave di importazione: CONTSH|TPMASH|MODESH|NRMASH
    customer_code            VARCHAR(20)  NOT NULL,                -- CONTSH: codice dell'account (si decodifica la ragione sociale con join su account.code)
    hw_type                  VARCHAR(30)  NOT NULL DEFAULT '',     -- TPMASH: tipo hardware
    machine_type             VARCHAR(20)  NOT NULL DEFAULT '',     -- MODESH prima del «-»
    model                    VARCHAR(20)  NOT NULL DEFAULT '',     -- MODESH dopo il «-» (vuoto se manca il «-»)
    serial_number            VARCHAR(40)  NOT NULL DEFAULT '',     -- NRMASH: matricola
    install_date             DATE,                                 -- DTINSH
    version                  VARCHAR(30)  NOT NULL DEFAULT '',     -- VERSSH
    pef_cumulative           VARCHAR(30)  NOT NULL DEFAULT '',     -- CUPTSH: cumulativo PEF
    tape_unit                VARCHAR(30)  NOT NULL DEFAULT '',     -- UNNASH: unita' nastro
    processor_type           VARCHAR(30)  NOT NULL DEFAULT '',     -- PROCSH: tipo processore
    sw_maintenance_expiry    DATE,                                 -- DTSWSH
    hw_maintenance_expiry    DATE,                                 -- DTHWSH
    sw_effective_expiry      DATE,                                 -- DTESSH: scadenza effettiva manutenzione software
    hw_effective_expiry      DATE,                                 -- DTEHSH: scadenza effettiva manutenzione hardware
    auto_renewal             BOOLEAN      NOT NULL DEFAULT FALSE,  -- RIAUSH
    sw_group                 VARCHAR(30)  NOT NULL DEFAULT '',     -- GRSWSH: gruppo software
    sw_service_type          VARCHAR(30)  NOT NULL DEFAULT '',     -- TSSWSH: tipo servizio software
    hw_service_type          VARCHAR(30)  NOT NULL DEFAULT '',     -- TSHWSH: tipo servizio hardware
    notes                    TEXT         NOT NULL DEFAULT '',     -- NOTESH
    license_count            INT,                                  -- NRLISH: numero licenze
    source_inserted_on       DATE,                                 -- DT01SH (informativa)
    source_modified_on       DATE,                                 -- DTMNSH (informativa)
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX ix_hw_card_customer ON hw_card (customer_code);

CREATE TABLE hw_sync_config (
    id              INT PRIMARY KEY CHECK (id = 1),
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    mode            VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (mode IN ('INTERVAL', 'DAILY')),
    interval_min    INT         NOT NULL DEFAULT 60 CHECK (interval_min BETWEEN 5 AND 1440),
    daily_time      VARCHAR(5)  NOT NULL DEFAULT '03:30',
    source_library  VARCHAR(10) NOT NULL DEFAULT 'INF90DAT',
    source_file     VARCHAR(10) NOT NULL DEFAULT 'CGYHW00F',
    last_sync_date  DATE,                                  -- data dell'ultima importazione riuscita
    last_run_at     TIMESTAMP
);
INSERT INTO hw_sync_config (id) VALUES (1);

CREATE TABLE hw_sync_run (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMP   NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    trigger_type  VARCHAR(10) NOT NULL,                   -- SCHEDULED | MANUAL
    ok            BOOLEAN,
    read_count    INT NOT NULL DEFAULT 0,
    inserted      INT NOT NULL DEFAULT 0,
    updated       INT NOT NULL DEFAULT 0,
    unchanged     INT NOT NULL DEFAULT 0,
    skipped       INT NOT NULL DEFAULT 0,
    message       VARCHAR(300),
    details       TEXT
);

-- journaling delle modifiche (vedi V61)
SELECT audit_attach(t) FROM unnest(ARRAY['hw_card', 'hw_sync_config']) AS t;
