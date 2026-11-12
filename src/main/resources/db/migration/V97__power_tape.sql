-- Opzione Hardware «TAPE» del Power System: LTO8 TAPE (predefinita) oppure TS4300 - TAPE LIBRARY; vale con il flag power_lto8 acceso
ALTER TABLE offer ADD COLUMN power_tape VARCHAR(10) NOT NULL DEFAULT 'LTO8' CHECK (power_tape IN ('LTO8', 'TS4300'));
ALTER TABLE offer_power_cbu ADD COLUMN power_tape VARCHAR(10) NOT NULL DEFAULT 'LTO8' CHECK (power_tape IN ('LTO8', 'TS4300'));
