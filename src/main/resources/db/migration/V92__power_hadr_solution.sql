-- Power HA/DR: soluzione scelta (NONE = nessuna, SMIDRI, QUICK-EDD)
ALTER TABLE offer ADD COLUMN power_hadr_solution VARCHAR(12) NOT NULL DEFAULT 'NONE' CHECK (power_hadr_solution IN ('NONE', 'SMIDRI', 'QUICK-EDD'));
