#!/usr/bin/env python3
"""
Anteprima (riferimento Python) dell'assemblaggio dell'offerta: stesso algoritmo di OfferDocxService (Java).
  copertina (offer-template.docx) + licenze + attivita + varie + pagamenti + condizioni-gdpr,
  con interruzione di pagina prima di ogni parte agganciata.
Uso: python3 -I tools/assemble_preview.py <cartella_modelli> <out.docx>
"""
import copy, io, os, re, sys
import docx
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from lxml import etree

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
q = lambda t: "{%s}%s" % (W, t)

PARTS = [  # (chiave, file, titoli (maiuscolo) con cui inizia questa parte)
    ("licenze", "licenze.docx", ["LICENZE"]),
    ("attivita", "attivita.docx", ["ATTIVITA"]),
    ("manutenzione", "manutenzione.docx", ["MANUTENZIONE"]),
    ("servizi-accessori", "servizi-accessori.docx", ["SERVIZI ACCESSORI"]),
    ("riepilogo-finale", "riepilogo-finale.docx", ["RIEPILOGO"]),
    ("varie", "varie.docx", ["VARIE"]),
    ("pagamenti", "pagamenti.docx", ["MODALITA"]),
    ("condizioni-gdpr", "condizioni-gdpr.docx", ["CONCLUSIONI"]),
]

def norm(s):
    return re.sub(r"[^A-Z ]", "", s.upper().replace("À", "A").replace("’", "").replace("'", "")).strip()

def ptext(p):
    return "".join(t.text or "" for t in p.iter(q("t"))).strip()

def is_heading(el):
    if el.tag != q("p"):
        return False
    st = el.find(q("pPr") + "/" + q("pStyle"))
    return st is not None and st.get(q("val")) in ("Heading1", "Titolo1")

def is_blank_or_break(el):
    """paragrafo vuoto o con solo interruzione di pagina"""
    if el.tag != q("p"):
        return False
    if el.find(q("pPr") + "/" + q("sectPr")) is not None:
        return False
    if ptext(el):
        return False
    return not list(el.iter(q("drawing"))) and not list(el.iter(q("pict")))

def has_page_break(el):
    return any(b.get(q("type")) == "page" for b in el.iter(q("br")))

def trim_part(body_elems, own_idx):
    """taglia la parte al primo titolo (Heading 1) che appartiene a una parte successiva"""
    later = [norm(t) for i, (_, _f, ts) in enumerate(PARTS) if i > own_idx for t in ts]
    for i, el in enumerate(body_elems):
        if is_heading(el):
            n = norm(ptext(el))
            if any(n.startswith(l) for l in later):
                j = i
                while j > 0 and is_blank_or_break(body_elems[j - 1]):
                    j -= 1
                return body_elems[:j]
    return body_elems

def strip_trailing_breaks(body):
    """toglie dal modello base i paragrafi finali vuoti / con solo salto pagina (ci pensa l'assemblaggio)"""
    kids = [k for k in body if k.tag != q("sectPr")]
    while kids and kids[-1].tag == q("p") and is_blank_or_break(kids[-1]) and kids[-1].find(q("pPr") + "/" + q("sectPr")) is None:
        body.remove(kids.pop())

def remap_rels(el, src_part, dst_doc, cache):
    for e in el.iter():
        for attr in ("embed", "id", "link", "pict"):
            k = "{%s}%s" % (R, attr)
            old = e.get(k)
            if not old:
                continue
            if old in cache:
                e.set(k, cache[old]); continue
            rel = src_part.rels.get(old)
            if rel is None:
                continue
            if rel.is_external:
                new = dst_doc.part.relate_to(rel.target_ref, rel.reltype, is_external=True)
            elif rel.reltype == RT.IMAGE:
                new, _ = dst_doc.part.get_or_add_image(io.BytesIO(rel.target_part.blob))
            else:
                continue
            cache[old] = new
            e.set(k, new)

def fill_licenze(d, lines, tot, net):
    """stessa logica di OfferDocxService.fillLicenceTable / fillTotals"""
    def set_text(tc, text):
        p = tc.find(q("p")); runs = p.findall(q("r"))
        runs[0].find(q("t")).text = text
        for r in runs[1:]: p.remove(r)
    def set_runs(p, text):
        runs = p.findall(q("r")); runs[0].find(q("t")).text = text
        for r in runs[1:]: p.remove(r)
    def fill_lines(tc, vals):
        ps = tc.findall(q("p")); proto = ps[0]
        for p in ps[1:]: tc.remove(p)
        set_runs(proto, vals[0]); prev = proto
        for v in vals[1:]:
            n = copy.deepcopy(proto); set_runs(n, v); prev.addnext(n); prev = n
    tbl = d.tables[0]._tbl
    rows = tbl.findall(q("tr")); A, B = rows[1], rows[2]
    anchor = B
    for ln in lines:
        a, b = copy.deepcopy(A), copy.deepcopy(B)
        ta, tb = a.findall(q("tc")), b.findall(q("tc"))
        fill_lines(ta[0], ln["codes"]); fill_lines(ta[1], ln["descr"])
        set_text(ta[2], ln["imp"]); set_text(ta[3], ln["man"]); set_text(tb[1], ln["posti"])
        anchor.addnext(a); a.addnext(b); anchor = b
    tbl.remove(A); tbl.remove(B)
    t2 = d.tables[1]._tbl.findall(q("tr"))
    for tr, (x, y) in zip(t2, [tot, net]):
        tcs = tr.findall(q("tc")); set_text(tcs[1], x); set_text(tcs[2], y)

# ------------------------------------------------------------------ servizi accessori e riepilogo (riferimento del Java)
import json
SECT = re.compile(r"\[\[SA:(\w+)\]\]")
TOK = re.compile(r"\{\{([XPQ]):([A-Z_]+):([^:}]+)(?::([A-Z]+))?(?::(S))?\}\}")

def eur(v):
    return "€ " + f"{v:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")

def money(v, suffix):
    if not v:
        return ""
    if not suffix:
        return eur(v)
    whole = float(v).is_integer()
    t = f"{v:,.0f}" if whole else f"{v:,.2f}"
    return t.replace(",", "X").replace(".", ",").replace("X", ".") + " €"

def resolve(m, bykey, groups, chosen):
    typ, grp, row, field, suf = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
    if typ == "P" and field == "TOTAL":
        return eur(sum((o.get("onetime") or 0) + (o.get("annual") or 0) for o in groups[grp]["options"]))
    o = bykey.get(grp + "|" + row)
    if o is None:
        return ""
    if typ == "X":
        return "X" if o["id"] in chosen else ""
    if typ == "Q":
        return str(chosen[o["id"]]) if o["id"] in chosen and o.get("qtyEnabled") else "____________"
    if field == "LABEL": return o["label"]
    if field == "ONETIME": return money(o.get("onetime"), suf)
    if o.get("adHoc"): return o.get("adHocText") or ""
    return money(o.get("annual") if field == "ANNUAL" else o.get("extraUnit"), suf)

def replace_tokens(root, fn):
    for t in root.iter(q("t")):
        if t.text and "{{" in t.text:
            t.text = fn(t.text)

def set_text_cell(tc, text):
    ps = tc.findall(q("p")); p = ps[0]
    for x in ps[1:]: tc.remove(x)
    runs = p.findall(q("r"))
    if not runs:                                  # celle del modello senza testo: nessun run
        r = etree.SubElement(p, q("r")); etree.SubElement(r, q("t")); runs = [r]
    runs[0].find(q("t")).text = text
    for r in runs[1:]: p.remove(r)

def activities_total(part):
    for t in part.tables:
        for tr in t._tbl.findall(q("tr")):
            tcs = tr.findall(q("tc"))
            if tcs and ptext_all(tcs[0]).upper().startswith("TOTALE IMPORTO STIMATO"):
                for tc in reversed(tcs):
                    m = re.search(r"(\d{1,3}(?:\.\d{3})+|\d+),(\d{2})", ptext_all(tc))
                    if m: return float(m.group(1).replace(".", "") + "." + m.group(2))
    return None

def ptext_all(el):
    return "".join(t.text or "" for t in el.iter(q("t"))).strip()

def remove_rows_containing(part, token):
    for t in part.tables:
        for tr in list(t._tbl.findall(q("tr"))):
            if token in ptext_all(tr):
                t._tbl.remove(tr)

def make_page_break(el):
    ppr = el.find(q("pPr"))
    if ppr is None:
        ppr = etree.Element(q("pPr")); el.insert(0, ppr)
    pb = etree.Element(q("pageBreakBefore")); pos = 0
    for i, c in enumerate(ppr):
        if c.tag in (q("pStyle"), q("keepNext"), q("keepLines")): pos = i + 1
    ppr.insert(pos, pb)

def hours_text(h):
    return (str(int(h)) if float(h).is_integer() else str(h)).replace(".", ",")

def fill_activities(part, acts, disc, repl, detail=True):
    """stessa logica di OfferDocxService.fillActivityTables (righe prototipo con {{ATT_*}})"""
    tbl = part.tables[0]._tbl
    rows = tbl.findall(q("tr"))
    band = next(r for r in rows if "{{ATT_APP}}" in ptext_all(r)); line = next(r for r in rows if "{{ATT_DESC}}" in ptext_all(r))
    sub = next(r for r in rows if "{{ATT_SUB_ORE}}" in ptext_all(r))
    cur = None; total = 0.0; hours = 0.0; sh = 0.0; st = 0.0
    def add_sub():
        r = copy.deepcopy(sub); tcs = r.findall(q("tc"))
        set_text_cell(tcs[0], "Totale " + cur); set_text_cell(tcs[1], hours_text(sh)); set_text_cell(tcs[2], ""); set_text_cell(tcs[3], eur(st)); tbl.append(r)
    for app, desc, prof, h, rate in acts:
        if app != cur:
            if cur is not None: add_sub()
            cur = app; sh = 0.0; st = 0.0
            b = copy.deepcopy(band); set_text_cell(b.findall(q("tc"))[0], app); tbl.append(b)
        net = round(rate * (1 - disc / 100) + 1e-9, 2); val = round(h * net + 1e-9, 2)
        r = copy.deepcopy(line); tcs = r.findall(q("tc"))
        set_text_cell(tcs[0], desc)
        if prof and detail:
            p1 = tcs[0].find(q("p")); p2 = copy.deepcopy(p1); tcs[0].append(p2)
            p2.find(q("r")).find(q("t")).text = prof
        set_text_cell(tcs[1], hours_text(h) if detail else ""); set_text_cell(tcs[2], eur(net) if detail else ""); set_text_cell(tcs[3], eur(val) if detail else "")
        tbl.append(r); total += val; hours += h; sh += h; st += val
    if cur is not None: add_sub()
    tbl.remove(band); tbl.remove(line); tbl.remove(sub)
    repl["{{ATT_TOT_ORE}}"] = hours_text(hours) + " ore"; repl["{{ATT_TOT}}"] = eur(total)
    return total

def build(tdir, out, flags, extras_json, sample):
    base = docx.Document(os.path.join(tdir, "offer-template.docx"))
    body = base.element.body
    strip_trailing_breaks(body)
    sect = body.find(q("sectPr"))
    repl = sample["tokens"]
    catalog = json.load(open(extras_json))
    bykey, groups = {}, {}
    for sv in catalog:
        for g in sv["groups"]:
            groups[g["code"]] = g
            for o in g["options"]: bykey[g["code"] + "|" + o["rowKey"]] = o
    chosen = {}
    for gcode, key, qty in sample["options"]: chosen[bykey[gcode + "|" + key]["id"]] = qty
    sel_services = {sv["code"] for sv in catalog if sv["id"] in sample["services"]}
    # totali servizi accessori
    lines_x = []
    for sv in catalog:
        if sv["id"] not in sample["services"]: continue
        one = ann = 0; adhoc = None
        for g in sv["groups"]:
            for o in g["options"]:
                qq = 1 if g["selection"] == "FIXED" else chosen.get(o["id"])
                if not qq: continue
                k = qq if o.get("qtyEnabled") else 1
                one += (o.get("onetime") or 0) * k; ann += (o.get("annual") or 0) * k
                if o.get("adHoc"): adhoc = o.get("adHocText")
        lines_x.append(dict(name=sv["summaryName"], inc=sv.get("includedText"), one=one, ann=ann, adhoc=adhoc))
    act_total = None
    emitted_ctx = {}
    for idx, (key, fname, _t) in enumerate(PARTS):
        if (key == "servizi-accessori" and not flags["services"]) or (key == "riepilogo-finale" and not flags["summary"]): continue
        part = docx.Document(os.path.join(tdir, fname))
        lit = lambda s_: [s_ := s_.replace(k, v) for k, v in repl.items()][-1] if repl else s_
        if key == "licenze":
            fill_licenze(part, sample["lines"], sample["tot"], sample["net"])
            elems = trim_part([e for e in part.element.body if e.tag in (q("p"), q("tbl"))], idx)
        elif key == "attivita":
            if not sample.get("activities"):
                continue
            act_total = fill_activities(part, sample["activities"], sample.get("act_discount", 0), repl, sample.get("act_detail", True))
            replace_tokens(part.element.body, lambda s_: [s_ := s_.replace(k, v) for k, v in repl.items()][-1])
            elems = trim_part([e for e in part.element.body if e.tag in (q("p"), q("tbl"))], idx)
        elif key == "servizi-accessori":
            def fn(s_):
                for k, v in repl.items(): s_ = s_.replace(k, v)
                return TOK.sub(lambda m: resolve(m, bykey, groups, chosen), s_)
            replace_tokens(part.element.body, fn)
            elems = []; current = None; brk = False; emitted = 0
            for e in part.element.body:
                if e.tag not in (q("p"), q("tbl")): continue
                m = SECT.fullmatch(ptext_all(e)) if e.tag == q("p") else None
                if m:
                    current = m.group(1); brk = False
                    if current in sel_services:
                        brk = current == "FIRMAE" and emitted > 0; emitted += 1
                    continue
                if current is not None and current not in sel_services: continue
                if brk and e.tag == q("p"): make_page_break(e); brk = False
                elems.append(e)
        elif key == "riepilogo-finale":
            remove_rows_containing(part, "{{RF_POWER}}"); remove_rows_containing(part, "{{RF_FOLLOWUP}}")
            if act_total is None: remove_rows_containing(part, "{{RF_ATTIVITA}}")
            net_lic, net_man = sample["net_lic"], sample["net_man"]
            tot = net_lic + (act_total or 0)
            m_ = {"{{RF_LICENZE}}": eur(net_lic), "{{RF_ATTIVITA}}": eur(act_total) if act_total else "", "{{RF_TOTALE}}": eur(tot),
                  "{{RF_MANUT}}": eur(net_man), "{{RF_MANUT_NOTE}}": " IN AGGIUNTA ALL'ATTUALE" if flags["maint"] else ""}
            replace_tokens(part.element.body, lambda s_: [s_ := s_.replace(k, v) for k, v in m_.items()][-1])
            elems = trim_part([e for e in part.element.body if e.tag in (q("p"), q("tbl"))], idx)
        else:
            elems = trim_part([e for e in part.element.body if e.tag in (q("p"), q("tbl"))], idx)
        append(base, part, elems, sect, True)
        if key == "riepilogo-finale" and flags["services"]:
            sa = docx.Document(os.path.join(tdir, "riepilogo-finale-servizi-accessori.docx"))
            tbl = sa.tables[0]._tbl; trs = tbl.findall(q("tr")); proto = trs[1]
            for l in lines_x:
                r = copy.deepcopy(proto); tcs = r.findall(q("tc"))
                set_text_cell(tcs[0], l["name"])
                set_text_cell(tcs[1], l["inc"] or (eur(l["one"]) if l["one"] else "-"))
                set_text_cell(tcs[2], "-" if l["inc"] else (l["adhoc"] if l["adhoc"] and not l["ann"] else (eur(l["ann"]) + (" + " + l["adhoc"] if l["adhoc"] else "") if l["ann"] else "-")))
                tbl.append(r)
            tbl.remove(proto)
            append(base, sa, [e for e in sa.element.body if e.tag in (q("p"), q("tbl"))], sect, False)
    for t in body.iter(q("t")):
        if t.text:
            for k, v in repl.items():
                if k in t.text: t.text = t.text.replace(k, v)
    base.save(out)

def append(base, part, elems, sect, page_break):
    body = base.element.body
    last = [k for k in body if k.tag != q("sectPr")][-1]
    need = page_break and not (last.tag == q("p") and last.find(q("pPr") + "/" + q("sectPr")) is not None)
    cache = {}
    new = [copy.deepcopy(e) for e in elems]
    for e in new:
        for b in list(e.iter(q("bookmarkStart"), q("bookmarkEnd"))): b.getparent().remove(b)
        remap_rels(e, part.part, base, cache)
    if need and new:
        first = new[0]
        if first.tag != q("p"):
            pe = etree.Element(q("p")); new.insert(0, pe); first = pe
        make_page_break(first)
    for e in new: sect.addprevious(e)

if __name__ == "__main__":
    tdir, out = sys.argv[1], sys.argv[2]
    flags = dict(maint=sys.argv[3] == "1", services=sys.argv[4] == "1", summary=sys.argv[5] == "1")
    sample = dict(
        tokens={"{{TITOLO}}": "Progetto Discovery Quality - JPA", "{{CLIENTE}}": "EFORMA SRL", "{{CLIENTE_BREVE}}": "EFORMA",
                "{{INDIRIZZO1}}": "Via dell’Elettronica, 9", "{{INDIRIZZO2}}": "36061 Thiene (VI)", "{{PIVA}}": "04133090243",
                "{{RIF}}": "Enrico Strobbe", "{{DATA}}": "26 ottobre 2026", "{{NUMERO}}": "20261026.1ER",
                "{{PAG_LIC_FATT}}": "In data 15/12/2026", "{{PAG_LIC_PAG}}": "Solito in uso",
                "{{PAG_CAN_FATT}}": "Entro il mese successivo della data di fatturazione delle licenze software – fatturazione semestrale",
                "{{PAG_CAN_PAG}}": "IVA RI.BA. 30 gg dffm, IMPONIBILE 60-90-120 gg dffm",
                "{{PAG_ATT_FATT}}": "A consuntivo", "{{PAG_ATT_PAG}}": "RI.BA. 30 gg dffm"},
        lines=[{"codes": ["SMIDBAS", "SMIDQRNC", "SMIDQRCL"], "descr": ["SMIDBAS - Modulo Base Qualità", "SMIDQRNC - Rapporti non conformità", "SMIDQRCL - Reclami"],
                "imp": "€ 8.000,00", "man": "€ 1.440,00", "posti": "Licenze Posti di Lavoro n. 10"},
               {"codes": ["SMIJPA"], "descr": ["SMIJPA - Engine - JPA Synergy V300"], "imp": "€ 10.500,00", "man": "€ 1.890,00", "posti": "Licenze Posti di Lavoro n. 10"}],
        tot=("€ 18.500,00", "€ 3.330,00"), net=("€ 14.800,00", "€ 2.664,00"), net_lic=14800.0, net_man=2664.0,
        act_discount=20, act_detail=(os.environ.get('ACT_DETAIL', '1') == '1'),
        activities=[("DISCOVERY QUALITY", "Installazione e configurazione ambiente", "Sviluppatore", 7.5, 85.0),
                    ("DISCOVERY QUALITY", "Formazione utenti", "Istruttore", 4, 90.0),
                    ("JPA3 (JPA Synergy V300)", "Analisi e disegno workflow", "Analista", 10, 95.0)],
        services=[1, 2, 3, 4],
        options=[("DXC_BAND", "C", 1), ("DXC_CERT", "SMIXCFDA", 1), ("FIRMAE_BAND", "E", 1), ("CRC_IMPORT", "SMINHCRI", 150), ("CRC_BAND", "D", 1)])
    build(tdir, out, flags, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "test", "resources", "extras-api.json"), sample)
    print("scritto", out)
