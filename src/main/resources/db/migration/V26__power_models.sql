-- Anagrafica modelli IBM Power System
CREATE TABLE power_model (
    id          BIGSERIAL PRIMARY KEY,
    model       VARCHAR(20)  NOT NULL,                 -- es. 9824-22A
    type        VARCHAR(20)  NOT NULL,                 -- tipo/serie, es. S1122
    cores       INT          NOT NULL CHECK (cores > 0),
    mounting    VARCHAR(10)  NOT NULL CHECK (mounting IN ('DESK', 'RACK')),
    version     VARCHAR(20)  NOT NULL,                 -- es. POWER 11
    cpw         INT          NOT NULL CHECK (cpw >= 0),  -- potenza di calcolo (CPW)
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMP,
    deleted_by  BIGINT REFERENCES app_user (id)
);
CREATE UNIQUE INDEX ux_power_model ON power_model (lower(model), lower(type), cores, mounting) WHERE deleted_at IS NULL;
