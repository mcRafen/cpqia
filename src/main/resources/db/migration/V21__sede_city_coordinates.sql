-- Sedi Sanmarco: comune (usato per la geocodifica; "name" e' il paese mostrato in elenco, es. "Headquarter") e coordinate di partenza
ALTER TABLE sede ADD COLUMN city VARCHAR(100);
UPDATE sede SET city = trim(regexp_replace(name, '\s*\(.*\)', ''));
UPDATE sede SET city = 'Grisignano di Zocco' WHERE lower(name) = 'headquarter';

-- coordinate indicative del centro del comune (modificabili in Amministrazione > Sedi Sanmarco; geo_key 'seed' = non ricalcolare)
UPDATE sede SET latitude = 45.497500, longitude = 11.602500, geo_key = 'seed' WHERE lower(name) = 'headquarter';
UPDATE sede SET latitude = 44.900800, longitude =  8.206400, geo_key = 'seed' WHERE lower(name) = 'asti';
UPDATE sede SET latitude = 41.319700, longitude = 16.283600, geo_key = 'seed' WHERE lower(name) = 'barletta';
UPDATE sede SET latitude = 45.541600, longitude = 10.211800, geo_key = 'seed' WHERE lower(name) = 'brescia';
UPDATE sede SET latitude = 44.698900, longitude = 10.629700, geo_key = 'seed' WHERE lower(name) = 'reggio emilia';
UPDATE sede SET latitude = 46.125600, longitude = 13.232800, geo_key = 'seed' WHERE lower(name) = 'tavagnacco';
UPDATE sede SET latitude = 46.067900, longitude = 11.121100, geo_key = 'seed' WHERE lower(name) = 'trento';
UPDATE sede SET latitude = 45.613900, longitude =  9.368900, geo_key = 'seed' WHERE lower(name) LIKE 'vimercate%';
