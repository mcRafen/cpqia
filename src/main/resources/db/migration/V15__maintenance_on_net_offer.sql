-- "Manutenzione su netto scontato" diventa un unico flag dell'offerta (valido per tutte le manutenzioni): si tolgono i flag per applicazione
ALTER TABLE application DROP COLUMN maint_on_net;
ALTER TABLE offer_line  DROP COLUMN maint_on_net;
ALTER TABLE offer ADD COLUMN maint_on_net BOOLEAN NOT NULL DEFAULT TRUE;     -- predefinito: la manutenzione segue lo sconto (come prima)
