-- Creazione database e utente PostgreSQL per un'istanza CPQIA (eseguire come superuser, es. psql -U postgres -f setup-postgres.sql)
-- Lo schema e il catalogo vengono creati da Flyway al primo avvio del WAR (db/migration/V1, V2).
CREATE ROLE cpqia LOGIN PASSWORD 'CAMBIAMI';
CREATE DATABASE cpqia OWNER cpqia ENCODING 'UTF8' LC_COLLATE 'it_IT.UTF-8' LC_CTYPE 'it_IT.UTF-8' TEMPLATE template0;
-- Se la collation it_IT non e' installata usare: CREATE DATABASE cpqia OWNER cpqia ENCODING 'UTF8';
