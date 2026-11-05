-- Comportamento delle griglie (Impostazioni > Comportamento griglie): funzioni abilitate per ciascuna griglia
CREATE TABLE grid_behavior (
    grid_key       VARCHAR(40) PRIMARY KEY,
    configurable   BOOLEAN NOT NULL DEFAULT TRUE,
    move_columns   BOOLEAN NOT NULL DEFAULT TRUE,
    sort_columns   BOOLEAN NOT NULL DEFAULT TRUE,
    pivot          BOOLEAN NOT NULL DEFAULT TRUE,
    totals         BOOLEAN NOT NULL DEFAULT TRUE,
    subtotals      BOOLEAN NOT NULL DEFAULT TRUE
);
