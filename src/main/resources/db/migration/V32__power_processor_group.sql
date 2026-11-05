-- Gruppo processore dei modelli Power System: 4 core = P05, 8 core = P10 (1 core: non definito)
UPDATE power_model SET processor_group = 'P05' WHERE cores = 4;
UPDATE power_model SET processor_group = 'P10' WHERE cores = 8;
