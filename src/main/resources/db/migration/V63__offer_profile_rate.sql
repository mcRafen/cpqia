-- Tariffe per profilo professionale modificate nell'offerta (pagina Contenuti): prevalgono su quelle dell'account e di listino
CREATE TABLE offer_profile_rate (
    offer_id    BIGINT        NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    profile_id  BIGINT        NOT NULL REFERENCES professional_profile (id) ON DELETE CASCADE,
    amount      NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (offer_id, profile_id)
);
