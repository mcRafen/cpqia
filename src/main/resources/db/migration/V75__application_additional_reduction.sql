-- Anagrafica applicazioni: «Riduzione su moduli aggiuntivi» (percentuale, facoltativa)
ALTER TABLE application ADD COLUMN additional_reduction_pct NUMERIC(6,2) CHECK (additional_reduction_pct IS NULL OR (additional_reduction_pct >= 0 AND additional_reduction_pct <= 100));
