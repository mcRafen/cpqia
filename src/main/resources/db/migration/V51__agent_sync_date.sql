-- Data dell'ultima sincronizzazione agenti riuscita (informativa: la lettura degli agenti e' sempre completa)
ALTER TABLE agent_sync_config ADD COLUMN last_sync_date DATE;
