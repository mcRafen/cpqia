-- Importi del Power System nell'offerta: prezzo di listino e prezzo netto
ALTER TABLE offer ADD COLUMN power_list_price NUMERIC(14,2) CHECK (power_list_price >= 0),
                  ADD COLUMN power_net_price  NUMERIC(14,2) CHECK (power_net_price >= 0);
