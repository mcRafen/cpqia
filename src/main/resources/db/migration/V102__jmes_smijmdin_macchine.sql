-- JMES: il numero di macchine da collegare torna ad essere richiesto, ma solo se e' selezionato il modulo SMIJMDIN (Digital industry):
-- il gruppo di prezzo «Collegamento macchine» (base 5.000 + fasce per macchina) passa dal gruppo «Moduli per utente» al modulo SMIJMDIN.
-- Annulla la cancellazione logica della V101.
UPDATE price_group SET deleted_at = NULL, deleted_by = NULL
 WHERE code = 'MAC' AND deleted_at IS NOT NULL AND application_id = (SELECT id FROM application WHERE code = 'JMES');
UPDATE app_driver SET deleted_at = NULL, deleted_by = NULL
 WHERE code = 'MACCHINE' AND deleted_at IS NOT NULL AND application_id = (SELECT id FROM application WHERE code = 'JMES');
UPDATE module SET group_id = (SELECT g.id FROM price_group g WHERE g.code = 'MAC' AND g.deleted_at IS NULL AND g.application_id = module.application_id),
                  note = 'Selezionando il modulo si indica il numero di macchine da collegare (prezzo a fasce)'
 WHERE code = 'SMIJMDIN' AND application_id = (SELECT id FROM application WHERE code = 'JMES');
UPDATE module_rule SET message = 'Indica il numero di macchine da collegare'
 WHERE module_id = (SELECT m.id FROM module m JOIN application a ON a.id = m.application_id WHERE a.code = 'JMES' AND m.code = 'SMIJMDIN')
   AND rule_type = 'INFO';
