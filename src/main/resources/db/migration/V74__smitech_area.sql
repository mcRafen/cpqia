-- La pagina «Servizi SMITECH» dell'offerta carica i moduli di SMITECH con Area applicativa = SMITECH: i sei servizi del documento servizi smitech v4
UPDATE module SET area = 'SMITECH'
    WHERE code IN ('SMINGF', 'SMISEP', 'SMIACR', 'SMISMP', 'SMIMGT', 'SMICAI') AND area IS NULL
      AND application_id = (SELECT id FROM application WHERE code = 'SMITECH' AND deleted_at IS NULL);
