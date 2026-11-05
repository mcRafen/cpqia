#!/usr/bin/env python3
"""Elenco sedi.xlsx -> src/main/resources/db/migration/V8__seed_sedi.sql   (uso: python3 -I tools/gen_sedi_seed.py "<Elenco sedi.xlsx>")"""
import os, sys
import openpyxl
src = sys.argv[1]
dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "db", "migration", "V8__seed_sedi.sql")
ws = openpyxl.load_workbook(src).active
q = lambda s: "'" + str(s).strip().replace("'", "''") + "'"
out = ["-- GENERATO da tools/gen_sedi_seed.py a partire da 'Elenco sedi.xlsx'", ""]
n = 0
for r in ws.iter_rows(min_row=2, values_only=True):
    if not r or r[1] is None:
        continue
    order, name, street, civ, cap, prov = r[:6]
    out.append("INSERT INTO sede (sort_order, name, street, street_number, postal_code, province) VALUES "
               f"({int(order)}, {q(name)}, {q(street)}, {q(int(civ) if isinstance(civ, (int, float)) else civ)}, {q(str(int(cap)).zfill(5))}, {q(str(prov).upper())});")
    n += 1
open(dest, "w", encoding="utf8").write("\n".join(out) + "\n")
print("scritto", os.path.normpath(dest), n, "sedi")
