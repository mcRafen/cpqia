-- Prodotti IBM i dell'offerta Power System: per ogni prodotto attivo un valore obbligatorio (1, 2, 3, 4 o Migrato)
CREATE TABLE offer_power_product (
    offer_id    BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    product_id  BIGINT       NOT NULL REFERENCES ibmi_product (id),
    code        VARCHAR(20)  NOT NULL,               -- copia al momento del salvataggio
    description VARCHAR(200) NOT NULL,
    value       VARCHAR(10)  NOT NULL CHECK (value IN ('1', '2', '3', '4', 'MIGRATO')),
    PRIMARY KEY (offer_id, product_id)
);
