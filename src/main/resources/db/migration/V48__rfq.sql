-- «Usa RFQ»: le attivita' del passo Servizi si importano da un foglio Google (RFQ) invece di inserirle a mano
ALTER TABLE offer ADD COLUMN opt_use_rfq BOOLEAN NOT NULL DEFAULT FALSE;

-- campi di servizio: riga importata da RFQ e titolo del gruppo (livello sotto l'applicazione)
ALTER TABLE offer_activity ADD COLUMN rfq_import BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offer_activity ADD COLUMN rfq_group  VARCHAR(250);
