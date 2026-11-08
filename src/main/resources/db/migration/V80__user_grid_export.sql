-- Esportazione dati delle griglie (Excel, CSV, Google Sheets) abilitata per utente e per griglia; grid_key = '*' vale per tutte le griglie.
-- L'administrator esporta sempre. Gli utenti gia' presenti mantengono l'esportazione su tutte le griglie (come prima); i nuovi utenti non hanno griglie abilitate.
CREATE TABLE user_grid_export (
    user_id  BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    grid_key VARCHAR(60) NOT NULL,
    PRIMARY KEY (user_id, grid_key)
);
INSERT INTO user_grid_export (user_id, grid_key) SELECT id, '*' FROM app_user;
