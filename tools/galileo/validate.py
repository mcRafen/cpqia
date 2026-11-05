#!/usr/bin/env python3
"""Valida il motore di riferimento (ref.py) contro il ricalcolo LibreOffice del listino Excel.
Per ogni profilo genera scenari casuali (utenti, release, moduli in D/F, ...), li scrive in una copia del foglio, ricalcola e confronta.
Scrive tools/galileo/golden_<TIPO>.json con gli scenari verificati (usati dai test Java).
Uso: python3 -I tools/galileo/validate.py "<listino.xlsm>" <TIPO[,TIPO]> [N_PER_PROFILO]      TIPO = NEW|CONS|MIG|MIGCONS|IMMOB"""
import datetime, glob, json, os, random, subprocess, sys
import openpyxl
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ref

SRC = sys.argv[1]
KINDS = set(sys.argv[2].split(","))
NSC = int(sys.argv[3]) if len(sys.argv) > 3 else 6
HERE = os.path.dirname(os.path.abspath(__file__))
TMP = "/tmp/galileo_val"
os.makedirs(TMP + "/in", exist_ok=True)
for f in glob.glob(TMP + "/in/*") + glob.glob(TMP + "/out/*"):
    os.remove(f)
random.seed(20260930)
USERS = [1, 2, 3, 4, 5, 6, 9, 10, 11, 15, 20, 21, 33, 40, 41, 60, 80, 81, 100, 120, 121, 200]
data = ref.load_profiles()
profiles = [p for p in data["profiles"] if p["kind"] in KINDS]

wb_src = openpyxl.load_workbook(SRC)
scen = []
files = []
for p in profiles:
    sheet = p["sheet"]
    wb = openpyxl.load_workbook(SRC)
    for w in list(wb):
        w._images = []
        w._charts = []
        if w.title not in ("RIEPILOGO", sheet):
            wb.remove(w)
    wb["RIEPILOGO"]["E3"].value = datetime.datetime(2030, 1, 1)         # listino in corso di validita'
    base = wb[sheet]
    e10 = str(base["E10"].value)
    if '$N$5=2,""' in e10:                                              # aggiunta moduli: la formula originale da' #VALUE!
        base["E10"].value = e10.replace('$N$5=2,""', "$N$5=2,0")
    mods = p["modules"]
    has_n5 = ref.supports_aggiunta(p)
    for n in range(NSC):
        ws = wb.copy_worksheet(base)
        ws.title = "S%d" % n
        kind = p["kind"]
        U = random.choice(USERS) if kind != "IMMOB" else random.choice([0, 1, 2, 3, 4, 8])
        rel = random.choice(["V9", "V11"]) if p["cfg"].get("V11") is not None else "V9"
        inp = {"users": U, "release": rel, "D": [], "F": [], "addUsers": 0, "source": 0, "aggiunta": False}
        fa = [m for m in mods if m["flag"] == "FA" and m["price"] is not None]
        D = {m["code"] for m in random.sample(fa, random.randint(0, min(22, len(fa))))}
        F = set()
        if kind in ("CONS", "MIG", "MIGCONS"):
            cand = [m for m in mods if m["price"] is not None and m["code"] not in D and m["flag"] != "V11"]
            F = {m["code"] for m in random.sample(cand, random.randint(0, min(8, len(cand))))}
        cbs = {m["code"] for m in mods if m["flag"] == "CB" and m["code"] not in F}
        D |= cbs
        v11 = [m for m in mods if m["flag"] == "V11"]
        if rel == "V11":
            D |= {m["code"] for m in v11}
        if kind in ("MIG", "MIGCONS"):
            inp["source"], rel2 = random.choice([(1, "V9"), (2, "V9"), (1, "V11"), (2, "V11"), (3, "V11"), (3, "V9"), (0, "V11")])
            inp["release"] = rel = rel2
            if rel == "V11":
                D |= {m["code"] for m in v11}
            else:
                D -= {m["code"] for m in v11}
            inp["addUsers"] = random.choice([0, 0, 3, 12, 25, 45, 90])
        if has_n5 and n % 3 == 2:
            inp["aggiunta"] = True
        inp["D"], inp["F"] = sorted(D), sorted(F)
        ws["B7"].value = U or None
        if kind in ("NEW", "CONS") and p["cfg"].get("V11") is not None or kind in ("MIG", "MIGCONS", "CONS"):
            ws["I5"].value = (1 if rel == "V9" else (2 if kind == "CONS" else 4)) if kind != "NEW" else (1 if rel == "V9" else 4)
        elif kind == "CONS":
            ws["I5"].value = 1
        if kind in ("MIG", "MIGCONS"):
            ws["I5"].value = 1 if rel == "V9" else 4
            ws["K5"].value = inp["source"]
            ws["B8"].value = inp["addUsers"] or None
        if has_n5:
            ws["N5"].value = 2 if inp["aggiunta"] else 1
        if kind != "IMMOB":
            ws["D10"].value = "x"
        for m in mods:
            if m["code"] in D:
                ws.cell(m["row"], 4).value = "x"
            if m["code"] in F:
                ws.cell(m["row"], 6).value = "x"
        scen.append({"profile": p, "inp": inp, "sheet": "S%d" % n, "file": "%s/in/%s.xlsx" % (TMP, sheet.replace(" ", "_").replace(".", ""))})
    wb.remove(base)
    fn = "%s/in/%s.xlsx" % (TMP, sheet.replace(" ", "_").replace(".", ""))
    wb.save(fn)
    files.append(fn)
subprocess.run(["soffice", "--headless", "--calc", "--convert-to", "xlsx", "--outdir", TMP + "/out"] + files,
               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=900)
ok = bad = 0
golden = []
cache = {}
for s in scen:
    p = s["profile"]
    out = TMP + "/out/" + os.path.basename(s["file"])
    wbo = cache.setdefault(out, openpyxl.load_workbook(out, data_only=True))
    xl = wbo[s["sheet"]][p["resultCell"]].value
    mine = ref.calc(p, s["inp"])
    if mine is None:
        good = xl is None or isinstance(xl, str)
        exp = None
    else:
        good = isinstance(xl, (int, float)) and abs(xl - mine) < 0.01
        exp = round(xl, 2) if good else None
    if good:
        ok += 1
        golden.append({"sheet": p["sheet"], "inputs": s["inp"], "expected": exp})
    else:
        bad += 1
        i = s["inp"]
        print("DIFF", p["sheet"], "U", i["users"], i["release"], "src", i["source"], "add", i["addUsers"], "agg", i["aggiunta"], "D", len(i["D"]), "F", len(i["F"]), "excel", xl, "mine", mine)
print("OK", ok, "DIFF", bad)
json.dump(golden, open(os.path.join(HERE, "golden_%s.json" % "_".join(sorted(KINDS))), "w"), ensure_ascii=False)
