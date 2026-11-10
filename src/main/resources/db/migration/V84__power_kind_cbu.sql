-- Power System: configurazione dell'offerta (NEW = Nuovo Power, HADR = Power HA/DR, REMARKETING = Power da Remarketing)
-- e, per HA/DR, la configurazione della scheda «Power CBU» (JSON con gli stessi campi della scheda Produzione, senza i prodotti IBM i)
ALTER TABLE offer ADD COLUMN power_kind VARCHAR(12) NOT NULL DEFAULT 'NEW' CHECK (power_kind IN ('NEW', 'HADR', 'REMARKETING'));
ALTER TABLE offer ADD COLUMN power_cbu TEXT;
