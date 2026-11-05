-- Giorni (gg) delle attivita' importate da RFQ (colonna E del foglio; le ore sono la colonna D)
ALTER TABLE offer_activity ADD COLUMN days NUMERIC(8,2);
