-- Galileo: prezzo di listino e prezzo a cliente modificabili per singolo modulo selezionato (NULL = prezzo del listino / uguale al listino)
ALTER TABLE offer_galileo_module ADD COLUMN list_price NUMERIC(14,2), ADD COLUMN customer_price NUMERIC(14,2);
