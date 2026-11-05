-- Tariffe dedicate al cliente: per ogni profilo professionale e per ogni voce di trasferta si puo' indicare un valore diverso dal listino
CREATE TABLE account_profile_rate (
    account_id BIGINT        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    profile_id BIGINT        NOT NULL REFERENCES professional_profile (id) ON DELETE CASCADE,
    rate       NUMERIC(12,2) NOT NULL CHECK (rate >= 0),
    PRIMARY KEY (account_id, profile_id)
);
CREATE TABLE account_travel_rate (
    account_id     BIGINT        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    travel_rate_id BIGINT        NOT NULL REFERENCES travel_rate (id) ON DELETE CASCADE,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (account_id, travel_rate_id)
);
