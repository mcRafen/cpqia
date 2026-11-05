-- Manutenzione SW/HW del Power System: uno o piu' periodi (36 / 48 / 60 mesi) e, per ciascuno, gli importi (listino e netto scontato)
-- della sezione Power e, con LTO8, della sezione LTO8. power_list_price / power_net_price dell'offerta restano come totali.
CREATE TABLE offer_power_maint (
    offer_id    BIGINT        NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    months      INT           NOT NULL CHECK (months IN (36, 48, 60)),
    kind        VARCHAR(5)    NOT NULL CHECK (kind IN ('POWER', 'LTO8')),
    list_price  NUMERIC(14,2) NOT NULL CHECK (list_price >= 0),
    net_price   NUMERIC(14,2) NOT NULL CHECK (net_price >= 0),
    PRIMARY KEY (offer_id, months, kind)
);
