-- SMISMP: canone annuo per fasce di utenti (documento «servizi smitech v4», pag. 9): importo unico della fascia in cui cadono gli utenti.
-- Nuovo calcolo dei gruppi di prezzo «STEP» (importo per fascia): vedi PricingService.
DO $chk$
DECLARE c TEXT;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'price_group'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%LINEAR%' LOOP
        EXECUTE format('ALTER TABLE price_group DROP CONSTRAINT %I', c);
    END LOOP;
END
$chk$;
ALTER TABLE price_group ADD CONSTRAINT price_group_base_mode_check CHECK (base_mode IN ('TIER1', 'LINEAR', 'STEP'));

INSERT INTO app_driver (application_id, code, label, unit_label, sort_order)
    SELECT a.id, 'UTENTI', 'Utenti', 'utenti', 10 FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM app_driver d WHERE d.application_id = a.id AND d.code = 'UTENTI');

INSERT INTO price_group (application_id, driver_id, code, label, base_mode, sort_order)
    SELECT a.id, d.id, 'SMISMP', 'SMISMP – canone annuo per fascia di utenti', 'STEP', 10
    FROM application a JOIN app_driver d ON d.application_id = a.id AND d.code = 'UTENTI'
    WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM price_group x WHERE x.application_id = a.id AND x.code = 'SMISMP' AND x.deleted_at IS NULL);

-- fasce: fino a 15 / 16-40 / 41-100 utenti con il canone annuo; oltre 100 senza prezzo (valutazione ad hoc)
INSERT INTO pricing_tier (group_id, from_qty, to_qty, label, pct, sort_order)
    SELECT g.id, v.f, v.t, v.l, NULL, v.f
    FROM price_group g JOIN application a ON a.id = g.application_id AND a.code = 'SMITECH'
    CROSS JOIN (VALUES (1, 15, 'Fino a 15 utenti'), (16, 40, 'Da 16 a 40 utenti'), (41, 100, 'Da 41 a 100 utenti'),
                       (101, NULL, 'Oltre 100 utenti – valutazione ad hoc')) AS v(f, t, l)
    WHERE g.code = 'SMISMP' AND NOT EXISTS (SELECT 1 FROM pricing_tier x WHERE x.group_id = g.id AND x.deleted_at IS NULL);

INSERT INTO pricing_tier_price (tier_id, mode_code, unit_price)
    SELECT t.id, 'STD', v.p
    FROM pricing_tier t JOIN price_group g ON g.id = t.group_id JOIN application a ON a.id = g.application_id AND a.code = 'SMITECH'
    JOIN (VALUES (1, 1500.00), (16, 1800.00), (41, 2400.00)) AS v(f, p) ON v.f = t.from_qty
    WHERE g.code = 'SMISMP' AND t.deleted_at IS NULL
    ON CONFLICT (tier_id, mode_code) DO NOTHING;

-- il modulo SMISMP usa il gruppo di prezzo (il suo importo e' quello della fascia)
UPDATE module SET group_id = (SELECT g.id FROM price_group g WHERE g.application_id = module.application_id AND g.code = 'SMISMP')
    WHERE code = 'SMISMP' AND application_id = (SELECT id FROM application WHERE code = 'SMITECH' AND deleted_at IS NULL);
INSERT INTO module_price (module_id, mode_code, price)
    SELECT m.id, 'STD', 0 FROM module m JOIN application a ON a.id = m.application_id AND a.code = 'SMITECH'
    WHERE m.code = 'SMISMP' AND m.deleted_at IS NULL
    ON CONFLICT (module_id, mode_code) DO NOTHING;
