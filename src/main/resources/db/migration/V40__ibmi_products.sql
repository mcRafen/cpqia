-- Anagrafica prodotti IBM i (licenze software associate ai sistemi Power)
CREATE TABLE ibmi_product (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,                 -- es. 5722-SS1
    description VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMP,
    deleted_by  BIGINT REFERENCES app_user (id)
);
CREATE UNIQUE INDEX ux_ibmi_product_code ON ibmi_product (lower(code)) WHERE deleted_at IS NULL;

INSERT INTO ibmi_product (code, description, sort_order) VALUES
    ('5722-SS1', 'Sistema Operativo OS/400', 10),
    ('5773-PVE', 'Licenza Power VM', 20),
    ('5722-XW1', 'iSeries Access', 30),
    ('5770-WDS', 'IBM WebSphere Development Studio for iSeries', 40);
