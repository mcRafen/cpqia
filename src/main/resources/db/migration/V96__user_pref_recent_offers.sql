-- Preferenze utente: quante «Ultime offerte gestite» mostrare nel menu laterale (NULL = predefinito 5, 0 = nascondi)
ALTER TABLE user_preference ADD COLUMN recent_offers INT CHECK (recent_offers BETWEEN 0 AND 20);
