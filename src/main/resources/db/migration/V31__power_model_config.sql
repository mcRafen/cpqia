-- Modelli Power System: descrizione, gruppo processore e tagli di RAM / disco supportati
ALTER TABLE power_model ADD COLUMN description     VARCHAR(200),                       -- vuoto = "Model <modello> <core> core <montaggio>"
                        ADD COLUMN processor_group VARCHAR(3) CHECK (processor_group IN ('P05', 'P10')),
                        ADD COLUMN ram_options     VARCHAR(40) NOT NULL DEFAULT '',     -- GB separati da virgola: 64,96,128,256,384,512
                        ADD COLUMN disk_options    VARCHAR(40) NOT NULL DEFAULT '';     -- 1.6TB,3.2TB,6.4TB
-- punto di partenza: tutti i tagli supportati (da raffinare per ogni modello in anagrafica)
UPDATE power_model SET ram_options = '64,96,128,256,384,512', disk_options = '1.6TB,3.2TB,6.4TB';

-- nell'offerta sono ammessi anche 96 e 384 GB
ALTER TABLE offer DROP CONSTRAINT IF EXISTS offer_power_ram_gb_check;
ALTER TABLE offer ADD CONSTRAINT offer_power_ram_gb_check CHECK (power_ram_gb IN (64, 96, 128, 256, 384, 512));
