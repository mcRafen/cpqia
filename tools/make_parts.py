#!/usr/bin/env python3
"""
Trasforma i file Word originali (tools/originals/) in modelli con segnaposto per il generatore:
  servizi-accessori.docx, riepilogo-finale.docx, riepilogo-finale-servizi-accessori.docx  (manutenzione.docx e' statico)
Uso: python3 -I tools/make_parts.py <cartella_originali> <cartella_output> [<altra_cartella_output> ...]

Segnaposto (tutti in un'unica "run" di testo):
  [[SA:<SERVIZIO>]]                         inizio sezione di un servizio accessorio (AUDIT, DXC, FIRMAE, CRC); rimossi in generazione
  {{X:<GRUPPO>:<RIGA>}}                      "X" se la riga e' selezionata
  {{P:<GRUPPO>:<RIGA>:<CAMPO>[:S]}}          LABEL | ONETIME | ANNUAL | EXTRA | TOTAL (":S" = formato "180 €")
  {{Q:<GRUPPO>:<RIGA>}}                      quantita' scelta (o riga di sottolineatura se vuota)
  {{RF_*}}                                   riepilogo finale
  {{SA_NAME}} {{SA_UT}} {{SA_CAN}}           riga prototipo del riepilogo servizi accessori
  {{ATT_STIMA}}                              intestazione della colonna quantita': sotto "STIMA" (a capo) "GG" / "ORE"
  {{ATT_INT_TARIFFA}}                        intestazione della terza colonna ("TARIFFA" solo con il flag Dettaglio importi attivita', altrimenti vuota)
  {{APP_TITLE}}                              riga prototipo con il nome dell'applicazione di attivita.docx
  {{ATT_APP}}                                riga-fascia prototipo (titolo del gruppo di attivita') di attivita.docx
  {{ATT_DESC}} {{ATT_ORE}} {{ATT_TARIFFA}} {{ATT_VALORE}}   riga prototipo di una attivita' sistemistica
  {{ATT_SUB_NAME}} {{ATT_SUB_ORE}} {{ATT_SUB_VALORE}}   riga di sub-totale per applicazione
  {{ATT_TOT_ORE}} {{ATT_TOT}}                totale ore e importo della riga "TOTALE IMPORTO STIMATO"
"""
import copy, os, re, sys
import docx
from docx.oxml.ns import qn
from lxml import etree

XML_SPACE = "{http://www.w3.org/XML/1998/namespace}space"

def ptext(p):
    return "".join(t.text or "" for t in p.iter(qn("w:t"))).strip()

def set_par_text(p, text):
    """sostituisce il contenuto di un paragrafo con un'unica run (formato della prima run con testo)"""
    runs = p.findall(qn("w:r"))
    base = None
    for r in runs:
        if r.find(qn("w:t")) is not None:
            base = r
            break
    if base is None:
        base = etree.SubElement(p, qn("w:r"))
        rpr = p.find(qn("w:pPr") + "/" + qn("w:rPr"))
        if rpr is not None:
            base.append(copy.deepcopy(rpr))
        etree.SubElement(base, qn("w:t"))
    for r in runs:
        if r is not base:
            p.remove(r)
    ts = base.findall(qn("w:t"))
    for t in ts[1:]:
        base.remove(t)
    t = ts[0] if ts else etree.SubElement(base, qn("w:t"))
    t.text = text
    t.set(XML_SPACE, "preserve")

def cell_pars(tc):
    return tc.findall(qn("w:p"))

def set_cell(tc, text, idx=0):
    set_par_text(cell_pars(tc)[idx], text)

def rows(tbl):
    return tbl._tbl.findall(qn("w:tr"))

def cells(tr):
    return tr.findall(qn("w:tc"))

def tokenise_bands(tbl, group, keys, label_col=None, one_col=None, ann_col=None, ext_col=None, style=""):
    """tabella con colonna X (0): una riga per chiave (A, B, ...)"""
    trs = rows(tbl)[1:]
    assert len(trs) == len(keys), (group, len(trs), len(keys))
    for tr, k in zip(trs, keys):
        tcs = cells(tr)
        set_cell(tcs[0], "{{X:%s:%s}}" % (group, k))
        if label_col is not None:
            set_cell(tcs[label_col], "{{P:%s:%s:LABEL}}" % (group, k))
        if one_col is not None:
            set_cell(tcs[one_col], "{{P:%s:%s:ONETIME%s}}" % (group, k, style))
        if ann_col is not None:
            set_cell(tcs[ann_col], "{{P:%s:%s:ANNUAL%s}}" % (group, k, style))
        if ext_col is not None:
            set_cell(tcs[ext_col], "{{P:%s:%s:EXTRA%s}}" % (group, k, style))

def make_servizi_accessori(src, outs):
    d = docx.Document(src)
    body = d.element.body
    # --- marcatori di sezione (prima del titolo di ciascun servizio); il salto pagina davanti a FIRMAE e' gestito dal generatore
    paras = {ptext(p): p for p in body.findall(qn("w:p")) if ptext(p)}
    def marker(code):
        m = etree.Element(qn("w:p"))
        r = etree.SubElement(m, qn("w:r"))
        t = etree.SubElement(r, qn("w:t")); t.text = "[[SA:%s]]" % code
        return m
    for code, title in (("AUDIT", "AUDIT PER CONSERVAZIONE DIGITALE A NORMA"), ("DXC", "DISCOVERY XCHANGE"),
                        ("FIRMAE", "FIRMAE"), ("CRC", "CRC CREDIT RECOVERY CONTROL")):
        paras[title].addprevious(marker(code))
    for p in list(body.findall(qn("w:p"))):   # salto pagina esplicito prima di FIRMAE (paragrafo con solo <w:br type=page>)
        if not ptext(p) and any(b.get(qn("w:type")) == "page" for b in p.iter(qn("w:br"))):
            body.remove(p)
    T = d.tables
    # T0 Discovery Xchange: scaglioni A-I
    tokenise_bands(T[0], "DXC_BAND", list("ABCDEFGHI"), label_col=2, one_col=3, ann_col=4)
    # T1 certificato di firma (opzionale)
    for tr, k in zip(rows(T[1])[1:], ("SMIXCFDA", "SMINHFQR")):
        tcs = cells(tr)
        set_cell(tcs[0], "{{X:DXC_CERT:%s}}" % k)
        set_cell(tcs[2], "{{P:DXC_CERT:%s:ONETIME}}" % k)
        set_cell(tcs[3], "{{P:DXC_CERT:%s:ANNUAL}}" % k)
    # T2/T3 Firmae startup
    r = rows(T[2])
    set_cell(cells(r[1])[1], "{{P:FIRMAE_START:SMINHAT:ONETIME}}")
    set_cell(cells(r[2])[1], "{{P:FIRMAE_START:SMINHSIG:ANNUAL}}")
    set_cell(cells(rows(T[3])[0])[1], "{{P:FIRMAE_START:*:TOTAL}}")
    # T4 Firmae fasce A-Q
    tokenise_bands(T[4], "FIRMAE_BAND", list("ABCDEFGHIJKLMNOPQ"), label_col=2, ann_col=3, ext_col=4, style=":S")
    # T5 CRC attivazione + importazione storico
    r = rows(T[5])
    set_cell(cells(r[1])[1], "{{P:CRC_START:SMINHCRA:ONETIME}}")
    c = cells(r[2])[1]
    ps = cell_pars(c)
    set_par_text(ps[0], "{{P:CRC_IMPORT:SMINHCRI:ONETIME}} cad.")
    set_par_text(ps[-1], "Nr partite {{Q:CRC_IMPORT:SMINHCRI}}")
    # T6 CRC fasce A-L, M (ad hoc)
    tokenise_bands(T[6], "CRC_BAND", list("ABCDEFGHILM"), label_col=2, ann_col=3, ext_col=4)
    for o in outs:
        d.save(os.path.join(o, "servizi-accessori.docx"))

def make_riepilogo(src, outs):
    d = docx.Document(src)
    t = d.tables[0]
    trs = rows(t)
    amounts = {1: "{{RF_LICENZE}}", 2: "{{RF_POWER}}", 3: "{{RF_ATTIVITA}}", 4: "{{RF_FOLLOWUP}}", 5: "{{RF_TOTALE}}", 6: "{{RF_MANUT}}"}
    for i, tok in amounts.items():
        set_cell(cells(trs[i])[1], tok)
    # etichetta del canone: la dicitura "IN AGGIUNTA ALL'ATTUALE" e' opzionale
    set_cell(cells(trs[6])[0], "CANONE MANUTENZIONE ANNUALE E ATC{{RF_MANUT_NOTE}}")
    for o in outs:
        d.save(os.path.join(o, "riepilogo-finale.docx"))

def make_riepilogo_sa(src, outs):
    d = docx.Document(src)
    t = d.tables[0]
    trs = rows(t)
    proto = trs[2]                           # riga DISCOVERY XCHANGE come prototipo
    tcs = cells(proto)
    set_cell(tcs[0], "{{SA_NAME}}"); set_cell(tcs[1], "{{SA_UT}}"); set_cell(tcs[2], "{{SA_CAN}}")
    for i in (1, 3, 4):
        t._tbl.remove(trs[i])
    for o in outs:
        d.save(os.path.join(o, "riepilogo-finale-servizi-accessori.docx"))

def make_pagamenti(src, outs):
    """tabella modalita' di fatturazione e pagamento: celle FATTURAZIONE / PAGAMENTO con segnaposto"""
    d = docx.Document(src)
    trs = rows(d.tables[0])
    for tr, k in zip(trs[1:], ("LIC", "CAN", "ATT")):
        tcs = cells(tr)
        set_cell(tcs[1], "{{PAG_%s_FATT}}" % k)
        set_cell(tcs[2], "{{PAG_%s_PAG}}" % k)
    for o in outs:
        d.save(os.path.join(o, "pagamenti.docx"))

def make_attivita(src, outs):
    """attivita.docx: tiene intestazione + una fascia + una riga attivita' come prototipi con segnaposto; toglie le righe di esempio"""
    d = docx.Document(src)
    t = d.tables[0]
    trs = rows(t)
    for tr in trs[3:]:
        t._tbl.remove(tr)
    # intestazione: «STIMA» a capo e {{ATT_STIMA}} = «GG» o «ORE» secondo l'opzione «Servizi espressi in gg»
    hc = cells(trs[0])[1]
    set_cell(hc, "STIMA")
    hp = cell_pars(hc)[0]
    r1 = hp.findall(qn("w:r"))[0]
    br_run = copy.deepcopy(r1)
    for t_ in br_run.findall(qn("w:t")):
        br_run.remove(t_)
    br_run.append(etree.Element(qn("w:br")))
    tok_run = copy.deepcopy(r1)
    for t_ in tok_run.findall(qn("w:t")):
        t_.text = "{{ATT_STIMA}}"
    r1.addnext(br_run)
    br_run.addnext(tok_run)
    set_cell(cells(trs[0])[2], "{{ATT_INT_TARIFFA}}")      # intestazione «TARIFFA»: solo con «Dettaglio importi attività»
    set_cell(cells(trs[1])[0], "{{ATT_APP}}")
    # riga con il nome dell'applicazione ({{APP_TITLE}}): copia della fascia, prima di quella del titolo del gruppo
    app_row = copy.deepcopy(trs[1])
    set_cell(cells(app_row)[0], "{{APP_TITLE}}")
    trs[1].addprevious(app_row)
    for tc, tok in zip(cells(trs[2]), ["{{ATT_DESC}}", "{{ATT_ORE}}", "{{ATT_TARIFFA}}", "{{ATT_VALORE}}"]):
        set_cell(tc, tok)
    # riga di sub-totale per applicazione: copia della riga attivita', in grassetto con sfondo chiaro
    sub = copy.deepcopy(trs[2])
    for tc, tok in zip(cells(sub), ["{{ATT_SUB_NAME}}", "{{ATT_SUB_ORE}}", "", "{{ATT_SUB_VALORE}}"]):
        set_cell(tc, tok)
        for r in tc.iter(qn("w:r")):
            rpr = r.find(qn("w:rPr"))
            if rpr is None:
                rpr = etree.Element(qn("w:rPr")); r.insert(0, rpr)
            if rpr.find(qn("w:b")) is None:
                rpr.insert(0, etree.Element(qn("w:b")))
        tcpr = tc.find(qn("w:tcPr"))
        shd = etree.SubElement(tcpr, qn("w:shd")); shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), "F2F2F2")
    trs[2].addnext(sub)
    for tbl in d.tables:
        for tr in rows(tbl):
            cs = cells(tr)
            if len(cs) >= 3 and ptext(cell_pars(cs[0])[0]).upper().startswith("TOTALE IMPORTO STIMATO"):
                set_cell(cs[1], "{{ATT_TOT_ORE}}")
                set_cell(cs[-1], "{{ATT_TOT}}")
    for o in outs:
        d.save(os.path.join(o, "attivita.docx"))

if __name__ == "__main__":
    src = sys.argv[1]
    outs = sys.argv[2:]
    for o in outs:
        os.makedirs(o, exist_ok=True)
    make_servizi_accessori(os.path.join(src, "servizi-accessori.docx"), outs)
    make_riepilogo(os.path.join(src, "riepilogo-finale.docx"), outs)
    make_riepilogo_sa(os.path.join(src, "riepilogo-finale-servizi-accessori.docx"), outs)
    make_pagamenti(os.path.join(src, "pagamenti.docx"), outs)
    make_attivita(os.path.join(src, "attivita.docx"), outs)
    for o in outs:
        for f in ("manutenzione.docx",):
            import shutil
            shutil.copy(os.path.join(src, f), os.path.join(o, f))
    print("modelli scritti in", outs)
