-- Taglio di RAM da 32 GB ammesso nelle offerte (nei modelli si abilita dalla scheda di anagrafica)
ALTER TABLE offer DROP CONSTRAINT IF EXISTS offer_power_ram_gb_check;
ALTER TABLE offer ADD CONSTRAINT offer_power_ram_gb_check CHECK (power_ram_gb IN (32, 64, 96, 128, 256, 384, 512));
