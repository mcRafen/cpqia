#!/usr/bin/env python3
"""Trasforma il modello Word di esempio in template con segnaposto {{...}} (uso: make_template.py in.docx out.docx)"""
import sys, copy, re
import docx
from lxml import etree
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
q = lambda t: "{%s}%s" % (W, t)

d = docx.Document(sys.argv[1])
body = d.element.body

def make_run(rpr, text):
    r = etree.Element(q("r"))
    if rpr is not None:
        r.append(copy.deepcopy(rpr))
    t = etree.SubElement(r, q("t")); t.text = text
    t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    return r

# ---- 1. campi FILLIN complessi (titolo, cliente) e ASK
TOK_COMPLEX = ["{{TITOLO}}", None, "{{CLIENTE}}"]       # ordine: FILLIN titolo, ASK, FILLIN cliente
ci = 0
for p in list(body.iter(q("p"))):
    begins = [r for r in p.iter(q("fldChar")) if r.get(q("fldCharType")) == "begin"]
    instrs = "".join(i.text or "" for i in p.iter(q("instrText")))
    if not begins or ("FILLIN" not in instrs and "ASK" not in instrs):
        continue
    runs = [c for c in p if c.tag == q("r")]
    # run del risultato = quello con <w:t> tra separate e end
    state = None; result = None; first = None; last = None
    for r in runs:
        fc = r.find(q("fldChar"))
        if fc is not None and fc.get(q("fldCharType")) == "begin" and first is None:
            first = r
        if fc is not None and fc.get(q("fldCharType")) == "separate":
            state = "res"; continue
        if fc is not None and fc.get(q("fldCharType")) == "end":
            last = r; state = None; continue
        if state == "res" and r.find(q("t")) is not None:
            result = r
    tok = TOK_COMPLEX[ci]; ci += 1
    idx = list(p).index(first)
    rpr = result.find(q("rPr")) if result is not None else first.find(q("rPr"))
    # rimuovi tutti i run del campo
    in_field = False
    for r in runs:
        if r is first: in_field = True
        if in_field: p.remove(r)
        if r is last: break
    if tok:
        p.insert(idx, make_run(rpr, tok))

# ---- 2. fldSimple (indirizzo, p.iva, rif, data, numero)
simple_tokens = [["{{INDIRIZZO1}}", "{{INDIRIZZO2}}"], ["{{PIVA}}"], ["{{RIF}}"], ["{{DATA}}"], ["{{NUMERO}}"]]
si = 0
for fs in list(body.iter(q("fldSimple"))):
    toks = simple_tokens[si]; si += 1
    p = fs.getparent(); idx = list(p).index(fs)
    new = []
    runs = fs.findall(q("r"))
    for k, tk in enumerate(toks):
        src = runs[min(k, len(runs) - 1)]
        r = copy.deepcopy(src)
        for t in r.findall(q("t")): r.remove(t)
        t = etree.SubElement(r, q("t")); t.text = tk
        new.append(r)
    p.remove(fs)
    for k, r in enumerate(new):
        p.insert(idx + k, r)
assert ci == 3 and si == 5, (ci, si)

# ---- 3. EFORMA nel testo
for t in body.iter(q("t")):
    if t.text and "EFORMA" in t.text:
        t.text = t.text.replace("EFORMA", "{{CLIENTE_BREVE}}")

# ---- 4. tabella licenze
tbls = d.tables
lic = tbls[0]
rows = lic._tbl.findall(q("tr"))
for r in rows[3:]:
    lic._tbl.remove(r)
def set_cell_text(tc, text, keep_first_p=True):
    """lascia un solo paragrafo con un solo run contenente text"""
    ps = tc.findall(q("p"))
    for p in ps[1:]: tc.remove(p)
    p = ps[0]
    runs = p.findall(q("r"))
    rpr = runs[0].find(q("rPr")) if runs else None
    for r in runs: p.remove(r)
    p.append(make_run(rpr, text))
rowA, rowB = rows[1], rows[2]
tcsA = rowA.findall(q("tc")); tcsB = rowB.findall(q("tc"))
set_cell_text(tcsA[0], "{{L_CODICI}}")
set_cell_text(tcsA[1], "{{L_DESCRIZIONI}}")
set_cell_text(tcsA[2], "{{L_IMPORTO}}")
set_cell_text(tcsA[3], "{{L_MANUT}}")
set_cell_text(tcsB[1], "{{L_POSTI}}")

# ---- 5. totali
tot = tbls[1]
trs = tot._tbl.findall(q("tr"))
for tr, (a, b) in zip(trs, [("{{T_IMPORTO}}", "{{T_MANUT}}"), ("{{N_IMPORTO}}", "{{N_MANUT}}")]):
    tcs = tr.findall(q("tc"))
    set_cell_text(tcs[1], a); set_cell_text(tcs[2], b)

d.save(sys.argv[2])
txt = []
for p in d.paragraphs: txt.append(p.text)
for t in d.tables:
    for r in t.rows:
        for c in r.cells: txt.append(c.text)
alltxt = "\n".join(txt)
print("segnaposto trovati:", sorted(set(re.findall(r"\{\{\w+\}\}", alltxt))))
