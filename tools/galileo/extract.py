#!/usr/bin/env python3
"""
Estrae dal listino Excel JGalileo (xlsm) i profili di calcolo e il catalogo moduli in tools/galileo/profiles.json.
Uso: python3 -I tools/galileo/extract.py "<listino_jgalileo.xlsm>" [tools/galileo/profiles.json]

Mercati e fogli (come indicato dal committente):
  COMMESSA E MACHINERY <- PRODUZIONE SU COMMESSA (+ CONSOCIATA PROD SU COMMESSA)
  PRODUZIONE IN SERIE  <- PRODUZ. DI SERIE (+ CONSOCIATA PROD DI SERIE) e MIGRAZIONE AZ. PRODUZIONE (+ MIG. AZ. CONSOCIATA PRODUZIONE)
  ALIMENTARE E GDO / VINICOLO <- ALIMENTARE VINICOLO (+ CONSOCIATA ALIMENTARE VINICOLO)
  MODA                 <- MODA (+ CONSOCIATA MODA)
  COMMERCIALE          <- COMMERCIALE (+ CONSOCIATA COMMERCIALE) e MIGRAZIONE AZ. COMMERCIALE (+ MIG. AZ. CONSOCIATA COMMERCIALE)
  AMMINISTRAZIONE      <- HOLDING AMMINISTRATIVA (+ CONSOCIATA HOLDING AMMINISTRAZI) e IMMOBILIARE (prezzo fisso fino a 3 utenti)
Il foglio RIEPILOGO e' ignorato (salvo scadenza/versione del listino). Catalogo, aree e vincoli dal foglio AGGIUNTA NUOVI MODULI.

Le regole di addebito di ogni modulo (colonne E e G dei fogli) sono ricavate dalle formule: restano cosi' fedeli al listino,
comprese le sue anomalie, elencate in "anomalie".
"""
import json, re, sys, datetime
import openpyxl

SRC = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else "tools/galileo/profiles.json"
wbf = openpyxl.load_workbook(SRC, keep_vba=True)
wbv = openpyxl.load_workbook(SRC, data_only=True)

# (foglio, mercato, tipo)
SHEETS = [
    ("PRODUZIONE SU COMMESSA", ["COMMESSA E MACHINERY"], "NEW"), ("CONSOCIATA PROD SU COMMESSA", ["COMMESSA E MACHINERY"], "CONS"),
    ("PRODUZ. DI SERIE", ["PRODUZIONE IN SERIE"], "NEW"), ("CONSOCIATA PROD DI SERIE", ["PRODUZIONE IN SERIE"], "CONS"),
    ("MIGRAZIONE AZ. PRODUZIONE", ["PRODUZIONE IN SERIE"], "MIG"), ("MIG. AZ. CONSOCIATA PRODUZIONE", ["PRODUZIONE IN SERIE"], "MIGCONS"),
    ("ALIMENTARE VINICOLO", ["ALIMENTARE E GDO", "VINICOLO"], "NEW"), ("CONSOCIATA ALIMENTARE VINICOLO", ["ALIMENTARE E GDO", "VINICOLO"], "CONS"),
    ("MODA", ["MODA"], "NEW"), ("CONSOCIATA MODA", ["MODA"], "CONS"),
    ("COMMERCIALE", ["COMMERCIALE"], "NEW"), ("CONSOCIATA COMMERCIALE", ["COMMERCIALE"], "CONS"),
    ("MIGRAZIONE AZ. COMMERCIALE", ["COMMERCIALE"], "MIG"), ("MIG. AZ. CONSOCIATA COMMERCIALE", ["COMMERCIALE"], "MIGCONS"),
    ("HOLDING AMMINISTRATIVA", ["AMMINISTRAZIONE"], "NEW"), ("CONSOCIATA HOLDING AMMINISTRAZI", ["AMMINISTRAZIONE"], "CONS"),
    ("IMMOBILIARE", ["AMMINISTRAZIONE"], "IMMOB"),
]
LABEL = {"NEW": "Nuova configurazione", "CONS": "Consociata", "MIG": "Migrazione", "MIGCONS": "Migrazione consociata", "IMMOB": "Immobiliare (fino a 3 utenti)"}
RESULT = {"PRODUZIONE SU COMMESSA": "L24", "PRODUZ. DI SERIE": "L24", "ALIMENTARE VINICOLO": "L24", "MODA": "L31", "COMMERCIALE": "L24",
          "HOLDING AMMINISTRATIVA": "L24", "IMMOBILIARE": "C30", "CONSOCIATA PROD SU COMMESSA": "N26", "CONSOCIATA PROD DI SERIE": "N26",
          "CONSOCIATA ALIMENTARE VINICOLO": "N26", "CONSOCIATA MODA": "N25", "CONSOCIATA COMMERCIALE": "N26", "CONSOCIATA HOLDING AMMINISTRAZI": "N26",
          "MIGRAZIONE AZ. COMMERCIALE": "N27", "MIG. AZ. CONSOCIATA COMMERCIALE": "N27", "MIGRAZIONE AZ. PRODUZIONE": "N27", "MIG. AZ. CONSOCIATA PRODUZIONE": "N27"}

def norm(v):
    return re.sub(r"\d+", "#", v) if isinstance(v, str) else None

def code_desc(b):
    if not isinstance(b, str):
        return None
    m = re.match(r"\s*([A-Za-z0-9_]+)\s*-\s*(.*)$", b.strip())
    if m:
        return m.group(1).upper(), m.group(2).strip()
    m = re.match(r"\s*(SMI[A-Za-z0-9_]+)\s+(.*)$", b.strip())     # es. "SMIIN intercompany"
    return (m.group(1).upper(), m.group(2).strip()) if m else None

PLAIN = {'=IF(D#="x",C#,"")': "D", '=IF((D#="x"),C#,"")': "D", '=IF((F#="x"),C#,"")': "F",
         '=IF(A#="CB","",(IF(D#="x",C#,"")))': "DFA", '=IF(A#="V#","",(IF(D#="x",C#,"")))': "DFA"}

def e_rule(ws, r):
    v = ws.cell(r, 5).value
    if v is None:
        return ""
    k = norm(v)
    if k in PLAIN:
        return PLAIN[k]
    m = re.fullmatch(r'=IF\(A\d+="(CB|V11)",IF\(\$N\$5=(\d),"",IF\(D\d+="x",C\d+,""\)\),IF\(D\d+="x",C\d+,""\)\)', v)
    if m:
        return "G" + m.group(2)                 # CB/V11: non addebitato se N5 == k (nuova configurazione); altrimenti come D
    if re.fullmatch(r'=IF\(\$N\$5=\d,"",IF\(D\d+="x",C\d+,""\)\)', v) or re.fullmatch(r'=IF\(\$J\$5=\d,"",IF\(D\d+="x",C\d+,""\)\)', v):
        return "CFG"                            # riga della configurazione (gestita a parte)
    m = re.fullmatch(r'=IF\(A\d+="CB",IF\(\$J\$5=\d,"",IF\(D\d+="x",C\d+,""\)\),IF\(D\d+="x",C\d+,""\)\)', v)
    if m:
        return "D"                              # IMMOBILIARE: moduli base, prezzo fisso a parte
    raise SystemExit("formula E non riconosciuta in %s riga %d: %s" % (ws.title, r, v))

def g_rule(ws, r):
    v = ws.cell(r, 7).value
    if v is None:
        return False
    if norm(v) != '=IF((F#="x"),C#,"")':
        raise SystemExit("formula G non riconosciuta in %s riga %d: %s" % (ws.title, r, v))
    return True

def sum_range(ws, col, r0=100):
    for r in range(r0, ws.max_row + 1):
        v = ws[f"{col}{r}"].value
        if isinstance(v, str):
            m = re.match(r"=SUM\(%s(\d+):%s(\d+)\)" % (col, col), v)
            if m:
                return int(m.group(1)), int(m.group(2))
    return None

def scan_tiers(ws, r0, r1, cf, ct, cp):
    t = []
    for r in range(r0, r1):
        to = ws.cell(r, ct).value
        if isinstance(to, (int, float)) and not isinstance(to, bool):
            fr = ws.cell(r, cf).value
            pct = ws.cell(r, cp).value
            t.append({"from": int(fr) if isinstance(fr, (int, float)) else 1, "to": None if to >= 1e9 else int(to), "pct": pct})
    return t

def module_rows(ws, prereq_col):
    out = []
    area = None
    for r in range(9, ws.max_row + 1):
        a, b, c = ws.cell(r, 1).value, ws.cell(r, 2).value, ws.cell(r, 3).value
        if a == "T":
            area = str(b).strip()
            continue
        if a in ("CB", "FA", "V11") and isinstance(b, str):
            cd = code_desc(b)
            pc = ws.cell(r, prereq_col)
            out.append({"r": r, "flag": a, "code": cd[0] if cd else None, "desc": cd[1] if cd else b.strip(), "raw": b.strip(),
                        "price": c if isinstance(c, (int, float)) else None, "area": area, "e": e_rule(ws, r), "g": g_rule(ws, r),
                        "prereq": (str(pc.value).strip() if pc.value is not None else None),
                        "comment": (pc.comment.text.split("\n", 1)[-1].strip() if pc.comment else None)})
    return out

anomalie = []

def build_profile(sheet, markets, kind):
    wf, wv = wbf[sheet], wbv[sheet]
    prereq_col = 9 if kind in ("CONS", "MIGCONS") or kind in ("MIG",) else 6
    rows = module_rows(wf, prereq_col)
    mods = [m for m in rows if m["code"]]
    cfg_row = [m for m in rows if not m["code"]][0]
    p = {"sheet": sheet, "markets": markets, "kind": kind, "label": LABEL[kind], "resultCell": RESULT[sheet]}
    if kind == "NEW":
        d = wv["M12"].value
        if sheet == "MODA":
            cfg = {"V9": wv["C10"].value, "V11": None}
        else:
            cfg = {"V9": wv["K5"].value, "V11": wv["K6"].value}
        p.update(discount=d, cfg=cfg, tiers={"MAIN": scan_tiers(wv, 14, 22, 8, 9, 13)})
        p["eLast"] = int(re.search(r"SUM\(E(\d+):E(\d+)\)", str(wf["L14"].value)).group(2))
        if sheet == "MODA":
            # nel foglio MODA gli utenti della 2a fascia non hanno il minimo a zero (O16 senza IF): con meno di 6 utenti il prezzo scende in proporzione
            p["proRataBelowFirst"] = True
            anomalie.append("MODA: sotto i 6 utenti il foglio riduce il prezzo (utenti negativi nella 2a fascia: 1 utente = 60%, 2 = 70%, 3 = 80%, 4 = 90% del prezzo base); replicato")
    elif kind == "IMMOB":
        p.update(discount=0, cfg={"V9": wv["C10"].value, "V11": None}, tiers={}, maxUsers=3, eLast=0)
    elif kind == "CONS":
        d = wv["P12"].value
        i6, i7, c10 = wv["I6"].value, wv["I7"].value, wv["C10"].value
        cfg = {"V9": i6 if i6 is not None else c10, "V11": i7}
        hdr = [r for r in range(30, 45) if wv.cell(r, 11).value and "NUOVI MODULI PER CONSOCIATA" in str(wv.cell(r, 11).value)][0]
        o14 = str(wf["O14"].value)
        nodisc = "IF(I5=2,SUM(E" in o14 and "(1-P12)" not in o14.split("IF(I5=2,")[1].split(",SUM")[0]
        p.update(discount=d, discountNew=wv.cell(hdr, 16).value, cfg=cfg, v11Discount=not nodisc, consDivisor=2,
                 tiers={"MAIN": scan_tiers(wv, 14, 20, 11, 12, 16), "NEWMOD": scan_tiers(wv, hdr + 2, hdr + 12, 11, 12, 16)})
        p["eLast"] = int(re.search(r"SUM\(E(\d+):E(\d+)\)", o14).group(2))
        p["aggiuntaSupported"] = "$N$5" in str(wf["E10"].value)
        if sheet == "CONSOCIATA MODA":
            p["cfg"]["V11"] = None
    else:                                   # MIG / MIGCONS
        m = re.search(r'IF\(D(\d+)="x",(\d+),(\d+)\)', str(wf["C10"].value))
        o14 = str(wf["O14"].value)
        p.update(discount=wv["P12"].value, discountNew=0.3, cfg={"V9": float(m.group(3)), "V11": float(m.group(2))},
                 consDivisor=2 if "/2" in str(wf["N27"].value) else 1,
                 migCaratterePct=float(re.search(r"\*([0-9.]+)", str(wf["N53"].value)).group(1)) * 100,
                 migVisualePct=float(re.search(r"\*([0-9.]+)", str(wf["N54"].value)).group(1)) * 100,
                 migV9Flat=float(re.search(r"K5=3,(\d+)", str(wf["N24"].value)).group(1)),
                 tiers={"MAIN": scan_tiers(wv, 14, 20, 11, 12, 16), "NEWMOD": scan_tiers(wv, 37, 43, 11, 12, 16), "ADDUSR": scan_tiers(wv, 47, 53, 11, 12, 16)})
        p["eLast"] = int(re.search(r"SUM\(E(\d+):E(\d+)\)", o14).group(2))
        p["v11Row"] = int(m.group(1))
        p["aggiuntaSupported"] = "$N$5" in str(wf["E10"].value)
    # G: righe conteggiate
    gr = sum_range(wf, "G")
    gfirst = gr[0] if gr else 10 ** 9
    if sheet == "CONSOCIATA MODA":
        gfirst = 12
    p["modules"] = []
    for m in mods:
        e = m["e"] if (m["r"] <= p["eLast"]) else ""
        g = bool(m["g"] and m["r"] >= gfirst)
        if m["price"] is not None and m["flag"] != "CB" and kind in ("NEW",) and not e:
            anomalie.append("%s: %s (%s) prezzo %s non addebitato (formula mancante nella colonna E)" % (sheet, m["code"], m["flag"], m["price"]))
        if m["price"] is not None and m["flag"] == "FA" and kind != "NEW" and not e and not g:
            anomalie.append("%s: %s prezzo %s non addebitato (formule E/G mancanti)" % (sheet, m["code"], m["price"]))
        if m["r"] > p["eLast"] and m["e"] and m["flag"] == "FA" and kind in ("MIG", "MIGCONS", "NEW", "CONS"):
            anomalie.append("%s: %s (riga %d) fuori dall'intervallo SUM della colonna E (ultima riga %d): non addebitato come modulo migrato/in comune" % (sheet, m["code"], m["r"], p["eLast"]))
        mod = {"code": m["code"], "flag": m["flag"], "price": m["price"], "e": e, "g": g, "row": m["r"], "area": m["area"], "desc": m["desc"]}
        for col, key, on in ((5, "ePrice", e), (7, "gPrice", g)):
            f = wf.cell(m["r"], col).value
            if isinstance(f, str) and on:
                mc = re.search(r"(?<![\$A-Z])C(\d+)", f)
                if mc and int(mc.group(1)) != m["r"]:
                    ref_price = wf.cell(int(mc.group(1)), 3).value
                    mod[key] = ref_price
                    anomalie.append("%s: %s (riga %d) %s addebita il prezzo della riga %s (%s) invece del proprio %s; replicato" %
                                    (sheet, m["code"], m["r"], "colonna E" if col == 5 else "colonna G", mc.group(1), ref_price, m["price"]))
        p["modules"].append(mod)
    p["_rows"] = mods
    return p

profiles = []
for sheet, markets, kind in SHEETS:
    profiles.append(build_profile(sheet, markets, kind))

# ---------------------------------------------------------------- catalogo da AGGIUNTA NUOVI MODULI
wa = wbf["AGGIUNTA NUOVI MODULI"]
master = {}
area = None
order = 0
for r in range(10, wa.max_row + 1):
    b, c = wa.cell(r, 2).value, wa.cell(r, 3).value
    if wa.cell(r, 1).value == "TOT":
        continue
    f = wa.cell(r, 2)
    fill = f.fill
    dark = fill and fill.fill_type == "solid" and fill.fgColor.type == "theme" and fill.fgColor.theme == 0 and round(fill.fgColor.tint or 0, 2) == -0.35
    if dark and c is None and isinstance(b, str):
        area = b.strip()
        continue
    cd = code_desc(b)
    if cd and isinstance(c, (int, float)):
        pc = wa.cell(r, 6)
        order += 10
        master[cd[0]] = {"code": cd[0], "desc": cd[1], "area": area, "price": c, "order": order, "aggPrereq": (str(pc.value).strip() if pc.value is not None else None),
                         "aggComment": (pc.comment.text.split("\n", 1)[-1].strip() if pc.comment else None)}

# ---------------------------------------------------------------- vincoli (prerequisiti)
def parse_prereq(text):
    """-> (tipo, [codici]) oppure None"""
    if not text:
        return None
    t = text.replace(",", " ").replace("-", " ")
    toks = t.split()
    any_ = any(x.lower() == "o" for x in toks)
    codes = [x.upper() for x in toks if x.lower() != "o" and re.fullmatch(r"[A-Za-z0-9_]+", x)]
    if not codes or text.strip().upper() == "VEDERE COMMENTO":
        return None
    return ("REQUIRES_ANY" if any_ else "REQUIRES", codes)

# priorita': 1) foglio AGGIUNTA NUOVI MODULI (colonna F, o relativo commento se «VEDERE COMMENTO»); 2) fogli di mercato.
# Se per gli stessi codici un'altra variante usa l'operatore «o», il vincolo e' «uno tra».
sheet_variants = {}
for p in profiles:
    for m in p["_rows"]:
        for t in (m["prereq"], m["comment"]):
            if t:
                sheet_variants.setdefault(m["code"], []).append(t)
prereq = {}
for code in set(master) | set(sheet_variants):
    mm = master.get(code, {})
    agg = [parse_prereq(t) for t in (mm.get("aggPrereq"), mm.get("aggComment")) if t]
    agg = [x for x in agg if x]
    oth = [parse_prereq(t) for t in sheet_variants.get(code, [])]
    oth = [x for x in oth if x]
    pick = agg[0] if agg else (oth[0] if oth else None)
    if not pick:
        continue
    if pick[0] == "REQUIRES":
        for x in agg + oth:
            if x[0] == "REQUIRES_ANY" and set(x[1]) == set(pick[1]):
                pick = x
                break
    prereq[code] = {"type": pick[0], "targets": pick[1]}
    diff = {(x[0], tuple(sorted(x[1]))) for x in oth} - {(pick[0], tuple(sorted(pick[1])))}
    if diff and agg:
        anomalie.append("Vincoli di %s: nel foglio AGGIUNTA NUOVI MODULI '%s %s', nei fogli di mercato anche %s (usato AGGIUNTA NUOVI MODULI)" %
                        (code, "o" if pick[0] == "REQUIRES_ANY" else "e", " ".join(pick[1]), "; ".join("%s %s" % (a, " ".join(b)) for a, b in sorted(diff))))
for p in profiles:
    del p["_rows"]

ver = wbv["RIEPILOGO"]
listino = {"name": ver["B3"].value, "version": str(ver["D3"].value), "validUntil": ver["E3"].value.date().isoformat()}
out = {"listino": listino, "master": master, "prereq": prereq, "profiles": profiles, "anomalie": sorted(set(anomalie))}
json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("profili", len(profiles), "moduli master", len(master), "vincoli", len(prereq), "anomalie", len(out["anomalie"]))
