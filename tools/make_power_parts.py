#!/usr/bin/env python3
"""Modelli Word della sezione Power System: trasforma gli originali (tools/originals/power/) in modelli con segnaposto.
  tabelle-power-hardware.docx: riga prototipo con {{PWH_CODE}} {{PWH_DESC}} {{PWH_QTY}} (una riga per ogni voce hardware)
  tabelle-power-manutenzione.docx: {{PW_PREZZO_LISTINO}} e {{PW_PREZZO_NETTO}} nei totali del Power
  tabelle-power-software.docx: riga prototipo con {{PWS_CODE}} {{PWS_DESC}} {{PWS_QTY}} (gruppo SW + prodotti IBM i)
Uso: python3 -I tools/make_power_parts.py <cartella_originali> <cartella_output>"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx
import make_parts as mp

def make_hardware(src, out, prefix="PWH"):
    d = docx.Document(src)
    tbl = d.tables[0]
    trs = mp.rows(tbl)
    for tr in trs[2:]:
        tbl._tbl.remove(tr)                    # una sola riga prototipo
    tcs = mp.cells(trs[1])
    mp.set_cell(tcs[0], "{{%s_CODE}}" % prefix)
    mp.set_cell(tcs[1], "{{%s_DESC}}" % prefix)
    mp.set_cell(tcs[2], "{{%s_QTY}}" % prefix)
    # il modello originale evidenzia in giallo i campi da compilare e ha l'altezza riga fissa: con piu' righe nella cella il testo verrebbe tagliato
    for tc in tcs:
        for hl in list(tc.iter(mp.qn("w:highlight"))):
            hl.getparent().remove(hl)
    for h in trs[1].iter(mp.qn("w:trHeight")):
        h.set(mp.qn("w:hRule"), "atLeast")
    d.save(out)

def make_manutenzione(src, out):
    """tabella manutenzione/totali: «TOTALE POWER SYSTEM» -> {{PW_PREZZO_LISTINO}}, «TOTALE POWER SYSTEM A VOI RISERVATO» -> {{PW_PREZZO_NETTO}}"""
    d = docx.Document(src)
    for t in d.tables:
        for tr in mp.rows(t):
            tcs = mp.cells(tr)
            if len(tcs) < 2:
                continue
            label = mp.ptext(mp.cell_pars(tcs[0])[0]).upper()
            if label.startswith("MANUTENZIONE HARDWARE 7X24"):
                # «MANUTENZIONE HARDWARE 7x24 – » completato con i mesi di manutenzione scelti in offerta
                mp.set_cell(tcs[0], mp.ptext(mp.cell_pars(tcs[0])[0]).rstrip() + " {{PW_MESI_MANUTENZIONE}} MESI")
            elif label.startswith("TOTALE POWER SYSTEM A VOI RISERVATO"):
                mp.set_cell(tcs[1], "{{PW_PREZZO_NETTO}}")
            elif label.startswith("TOTALE POWER SYSTEM"):
                mp.set_cell(tcs[1], "{{PW_PREZZO_LISTINO}}")
    d.save(out)

if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    make_hardware(os.path.join(src, "tabelle-power-hardware.docx"), os.path.join(out, "tabelle-power-hardware.docx"))
    make_hardware(os.path.join(src, "tabelle-power-software.docx"), os.path.join(out, "tabelle-power-software.docx"), "PWS")
    make_manutenzione(os.path.join(src, "tabelle-power-manutenzione.docx"), os.path.join(out, "tabelle-power-manutenzione.docx"))
    print("scritto", out)
