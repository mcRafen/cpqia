-- Impostazioni di griglia per utente: righe per pagina (0 = tutte)
CREATE TABLE grid_setting (
    user_id   BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    grid_key  VARCHAR(40) NOT NULL,
    page_size INT         NOT NULL CHECK (page_size IN (0, 10, 25, 50, 100, 200, 500)),
    PRIMARY KEY (user_id, grid_key)
);
