-- Manutenzione SW/HW del Power: terza sezione «TS4300» (TAPE LIBRARY) accanto a POWER e LTO8, per Produzione e CBU
ALTER TABLE offer_power_maint ALTER COLUMN kind TYPE VARCHAR(10);
ALTER TABLE offer_power_maint DROP CONSTRAINT IF EXISTS offer_power_maint_kind_check;
ALTER TABLE offer_power_maint ADD CONSTRAINT offer_power_maint_kind_check CHECK (kind IN ('POWER', 'LTO8', 'TS4300'));
ALTER TABLE offer_power_cbu_maint ALTER COLUMN kind TYPE VARCHAR(10);
ALTER TABLE offer_power_cbu_maint DROP CONSTRAINT IF EXISTS offer_power_cbu_maint_kind_check;
ALTER TABLE offer_power_cbu_maint ADD CONSTRAINT offer_power_cbu_maint_kind_check CHECK (kind IN ('POWER', 'LTO8', 'TS4300'));
