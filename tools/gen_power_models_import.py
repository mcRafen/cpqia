#!/usr/bin/env python3
"""Genera la migrazione di importazione dei modelli IBM Power Systems da 'Modelli_IBM_Power_Systems.xlsx'.

Uso:  python3 tools/gen_power_models_import.py Modelli_IBM_Power_Systems.xlsx > src/main/resources/db/migration/V89__import_power_models.sql

Colonne del foglio:  A generazione -> Versione | B modello -> Codice | C tipo-modello (non usata) | D -> Tipo macchina | E -> Modello | F -> Fascia
                     G «Non importare» (VERO = riga esclusa) | H «Attivo» (informativa: i record si importano comunque NON attivi).
Le colonne D ed E sono formule: si ricavano da C come fa Excel.
I record si importano con attivo = FALSO; se tipo macchina + modello esistono gia' si aggiornano solo Codice e Fascia.
"""
import sys
import openpyxl

VERSIONS = {'POWER11': 'POWER 11', 'POWER10': 'POWER 10', 'POWER9': 'POWER 9', 'POWER8': 'POWER 8',
            'POWER7+': 'POWER 7+', 'POWER7': 'POWER 7', 'POWER6': 'POWER 6'}


def q(s):
    return "'" + str(s).replace("'", "''") + "'"


def main(path):
    ws = openpyxl.load_workbook(path).active
    out = ["-- GENERATO da tools/gen_power_models_import.py a partire da 'Modelli_IBM_Power_Systems.xlsx'",
           "-- Modelli gia' presenti (stesso tipo macchina + modello): si completano solo Codice e Fascia. Gli altri si inseriscono NON attivi."]
    n = skipped = 0
    for r in range(2, ws.max_row + 1):
        skip = ws.cell(r, 7).value
        if skip is True or str(skip or '').strip().upper() in ('VERO', 'TRUE', 'SI', 'SÌ', '1'):
            skipped += 1
            continue
        gen = ws.cell(r, 1).value
        if isinstance(gen, str) and gen.startswith('='):                 # es. «=F60»: la generazione e' la colonna F
            gen = ws.cell(int(gen[2:]), 6).value
        code = ws.cell(r, 2).value
        full = ws.cell(r, 3).value
        band = ws.cell(r, 6).value or ''
        if not (gen and code and full):
            continue
        mt, _, mo = str(full).partition('-')                               # come le formule delle colonne D ed E
        version = VERSIONS[str(gen).replace(' ', '').upper()]
        n += 1
        match = f"lower(machine_type) = lower({q(mt)}) AND lower(model) = lower({q(mo)}) AND deleted_at IS NULL"
        out.append(f"UPDATE power_model SET code = {q(code)}, band = {q(band)} WHERE {match} AND code = '';")
        out.append("INSERT INTO power_model (machine_type, model, type, version, code, band, active, sort_order) "
                   f"SELECT {q(mt)}, {q(mo)}, '', {q(version)}, {q(code)}, {q(band)}, FALSE, {1000 + n * 10} "
                   f"WHERE NOT EXISTS (SELECT 1 FROM power_model WHERE {match});")
    sys.stderr.write(f"{n} righe elaborate, {skipped} escluse (colonna G «Non importare»)\n")
    print("\n".join(out))


main(sys.argv[1])
