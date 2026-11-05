-- Aggiornamento dei modelli Word nella cartella cpqia.template-dir: impronta (SHA-256) dell'ultima versione del modello distribuita con il WAR.
-- Serve a distinguere i modelli non modificati (che l'aggiornamento sostituisce) da quelli personalizzati (che restano).
CREATE TABLE template_shipped (
    path        VARCHAR(300) PRIMARY KEY,
    sha256      CHAR(64)     NOT NULL,
    shipped_at  TIMESTAMP    NOT NULL DEFAULT now()
);
