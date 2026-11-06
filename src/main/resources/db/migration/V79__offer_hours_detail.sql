-- Riepilogo finale > Opzioni del documento: «Dettaglio ore per singola attività» (ore o giorni di ogni riga di attività nella tabella dei servizi); predefinito: spento = solo i totali
ALTER TABLE offer ADD COLUMN opt_hours_detail BOOLEAN NOT NULL DEFAULT FALSE;
