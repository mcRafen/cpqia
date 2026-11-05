-- Dettaglio SMITECH «SMIDRI+SMISMP» (documento «SMIDRI & SMISMP rev6»): servizio di Disaster Recovery SMIDRI e, in abbinamento, SMISMP a prezzi dedicati.
-- Prezzi per classe del sistema Power (modalità P05 / P10 di SMITECH), canone annuo valido per entrambi i sistemi.
--   SMIDRI                      P05 € 4.800,00   P10 € 7.250,00   (pag. 2)
--   SMISMP abbinato a SMIDRI    P05 € 1.200,00   P10 € 1.750,00   (pag. 5; con il solo SMISMP vale il listino «SMISMP» per fasce di utenti)
INSERT INTO module (application_id, code, description, section, kind, area, sort_order, note, word_file, active)
    SELECT a.id, 'SMIDRI+SMISMP', 'SERVIZIO SMIDRI (DISASTER RECOVERY PER AMBIENTE IBM I) E SERVIZIO SMISMP (SMART MONITORING POWER)', 'FIXED', 'ANNUAL', 'SMIDRI+SMISMP', 70,
           'Canone annuo SMIDRI per classe del sistema Power: P05 € 4.800,00, P10 € 7.250,00 (corrispettivo valido per entrambi i sistemi, primario e di DR). Durata minima 12 mesi, fatturazione anticipata del canone annuale al momento dell''ordine. Prerequisito: prodotto SMIBK. Servizi di attivazione stimati: 68 ore (installazione nuovo Power di produzione 24, Power di backup 12, attivazione SMIDRI 16, attivazione SMISMP sui 2 sistemi 16). Con il solo SMISMP usare il modulo SMISMP.',
           'SMIDRI-SMISMP.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMIDRI+SMISMP' AND m.deleted_at IS NULL);

INSERT INTO module (application_id, code, description, section, kind, area, sort_order, note, word_file, active)
    SELECT a.id, 'SMISMP-DRI', 'SMISMP (SMART MONITORING IBM POWER SYSTEMS) ABBINATO A SMIDRI', 'FIXED', 'ANNUAL', 'SMIDRI+SMISMP', 80,
           'Canone annuo SMISMP quando abbinato a SMIDRI, per classe del sistema Power: P05 € 1.200,00, P10 € 1.750,00 (corrispettivo valido per entrambi i sistemi). Durata minima 12 mesi, il primo canone è fatturato a completamento attivazione del servizio.',
           'SMIDRI-SMISMP.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMISMP-DRI' AND m.deleted_at IS NULL);

INSERT INTO module_price (module_id, mode_code, price)
    SELECT m.id, v.mode, v.price
    FROM module m JOIN application a ON a.id = m.application_id AND a.code = 'SMITECH'
    JOIN (VALUES ('SMIDRI+SMISMP', 'P05', 4800.00), ('SMIDRI+SMISMP', 'P10', 7250.00), ('SMISMP-DRI', 'P05', 1200.00), ('SMISMP-DRI', 'P10', 1750.00)) AS v(code, mode, price) ON v.code = m.code
    WHERE m.deleted_at IS NULL
    ON CONFLICT (module_id, mode_code) DO NOTHING;

-- SMISMP a prezzo abbinato si puo' scegliere solo insieme a SMIDRI
INSERT INTO module_rule (module_id, rule_type, target_codes, severity, message)
    SELECT m.id, 'REQUIRES', 'SMIDRI+SMISMP', 'ERROR', 'Il canone SMISMP abbinato vale solo con il servizio SMIDRI (per il solo SMISMP usa il modulo SMISMP)'
    FROM module m JOIN application a ON a.id = m.application_id AND a.code = 'SMITECH'
    WHERE m.code = 'SMISMP-DRI' AND m.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module_rule r WHERE r.module_id = m.id AND r.target_codes = 'SMIDRI+SMISMP' AND r.deleted_at IS NULL);
