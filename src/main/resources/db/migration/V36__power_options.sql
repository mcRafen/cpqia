-- Opzioni Power System: cavo di collegamento (LTO6/LTO7), Transfer-Conversion (con riferimenti del Power di origine), Edition
ALTER TABLE offer ADD COLUMN power_cable        VARCHAR(5)  CHECK (power_cable IN ('LTO6', 'LTO7')),
                  ADD COLUMN power_transfer     VARCHAR(10) NOT NULL DEFAULT 'NO' CHECK (power_transfer IN ('NO', 'TRANSFER', 'CONVERSION')),
                  ADD COLUMN power_transfer_ref VARCHAR(500),
                  ADD COLUMN power_edition      VARCHAR(10) NOT NULL DEFAULT 'SOLUTION' CHECK (power_edition IN ('SOLUTION', 'EXPRESS'));
