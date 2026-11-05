-- GENERATO da tools/gen_extras_seed.py a partire da servizi-accessori.docx - servizi accessori (schema, catalogo, selezioni)

CREATE TABLE extra_service (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    title        VARCHAR(150) NOT NULL,
    summary_name VARCHAR(150) NOT NULL,             -- nome nella tabella del riepilogo finale
    included_text VARCHAR(30),                      -- es. COMPRESO: servizio senza costo
    sort_order   INT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE extra_group (
    id          BIGSERIAL PRIMARY KEY,
    service_id  BIGINT NOT NULL REFERENCES extra_service (id) ON DELETE CASCADE,
    code        VARCHAR(30) NOT NULL UNIQUE,        -- usato nei segnaposto del Word
    title       VARCHAR(250) NOT NULL,
    selection   VARCHAR(5) NOT NULL CHECK (selection IN ('FIXED', 'ONE', 'MANY')),  -- tutte incluse / una sola riga / piu' righe
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE TABLE extra_option (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT NOT NULL REFERENCES extra_group (id) ON DELETE CASCADE,
    row_key      VARCHAR(30) NOT NULL,              -- lettera della fascia o codice articolo
    article_code VARCHAR(40),
    label        VARCHAR(500) NOT NULL,
    onetime      NUMERIC(14,2),
    annual       NUMERIC(14,2),
    extra_unit   NUMERIC(14,4),                     -- valore per unita' oltre soglia
    ad_hoc       BOOLEAN NOT NULL DEFAULT FALSE,
    ad_hoc_text  VARCHAR(100),
    qty_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order   INT NOT NULL DEFAULT 0,
    UNIQUE (group_id, row_key)
);
CREATE TABLE offer_extra_service (
    offer_id   BIGINT NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES extra_service (id),
    PRIMARY KEY (offer_id, service_id)
);
CREATE TABLE offer_extra_option (
    offer_id  BIGINT NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    option_id BIGINT NOT NULL REFERENCES extra_option (id),
    qty       INT NOT NULL DEFAULT 1,
    PRIMARY KEY (offer_id, option_id)
);

INSERT INTO extra_service (id, code, title, summary_name, included_text, sort_order) VALUES (1, 'AUDIT', 'Audit per conservazione digitale a norma', 'AUDIT PER CONSERVAZIONE DIGITALE A NORMA', 'COMPRESO', 10);
INSERT INTO extra_service (id, code, title, summary_name, included_text, sort_order) VALUES (2, 'DXC', 'Discovery Xchange', 'DISCOVERY XCHANGE', NULL, 20);
INSERT INTO extra_service (id, code, title, summary_name, included_text, sort_order) VALUES (3, 'FIRMAE', 'Firmae', 'FIRMAE', NULL, 30);
INSERT INTO extra_service (id, code, title, summary_name, included_text, sort_order) VALUES (4, 'CRC', 'CRC Credit Recovery Control', 'CRC', NULL, 40);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (1, 2, 'DXC_BAND', 'Scaglioni documenti (fatture contenute nei file XML o inviate/ricevute)', 'ONE', TRUE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (1, 1, 'A', 'SMIXC / SMIDESOU', 'Fino a 100', 150.00, 370.00, NULL, FALSE, NULL, FALSE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (2, 1, 'B', 'SMIXC / SMIDESOU', 'Fino a 1.000', 150.00, 620.00, NULL, FALSE, NULL, FALSE, 20);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (3, 1, 'C', 'SMIXC / SMIDESOU', 'Fino a 5.000', 150.00, 1320.00, NULL, FALSE, NULL, FALSE, 30);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (4, 1, 'D', 'SMIXC / SMIDESOU', 'Fino a 10.000', 150.00, 1920.00, NULL, FALSE, NULL, FALSE, 40);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (5, 1, 'E', 'SMIXC / SMIDESOU', 'Fino a 25.000', 150.00, 2895.00, NULL, FALSE, NULL, FALSE, 50);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (6, 1, 'F', 'SMIXC / SMIDESOU', 'Fino a 50.000', 150.00, 4070.00, NULL, FALSE, NULL, FALSE, 60);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (7, 1, 'G', 'SMIXC / SMIDESOU', 'Fino a 100.000', 150.00, 6230.00, NULL, FALSE, NULL, FALSE, 70);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (8, 1, 'H', 'SMIXC / SMIDESOU', 'Fino a 250.000', 150.00, 8970.00, NULL, FALSE, NULL, FALSE, 80);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (9, 1, 'I', 'SMIXC / SMIDESOU', 'Oltre i 250.000', 150.00, NULL, NULL, TRUE, 'Offerta ad hoc', FALSE, 90);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (2, 2, 'DXC_CERT', 'Certificato di firma - opzionale', 'MANY', FALSE, 20);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (10, 2, 'SMIXCFDA', 'SMIXCFDA', 'Firma digitale automatica qualificata remota - importo annuo', NULL, 100.00, NULL, FALSE, NULL, FALSE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (11, 2, 'SMINHFQR', 'SMINHFQR', 'Riconoscimento tramite operatore (se l''utente non vuole usare SPID o CIE) - importo unitario una tantum', 20.00, NULL, NULL, FALSE, NULL, TRUE, 20);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (3, 3, 'FIRMAE_START', 'Startup e firma eIDAS', 'FIXED', FALSE, 30);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (12, 3, 'SMINHAT', 'SMINHAT', 'Attivazione servizio una tantum', 100.00, NULL, NULL, FALSE, NULL, FALSE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (13, 3, 'SMINHSIG', 'SMINHSIG', 'Certificato di firma "sigillo" - importo annuo per singolo certificato/azienda attivata', NULL, 80.00, NULL, FALSE, NULL, FALSE, 20);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (4, 3, 'FIRMAE_BAND', 'Fasce numero firme annue - eIDAS firma avanzata (APP + OTP)', 'ONE', TRUE, 40);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (14, 4, 'A', 'SMINHDAS', '250', NULL, 180, 0.80, FALSE, NULL, FALSE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (15, 4, 'B', 'SMINHDAS', '400', NULL, 260, 0.80, FALSE, NULL, FALSE, 20);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (16, 4, 'C', 'SMINHDAS', '500', NULL, 300, 0.70, FALSE, NULL, FALSE, 30);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (17, 4, 'D', 'SMINHDAS', '800', NULL, 400, 0.70, FALSE, NULL, FALSE, 40);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (18, 4, 'E', 'SMINHDAS', '1.000', NULL, 500, 0.70, FALSE, NULL, FALSE, 50);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (19, 4, 'F', 'SMINHDAS', '2.500', NULL, 1000, 0.60, FALSE, NULL, FALSE, 60);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (20, 4, 'G', 'SMINHDAS', '5.000', NULL, 1750, 0.50, FALSE, NULL, FALSE, 70);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (21, 4, 'H', 'SMINHDAS', '7.000', NULL, 2150, 0.40, FALSE, NULL, FALSE, 80);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (22, 4, 'I', 'SMINHDAS', '10.000', NULL, 2750, 0.40, FALSE, NULL, FALSE, 90);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (23, 4, 'J', 'SMINHDAS', '15.000', NULL, 3750, 0.30, FALSE, NULL, FALSE, 100);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (24, 4, 'K', 'SMINHDAS', '20.000', NULL, 4750, 0.30, FALSE, NULL, FALSE, 110);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (25, 4, 'L', 'SMINHDAS', '50.000', NULL, 10750, 0.30, FALSE, NULL, FALSE, 120);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (26, 4, 'M', 'SMINHDAS', '100.000', NULL, 20750, 0.20, FALSE, NULL, FALSE, 130);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (27, 4, 'N', 'SMINHDAS', '250.000', NULL, 42500, 0.20, FALSE, NULL, FALSE, 140);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (28, 4, 'O', 'SMINHDAS', '500.000', NULL, 70000, 0.18, FALSE, NULL, FALSE, 150);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (29, 4, 'P', 'SMINHDAS', '1.000.000', NULL, 120000, 0.15, FALSE, NULL, FALSE, 160);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (30, 4, 'Q', 'SMINHDAS', '2.000.000', NULL, 200000, 0.15, FALSE, NULL, FALSE, 170);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (5, 4, 'CRC_START', 'Attivazione servizio', 'FIXED', FALSE, 50);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (31, 5, 'SMINHCRA', 'SMINHCRA', 'Attivazione servizio SaaS (una tantum)', 100.00, NULL, NULL, FALSE, NULL, FALSE, 10);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (6, 4, 'CRC_IMPORT', 'Servizi SaaS facoltativi', 'MANY', FALSE, 60);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (32, 6, 'SMINHCRI', 'SMINHCRI', 'Importazione storico partite aperte (una tantum, per partita)', 0.5, NULL, NULL, FALSE, NULL, TRUE, 10);
INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES (7, 4, 'CRC_BAND', 'Fasce partite gestite all''anno (fatture emesse all''anno)', 'ONE', TRUE, 70);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (33, 7, 'A', 'SMINHCRC', 'Fino a 100', NULL, 400.00, 5.00, FALSE, NULL, FALSE, 10);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (34, 7, 'B', 'SMINHCRC', 'Fino a 300', NULL, 600.00, 4.50, FALSE, NULL, FALSE, 20);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (35, 7, 'C', 'SMINHCRC', 'Fino a 500', NULL, 800.00, 4.30, FALSE, NULL, FALSE, 30);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (36, 7, 'D', 'SMINHCRC', 'Fino a 1.000', NULL, 1000.00, 3.75, FALSE, NULL, FALSE, 40);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (37, 7, 'E', 'SMINHCRC', 'Fino a 2.000', NULL, 1300.00, 3.00, FALSE, NULL, FALSE, 50);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (38, 7, 'F', 'SMINHCRC', 'Fino a 5.000', NULL, 1750.00, 2.00, FALSE, NULL, FALSE, 60);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (39, 7, 'G', 'SMINHCRC', 'Fino a 10.000', NULL, 2250.00, 1.00, FALSE, NULL, FALSE, 70);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (40, 7, 'H', 'SMINHCRC', 'Fino a 20.000', NULL, 3000.00, 0.50, FALSE, NULL, FALSE, 80);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (41, 7, 'I', 'SMINHCRC', 'Fino a 50.000', NULL, 5000.00, 0.25, FALSE, NULL, FALSE, 90);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (42, 7, 'L', 'SMINHCRC', 'Fino a 100.000', NULL, 6500.00, 0.20, FALSE, NULL, FALSE, 100);
INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES (43, 7, 'M', 'SMINHCRC', 'Oltre le 100.000', NULL, NULL, NULL, TRUE, 'Valorizzazione ad hoc', FALSE, 110);

SELECT setval('extra_service_id_seq', (SELECT MAX(id) FROM extra_service));
SELECT setval('extra_group_id_seq', (SELECT MAX(id) FROM extra_group));
SELECT setval('extra_option_id_seq', (SELECT MAX(id) FROM extra_option));
