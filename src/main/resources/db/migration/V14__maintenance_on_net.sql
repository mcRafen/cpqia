-- Manutenzione su netto scontato: per applicazione (predefinito: si, come il comportamento precedente) e copia sulla riga d'offerta
ALTER TABLE application ADD COLUMN maint_on_net BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE offer_line  ADD COLUMN maint_on_net BOOLEAN NOT NULL DEFAULT TRUE;
