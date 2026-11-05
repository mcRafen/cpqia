-- Agente associato all'utente (codice dalla tabella agent): limita l'elenco degli account visibili
ALTER TABLE app_user ADD COLUMN agent_code VARCHAR(20);
