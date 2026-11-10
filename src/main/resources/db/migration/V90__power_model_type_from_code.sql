-- Modelli Power System: dove il «Codice» e' valorizzato, lo stesso valore va nel campo «Tipo» (es. S1122)
UPDATE power_model SET type = left(code, 20) WHERE code <> '' AND type <> left(code, 20);
