-- Numero di dischi della configurazione Power System (predefinito 2, in mirroring)
ALTER TABLE offer ADD COLUMN power_disk_count INT NOT NULL DEFAULT 2 CHECK (power_disk_count >= 1);
