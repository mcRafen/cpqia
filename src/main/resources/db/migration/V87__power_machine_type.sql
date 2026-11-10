-- Modelli Power System: nuovo campo «Tipo macchina». Il vecchio modello («9824-22A») si divide al primo «-»:
-- i 4 caratteri prima del segno diventano il tipo macchina («9824»), quello che segue resta il modello («22A»).
-- Senza «-» il modello non cambia e il tipo macchina resta vuoto.
ALTER TABLE power_model ADD COLUMN machine_type VARCHAR(20) NOT NULL DEFAULT '';

UPDATE power_model
   SET machine_type = right(split_part(model, '-', 1), 4),
       model        = substr(model, position('-' IN model) + 1)
 WHERE position('-' IN model) > 0;

-- l'unicita' comprende ora anche il tipo macchina
DROP INDEX IF EXISTS ux_power_model;
CREATE UNIQUE INDEX ux_power_model ON power_model (lower(machine_type), lower(model), lower(type), cores, mounting) WHERE deleted_at IS NULL;
