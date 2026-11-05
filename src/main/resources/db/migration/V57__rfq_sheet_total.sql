-- Totale dei servizi letto dalla riga «TOTALE GENERALE PROGETTO» (colonna H) del foglio RFQ importato, per il controllo di coerenza
ALTER TABLE offer ADD COLUMN rfq_sheet_total NUMERIC(14,2);
