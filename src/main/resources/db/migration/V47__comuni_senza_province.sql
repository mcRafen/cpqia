-- Elimina la tabella delle province: sigla, nome della provincia e regione stanno direttamente nei comuni
ALTER TABLE comuni ADD COLUMN sigla     VARCHAR(2);
ALTER TABLE comuni ADD COLUMN provincia VARCHAR(100);
ALTER TABLE comuni ADD COLUMN regione   VARCHAR(60);

UPDATE comuni c SET sigla = p.sigla, provincia = p.nome, regione = p.regione FROM province p WHERE p.id = c.province_id;

ALTER TABLE comuni ALTER COLUMN sigla SET NOT NULL;
ALTER TABLE comuni ALTER COLUMN provincia SET NOT NULL;
DROP INDEX IF EXISTS ix_comuni_province;
ALTER TABLE comuni DROP COLUMN province_id;
DROP TABLE province;
CREATE INDEX ix_comuni_sigla ON comuni (sigla);

-- la griglia «Province» non esiste piu': via configurazioni e viste salvate
DELETE FROM grid_behavior WHERE grid_key = 'province';
DELETE FROM grid_layout   WHERE grid_key = 'province';
