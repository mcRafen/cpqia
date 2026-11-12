-- Ultimo utente che ha salvato l'offerta (menu «Ultime offerte gestite»); le offerte esistenti valgono per chi le ha create
ALTER TABLE offer ADD COLUMN updated_by BIGINT REFERENCES app_user (id);
UPDATE offer SET updated_by = created_by WHERE updated_by IS NULL;
CREATE INDEX ix_offer_updated_by ON offer (updated_by, updated_at DESC);
