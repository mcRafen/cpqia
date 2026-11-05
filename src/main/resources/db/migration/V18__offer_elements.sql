-- Pagina "Elementi" dell'offerta: componenti scelti (Power System, Galileo con mercato, Servizi SMITECH, Dipartimentali)
ALTER TABLE offer ADD COLUMN opt_power_system     BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN opt_smitech_services BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN opt_departmental     BOOLEAN NOT NULL DEFAULT TRUE,
                  ADD COLUMN galileo_application_id BIGINT REFERENCES application (id);
