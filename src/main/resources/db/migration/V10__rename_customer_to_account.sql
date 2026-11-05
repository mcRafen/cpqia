-- La tabella delle anagrafiche clienti/prospect si chiama ora "account" (come nell'interfaccia).
-- Le colonne di riferimento (offer.customer_id) mantengono il nome.
ALTER TABLE customer RENAME TO account;
ALTER SEQUENCE customer_code_seq RENAME TO account_code_seq;
ALTER INDEX ux_customer_vat RENAME TO ux_account_vat;
ALTER INDEX ix_customer_name RENAME TO ix_account_name;
