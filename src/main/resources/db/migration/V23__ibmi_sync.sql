-- Agente schedulato: importazione dei clienti da IBM Power System (file CGANA01J) nella tabella account
CREATE TABLE ibmi_sync_config (
    id              INT PRIMARY KEY CHECK (id = 1),
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    mode            VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (mode IN ('INTERVAL', 'DAILY')),
    interval_min    INT         NOT NULL DEFAULT 60 CHECK (interval_min BETWEEN 5 AND 1440),
    daily_time      VARCHAR(5)  NOT NULL DEFAULT '02:00',
    source_library  VARCHAR(10) NOT NULL DEFAULT 'INF90DAT',
    source_file     VARCHAR(10) NOT NULL DEFAULT 'CGANA01J',
    last_sync_date  DATE,                                  -- data dell'ultima importazione riuscita
    last_run_at     TIMESTAMP
);
INSERT INTO ibmi_sync_config (id) VALUES (1);

CREATE TABLE ibmi_sync_run (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMP   NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    trigger_type  VARCHAR(10) NOT NULL,                   -- SCHEDULED | MANUAL
    ok            BOOLEAN,
    read_count    INT NOT NULL DEFAULT 0,                  -- righe lette con CLFOCP = 'C'
    inserted      INT NOT NULL DEFAULT 0,
    updated       INT NOT NULL DEFAULT 0,
    unchanged     INT NOT NULL DEFAULT 0,
    skipped       INT NOT NULL DEFAULT 0,
    message       VARCHAR(300),
    details       TEXT
);
