-- GENERATO da tools/gen_mercati_seed.py a partire da 'mercati.xlsx' (famiglia, codice, descrizione); applicazioni attive
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_ALIM_GDO', 'ALIMENTARE E GDO', 'GALILEO', 'ALIMENTARE E GDO', TRUE, 2001 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_ALIM_GDO' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_AMM', 'AMMINISTRAZIONE', 'GALILEO', 'AMMINISTRAZIONE', TRUE, 2002 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_AMM' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_CAFFE_TOR', 'CAFFÈ E TORREFAZIONE', 'GALILEO', 'CAFFÈ E TORREFAZIONE', TRUE, 2003 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_CAFFE_TOR' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_COMMESSA', 'COMMESSA E MACHINERY', 'GALILEO', 'COMMESSA E MACHINERY', TRUE, 2004 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_COMMESSA' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_ITS', 'ITS', 'GALILEO', 'ITS', TRUE, 2005 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_ITS' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_LATT_CAS', 'LATTIERO CASEARIO', 'GALILEO', 'LATTIERO CASEARIO', TRUE, 2006 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_LATT_CAS' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_MOBILI', 'MOBILI', 'GALILEO', 'MOBILI', TRUE, 2007 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_MOBILI' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_MODA', 'MODA', 'GALILEO', 'MODA', TRUE, 2008 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_MODA' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_OLEI', 'OLEIFICI', 'GALILEO', 'OLEIFICI', TRUE, 2009 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_OLEI' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_PROD_SER', 'PRODUZIONE IN SERIE', 'GALILEO', 'PRODUZIONE IN SERIE', TRUE, 2010 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_PROD_SER' AND deleted_at IS NULL);
INSERT INTO application (code, name, family, description, active, sort_order)
    SELECT 'CRM_JGAL_VINI', 'VINICOLO', 'GALILEO', 'VINICOLO', TRUE, 2011 WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = 'CRM_JGAL_VINI' AND deleted_at IS NULL);
