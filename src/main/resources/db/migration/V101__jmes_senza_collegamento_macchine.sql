-- JMES: il gruppo di prezzo «Collegamento macchine» (e il relativo numero di macchine) non si considera piu':
-- le stesse logiche sono nel modulo SMIJMDIN. Cancellazione logica (visibile nel Cestino / ripristinabile), i dati delle offerte gia' salvate restano.
UPDATE price_group SET deleted_at = now()
 WHERE deleted_at IS NULL AND code = 'MAC' AND application_id = (SELECT id FROM application WHERE code = 'JMES');
UPDATE app_driver SET deleted_at = now()
 WHERE deleted_at IS NULL AND code = 'MACCHINE' AND application_id = (SELECT id FROM application WHERE code = 'JMES');
