#!/usr/bin/env python3
"""
Valida il motore di calcolo contro il listino Excel originale.
Per ogni applicazione genera scenari (moduli + utenti + modalita'), li scrive in copie del foglio,
fa ricalcolare tutto a LibreOffice (headless) e confronta 'Listino di vendita' e 'Canone di manutenzione'
con pricing_ref.calc().  Scrive src/test/resources/golden.json con gli scenari verificati.

Uso: python3 -I tools/validate_engine.py "<listino.xlsm>" [APP ...]
"""
import json, os, random, subprocess, sys, shutil, glob
import openpyxl
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pricing_ref as P

SRC = sys.argv[1]
ONLY = set(sys.argv[2:])
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "test", "resources")
cat = json.load(open(os.path.join(HERE, "catalog.json")))

# celle di input per driver e cella modalita'
USER_CELLS = {"default": {"UTENTI": "C6"}, "CRMS": {"UTENTI": "C9"}, "B2B": {"UTENTI": "C8"}, "ECM3": {"UTENTI": "C9"},
              "JMES": {"UTENTI": "C6", "MACCHINE": "C7"}, "APS": {"UTENTI": "C6", "VISUALIZZATORI": "C7"},
              "JPM": {"ENTERPRISE": "C6", "USER": "C7"}, "JPA3": {"UTENTI": "C7"}, "EPM": {"UTENTI": "C10"}}
MODE_CELL = {"CRMS": "Z4", "B2B": "Z4", "ECM3": "Z4", "JPA3": "Z4", "EPM": "Z4"}
SEL_COL = {"EPM": "F"}
MODE_VAL = {"NEW": 1, "MIGR": 2, "ADD": 3, "VIEW": 1, "SENSE": 2, "POWERBI": 4}
CHECK = ["ICM", "TV", "AT", "PIM", "CRMJ", "CRMS", "B2B", "IOC", "JTCE", "BPB", "DQ", "DBM", "SCC", "JMES", "APS", "JPM", "ECM3", "JPA3", "BCF", "EPM"]
USERS = [1, 2, 4, 5, 6, 9, 10, 11, 15, 20, 21, 33, 40, 41, 55, 80, 81, 120, 201, 350, 500, 501, 900, 1001, 2500]

random.seed(7)
wb_path = {}
os.makedirs("/tmp/scen", exist_ok=True)
for f in glob.glob("/tmp/scen/*"):
    os.remove(f)
for f in glob.glob("/tmp/scen_out/*"):
    os.remove(f)

def find_ws(wb, title):
    for ws in wb:
        if ws.title.strip() == title:
            return ws

def row_of(ws, code, used):
    for r in range(1, 80):
        v = ws[f"B{r}"].value
        if isinstance(v, str) and v.strip() == code and r not in used:
            return r
    return None

scenarios = []
for a in cat:
    if a["code"] not in CHECK or (ONLY and a["code"] not in ONLY):
        continue
    wb = openpyxl.load_workbook(SRC)
    for _w in wb:
        _w._images = []; _w._charts = []
    ws = find_ws(wb, a["sheet"])
    cells = USER_CELLS.get(a["code"], USER_CELLS["default"])
    # riga di ogni modulo
    used = set(); rows = {}
    for i, m in enumerate(a["modules"]):
        if m["section"] == "LINK" or m["included"]:
            continue
        if a["code"] == "JPA3":
            continue
        r = row_of(ws, m["code"], used)
        if r:
            rows[i] = r; used.add(r)
    if a["code"] == "JPA3":
        rows = {0: 11, 1: 15, 2: 16}
        for k, r in enumerate(range(21, 30)):
            rows[4 + k] = r
    selectable = [i for i, m in enumerate(a["modules"]) if i in rows and not (a["code"] == "JMES" and m["code"].startswith("PLC")) and not (a["code"] == "JPM" and m["code"] == "SMIEV") and not (a["code"] == "ECM3" and m["code"] == "SMIDESOU")]
    modes = [m["code"] for m in a["modes"]] or [None]
    for n in range(int(os.environ.get("NSCEN", "14"))):
        mode = random.choice(modes)
        sel = {}
        groups_used = set()
        order = selectable[:]
        random.shuffle(order)
        for i in order:
            m = a["modules"][i]
            if random.random() < 0.6:
                if m["excl_group"]:
                    if m["excl_group"] in groups_used:
                        continue
                    groups_used.add(m["excl_group"])
                sel[i] = random.choice([1, 2, 3]) if m["qty_enabled"] else 1
        if not any(a["modules"][i]["group"] for i in sel):
            # almeno un modulo scalato
            cand = [i for i in selectable if a["modules"][i]["group"] and not a["modules"][i]["excl_group"]]
            if cand:
                sel[cand[0]] = 1
        if a["code"] == "JPA3" and 0 not in sel and mode != "ADD":
            sel[0] = 1                      # engine base sempre presente
        if a["code"] == "JPA3" and mode == "ADD":
            sel.pop(0, None)
        dq = {d["code"]: random.choice(USERS) for d in a["drivers"]}
        if a["code"] == "JMES":
            dq["MACCHINE"] = random.choice([0, 1, 3, 12, 30, 60])
        if a["code"] == "APS":
            dq["VISUALIZZATORI"] = random.choice([0, 3, 12])
        if a["code"] == "JPM":
            dq["USER"] = random.choice([1, 8, 15, 33, 70, 100])
        # scrittura nel foglio
        for i, r in rows.items():
            m = a["modules"][i]
            col = SEL_COL.get(a["code"], "E")
            if a["code"] == "JPA3":
                if i in (1, 2):                              # quantita'
                    ws[f"E{r}"].value = sel.get(i)
                    continue
                if i == 0:
                    ws["E11"].value = "X" if i in sel else None
                    continue
                ws[f"E{r}"].value = None; ws[f"F{r}"].value = None
                if i in sel:
                    ws[f"{'E' if mode != 'MIGR' else 'F'}{r}"].value = "X"
                continue
            if m["section"] == "SCALED" or m["section"] == "FIXED":
                ws[f"{col}{r}"].value = "X" if i in sel else None
        if a["code"] == "JPA3":
            ws["E11"].value = "X" if 0 in sel else None
        for d, cell in cells.items():
            ws[cell].value = dq.get(d, 0) or None
        if a["code"] in MODE_CELL and mode:
            ws[MODE_CELL[a["code"]]].value = (3 if (a["code"] == "EPM" and mode == "MIGR") else MODE_VAL[mode])
        # per default scrivi tutto
        fn = f"/tmp/scen/{a['code']}_{n}.xlsx"
        wb.save(fn)
        scenarios.append({"app": a["code"], "n": n, "mode": mode, "selected": {str(i): q for i, q in sel.items()},
                          "driver_qty": dq, "file": fn})
    print("scritti scenari", a["code"])

# ricalcolo LibreOffice
files = sorted(glob.glob("/tmp/scen/*.xlsx"))
os.makedirs("/tmp/scen_out", exist_ok=True)
for k in range(0, len(files), 25):
    subprocess.run(["soffice", "--headless", "--calc", "--convert-to", "xlsx", "--outdir", "/tmp/scen_out"] + files[k:k + 25],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=900)

def read_result(ws):
    lic = man = None
    for row in ws.iter_rows(min_row=1, max_row=60):
        for c in row:
            if isinstance(c.value, str):
                t = c.value.strip().lower()
                if t == "listino di vendita":
                    lic = ws.cell(row=c.row, column=c.column + 2).value
                if t.startswith("canone di manutenzione annuo"):
                    man = ws.cell(row=c.row, column=c.column + 2).value
    return lic, man

bad = 0; ok = 0; golden = []
byapp = {a["code"]: a for a in cat}
for s in scenarios:
    out = f"/tmp/scen_out/{s['app']}_{s['n']}.xlsx"
    wb = openpyxl.load_workbook(out, data_only=True)
    ws = find_ws(wb, byapp[s["app"]]["sheet"])
    xl_lic, xl_man = read_result(ws)
    if not isinstance(xl_lic, (int, float)) or not isinstance(xl_man, (int, float)):
        xl_lic = xl_man = None
    a = byapp[s["app"]]
    mine = P.calc(a, s["mode"], {int(i): q for i, q in s["selected"].items()}, s["driver_qty"])
    good = (xl_lic is not None and xl_man is not None and abs(mine["license"] - xl_lic) < 0.01 and abs(mine["maintenance"] - (xl_man or 0)) < 0.01)
    if good:
        ok += 1
        golden.append({"app": s["app"], "mode": s["mode"], "selected": s["selected"], "driver_qty": s["driver_qty"],
                       "expected_license": round(xl_lic, 2), "expected_maintenance": round(xl_man, 2)})
    else:
        bad += 1
        print("DIFF", s["app"], s["n"], s["mode"], s["selected"], s["driver_qty"], "excel", xl_lic, xl_man, "mine", round(mine["license"], 2), round(mine["maintenance"], 2))
print(f"OK {ok}  DIFF {bad}")
os.makedirs(OUT, exist_ok=True)
gp = os.path.join(OUT, "golden.json")
old = json.load(open(gp)) if os.path.exists(gp) else []
ran = {x["app"] for x in scenarios}
json.dump([g for g in old if g["app"] not in ran] + golden, open(gp, "w"), indent=1)
