-- Modelli Power System: nuovi campi «Codice» e «Fascia», altre versioni e record importabili anche se incompleti.
-- I modelli importati dall'elenco IBM (V89) restano NON attivi finche' non si completano Tipo, Core, Montaggio, Gruppo processore e CPW.
ALTER TABLE power_model ADD COLUMN code VARCHAR(60)  NOT NULL DEFAULT '',
                        ADD COLUMN band VARCHAR(100) NOT NULL DEFAULT '';

ALTER TABLE power_model ALTER COLUMN cores DROP NOT NULL,
                        ALTER COLUMN mounting DROP NOT NULL,
                        ALTER COLUMN cpw DROP NOT NULL,
                        ALTER COLUMN processor_group DROP NOT NULL;

ALTER TABLE power_model DROP CONSTRAINT IF EXISTS power_model_version_check;
ALTER TABLE power_model ADD CONSTRAINT power_model_version_check
    CHECK (version IN ('POWER 11', 'POWER 10', 'POWER 9', 'POWER 8', 'POWER 7+', 'POWER 7', 'POWER 6'));

-- un modello attivo (selezionabile nelle offerte) deve essere completo
ALTER TABLE power_model ADD CONSTRAINT power_model_active_complete_check
    CHECK (NOT active OR (type <> '' AND cores IS NOT NULL AND mounting IS NOT NULL AND cpw IS NOT NULL AND processor_group IS NOT NULL));
