-- Distanza cliente -> sede Sanmarco piu' vicina e spese di trasferta dell'offerta
ALTER TABLE sede     ADD COLUMN latitude  NUMERIC(9,6), ADD COLUMN longitude NUMERIC(9,6), ADD COLUMN geo_key VARCHAR(500);
ALTER TABLE customer ADD COLUMN latitude  NUMERIC(9,6), ADD COLUMN longitude NUMERIC(9,6), ADD COLUMN geo_key VARCHAR(500);
ALTER TABLE offer    ADD COLUMN distance_km    NUMERIC(8,1),
                     ADD COLUMN nearest_sede   VARCHAR(100),
                     ADD COLUMN travel_rate_id BIGINT REFERENCES travel_rate (id);
