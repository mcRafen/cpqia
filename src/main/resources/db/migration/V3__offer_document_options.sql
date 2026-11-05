-- Opzioni di composizione del documento Word, scelte nel riepilogo dell'offerta
ALTER TABLE offer ADD COLUMN opt_maintenance_additional BOOLEAN NOT NULL DEFAULT FALSE;  -- "canone di manutenzione in aggiunta all'attuale"
ALTER TABLE offer ADD COLUMN opt_extra_services         BOOLEAN NOT NULL DEFAULT FALSE;  -- "aggiungi servizi accessori"
ALTER TABLE offer ADD COLUMN opt_final_summary          BOOLEAN NOT NULL DEFAULT FALSE;  -- "aggiungi riepilogo finale"
