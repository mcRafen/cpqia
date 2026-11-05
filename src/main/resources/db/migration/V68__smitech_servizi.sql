-- Servizi SMITECH (documento «servizi smitech v4.docx», pagine 1-16) come moduli dell'applicazione SMITECH.
-- word_file: documento Word (template) associato al modulo, nella sottocartella «Smitech» della cartella dei modelli Word.
ALTER TABLE module ADD COLUMN word_file VARCHAR(150);

-- nuova «Modalità di proposizione»: canone mensile (accanto a licenza, canone annuo, una tantum)
DO $chk$
DECLARE c TEXT;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'module'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%ANNUAL%' LOOP
        EXECUTE format('ALTER TABLE module DROP CONSTRAINT %I', c);
    END LOOP;
END
$chk$;
ALTER TABLE module ADD CONSTRAINT module_kind_check CHECK (kind IN ('LICENSE', 'ANNUAL', 'ONETIME', 'MONTHLY'));

INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMINGF', 'SERVIZIO SMINGF (NEXT GENERATION FIREWALL) – LA DIFESA PERIMETRALE DI VALORE', 'FIXED', 'MONTHLY', 10, 'Canone mensile a scaglioni di posti di lavoro: fino a 10 € 110,00; da 11 a 30 € 150,00; da 31 a 50 € 220,00; da 51 a 70 € 280,00; oltre 70 quotazione a progetto. Durata minima 48 mesi, fatturazione anticipata dei primi 6 canoni. Attivazione a forfait: € 500,00 con dispositivo in modo trasparente, altrimenti da definire previa analisi.', 'SMINGF.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMINGF' AND m.deleted_at IS NULL);
INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMISEP', 'SERVIZIO SMISEP (SECURITY ENDPOINT PROTECTION) – L’EVOLUZIONE DELL’ANTIVIRUS', 'FIXED', 'MONTHLY', 20, 'Canone mensile per quantità: Endpoint Protection per Client, pacchetto da 5 utenti € 20,00; per Server € 11,00. Durata minima 48 mesi, fatturazione anticipata dei primi 6 canoni. Attivazione a forfait da definire previa analisi.', 'SMISEP.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMISEP' AND m.deleted_at IS NULL);
INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMIACR', 'SERVIZIO SMIACR (ANTI-CRYPTOLOCKER E RANSOMWARE)', 'FIXED', 'MONTHLY', 30, 'Canone mensile per quantità: Anti-Cryptolocker e Ransomware per Client, pacchetto da 5 utenti € 17,00; € 12,00 se attivato in presenza del servizio SMISEP. Durata minima 48 mesi, fatturazione anticipata dei primi 6 canoni. Attivazione a forfait da definire (da non considerare se contestuale a SMISEP).', 'SMIACR.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMIACR' AND m.deleted_at IS NULL);
INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMISMP', 'SERVIZIO SMISMP (SMART MONITORING IBM POWER SYSTEMS)', 'FIXED', 'ANNUAL', 40, 'Canone annuo a scaglioni di utenti: fino a 15 € 1.500,00; da 16 a 40 € 1.800,00; da 41 a 100 € 2.400,00; oltre 100 valutazione ad hoc. Secondo sistema Power (High Availability): 70% del canone del sistema primario. Durata minima 12 mesi, fatturazione anticipata del canone annuale. Attivazione stimata: 8 ore (1 giorno) per ciascun sistema Power. Allegato 1: modalità standard minime di copertura dei servizi.', 'SMISMP.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMISMP' AND m.deleted_at IS NULL);
INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMIMGT', 'SERVIZIO SMIMGT - MONITORAGGIO INFRASTRUTTURA TECNOLOGICA x86', 'FIXED', 'ANNUAL', 50, 'Canone annuo € 1.200,00 per il monitoraggio dell''infrastruttura tecnologica x86. Durata minima 12 mesi, fatturazione anticipata del canone annuale all''attivazione.', 'SMIMGT.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMIMGT' AND m.deleted_at IS NULL);
INSERT INTO module (application_id, code, description, section, kind, sort_order, note, word_file, active)
    SELECT a.id, 'SMICAI', 'SERVIZIO SMICAI (CONTROLLO E ASSISTENZA SULLA INFRASTRUTTURA IT INTEL)', 'FIXED', 'ONETIME', 60, 'Importo una tantum: avviamento (attivazione con definizione degli asset da controllare, importo da definire) ed eventuale fornitura e attivazione VPN € 200,00. Pacchetti di gettoni prepagati: 100, 200 o 400 (importi da definire in base alla tariffa del cliente). 1 gettone = 15 minuti di attività, solo da remoto. Fatturazione a forfait a inizio attività.', 'SMICAI.docx', TRUE
    FROM application a WHERE a.code = 'SMITECH' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM module m WHERE m.application_id = a.id AND m.code = 'SMICAI' AND m.deleted_at IS NULL);
