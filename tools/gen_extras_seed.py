#!/usr/bin/env python3
"""
Legge le tabelle di tools/originals/servizi-accessori.docx e genera
src/main/resources/db/migration/V4__extra_services.sql  (servizi accessori: schema + catalogo + selezioni per offerta).
Uso: python3 -I tools/gen_extras_seed.py
"""
import os, re
from decimal import Decimal
import docx
from docx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "originals", "servizi-accessori.docx")
DEST = os.path.join(HERE, "..", "src", "main", "resources", "db", "migration", "V4__extra_services.sql")

def money(s):
    """'€ 1.320,00' | '180 €' | '0,80 €' | '€ 0,5 cad.' -> Decimal (None se vuoto o testuale)"""
    m = re.search(r"\d[\d\.]*(?:,\d+)?", s or "")
    if not m or "ad hoc" in (s or "").lower():
        return None
    return Decimal(m.group(0).replace(".", "").replace(",", "."))

def cell_text(tc):
    return " ".join("".join(t.text or "" for t in p.iter(qn("w:t"))).strip() for p in tc.findall(qn("w:p"))).strip()

def table_rows(tbl):
    out = []
    for tr in tbl._tbl.findall(qn("w:tr")):
        out.append([cell_text(tc) for tc in tr.findall(qn("w:tc"))])
    return out

d = docx.Document(SRC)
T = d.tables
q = lambda s: "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"
num = lambda v: "NULL" if v is None else str(v)

services = [  # code, title, nome nella tabella riepilogo, testo "incluso"
    ("AUDIT", "Audit per conservazione digitale a norma", "AUDIT PER CONSERVAZIONE DIGITALE A NORMA", "COMPRESO"),
    ("DXC", "Discovery Xchange", "DISCOVERY XCHANGE", None),
    ("FIRMAE", "Firmae", "FIRMAE", None),
    ("CRC", "CRC Credit Recovery Control", "CRC", None),
]
# gruppo: (codice, servizio, titolo, selezione, obbligatorio, opzioni[])
groups = []
opts = {}

# --- Discovery Xchange: scaglioni
dxc = []
for r in table_rows(T[0])[1:]:
    key, label, onet, ann = r[1], r[2], r[3], r[4]
    adhoc = "ad hoc" in ann.lower()
    dxc.append(dict(key=key, article="SMIXC / SMIDESOU", label=label, onetime=money(onet), annual=None if adhoc else money(ann),
                    extra=None, adhoc=adhoc, adhoc_text=ann if adhoc else None, qty=False))
groups.append(("DXC_BAND", "DXC", "Scaglioni documenti (fatture contenute nei file XML o inviate/ricevute)", "ONE", True, dxc))
# --- Discovery Xchange: certificato di firma opzionale
cert = []
rr = table_rows(T[1])
cert.append(dict(key="SMIXCFDA", article="SMIXCFDA", label="Firma digitale automatica qualificata remota - importo annuo",
                 onetime=money(rr[1][2]), annual=money(rr[1][3]), extra=None, adhoc=False, adhoc_text=None, qty=False))
cert.append(dict(key="SMINHFQR", article="SMINHFQR", label="Riconoscimento tramite operatore (se l'utente non vuole usare SPID o CIE) - importo unitario una tantum",
                 onetime=money(rr[2][2]), annual=money(rr[2][3]), extra=None, adhoc=False, adhoc_text=None, qty=True))
groups.append(("DXC_CERT", "DXC", "Certificato di firma - opzionale", "MANY", False, cert))
# --- Firmae: startup (fisso) + fasce
rr = table_rows(T[2])
groups.append(("FIRMAE_START", "FIRMAE", "Startup e firma eIDAS", "FIXED", False, [
    dict(key="SMINHAT", article="SMINHAT", label="Attivazione servizio una tantum", onetime=money(rr[1][1]), annual=None, extra=None, adhoc=False, adhoc_text=None, qty=False),
    dict(key="SMINHSIG", article="SMINHSIG", label='Certificato di firma "sigillo" - importo annuo per singolo certificato/azienda attivata', onetime=None, annual=money(rr[2][1]), extra=None, adhoc=False, adhoc_text=None, qty=False),
]))
fb = []
for r in table_rows(T[4])[1:]:
    fb.append(dict(key=r[1], article="SMINHDAS", label=r[2], onetime=None, annual=money(r[3]), extra=money(r[4]), adhoc=False, adhoc_text=None, qty=False))
groups.append(("FIRMAE_BAND", "FIRMAE", "Fasce numero firme annue - eIDAS firma avanzata (APP + OTP)", "ONE", True, fb))
# --- CRC: attivazione (fisso), importazione storico (facoltativo, a quantita'), fasce
rr = table_rows(T[5])
groups.append(("CRC_START", "CRC", "Attivazione servizio", "FIXED", False, [
    dict(key="SMINHCRA", article="SMINHCRA", label="Attivazione servizio SaaS (una tantum)", onetime=money(rr[1][1]), annual=None, extra=None, adhoc=False, adhoc_text=None, qty=False)]))
groups.append(("CRC_IMPORT", "CRC", "Servizi SaaS facoltativi", "MANY", False, [
    dict(key="SMINHCRI", article="SMINHCRI", label="Importazione storico partite aperte (una tantum, per partita)", onetime=money(rr[2][1].split("cad")[0]), annual=None, extra=None, adhoc=False, adhoc_text=None, qty=True)]))
cb = []
for r in table_rows(T[6])[1:]:
    adhoc = "ad hoc" in r[3].lower()
    cb.append(dict(key=r[1], article="SMINHCRC", label=r[2], onetime=None, annual=None if adhoc else money(r[3]),
                   extra=None if adhoc else money(r[4]), adhoc=adhoc, adhoc_text=r[3] if adhoc else None, qty=False))
groups.append(("CRC_BAND", "CRC", "Fasce partite gestite all'anno (fatture emesse all'anno)", "ONE", True, cb))

out = ["-- GENERATO da tools/gen_extras_seed.py a partire da servizi-accessori.docx - servizi accessori (schema, catalogo, selezioni)", ""]
out.append("""CREATE TABLE extra_service (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    title        VARCHAR(150) NOT NULL,
    summary_name VARCHAR(150) NOT NULL,             -- nome nella tabella del riepilogo finale
    included_text VARCHAR(30),                      -- es. COMPRESO: servizio senza costo
    sort_order   INT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE extra_group (
    id          BIGSERIAL PRIMARY KEY,
    service_id  BIGINT NOT NULL REFERENCES extra_service (id) ON DELETE CASCADE,
    code        VARCHAR(30) NOT NULL UNIQUE,        -- usato nei segnaposto del Word
    title       VARCHAR(250) NOT NULL,
    selection   VARCHAR(5) NOT NULL CHECK (selection IN ('FIXED', 'ONE', 'MANY')),  -- tutte incluse / una sola riga / piu' righe
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE TABLE extra_option (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT NOT NULL REFERENCES extra_group (id) ON DELETE CASCADE,
    row_key      VARCHAR(30) NOT NULL,              -- lettera della fascia o codice articolo
    article_code VARCHAR(40),
    label        VARCHAR(500) NOT NULL,
    onetime      NUMERIC(14,2),
    annual       NUMERIC(14,2),
    extra_unit   NUMERIC(14,4),                     -- valore per unita' oltre soglia
    ad_hoc       BOOLEAN NOT NULL DEFAULT FALSE,
    ad_hoc_text  VARCHAR(100),
    qty_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order   INT NOT NULL DEFAULT 0,
    UNIQUE (group_id, row_key)
);
CREATE TABLE offer_extra_service (
    offer_id   BIGINT NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES extra_service (id),
    PRIMARY KEY (offer_id, service_id)
);
CREATE TABLE offer_extra_option (
    offer_id  BIGINT NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    option_id BIGINT NOT NULL REFERENCES extra_option (id),
    qty       INT NOT NULL DEFAULT 1,
    PRIMARY KEY (offer_id, option_id)
);
""")
sid = {}
for i, (code, title, summ, inc) in enumerate(services, 1):
    sid[code] = i
    out.append(f"INSERT INTO extra_service (id, code, title, summary_name, included_text, sort_order) VALUES ({i}, {q(code)}, {q(title)}, {q(summ)}, {q(inc)}, {i * 10});")
oid = 0
for gi, (gcode, scode, gtitle, sel, req, options) in enumerate(groups, 1):
    out.append(f"INSERT INTO extra_group (id, service_id, code, title, selection, required, sort_order) VALUES ({gi}, {sid[scode]}, {q(gcode)}, {q(gtitle)}, {q(sel)}, {str(req).upper()}, {gi * 10});")
    for oi, o in enumerate(options, 1):
        oid += 1
        out.append("INSERT INTO extra_option (id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled, sort_order) VALUES "
                   f"({oid}, {gi}, {q(o['key'])}, {q(o['article'])}, {q(o['label'])}, {num(o['onetime'])}, {num(o['annual'])}, {num(o['extra'])}, "
                   f"{str(o['adhoc']).upper()}, {q(o['adhoc_text'])}, {str(o['qty']).upper()}, {oi * 10});")
out.append("")
out.append("SELECT setval('extra_service_id_seq', (SELECT MAX(id) FROM extra_service));")
out.append("SELECT setval('extra_group_id_seq', (SELECT MAX(id) FROM extra_group));")
out.append("SELECT setval('extra_option_id_seq', (SELECT MAX(id) FROM extra_option));")
open(DEST, "w", encoding="utf8").write("\n".join(out) + "\n")
print("scritto", os.path.normpath(DEST), "- gruppi", len(groups), "opzioni", oid)
for g in groups:
    print(" ", g[0], g[3], [(o['key'], o['onetime'], o['annual'], o['extra']) for o in g[5]][:3], "...")

# --- stessa struttura di GET /offers/api/extras, per test e anteprime
import json
api = []
oid = 0
for i, (code, title, summ, inc) in enumerate(services, 1):
    gs = []
    for gi, (gcode, scode, gtitle, sel, req, options) in enumerate(groups, 1):
        if scode != code:
            continue
        os_ = []
        for o in options:
            oid_ = sum(len(g[5]) for g in groups[:gi - 1]) + options.index(o) + 1
            os_.append({k: v for k, v in dict(id=oid_, groupId=gi, rowKey=o["key"], articleCode=o["article"], label=o["label"],
                        onetime=float(o["onetime"]) if o["onetime"] is not None else None,
                        annual=float(o["annual"]) if o["annual"] is not None else None,
                        extraUnit=float(o["extra"]) if o["extra"] is not None else None,
                        adHoc=o["adhoc"], adHocText=o["adhoc_text"], qtyEnabled=o["qty"]).items() if v is not None})
        gs.append(dict(id=gi, serviceId=i, code=gcode, title=gtitle, selection=sel, required=req, options=os_))
    api.append({k: v for k, v in dict(id=i, code=code, title=title, summaryName=summ, includedText=inc, groups=gs).items() if v is not None})
dest = os.path.join(HERE, "..", "src", "test", "resources", "extras-api.json")
json.dump(api, open(dest, "w"), ensure_ascii=False, indent=1)
print("scritto", os.path.normpath(dest))
