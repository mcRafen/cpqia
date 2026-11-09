#!/usr/bin/env python3
"""Prepara i modelli Word delle attivita' copiandoli da documenti/ a src/main/resources/offer-template/:
 - accorcia di 200 unita' la prima colonna delle tabelle a tutta pagina (10150-10210), cosi' restano dentro il margine destro;
 - in attivita.docx: unisce il segnaposto {{GG_HH}} spezzato in piu' run e mette i segnaposto {{TOT_LISTINO}} / {{TOT_NETTO}} nella tabella dei totali.
Uso: python3 tools/prepare_activity_templates.py"""
import os, re, shutil, zipfile
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "documenti")
DST = os.path.join(ROOT, "src", "main", "resources", "offer-template")
FILES = ["attivita.docx", "attivita-light.docx", "attivita-appendice.docx", "attivita-conversioni.docx", "attivita-personalizzazioni.docx", "attivita-followup.docx"]
CUT = 200


def narrow(d):
    def fix_tbl(m):
        t = m.group(0)
        grid = list(map(int, re.findall(r'<w:gridCol w:w="(\d+)"', t)))
        if not 10150 <= sum(grid) <= 10210:
            return t
        done = [False]

        def g(mm):
            if done[0]:
                return mm.group(0)
            done[0] = True
            return '<w:gridCol w:w="%d"/>' % (int(mm.group(1)) - CUT)
        t = re.sub(r'<w:gridCol w:w="(\d+)"/>', g, t)

        def fix_row(rm):
            col = [0]

            def fix_tc(cm):
                c = cm.group(0)
                sp = re.search(r'<w:gridSpan w:val="(\d+)"', c)
                sp = int(sp.group(1)) if sp else 1
                first = col[0] == 0
                col[0] += sp
                if first:
                    c = re.sub(r'(<w:tcW w:w=")(\d+)(")', lambda x: x.group(1) + str(int(x.group(2)) - CUT) + x.group(3), c, count=1)
                return c
            return re.sub(r'<w:tc>.*?</w:tc>', fix_tc, rm.group(0), flags=re.S)
        t = re.sub(r'<w:tr[ >].*?</w:tr>', fix_row, t, flags=re.S)
        return re.sub(r'<w:tblW [^>]*/>', '<w:tblW w:w="%d" w:type="dxa"/>' % (sum(grid) - CUT), t, count=1)
    return re.sub(r'<w:tbl>.*?</w:tbl>', fix_tbl, d, flags=re.S)


def join_runs_with(d, parts):
    """unisce in un solo run i run consecutivi di un paragrafo il cui testo concatenato e' un segnaposto spezzato"""
    def fix_p(m):
        p = m.group(0)
        runs = re.findall(r'<w:r[ >].*?</w:r>', p, re.S)
        txts = [''.join(re.findall(r'<w:t[^>]*>([^<]*)</w:t>', r)) for r in runs]
        full = ''.join(txts)
        if parts not in full:
            return p
        for i in range(len(runs)):
            acc = ''
            for j in range(i, len(runs)):
                acc += txts[j]
                if parts in acc and parts not in txts[i] and j > i:
                    first = runs[i]
                    merged = re.sub(r'(<w:t[^>]*>)[^<]*(</w:t>)', lambda x: '<w:t xml:space="preserve">' + acc + '</w:t>', first, count=1)
                    new = p.replace(runs[i], merged, 1)
                    for k in range(i + 1, j + 1):
                        new = new.replace(runs[k], '', 1)
                    return new
        return p
    return re.sub(r'<w:p[ >].*?</w:p>', fix_p, d, flags=re.S)


def process(name):
    src = os.path.join(SRC, name)
    zin = zipfile.ZipFile(src)
    items = [(i, zin.read(i.filename)) for i in zin.infolist()]
    zin.close()
    out = []
    for info, data in items:
        if info.filename == 'word/document.xml':
            d = data.decode('utf8')
            if name == 'attivita.docx':
                d = join_runs_with(d, '{{GG_HH}}')
                n = [0]

                def tot(m):
                    n[0] += 1
                    return '<w:t>' + ('{{TOT_LISTINO}}' if n[0] == 1 else '{{TOT_NETTO}}') + '</w:t>'
                d = re.sub(r'<w:t>€ xx\.000,00</w:t>', tot, d)
            d = narrow(d)
            data = d.encode('utf8')
        out.append((info, data))
    tmp = '/tmp/_prep.docx'
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as z:
        for info, data in out:
            z.writestr(info.filename, data)
    shutil.copy(tmp, os.path.join(DST, name))
    print('ok', name)


for f in FILES:
    process(f)
