-- Attivita' sistemistiche per applicazione (sezione "Servizi" dell'offerta): righe con profilo professionale, ore e tariffa oraria
ALTER TABLE offer ADD COLUMN activity_discount_pct NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (activity_discount_pct BETWEEN 0 AND 100);

CREATE TABLE offer_activity (
    id             BIGSERIAL PRIMARY KEY,
    offer_id       BIGINT        NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    application_id BIGINT        NOT NULL REFERENCES application (id),
    sort_order     INT           NOT NULL DEFAULT 0,
    description    VARCHAR(250)  NOT NULL,
    profile_id     BIGINT        NOT NULL REFERENCES professional_profile (id),
    profile_name   VARCHAR(150)  NOT NULL,                 -- copia del nome del profilo al momento del salvataggio
    hours          NUMERIC(8,2)  NOT NULL CHECK (hours > 0),
    rate           NUMERIC(12,2) NOT NULL CHECK (rate >= 0) -- tariffa oraria di listino del profilo al momento del salvataggio
);
CREATE INDEX ix_offer_activity_offer ON offer_activity (offer_id);
