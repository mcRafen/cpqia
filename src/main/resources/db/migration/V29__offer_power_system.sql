-- Configurazione IBM Power System dell'offerta (pagina "IBM Power System", attiva con l'elemento Power System)
ALTER TABLE offer ADD COLUMN power_model_id   BIGINT REFERENCES power_model (id),
                  ADD COLUMN power_ram_gb     INT         CHECK (power_ram_gb IN (64, 128, 256, 512)),
                  ADD COLUMN power_disk       VARCHAR(10) CHECK (power_disk IN ('1.6TB', '3.2TB', '6.4TB')),
                  ADD COLUMN power_lto8       BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN power_eth10      BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN power_ibmi_users INT         CHECK (power_ibmi_users >= 0);
