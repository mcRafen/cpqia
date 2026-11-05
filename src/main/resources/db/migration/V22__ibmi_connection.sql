-- Collegamento a IBM Power System (IBM i) via JDBC (JTOpen): una sola configurazione, gestita dall'administrator
CREATE TABLE ibmi_connection (
    id            INT PRIMARY KEY CHECK (id = 1),
    host          VARCHAR(255) NOT NULL DEFAULT '',     -- indirizzo IP o nome del server
    user_name     VARCHAR(50)  NOT NULL DEFAULT '',
    password_enc  VARCHAR(500),                          -- password cifrata (AES-GCM), mai restituita al browser
    library       VARCHAR(10)  NOT NULL DEFAULT '',      -- libreria IBM i (schema) a cui puntare
    secure        BOOLEAN      NOT NULL DEFAULT FALSE,   -- connessione SSL
    last_test_at  TIMESTAMP,
    last_test_ok  BOOLEAN,
    last_test_msg VARCHAR(1000),
    updated_at    TIMESTAMP,
    updated_by    BIGINT REFERENCES app_user (id)
);
INSERT INTO ibmi_connection (id) VALUES (1);
