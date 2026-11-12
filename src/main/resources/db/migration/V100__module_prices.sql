-- Dipartimentali: listino e «Importo a cliente» modificabili per singolo modulo
--  list_price     = prezzo unitario di listino diverso dal catalogo (NULL = quello del catalogo)
--  customer_price = prezzo unitario a cliente (NULL = uguale al listino); su questo si applica lo sconto globale dell'offerta
--  customer_*_amount di riga = licenze e manutenzione calcolate con i prezzi a cliente (NULL = uguali a quelle di listino)
ALTER TABLE offer_line_module ADD COLUMN list_price NUMERIC(14,2), ADD COLUMN customer_price NUMERIC(14,2);
ALTER TABLE offer_line ADD COLUMN customer_license_amount NUMERIC(14,2), ADD COLUMN customer_maintenance_amount NUMERIC(14,2);
