-- =====================================================================================
-- Cancellazione logica: ogni record "principale" ha deleted_at / deleted_by.
-- Gli indici univoci diventano parziali (solo record non eliminati) cosi' un codice/numero si puo' riusare.
-- =====================================================================================
ALTER TABLE offer               ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE customer            ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE app_user            ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE application         ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE module              ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE app_mode            ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE app_driver          ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE price_group         ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE pricing_tier        ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE module_rule         ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE professional_profile ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);
ALTER TABLE travel_rate         ADD COLUMN deleted_at TIMESTAMP, ADD COLUMN deleted_by BIGINT REFERENCES app_user (id);

ALTER TABLE offer DROP CONSTRAINT IF EXISTS offer_number_key;
CREATE UNIQUE INDEX ux_offer_number ON offer (number) WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS ux_customer_vat;
CREATE UNIQUE INDEX ux_customer_vat ON customer (vat_number) WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS ux_app_user_email;
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email)) WHERE deleted_at IS NULL;

ALTER TABLE application DROP CONSTRAINT IF EXISTS application_code_key;
CREATE UNIQUE INDEX ux_application_code ON application (code) WHERE deleted_at IS NULL;

ALTER TABLE app_mode DROP CONSTRAINT IF EXISTS app_mode_application_id_code_key;
CREATE UNIQUE INDEX ux_app_mode_code ON app_mode (application_id, code) WHERE deleted_at IS NULL;

ALTER TABLE app_driver DROP CONSTRAINT IF EXISTS app_driver_application_id_code_key;
CREATE UNIQUE INDEX ux_app_driver_code ON app_driver (application_id, code) WHERE deleted_at IS NULL;

ALTER TABLE price_group DROP CONSTRAINT IF EXISTS price_group_application_id_code_key;
CREATE UNIQUE INDEX ux_price_group_code ON price_group (application_id, code) WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS ux_professional_profile_name;
CREATE UNIQUE INDEX ux_professional_profile_name ON professional_profile (lower(name)) WHERE deleted_at IS NULL;
DROP INDEX IF EXISTS ux_travel_rate_name;
CREATE UNIQUE INDEX ux_travel_rate_name ON travel_rate (lower(name)) WHERE deleted_at IS NULL;

-- =====================================================================================
-- Configurazioni salvate delle griglie (ordine colonne, ordinamento, filtri, raggruppamenti, totali)
-- =====================================================================================
CREATE TABLE grid_layout (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    grid_key   VARCHAR(40)  NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    config     TEXT         NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (user_id, grid_key, name)
);

-- =====================================================================================
-- Sedi Sanmarco (gestione riservata all'amministratore)
-- =====================================================================================
CREATE TABLE sede (
    id            BIGSERIAL PRIMARY KEY,
    sort_order    INT          NOT NULL DEFAULT 0,       -- progressivo per ordinamento
    name          VARCHAR(100) NOT NULL,                  -- paese, come da visualizzare nell'elenco
    street        VARCHAR(150) NOT NULL,
    street_number VARCHAR(20)  NOT NULL,
    postal_code   VARCHAR(10)  NOT NULL,
    province      VARCHAR(5)   NOT NULL,
    deleted_at    TIMESTAMP,
    deleted_by    BIGINT REFERENCES app_user (id)
);
CREATE UNIQUE INDEX ux_sede_name ON sede (lower(name)) WHERE deleted_at IS NULL;
