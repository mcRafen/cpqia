-- Versione del modello Power System: elenco chiuso POWER 11 / POWER 10 (predefinito POWER 11)
ALTER TABLE power_model ALTER COLUMN version SET DEFAULT 'POWER 11';
ALTER TABLE power_model ADD CONSTRAINT power_model_version_check CHECK (version IN ('POWER 11', 'POWER 10'));
