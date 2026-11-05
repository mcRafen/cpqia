-- Opzione documento «Servizi espressi in gg» (predefinito: si): la colonna STIMA del documento riporta giorni invece di ore
ALTER TABLE offer ADD COLUMN opt_services_days BOOLEAN NOT NULL DEFAULT TRUE;
