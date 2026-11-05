-- Modelli Power System: i core ammessi sono 1, 4 e 8
ALTER TABLE power_model DROP CONSTRAINT IF EXISTS power_model_cores_check;
ALTER TABLE power_model ADD CONSTRAINT power_model_cores_check CHECK (cores IN (1, 4, 8));
