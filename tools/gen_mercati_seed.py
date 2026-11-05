#!/usr/bin/env python3
"""Genera la migrazione V17 dai mercati Galileo del foglio Excel: 1a colonna = FAMIGLIA, 2a = CODICE, 3a = DESCRIZIONE.
Uso: python3 -I tools/gen_mercati_seed.py "../mercati.xlsx" > src/main/resources/db/migration/V17__seed_mercati_galileo.sql"""
import sys, openpyxl
ws = openpyxl.load_workbook(sys.argv[1], data_only=True).active
q = lambda s: str(s).strip().replace("'", "''")
print("-- GENERATO da tools/gen_mercati_seed.py a partire da 'mercati.xlsx' (famiglia, codice, descrizione); applicazioni attive")
n = 0
for r in ws.iter_rows(values_only=True):
    if not r or not r[0] or not r[1]:
        continue
    fam, code, descr = q(r[0]), q(r[1]), q(r[2] or r[1])
    n += 1
    print("INSERT INTO application (code, name, family, description, active, sort_order)\n"
          "    SELECT '%s', '%s', '%s', '%s', TRUE, %d WHERE NOT EXISTS (SELECT 1 FROM application WHERE code = '%s' AND deleted_at IS NULL);" % (code, descr, fam, descr, 2000 + n, code))
