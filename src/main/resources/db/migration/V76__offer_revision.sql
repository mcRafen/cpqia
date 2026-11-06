-- Revisioni dell'offerta: ogni revisione e' una nuova offerta collegata (stesso numero, «revision» progressiva); revision_of punta all'offerta di partenza (revisione 0)
ALTER TABLE offer ADD COLUMN revision INT NOT NULL DEFAULT 0;
ALTER TABLE offer ADD COLUMN revision_of BIGINT REFERENCES offer (id);
CREATE INDEX ix_offer_revision_of ON offer (revision_of);
DROP INDEX IF EXISTS ux_offer_number;
CREATE UNIQUE INDEX ux_offer_number ON offer (number, revision) WHERE deleted_at IS NULL;
