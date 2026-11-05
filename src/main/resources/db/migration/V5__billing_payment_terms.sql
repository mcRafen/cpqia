-- Modalita' di fatturazione e pagamento dell'offerta (licenze, canoni di manutenzione, attivita' sistemistiche)
ALTER TABLE offer ADD COLUMN bill_lic       VARCHAR(12)  NOT NULL DEFAULT 'DAYS30';        -- DAYS30 | DATE
ALTER TABLE offer ADD COLUMN bill_lic_date  DATE;
ALTER TABLE offer ADD COLUMN bill_fee       VARCHAR(12)  NOT NULL DEFAULT 'NEXT_MONTH';    -- NEXT_MONTH | DATE
ALTER TABLE offer ADD COLUMN bill_fee_date  DATE;
ALTER TABLE offer ADD COLUMN fee_semiannual BOOLEAN      NOT NULL DEFAULT FALSE;           -- canoni fatturati semestralmente
ALTER TABLE offer ADD COLUMN bill_act       VARCHAR(12)  NOT NULL DEFAULT 'CONSUNTIVO';    -- CONSUNTIVO | PACCHETTO
ALTER TABLE offer ADD COLUMN pay_lic        VARCHAR(30)  NOT NULL DEFAULT 'USUAL';
ALTER TABLE offer ADD COLUMN pay_lic_other  VARCHAR(250);
ALTER TABLE offer ADD COLUMN pay_fee        VARCHAR(30)  NOT NULL DEFAULT 'USUAL';
ALTER TABLE offer ADD COLUMN pay_fee_other  VARCHAR(250);
ALTER TABLE offer ADD COLUMN pay_act        VARCHAR(30)  NOT NULL DEFAULT 'USUAL';
ALTER TABLE offer ADD COLUMN pay_act_other  VARCHAR(250);
