-- Preferenze di aspetto dell'utente: modalita' (giorno / notte / sistema) e palette di colori; seguono l'utente su ogni browser
CREATE TABLE user_preference (
    user_id    BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    ui_theme   VARCHAR(10),
    ui_palette VARCHAR(20),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
