-- Agenti (da IBM Power System, file SMTAB01L tipo tabella '01AG') e relativo agente di sincronizzazione
CREATE TABLE agent (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    description VARCHAR(100) NOT NULL DEFAULT ''
);

CREATE TABLE agent_sync_config (
    id              INT PRIMARY KEY CHECK (id = 1),
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    mode            VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (mode IN ('INTERVAL', 'DAILY')),
    interval_min    INT         NOT NULL DEFAULT 60 CHECK (interval_min BETWEEN 5 AND 1440),
    daily_time      VARCHAR(5)  NOT NULL DEFAULT '02:30',
    source_library  VARCHAR(10) NOT NULL DEFAULT 'INF90DAT',
    source_file     VARCHAR(10) NOT NULL DEFAULT 'SMTAB01L',
    last_run_at     TIMESTAMP
);
INSERT INTO agent_sync_config (id) VALUES (1);

CREATE TABLE agent_sync_run (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMP   NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    trigger_type  VARCHAR(10) NOT NULL,                   -- SCHEDULED | MANUAL
    ok            BOOLEAN,
    read_count    INT NOT NULL DEFAULT 0,                  -- righe lette con XTIPTB = '01AG'
    inserted      INT NOT NULL DEFAULT 0,
    updated       INT NOT NULL DEFAULT 0,
    unchanged     INT NOT NULL DEFAULT 0,
    skipped       INT NOT NULL DEFAULT 0,                  -- record annullati (XDATTB che inizia per **) o senza codice
    message       VARCHAR(300),
    details       TEXT
);
