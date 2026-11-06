-- Collegamento al servizio ATOKA (API per la consultazione delle aziende per partita IVA): una sola chiave API condivisa, gestita da un administrator
CREATE TABLE atoka_connection (
    id            INT PRIMARY KEY CHECK (id = 1),
    api_key_enc   VARCHAR(1000),                         -- chiave API (token) cifrata (AES-GCM), mai restituita al browser
    last_test_at  TIMESTAMP,
    last_test_ok  BOOLEAN,
    last_test_msg VARCHAR(1000),
    updated_at    TIMESTAMP,
    updated_by    BIGINT REFERENCES app_user (id)
);
INSERT INTO atoka_connection (id) VALUES (1);
