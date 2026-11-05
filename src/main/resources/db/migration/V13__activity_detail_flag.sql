-- Flag "Dettaglio importi attivita'": se attivo il documento riporta ore e valori di ogni riga, altrimenti solo il totale per applicazione
ALTER TABLE offer ADD COLUMN opt_activity_detail BOOLEAN NOT NULL DEFAULT FALSE;
