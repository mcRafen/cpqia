#!/usr/bin/env python3
"""
Estrae il catalogo (applicazioni, moduli, prezzi, fasce utenti, vincoli) dal listino Excel
e produce tools/catalog.json + src/main/resources/db/migration/V2__seed_catalog.sql

Uso:  python3 -I tools/build_catalog.py "<listino.xlsm>"

I fogli con schema standard sono letti in automatico; i fogli con logica speciale
(JPA3, EPM, JMES, APS, JPM, ECM3, XC, FIRMAE, CRC, ELEMENT, SMITECH, CONNETTORI, BARCODEFLOW)
sono descritti a mano in SPECIAL, leggendo i valori dalle celle dell'Excel (nessun prezzo e' hard-coded
tranne dove la cella e' una formula).  Fogli esclusi per scelta: RIEPILOGO, JPA (non piu' vendibile).
"""
import json, re, sys, os
import openpyxl

SRC = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
wb = openpyxl.load_workbook(SRC)          # formule
INF = 10**9

EXCLUDE = {"RIEPILOGO", "JPA"}

# sheet title (strip) -> (code, family, display name)
APPS = {
 "ICM RACCOLTA ORDINI": ("ICM", "4WORDS", "ICM Raccolta Ordini"),
 "APP TENTATA VENDITA": ("TV", "4WORDS", "App Tentata Vendita"),
 "ASSISTENZA TECNICA": ("AT", "4WORDS", "Assistenza Tecnica"),
 "PIM": ("PIM", "4WORDS", "PIM"),
 "CRM JGAL": ("CRMJ", "4WORDS", "CRM JGalileo"),
 "CRM SYN": ("CRMS", "4WORDS", "CRM Synergy"),
 "B2B": ("B2B", "4WORDS", "B2B"),
 "APP iOC": ("IOC", "4WORDS", "App Clienti iOC"),
 "CONNETTORI": ("CONN", "4WORDS", "Connettori"),
 "JTCE CPQ": ("JTCE", "TCE", "JTCE / CPQ"),
 "EPM": ("EPM", "NEXT BI", "EPM"),
 "BUSINESS PLACE BUDGET": ("BPB", "NEXT BI", "Business Place Budget"),
 "DISCOVERY QUALITY": ("DQ", "DQ", "Discovery Quality"),
 "JMES": ("JMES", "FACTORY", "JMES"),
 "APS": ("APS", "FACTORY", "APS"),
 "DBM": ("DBM", "FACTORY", "DBM"),
 "SCC": ("SCC", "FACTORY", "SCC"),
 "JPA3": ("JPA3", "JPA/JPM", "JPA3 (JPA Synergy V300)"),
 "JPM": ("JPM", "JPA/JPM", "JPM"),
 "SMITECH": ("SMITECH", "SMITECH", "SMITECH"),
 "ELEMENT": ("ELEMENT", "ELEMENT", "ELEMENT"),
 "XC": ("XC", "XC/ECM/FIRMAE", "XC"),
 "ECM3": ("ECM3", "XC/ECM/FIRMAE", "ECM3"),
 "BARCODEFLOW": ("BCF", "XC/ECM/FIRMAE", "BarcodeFlow"),
 "FIRMAE": ("FIRMAE", "XC/ECM/FIRMAE", "FIRMAE"),
 "CRC": ("CRC", "CRC", "CRC"),
}
# applicazioni che richiedono verifica manuale rispetto alla logica Excel
REVIEW = {"EPM", "FIRMAE", "CRC", "XC", "BCF", "CONN", "ELEMENT", "JMES", "JPM"}

def ws_of(title):
    for ws in wb:
        if ws.title.strip() == title:
            return ws
    raise KeyError(title)

def val(ws, ref):
    return ws[ref].value

def num(v):
    return float(v) if isinstance(v, (int, float)) else None

# --------------------------------------------------------------------------- commenti
def comments(ws):
    out = {}
    for row in ws.iter_rows():
        for c in row:
            if c.comment:
                t = re.sub(r"={3,}\s*ID#\S+\s+[^\n(]*\(\d{4}-\d{2}-\d{2} [\d:]+\)", "", c.comment.text)
                t = re.sub(r"\s+", " ", t).strip()
                out[(c.row, c.column_letter)] = t
    return out

# --------------------------------------------------------------------------- lettura standard
def parse_tiers(ws, hdr_row, pct_col="N", kcol="K", icol="I", jcol="J", max_rows=8):
    """fasce: righe sotto l'intestazione 'Da/A' dove K inizia con fino/da/oltre"""
    tiers = []
    r = hdr_row + 1
    while r < hdr_row + 1 + max_rows:
        k = val(ws, f"{kcol}{r}")
        if isinstance(k, str) and re.match(r"(?i)\s*(fino|da|oltre|\d+\s+macchin)", k):
            frm = val(ws, f"{icol}{r}")
            to = val(ws, f"{jcol}{r}")
            frm = int(frm) if isinstance(frm, (int, float)) else (tiers[-1]["to"] + 1 if tiers else 1)
            to = INF if (not isinstance(to, (int, float)) or to > 10**7) else int(to)
            pct = num(val(ws, f"{pct_col}{r}"))
            tiers.append({"from": frm, "to": to, "label": k.strip(), "pct": pct, "unit_by_mode": {}})
            r += 1
        else:
            break
    return tiers

def parse_standard(ws, code, name, family, user_cell="C6", header_row=8, tier_hdr=8,
                   fixed_marker="MODULI A PREZZO FISSO"):
    cm = comments(ws)
    app = {"code": code, "name": name, "family": family, "sheet": ws.title.strip(),
           "maintenance_pct": 18.0, "modes": [], "drivers": [], "groups": [], "modules": [],
           "notes": [], "review": code in REVIEW}
    ulabel = val(ws, f"B{int(re.sub(r'[A-Z]', '', user_cell))}") or "Numero utenti"
    lic = re.search(r"(SMI\w+LIC)", str(ulabel))
    app["drivers"].append({"code": "UTENTI", "label": "Numero utenti", "license_code": lic.group(1) if lic else None,
                           "unit": "utenti"})
    tiers = parse_tiers(ws, tier_hdr)
    app["groups"].append({"code": "MOD", "label": "Moduli per utente", "driver": "UTENTI", "fixed_base": None,
                          "base_mode": "TIER1", "tiers": tiers})
    section = "SCALED"
    sort = 0
    last = ws.max_row
    for r in range(header_row + 1, min(last, 80) + 1):
        b, c, d, e = (val(ws, f"B{r}"), val(ws, f"C{r}"), val(ws, f"D{r}"), val(ws, f"E{r}"))
        if isinstance(b, str) and fixed_marker in b.upper():
            section = "FIXED"
            continue
        if isinstance(b, str) and b.strip().upper().startswith("ATTENZIONE"):
            if not b.strip().upper().startswith("ATTENZIONE MODULO"):
                break
        if isinstance(e, str) and e.strip().lower().startswith("tot"):
            if section == "SCALED":
                continue
        # riga modulo: serve una descrizione
        if not isinstance(c, str) or c.strip() in ("DESCRIZIONE",):
            continue
        if isinstance(b, str) and b.strip().upper().startswith("ATTENZIONE MODULO"):
            app["notes"].append(b.strip())
            continue
        mcode = (str(b).strip() if b else None)
        desc = c.strip()
        if not mcode:
            m = re.match(r"^(SMI\w+)\s+(.*)$", desc)
            if m:
                mcode, desc = m.group(1), m.group(2)
        if not mcode:
            continue
        if mcode.startswith("Presente nel listino"):
            continue
        price = num(d)
        link = None
        if isinstance(d, str) and re.search(r"(?i)collegamento|vedi", d):
            link = d.strip()
        module = {"code": mcode, "description": desc, "section": section,
                  "group": "MOD" if section == "SCALED" else None,
                  "prices": {"STD": price if price is not None else 0.0},
                  "kind": "LICENSE", "qty_enabled": False, "excl_group": None,
                  "default_selected": isinstance(e, str) and e.strip().upper() == "X",
                  "maintenance_pct": None, "link": link, "included": price is None and link is None,
                  "note": cm.get((r, "C")) or cm.get((r, "B")) or cm.get((r, "D")), "rules": [], "sort": sort}
        sort += 10
        app["modules"].append(module)
    return app

# --------------------------------------------------------------------------- vincoli (da commenti, curati)
def R(app, code, rtype, target, msg, sev="ERROR"):
    for m in app["modules"]:
        if m["code"] == code:
            m["rules"].append({"type": rtype, "target": target, "message": msg, "severity": sev})
            return
    raise KeyError(f"{app['code']}: modulo {code} non trovato per regola")

def apply_rules(a):
    c = a["code"]
    if c == "ICM":
        R(a, "SMIWCP", "REQUIRES", "SMIWPS4", "Prerequisito obbligatorio SMIWPS4 (Manager controllo e distribuzione App)")
        R(a, "SMIWCO", "REQUIRES", "SMIWCP", "Prerequisito obbligatorio SMIWCP (Catalogo prodotti)")
        R(a, "SMIWCO", "REQUIRES", "SMIWOR", "Se il cliente ha Jgalileo e' necessario SMIWOR come basket di raccolta per gli ordini da app. Non necessario (opzionale) se l'app deve inviare ordini ad altri ERP", "WARNING")
        R(a, "SMIWCX", "REQUIRES", "SMIWCP", "Se selezionato Note visita, CRM obbligatorio; va calcolato anche SMIWCP", "ERROR")
    elif c == "TV":
        R(a, "SMIGTV", "REQUIRES", "SMIWPS4", "Prerequisito obbligatorio SMIWPS4")
    elif c == "AT":
        R(a, "SMIWAT", "REQUIRES", "SMIWOR", "Prerequisito la presenza del modulo SMIWOR se cliente con Galileo (prezzo in bundle)", "WARNING")
        R(a, "SMIWMAN", "REQUIRES", "SMIWAT", "Prerequisito SMIWAT")
    elif c == "PIM":
        R(a, "SMIWCS", "REQUIRES", "SMIWPS4", "Prerequisito obbligatorio SMIWPS4")
    elif c == "CRMJ":
        R(a, "SMIWCXS", "REQUIRES", "SMIWPS4", "Prerequisito SMIWPS4")
        R(a, "CRMMAIL", "REQUIRES", "CRMWEBL", "Prerequisito CRMWEBL")
    elif c == "CRMS":
        R(a, "SMIWCXS", "REQUIRES", "SMIWPS4", "Prerequisito SMIWPS4")
        R(a, "CRMMAIL", "REQUIRES", "SMICRSYN", "Prerequisito SMICRSYN")
    elif c == "B2B":
        R(a, "SMIWOR", "INFO", None, "Jgalileo B2B (R24): uno e' alternativo all'altro", "WARNING")
        R(a, "SMIWCR", "REQUIRES", "SMIWOR", "Prerequisito SMIWOR")
    elif c == "IOC":
        R(a, "SMIWCC", "REQUIRES", "SMIWPS4", "Prerequisito SMIWPS4")
        R(a, "SMIWCC", "REQUIRES", "SMIWOR", "Prerequisito SMIWOR (listino B2B)")
    elif c == "JTCE":
        R(a, "SMITCE", "REQUIRES", "SMITWEB", "Da vendere su nuovo cliente con SMITWEB", "WARNING")
        R(a, "SMITWEB", "REQUIRES", "SMITCE", "Licenza applicabile singolarmente solo in caso di migrazione dal vecchio TCE; per un nuovo cliente prerequisito SMITCE", "WARNING")
        R(a, "SMITC3D", "REQUIRES", "SMITCE", "Prerequisiti SMITCE + SMITWEB")
        R(a, "SMITC3D", "REQUIRES", "SMITWEB", "Prerequisiti SMITCE + SMITWEB")
        R(a, "SMITCQUO", "INFO", None, "Nuova piattaforma dedicata alla preventivazione semplice e configurata: da analizzare con BU TCE", "WARNING")
        R(a, "SMITCPRV", "INFO", None, "Prodotto obsoleto in fase di sostituzione con CPQ: da prevedere solo previo contatto con BU TCE", "WARNING")
    elif c == "BPB":
        R(a, "SMIBPSPG", "REQUIRES", "SMIBPBDG", "Prerequisito Business Place Budget SMIBPBDG + Contabilita' analitica SMIAN")
        R(a, "SMIBPACQ", "REQUIRES", "SMIBPBDG", "Prerequisito SMIBPBDG Business Place Budget")
        R(a, "SMIBPINV", "REQUIRES", "SMIBPBDG", "Prerequisito SMIBPBDG + Spese Generali")
        R(a, "SMIBPINV", "REQUIRES", "SMIBPSPG", "Prerequisito SMIBPBDG + Spese Generali")
    elif c == "DQ":
        for k in ("SMIDQRCL", "SMIDQRNC", "SMIDQAZN"):
            R(a, k, "INFO", None, "Prerequisito per il funzionamento di JPA Synergy V300", "WARNING")
        R(a, "SMIDQBK", "INFO", None, "Vendita previa analisi della BU", "WARNING")
    elif c == "JMES":
        R(a, "SMIJM", "INFO", None, "Prereq. SMIPC da rilasciare con JMES se collegato a Jgalileo - obbligatorio", "WARNING")
        R(a, "SMIJMDIN", "INFO", None, "Modulo obbligatorio se inserito il numero di macchine da collegare", "WARNING")
    elif c == "APS":
        R(a, "SMIJAPSM", "REQUIRES_ANY", "SMIJAPS,SMIJAPSP,SMIJAPSA", "Prerequisito JAPS (Standard, Professional o Advanced)")

# --------------------------------------------------------------------------- SPECIAL
def tune_standard(a, ws):
    c = a["code"]
    # DBM: unico modulo, prezzo in D9; moduli senza E='X' di default
    if c == "DQ":
        for m in a["modules"]:
            if m["code"] in ("(EX SMIDWKF)", "SMIECREP", "SMIEQNC") or m["link"]:
                m["section"] = "LINK"; m["group"] = None
    if c in ("AT", "IOC", "B2B", "JTCE", "CRMS"):
        for m in a["modules"]:
            if m["link"]:
                m["section"] = "LINK"; m["group"] = None
    # iOC: la riga 13 e' percentuale orfana (0.1), ignorata come nel listino (fasce a rr.9-12)
    return a

def two_modes(a, key_codes, label1="Nuova installazione", label2="In migrazione"):
    a["modes"] = [{"code": "NEW", "label": label1, "default": True},
                  {"code": "MIGR", "label": label2, "default": False}]

def build():
    apps = []
    for title, (code, fam, name) in APPS.items():
        ws = ws_of(title)
        if code in SPECIAL:
            a = SPECIAL[code](ws, code, name, fam)
        else:
            user_cell, hdr = ("C6", 8)
            if code == "CRMS": user_cell, hdr = ("C9", 11)
            if code == "B2B": user_cell, hdr = ("C8", 10)
            if code == "ECM3": user_cell, hdr = ("C9", 11)
            a = parse_standard(ws, code, name, fam, user_cell=user_cell, header_row=hdr, tier_hdr=hdr)
            tune_standard(a, ws)
        apply_rules(a)
        apps.append(a)
    return apps

SPECIAL = {}

def special(code):
    def deco(f):
        SPECIAL[code] = f
        return f
    return deco

def mod(code, desc, price, section="FIXED", group=None, kind="LICENSE", qty=False, excl=None, default=False,
        mpct=None, note=None, link=None, sort=0, prices=None):
    return {"code": code, "description": desc, "section": section, "group": group,
            "prices": prices or {"STD": price}, "kind": kind, "qty_enabled": qty, "excl_group": excl,
            "default_selected": default, "maintenance_pct": mpct, "link": link, "included": False,
            "note": note, "rules": [], "sort": sort}

def base_app(ws, code, name, fam):
    return {"code": code, "name": name, "family": fam, "sheet": ws.title.strip(), "maintenance_pct": 18.0,
            "modes": [], "drivers": [], "groups": [], "modules": [], "notes": [], "review": code in REVIEW}

# ---- CRM SYN e B2B: parametri per modalita'
@special("CRMS")
def crms(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam, user_cell="C9", header_row=11, tier_hdr=11)
    tune_standard(a, ws)
    a["modes"] = [{"code": "NEW", "label": "Nuova installazione", "default": True},
                  {"code": "MIGR", "label": "In migrazione", "default": False},
                  {"code": "ADD", "label": "Funzionalita' aggiuntiva", "default": False}]
    for m in a["modules"]:
        if m["code"] == "SMICRSYN":
            m["prices"] = {"NEW": 12000.0, "MIGR": 6000.0, "ADD": 0.0}   # D12: =IF(Z4=1,12000,IF(Z4=2,6000,0))
            m["default_selected"] = True; m["included"] = False
    return a

@special("B2B")
def b2b(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam, user_cell="C8", header_row=10, tier_hdr=9)
    tune_standard(a, ws)
    a["modes"] = [{"code": "NEW", "label": "Nuova installazione", "default": True},
                  {"code": "MIGR", "label": "In migrazione", "default": False}]
    for m in a["modules"]:
        if m["code"] == "SMIWOR":
            m["prices"] = {"NEW": 10000.0, "MIGR": 5000.0}               # D11
            m["default_selected"] = True; m["included"] = False
        if m["code"] == "SMIWCMS":
            m["section"] = "LINK"; m["group"] = None
    # i tier B2B partono da riga 10 (intestazione fasce alla riga 9 con Da/A)
    # righe 12/13 senza codice: "SMIWSW Galileo Stampe WEB", "SMIWIN Galileo Interrogazioni WEB" (inclusi nel base)
    return a

@special("DBM")
def dbm(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam)
    for m in a["modules"]:
        m["default_selected"] = True
    return a

@special("ECM3")
def ecm3(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam, user_cell="C9", header_row=11, tier_hdr=10)
    tune_standard(a, ws)
    a["modes"] = [{"code": "NEW", "label": "Nuova installazione", "default": True},
                  {"code": "MIGR", "label": "Migrazione", "default": False}]
    a["modules"] = [m for m in a["modules"] if m["code"] not in ("SMIDESOU",)]
    for m in a["modules"]:
        if m["code"] in ("SMIECM3", "SMIECM3B", "SMIECM3E"):
            z = {"SMIECM3": 5, "SMIECM3B": 6, "SMIECM3E": 7}[m["code"]]
            p = num(ws[f"Z{z}"].value)
            m["prices"] = {"NEW": p, "MIGR": p / 2}
            m["excl_group"] = "EDIZIONE"
        if m["code"] == "SMIDWBFL":
            m["section"] = "LINK"; m["group"] = None
    # fasce numero pagine (canone annuo + 100 una tantum)  rr.31-41
    for r in range(31, 42):
        pages, price = ws[f"C{r}"].value, ws[f"E{r}"].value
        mm = mod("SMIDESOU", f"Canone annuo fascia fino a {int(pages):,} pagine".replace(",", "."), 0.0,
                 section="FIXED", kind="ANNUAL", excl="FASCIA_PAGINE", sort=1000 + r,
                 prices={"STD": float(price)})
        mm["onetime"] = 100.0
        a["modules"].append(mm)
    return a

@special("CONN")
def conn(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["notes"].append("Modulo non va ad utenti: prezzi a modulo, manutenzione 18%")
    for i, r in enumerate((9, 10)):
        a["modules"].append(mod(ws[f"B{r}"].value.strip(), ws[f"C{r}"].value.strip(), num(ws[f"D{r}"].value), sort=i * 10))
    return a

@special("JMES")
def jmes(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    cm = comments(ws)
    a["drivers"] = [{"code": "UTENTI", "label": "Numero utenti", "license_code": "SMIJMESLIC", "unit": "utenti"},
                    {"code": "MACCHINE", "label": "Numero macchine", "license_code": "SMIJMDINLIC", "unit": "macchine"}]
    ut = parse_tiers(ws, 9)
    mac = parse_tiers(ws, 18)
    for t in mac:
        t["label"] = t["label"]
    a["groups"] = [
        {"code": "MOD", "label": "Moduli per utente", "driver": "UTENTI", "fixed_base": None, "base_mode": "TIER1", "tiers": ut},
        {"code": "MAC", "label": "Collegamento macchine", "driver": "MACCHINE", "fixed_base": {"STD": num(ws["M19"].value)},
         "base_mode": "TIER1", "tiers": mac}]
    for i, r in enumerate((10, 11, 12, 13)):
        b, c, d = ws[f"B{r}"].value, ws[f"C{r}"].value, ws[f"D{r}"].value
        m = mod(str(b).strip(), str(c).strip(), num(d) or 0.0, section="SCALED", group="MOD", sort=i * 10,
                default=(r in (10, 11)), note=cm.get((r, "C")))
        a["modules"].append(m)
    a["modules"].append(mod("SMIECREP", "ECM3 Repository", None, section="LINK", link="collegamento listino ECM3", sort=50))
    a["modules"].append(mod("SMIJMTRX", "Jmetrix", num(ws["D24"].value), note=cm.get((24, "C")), sort=60))
    a["modules"].append(mod("SMISDV", "DataViz - Reportistica avanzata", num(ws["D25"].value), note=cm.get((25, "C")), sort=70))
    # PLC Schneider: indicativi, senza manutenzione (F50 = M28 + F48, solo totale generale)
    for r, c in ((30, "PLC-M221-DI09"), (35, "PLC-M221-DI25"), (41, "PLC-M221-DI41"), (46, "PLC-TM251-MASTER")):
        desc = " ".join(str(ws[f"B{r}"].value).split())[:160]
        a["modules"].append(mod(c, "Schneider: " + desc, num(ws[f"D{r}"].value), kind="LICENSE", mpct=0.0,
                                note="Listino Schneider indicativo - chiedere quotazione puntuale", sort=100 + r))
    return a

@special("APS")
def aps(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam, user_cell="C6", header_row=9, tier_hdr=9)
    cm = comments(ws)
    a["drivers"].append({"code": "VISUALIZZATORI", "label": "Utenti visualizzatori", "license_code": "SMIJAPSVIS", "unit": "utenti"})
    a["groups"].append({"code": "VIS", "label": "Utenti visualizzatori", "driver": "VISUALIZZATORI", "fixed_base": None,
                        "base_mode": "LINEAR",
                        "tiers": [{"from": 1, "to": INF, "label": "per utente visualizzatore", "pct": None,
                                   "unit_by_mode": {"STD": num(ws["L18"].value)}}]})
    for m in a["modules"]:
        if m["code"] in ("SMIJAPS", "SMIJAPSP", "SMIJAPSA"):
            m["excl_group"] = "EDIZIONE_APS"
    return a

@special("JPM")
def jpm(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    cm = comments(ws)
    a["drivers"] = [{"code": "ENTERPRISE", "label": "Utenti Enterprise", "license_code": "SMIJPM-ENT", "unit": "utenti"},
                    {"code": "USER", "label": "Utenti User", "license_code": "SMIJPM-USR", "unit": "utenti"}]
    ent = parse_tiers(ws, 6)
    usr = parse_tiers(ws, 14)
    a["groups"] = [
        {"code": "MOD", "label": "Moduli per utente Enterprise", "driver": "ENTERPRISE", "fixed_base": None, "base_mode": "TIER1", "tiers": ent},
        {"code": "USR", "label": "Licenze User", "driver": "USER", "fixed_base": {"STD": num(ws["M15"].value)}, "base_mode": "TIER1", "tiers": usr}]
    for i, r in enumerate(range(10, 15)):
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), num(ws[f"D{r}"].value),
                                section="SCALED", group="MOD", default=(r == 10), sort=i * 10, note=cm.get((r, "C"))))
    m = mod("SMIEV", "JPM Project monitoring", num(ws["D15"].value), sort=60,
            note="Nel listino Excel questo modulo NON e' incluso nei totali (F16=SUM(F10:F14)): verificare")
    a["modules"].append(m)
    a["modules"].append(mod("SMICAL", "Sinergy - integrazione calendari Exchange", num(ws["D23"].value), sort=70))
    return a

@special("JPA3")
def jpa3(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["modes"] = [{"code": "NEW", "label": "Nuova installazione", "default": True},
                  {"code": "MIGR", "label": "Migrazione", "default": False},
                  {"code": "ADD", "label": "Funzionalita' o flusso aggiuntivo", "default": False}]
    a["drivers"] = [{"code": "UTENTI", "label": "Numero utenti nominali", "license_code": "SMIJPALIC", "unit": "utenti"}]
    # prezzi unitari fasce: AC (nuova) / AD (migrazione), righe 6..10 ; fasce righe 11..15 colonne H,I,J
    tiers = [{"from": 1, "to": 1, "label": "Fino a 1 utente", "pct": None, "unit_by_mode": {}}]
    for k, r in enumerate(range(11, 16)):
        tiers.append({"from": int(ws[f"H{r}"].value), "to": INF if r == 15 else int(ws[f"I{r}"].value),
                      "label": str(ws[f"J{r}"].value).strip(), "pct": None,
                      "unit_by_mode": {"NEW": num(ws[f"AC{6+k}"].value), "MIGR": num(ws[f"AD{6+k}"].value), "ADD": num(ws[f"AD{6+k}"].value)}})
    a["groups"] = [{"code": "ENG", "label": "Engine JPA Synergy V300 + utenti", "driver": "UTENTI", "fixed_base": None,
                    "base_mode": "TIER1", "tiers": tiers}]
    # Engine con prezzo in base alla modalita' (D11: 6000 / 3000 / 0)
    a["modules"].append(mod("SMIJPA", "Engine - JPA Synergy V300", None, section="SCALED", group="ENG",
                            default=True, sort=0, prices={"NEW": num(ws["Z5"].value), "MIGR": num(ws["AA5"].value), "ADD": 0.0}))
    # moduli aggiuntivi con quantita': Engine aggiuntivo 4000 e Designer 5000 (2500 in migrazione)
    a["modules"].append(mod("SMIJPA", "Engine - JPA V300 aggiuntivo (motore aggiuntivo)", num(ws["D15"].value), qty=True, sort=10,
                            note="MODULO ENGINE = motore aggiuntivo e DESIGNER"))
    a["modules"][-1]["code"] = "SMIJPA-ADD"
    a["modules"][-1]["print_code"] = "SMIJPA"
    a["modules"].append(mod("SMIJPAF", "Designer - JPA Synergy V300", None, qty=True, sort=20,
                            prices={"NEW": 5000.0, "MIGR": 2500.0, "ADD": 5000.0}))
    a["modules"].append(mod("SMIECREP", "ECM3 Repository", None, section="LINK", link="collegamento listino ECM3", sort=30))
    for i, r in enumerate(range(21, 30)):
        d = num(ws[f"D{r}"].value)
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), None, sort=100 + i * 10,
                                prices={"NEW": d, "MIGR": d / 2, "ADD": d}))
    return a

@special("EPM")
def epm(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["modes"] = [{"code": "VIEW", "label": "View", "default": False},
                  {"code": "SENSE", "label": "Sense", "default": True},
                  {"code": "MIGR", "label": "Migrazione da View a Sense", "default": False},
                  {"code": "POWERBI", "label": "Power BI", "default": False}]
    a["notes"].append("Listino EPM: prerequisito licenze Qlik. Il listino Excel usa tre gruppi di calcolo (moduli View/Sense/Migrazione, moduli 'a nuovo' solo Sense, Power BI): qui sono modellati con gruppi dedicati")
    a["drivers"] = [{"code": "UTENTI", "label": "Numero utenti", "license_code": None, "unit": "utenti"}]
    pcts = {}
    tiers = []
    for r in range(13, 18):
        pass
    # fasce: colonne J,K,L righe 13-17; percentuali in colonna O
    tdef = [(1, 5), (6, 10), (11, 20), (21, 40), (41, INF)]
    labels = ["fino a 5 utenti", "da 6 a 10 utenti", "da 11 a 20 utenti", "da 21 a 40 utenti", "oltre i 41 utenti"]
    def tiers_from(first_row):
        # le percentuali di ogni gruppo di calcolo sono in colonna O (righe first_row..first_row+3): cella vuota = 0%
        pct = [None] + [(num(ws[f"O{r}"].value) or 0.0) for r in range(first_row, first_row + 4)]
        return [{"from": f, "to": t, "label": l, "pct": p, "unit_by_mode": {}} for (f, t), l, p in zip(tdef, labels, pct)]
    a["groups"] = [
        {"code": "BASE", "label": "Moduli View/Sense/Migrazione", "driver": "UTENTI", "fixed_base": None, "base_mode": "TIER1", "tiers": tiers_from(14)},
        {"code": "NUOVO", "label": "Moduli a nuovo (solo Sense)", "driver": "UTENTI", "fixed_base": None, "base_mode": "TIER1", "tiers": tiers_from(22)},
        {"code": "PBI", "label": "Moduli Power BI", "driver": "UTENTI", "fixed_base": None, "base_mode": "TIER1", "tiers": tiers_from(30)}]
    if num(ws["O25"].value) is None:
        a["notes"].append("ANOMALIA LISTINO: per i moduli 'a nuovo' la % della fascia oltre i 41 utenti (cella O25 del listino) e' vuota, quindi 0%: ripetuta cosi' com'e'. Correggere la fascia in Anagrafica se e' una dimenticanza")
    # prezzi: Z=VIEW, AA=SENSE, AB=MIGRAZIONE, riga lista modulo r=13..29 -> side row r-6
    def side(r):
        z = ws[f"Z{r}"].value; aa = ws[f"AA{r}"].value; ab = ws[f"AB{r}"].value
        sense = num(aa)
        view = num(z)
        migr = (sense / 2 if isinstance(ab, str) and "/2" in ab else sense)
        return view, sense, migr
    for i, r in enumerate(range(13, 30)):
        code = str(ws[f"B{r}"].value).strip()
        desc = str(ws[f"D{r}"].value).strip()
        view, sense, migr = side(r - 6)
        only_sense = r >= 18 and r not in (20,) and view is None
        prices = {}
        if view is not None: prices["VIEW"] = view
        prices["SENSE"] = sense
        prices["MIGR"] = migr
        grp = "BASE" if r <= 17 or r == 20 else "NUOVO"
        m = mod(code, desc, None, section="SCALED", group=grp, sort=i * 10, prices=prices)
        a["modules"].append(m)
    for i, r in enumerate((34, 35)):
        desc = str(ws[f"D{r}"].value).strip()
        p = num(ws[f"AC{r-27}"].value)
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), desc, None, section="SCALED", group="PBI", sort=300 + i * 10,
                                prices={"POWERBI": p},
                                note="Progetto ad hoc su cliente, contattare la BU" if r == 35 else None))
    a["modules"].append(mod("SMIMCE", "Estrattore dati", num(ws["E41"].value), sort=400,
                            note="Per clienti che non hanno la BI Sanmarco. Prevedere 2 ore di attivita' per configurazione"))
    return a

@special("SMITECH")
def smitech(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["modes"] = [{"code": "P05", "label": "Classe software P05", "default": True},
                  {"code": "P10", "label": "Classe software P10", "default": False}]
    for i, r in enumerate(range(10, 15)):
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), None, sort=i * 10,
                                prices={"P05": num(ws[f"L{r}"].value), "P10": num(ws[f"M{r}"].value)}))
    return a

@special("ELEMENT")
def element(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["modes"] = [{"code": "NO", "label": "Senza collegamento a JGalileo", "default": True},
                  {"code": "SI", "label": "Con collegamento a JGalileo", "default": False}]
    for i, r in enumerate(range(10, 13)):
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), None, sort=i * 10,
                                prices={"NO": num(ws[f"L{r}"].value), "SI": num(ws[f"M{r}"].value)}))
    # pacchetti: canone annuo (100%, senza 18%); i tre pacchetti operativi sono alternativi
    for i, r in enumerate(range(18, 22)):
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), num(ws[f"D{r}"].value),
                                kind="ANNUAL", excl=("PACCHETTO_OPE" if r <= 20 else None), sort=100 + i * 10))
    return a

@special("XC")
def xc(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["notes"].append("Moduli di servizio con link informativo; moduli XC/Conservazione 'Vedere offerta dedicata nella intranet aziendale'")
    for i, r in enumerate(range(9, 13)):
        a["modules"].append(mod(str(ws[f"B{r}"].value).strip(), str(ws[f"C{r}"].value).strip(), 0.0, kind="LICENSE",
                                link=str(ws[f"D{r}"].value), sort=i * 10, note="Listino non presente nel foglio: vedere link / offerta dedicata"))
        a["modules"][-1]["section"] = "LINK"
    for i, r in enumerate(range(20, 25)):
        code_ = str(ws[f"B{r}"].value).strip()
        kind = "ANNUAL" if code_ in ("SMIDESOUUG", "SMIXCDOW") else "LICENSE"      # F28: utente aggiuntivo e download massivo = canone
        a["modules"].append(mod(code_, str(ws[f"C{r}"].value).strip(), num(ws[f"D{r}"].value), kind=kind, sort=100 + i * 10,
                                mpct=(0.0 if kind == "ANNUAL" else None)))
    nso = [m for m in a["modules"] if m["code"] == "SMINSO"][0]
    nso["onetime"] = 250.0      # E26: attivazione 250 se Nodo Smistamento Ordini
    return a

@special("BCF")
def bcf(ws, code, name, fam):
    a = parse_standard(ws, code, name, fam, user_cell="C6", header_row=8, tier_hdr=8)
    for m in a["modules"]:
        if m["code"] == "SMIDWBFL":
            m["section"] = "FIXED"; m["group"] = None
            m["prices"] = {"STD": num(ws["D22"].value) or 1000.0}
            m["note"] = "Barcode Flow - riconoscimento codice a barre (valorizzare 4 ore di attivita' tecnica)"
        # D14 'Vedi listino Docuwave' : duplicato del fisso r.22
    a["modules"] = [m for m in a["modules"] if not (m["code"] == "SMIDWBFL" and m["section"] == "SCALED")]
    a["modules"] = [m for m in a["modules"] if not (m["code"] == "SMIDWBFL" and m["link"])]
    return a

@special("FIRMAE")
def firmae(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    a["notes"].append("Le fasce eIDAS/FEQ sono modellate come canone annuo (alternative per tipo di firma). I valori 'extra fascia' sono riportati nelle note")
    s = 0
    def add(m):
        nonlocal s
        m["sort"] = s; s += 10; a["modules"].append(m)
    add(mod("SMINHAT", "Attivazione (una tantum, solo nuovo cliente)", num(ws["D9"].value), kind="ONETIME"))
    add(mod("SMINHSIG", "Certificato di firma 'sigillo' SMI (annuale)", num(ws["D10"].value), kind="ANNUAL"))
    def bands(first, last, code_, label, excl):
        for r in range(first, last + 1):
            n = ws[f"C{r}"].value; p = num(ws[f"D{r}"].value)
            extra = ws[f"E{r}"].value
            ex = extra if isinstance(extra, (int, float)) else None
            add(mod(code_, f"{label} - fascia fino a {int(n):,} firme/anno".replace(",", "."), p, kind="ANNUAL", excl=excl,
                    note=(f"Valore extra fascia: {ex} euro/firma" if ex is not None else None)))
    bands(15, 31, "SMINHDAS", "Firma eIDAS avanzata OTP + APP", "FIRMAE_DAS")
    bands(34, 50, "SMINHDAO", "Firma eIDAS OTP + APP + SMS (SMS inclusi)", "FIRMAE_DAO")
    for r in range(55, 59):
        n = ws[f"C{r}"].value
        d = ws[f"D{r}"].value
        p = float(d) if isinstance(d, (int, float)) else float(str(d).strip("=").replace("*", " ").split()[0]) * float(str(d).strip("=").replace("*", " ").split()[1])
        add(mod("SMINHFEQ", f"Firma FEQ one-shot (CIE/SPID) - {int(n)} firme", p, kind="ONETIME", excl="FIRMAE_FEQ"))
    bands(63, 79, "SMINHFAU", "Firma FEQ con certificato", "FIRMAE_FAU")
    add(mod("SMINHFQA", "Certificato triennale FEQ (fatturazione annuale), per certificato", num(ws["D81"].value), kind="ANNUAL", qty=True))
    add(mod("SMINHFQR", "Riconoscimento tramite operatore (una tantum), per riconoscimento", num(ws["D82"].value), kind="ONETIME", qty=True))
    add(mod("SMINHEDT", "Modulo documenti editabili (annuale)", num(ws["D86"].value) or 0.0, kind="ANNUAL",
            note="Importo non valorizzato nel listino Excel: da definire"))
    add(mod("SMINHTOT", "Postazioni TOTEM - fino a 1 device (annuale)", num(ws["D88"].value) or 0.0, kind="ANNUAL", excl="FIRMAE_TOTEM",
            note="Importo non valorizzato nel listino Excel: da definire"))
    add(mod("SMINHTOT", "Postazioni TOTEM - fino a 5 device (annuale)", num(ws["D89"].value) or 0.0, kind="ANNUAL", excl="FIRMAE_TOTEM",
            note="Importo non valorizzato nel listino Excel: da definire"))
    for r in (94, 95, 96):
        add(mod(str(ws[f"B{r}"].value).strip(), "API " + str(ws[f"C{r}"].value).replace("API ", "").strip(), num(ws[f"D{r}"].value), excl="FIRMAE_API"))
    return a

@special("CRC")
def crc(ws, code, name, fam):
    a = base_app(ws, code, name, fam)
    s = 0
    for r in range(7, 17):
        n = ws[f"C{r}"].value
        txt = str(n).strip()
        a["modules"].append(mod("SMINHCRC/" + str(ws[f"B{r}"].value).split("/")[1].strip(),
                                f"Canone annuo fascia fino a {txt} fatture emesse/anno", num(ws[f"D{r}"].value), kind="ANNUAL",
                                excl="CRC_FASCIA", sort=s, note=f"Partita extra fascia: {ws[f'E{r}'].value} euro"))
        s += 10
    a["modules"].append(mod("SMINHCRC/Z", "Quotazione ad hoc", 0.0, kind="ANNUAL", excl="CRC_FASCIA", sort=s,
                            note="Quotazione ad hoc: indicare l'importo a mano")); s += 10
    a["modules"].append(mod("SMINHCRA", "Attivazione servizio una tantum", num(ws["D21"].value), kind="ONETIME", sort=s)); s += 10
    a["modules"].append(mod("SMINHCRI", "Importazione partite storiche aperte (per partita)", num(ws["D25"].value), kind="ONETIME", qty=True, sort=s))
    return a

# --------------------------------------------------------------------------- output
def main():
    apps = build()
    with open(os.path.join(HERE, "catalog.json"), "w", encoding="utf8") as f:
        json.dump(apps, f, ensure_ascii=False, indent=1)
    print("apps", len(apps), "modules", sum(len(a["modules"]) for a in apps))
    for a in apps:
        print(f"{a['code']:8} mod={len(a['modules']):3} groups={[(g['code'], len(g['tiers'])) for g in a['groups']]} modes={[m['code'] for m in a['modes']]}")

if __name__ == "__main__":
    main()
