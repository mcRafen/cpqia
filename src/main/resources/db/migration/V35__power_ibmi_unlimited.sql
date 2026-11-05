-- Utenti IBM i licenziati: numero obbligatorio oppure ILLIMITATI
ALTER TABLE offer ADD COLUMN power_ibmi_unlimited BOOLEAN NOT NULL DEFAULT FALSE;
