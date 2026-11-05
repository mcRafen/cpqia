-- Gruppo processore obbligatorio: i modelli senza gruppo (1 core) passano a P05
UPDATE power_model SET processor_group = 'P05' WHERE processor_group IS NULL;
ALTER TABLE power_model ALTER COLUMN processor_group SET NOT NULL;
