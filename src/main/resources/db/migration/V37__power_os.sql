-- Opzione Power System: sistema operativo IBM i (7.5 o 7.6, predefinito 7.5)
ALTER TABLE offer ADD COLUMN power_os VARCHAR(3) NOT NULL DEFAULT '7.5' CHECK (power_os IN ('7.5', '7.6'));
