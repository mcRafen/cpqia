-- Spese di trasferta dell'account: un'unica voce scelta dalla tabella "Trasferte Italia" (al posto di importi dedicati per voce)
DROP TABLE account_travel_rate;
ALTER TABLE account ADD COLUMN travel_rate_id BIGINT REFERENCES travel_rate (id) ON DELETE SET NULL;
