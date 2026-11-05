-- Righe per pagina: ammesso anche 15 (nuovo valore predefinito); il controllo si allarga a un intervallo
ALTER TABLE grid_setting DROP CONSTRAINT IF EXISTS grid_setting_page_size_check;
ALTER TABLE grid_setting ADD CONSTRAINT grid_setting_page_size_check CHECK (page_size BETWEEN 0 AND 1000);
