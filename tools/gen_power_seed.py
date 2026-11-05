#!/usr/bin/env python3
"""Genera la migrazione V27 dai modelli IBM Power del foglio Excel (Modello, Tipo, Core, Tipo montaggio, Versione, CPW).
Uso: python3 -I tools/gen_power_seed.py "../Modelli Power.xlsx" > src/main/resources/db/migration/V27__seed_power_models.sql"""
import sys, openpyxl
ws = openpyxl.load_workbook(sys.argv[1], data_only=True).active
q = lambda s: str(s).strip().replace("'", "''")
print("-- GENERATO da tools/gen_power_seed.py a partire da 'Modelli Power.xlsx'")
n = 0
for i, r in enumerate(ws.iter_rows(min_row=2, values_only=True)):
    if not r or not r[0]:
        continue
    model, typ, cores, mount, ver, cpw = r[:6]
    n += 1
    print("INSERT INTO power_model (model, type, cores, mounting, version, cpw, sort_order) VALUES ('%s', '%s', %d, '%s', '%s', %d, %d);"
          % (q(model), q(typ), int(cores), q(mount).upper(), q(ver).upper(), int(cpw), n * 10))
