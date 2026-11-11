-- Modalita' di fatturazione e pagamento del canone SMIDRI / SMISMP (presente solo se l'offerta comprende SMIDRI e/o SMISMP)
ALTER TABLE offer ADD COLUMN bill_dri      VARCHAR(12) NOT NULL DEFAULT 'INSTALL';     -- INSTALL (mese di installazione) | DATE
ALTER TABLE offer ADD COLUMN bill_dri_date DATE;
ALTER TABLE offer ADD COLUMN pay_dri       VARCHAR(30) NOT NULL DEFAULT 'USUAL';
ALTER TABLE offer ADD COLUMN pay_dri_other VARCHAR(250);
