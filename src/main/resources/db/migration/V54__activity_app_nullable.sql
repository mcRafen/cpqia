-- Attivita' RFQ con applicazione non trovata in anagrafica: application_id nullo (gruppo «NON TROVATO»)
ALTER TABLE offer_activity ALTER COLUMN application_id DROP NOT NULL;
