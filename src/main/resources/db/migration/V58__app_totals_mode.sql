-- «Aggiungi totale attivita' per applicazione» diventa un menu: NO | HOURS | AMOUNT | BOTH
ALTER TABLE offer ADD COLUMN app_totals_mode VARCHAR(10) NOT NULL DEFAULT 'NO' CHECK (app_totals_mode IN ('NO', 'HOURS', 'AMOUNT', 'BOTH'));
UPDATE offer SET app_totals_mode = 'BOTH' WHERE opt_app_totals;
ALTER TABLE offer DROP COLUMN opt_app_totals;
