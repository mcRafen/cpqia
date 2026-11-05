-- Opzione Power System: durata della manutenzione SW/HW in mesi (36, 48 o 60; predefinito 36)
ALTER TABLE offer ADD COLUMN power_maint_months INT NOT NULL DEFAULT 36 CHECK (power_maint_months IN (36, 48, 60));
