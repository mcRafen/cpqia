-- Power HA/DR con soluzione SMIDRI: prezzo al cliente di SMIDRI e, facoltativo, di SMISMP abbinato a SMIDRI (il listino viene dal gruppo processore del Power di Produzione)
ALTER TABLE offer ADD COLUMN power_smidri_price NUMERIC(14,2);
ALTER TABLE offer ADD COLUMN power_smidri_smismp BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offer ADD COLUMN power_smidri_smismp_price NUMERIC(14,2);
