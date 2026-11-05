-- Codice agente dell'account (CDAGCA del file CGANA01J, importato dalla Sync Clienti); la descrizione si decodifica dalla tabella agent
ALTER TABLE account ADD COLUMN agent_code VARCHAR(20);

-- la prossima Sync Clienti rilegge tutti i clienti (importazione ripetibile) per valorizzare il codice agente degli account gia' presenti
UPDATE ibmi_sync_config SET last_sync_date = NULL;
