-- Elemento «Servizi» dell'offerta: NONE (nessuno), MANUAL (attivita' inserite a mano), RFQ (importate dal foglio Google)
ALTER TABLE offer ADD COLUMN services_mode VARCHAR(6) NOT NULL DEFAULT 'MANUAL' CHECK (services_mode IN ('NONE', 'MANUAL', 'RFQ'));
UPDATE offer SET services_mode = 'RFQ' WHERE opt_use_rfq;
