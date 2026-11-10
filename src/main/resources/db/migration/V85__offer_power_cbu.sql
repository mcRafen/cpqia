-- Power HA/DR: la scheda «Power CBU» ha tabelle proprie (al posto del JSON provvisorio di V84, che non conteneva dati reali)
ALTER TABLE offer DROP COLUMN power_cbu;

CREATE TABLE offer_power_cbu (
    offer_id             BIGINT      PRIMARY KEY REFERENCES offer (id) ON DELETE CASCADE,
    power_model_id       BIGINT      NOT NULL REFERENCES power_model (id),
    power_ram_gb         INT         NOT NULL CHECK (power_ram_gb IN (32, 64, 96, 128, 256, 384, 512)),
    power_disk           VARCHAR(10) NOT NULL CHECK (power_disk IN ('1.6TB', '3.2TB', '6.4TB')),
    power_disk_count     INT         NOT NULL DEFAULT 2 CHECK (power_disk_count >= 1),
    power_lto8           BOOLEAN     NOT NULL DEFAULT FALSE,
    power_eth10          BOOLEAN     NOT NULL DEFAULT FALSE,
    power_ibmi_users     INT         CHECK (power_ibmi_users >= 0),
    power_ibmi_unlimited BOOLEAN     NOT NULL DEFAULT FALSE,
    power_cable          VARCHAR(5)  CHECK (power_cable IN ('LTO6', 'LTO7')),
    power_transfer       VARCHAR(10) NOT NULL DEFAULT 'NO' CHECK (power_transfer IN ('NO', 'TRANSFER', 'CONVERSION')),
    power_transfer_ref   VARCHAR(500),
    power_edition        VARCHAR(10) NOT NULL DEFAULT 'SOLUTION' CHECK (power_edition IN ('SOLUTION', 'EXPRESS')),
    power_os             VARCHAR(3)  NOT NULL DEFAULT '7.5' CHECK (power_os IN ('7.5', '7.6'))
);

-- importi della manutenzione SW/HW della scheda CBU (stessa struttura di offer_power_maint)
CREATE TABLE offer_power_cbu_maint (
    offer_id   BIGINT        NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    months     INT           NOT NULL CHECK (months IN (36, 48, 60)),
    kind       VARCHAR(5)    NOT NULL CHECK (kind IN ('POWER', 'LTO8')),
    list_price NUMERIC(14,2) NOT NULL CHECK (list_price >= 0),
    net_price  NUMERIC(14,2) NOT NULL CHECK (net_price >= 0),
    PRIMARY KEY (offer_id, months, kind)
);
