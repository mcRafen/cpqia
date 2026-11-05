-- Tabelle di riferimento dell'account: "Tipo Azienda" (Prospect, Cliente) e "Tipo Indirizzo" (Via, Viale, Piazza, ...)
CREATE TABLE company_type (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL UNIQUE,          -- valore salvato in account.type
    label      VARCHAR(60)  NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0
);
INSERT INTO company_type (code, label, sort_order) VALUES ('PROSPECT', 'Prospect', 10), ('CLIENTE', 'Cliente', 20);

CREATE TABLE address_type (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(30)  NOT NULL UNIQUE,          -- valore salvato in account.street_type
    sort_order INT          NOT NULL DEFAULT 0
);
INSERT INTO address_type (name, sort_order) VALUES
    ('Via', 10), ('Viale', 20), ('Piazza', 30), ('Piazzale', 40), ('Corso', 50), ('Largo', 60), ('Strada', 70),
    ('Vicolo', 80), ('Contrada', 90), ('Borgo', 100), ('Lungomare', 110), ('Frazione', 120), ('Località', 130);
-- valori gia' presenti in anagrafica (es. arrivati dal recupero da partita IVA) non previsti nell'elenco
INSERT INTO address_type (name, sort_order)
    SELECT DISTINCT street_type, 500 FROM account WHERE street_type IS NOT NULL AND street_type <> ''
    ON CONFLICT (name) DO NOTHING;

ALTER TABLE account DROP CONSTRAINT IF EXISTS customer_type_check;
ALTER TABLE account DROP CONSTRAINT IF EXISTS account_type_check;
ALTER TABLE account ADD CONSTRAINT fk_account_company_type FOREIGN KEY (type) REFERENCES company_type (code) ON UPDATE CASCADE;
ALTER TABLE account ADD CONSTRAINT fk_account_address_type FOREIGN KEY (street_type) REFERENCES address_type (name) ON UPDATE CASCADE;
