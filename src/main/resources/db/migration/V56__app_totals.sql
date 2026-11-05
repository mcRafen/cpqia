-- Opzione documento «Aggiungi totale attivita' per applicazione» (predefinito: no)
ALTER TABLE offer ADD COLUMN opt_app_totals BOOLEAN NOT NULL DEFAULT FALSE;
