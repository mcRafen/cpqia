-- Tabelle di supporto: profili professionali (tariffa oraria) e trasferte Italia (prezzo per fascia)
CREATE TABLE professional_profile (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount >= 0),     -- tariffa oraria in euro
    sort_order INT           NOT NULL DEFAULT 0,
    active     BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_professional_profile_name ON professional_profile (lower(name));

CREATE TABLE travel_rate (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount >= 0),     -- prezzo in euro
    sort_order INT           NOT NULL DEFAULT 0,
    active     BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_travel_rate_name ON travel_rate (lower(name));

INSERT INTO professional_profile (name, amount, sort_order) VALUES
    ('Consulente di organizzazione', 125.00, 10),
    ('Capo Progetto',                110.00, 20),
    ('Analista',                      95.00, 30),
    ('Istruttore',                    90.00, 40),
    ('Sviluppatore',                  85.00, 50),
    ('ITech',                         90.00, 60);

INSERT INTO travel_rate (name, amount, sort_order) VALUES
    ('Italia Distanza 0-25 km',      50.00, 10),
    ('Italia Distanza 25-100km',    100.00, 20),
    ('Italia Distanza 100-200 km',  150.00, 30),
    ('Italia Distanza 200-400 km',  250.00, 40),
    ('Italia Distanza > 400 km',    350.00, 50),
    ('Italia Isole',                400.00, 60);
