-- Modelli Power System: il campo «Codice» (V88) non serve piu': il suo valore e' gia' nel campo «Tipo» (V90)
ALTER TABLE power_model DROP COLUMN code;
