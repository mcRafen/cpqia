-- Modelli Power System: frequenza del processore (testo libero, es. "3.0 - 3.9 GHz")
ALTER TABLE power_model ADD COLUMN ghz VARCHAR(30);
