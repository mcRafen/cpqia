-- TAPE del Power System: LTO8 (power_lto8) e TS4300 TAPE LIBRARY sono due scelte indipendenti (almeno una con TAPE attivo); sostituisce power_tape di V97
ALTER TABLE offer DROP COLUMN power_tape;
ALTER TABLE offer_power_cbu DROP COLUMN power_tape;
ALTER TABLE offer ADD COLUMN power_ts4300 BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offer_power_cbu ADD COLUMN power_ts4300 BOOLEAN NOT NULL DEFAULT FALSE;
