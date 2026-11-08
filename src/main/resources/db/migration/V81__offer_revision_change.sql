-- Modifiche memorizzate tra una revisione dell'offerta e la precedente (ricalcolate a ogni salvataggio della revisione)
CREATE TABLE offer_revision_change (
    id         BIGSERIAL PRIMARY KEY,
    offer_id   BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,   -- la revisione modificata
    prev_id    BIGINT       REFERENCES offer (id) ON DELETE SET NULL,           -- la revisione precedente usata per il confronto
    section    VARCHAR(80)  NOT NULL,
    field      VARCHAR(250) NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    sort_order INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_offer_revision_change_offer ON offer_revision_change (offer_id);
