-- Comportamento griglie: nuova opzione «Modifica in griglia» (modifica dei campi non calcolati direttamente nelle celle)
ALTER TABLE grid_behavior ADD COLUMN inline_edit BOOLEAN NOT NULL DEFAULT TRUE;
