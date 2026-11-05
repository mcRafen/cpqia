#!/usr/bin/env python3
"""Anteprima: riempie il template come fara' il generatore Java (stesso algoritmo) con un'offerta d'esempio."""
import sys, copy
import docx
from lxml import etree
W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
q = lambda t: "{%s}%s" % (W, t)
d = docx.Document(sys.argv[1])
body = d.element.body

def eur(v):
    s = f"{v:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")
    return "€ " + s

repl = {"{{TITOLO}}": "Progetto Discovery Quality - JPA", "{{CLIENTE}}": "EFORMA SRL", "{{CLIENTE_BREVE}}": "EFORMA",
        "{{INDIRIZZO1}}": "Via dell’Elettronica, 9", "{{INDIRIZZO2}}": "36061 Thiene (VI)", "{{PIVA}}": "04133090243",
        "{{RIF}}": "Enrico Strobbe", "{{DATA}}": "25 ottobre 2026", "{{NUMERO}}": "20261025.1ER"}
lines = [
    {"codes": ["SMIDBAS", "SMIDQRNC", "SMIDQRCL"], "descr": ["SMIDBAS - Modulo Base Qualità", "SMIDQRNC - Rapporti non conformità", "SMIDQRCL - Reclami"],
     "imp": 8000, "man": 1440, "posti": "Licenze Posti di Lavoro n. 10"},
    {"codes": ["SMIJPA", "SMIJPAF"], "descr": ["SMIJPA - Engine - JPA Synergy V300", "SMIJPAF - Designer - JPA Synergy V300 (x 2)"],
     "imp": 20500, "man": 3690, "posti": "Licenze Posti di Lavoro n. 10"},
]
disc = 20.0
# 1. testo semplice
for t in body.iter(q("t")):
    if t.text:
        for k, v in repl.items():
            if k in t.text: t.text = t.text.replace(k, v)

def set_runs_text(p, text):
    runs = p.findall(q("r"))
    runs[0].find(q("t")).text = text
    for r in runs[1:]: p.remove(r)

def fill_cell_lines(tc, values):
    ps = tc.findall(q("p")); proto = ps[0]
    for p in ps[1:]: tc.remove(p)
    set_runs_text(proto, values[0])
    prev = proto
    for v in values[1:]:
        np_ = copy.deepcopy(proto); set_runs_text(np_, v); prev.addnext(np_); prev = np_

def set_text(tc, text):
    p = tc.find(q("p")); runs = p.findall(q("r"))
    runs[0].find(q("t")).text = text
    for r in runs[1:]: p.remove(r)

tbl = d.tables[0]._tbl
rows = tbl.findall(q("tr")); rowA, rowB = rows[1], rows[2]
anchor = rowB
for ln in lines:
    a = copy.deepcopy(rowA); b = copy.deepcopy(rowB)
    ta = a.findall(q("tc")); tb = b.findall(q("tc"))
    fill_cell_lines(ta[0], ln["codes"]); fill_cell_lines(ta[1], ln["descr"])
    set_text(ta[2], eur(ln["imp"])); set_text(ta[3], eur(ln["man"])); set_text(tb[1], ln["posti"])
    anchor.addnext(a); a.addnext(b); anchor = b
tbl.remove(rowA); tbl.remove(rowB)

tot = d.tables[1]._tbl.findall(q("tr"))
ti, tm = sum(l["imp"] for l in lines), sum(l["man"] for l in lines)
def set_tot(tr, a, b):
    tcs = tr.findall(q("tc")); set_text(tcs[1], a); set_text(tcs[2], b)
set_tot(tot[0], eur(ti), eur(tm)); set_tot(tot[1], eur(ti * (1 - disc / 100)), eur(tm * (1 - disc / 100)))
d.save(sys.argv[2])
