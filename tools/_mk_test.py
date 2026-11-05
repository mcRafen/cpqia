#!/usr/bin/env python3
"""Anteprima statica della griglia (API simulate in memoria): tools/make_grid_preview.py <out_dir>"""
import re, sys, json, os
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
S = os.path.join(root, 'src/main/resources')
rd = lambda p: open(os.path.join(S, p), encoding='utf-8').read()
sprite = ''.join(re.findall(r'<symbol .*?</symbol>', rd('templates/fragments/layout.html'), re.S))
rows0 = [
 dict(id=1, number='20261022.1ER', offerDate='2026-10-22', customer='EFORMA SRL', customerCode='C000001', projectTitle='Progetto Discovery Quality', contactRef='Enrico Strobbe', owner='Enrico Rossi', status='BOZZA', totalLicense=18500, totalMaintenance=3330, discountPct=20, margin=17464, updatedAt='2026-10-22T10:00:00'),
 dict(id=2, number='20261023.2ER', offerDate='2026-10-23', customer='ALFA SPA', customerCode='C000002', projectTitle='Progetto JPA', contactRef='Mario Bianchi', owner='Enrico Rossi', status='EMESSA', totalLicense=42000, totalMaintenance=7560, discountPct=10, margin=44604, updatedAt='2026-10-23T11:00:00'),
 dict(id=3, number='20261024.1MV', offerDate='2026-10-24', customer='ALFA SPA', customerCode='C000002', projectTitle='Progetto DQ', contactRef='', owner='Maria Verdi', status='BOZZA', totalLicense=8000, totalMaintenance=1440, discountPct=0, margin=9440, updatedAt='2026-10-24T09:00:00'),
 dict(id=4, number='20261025.3ER', offerDate='2026-10-25', customer='BETA SRL', customerCode='C000003', projectTitle='Progetto EPM', contactRef='Giulia Neri', owner='Enrico Rossi', status='EMESSA', totalLicense=25300, totalMaintenance=4554, discountPct=15, margin=25376.9, updatedAt='2026-10-25T09:00:00'),
 dict(id=5, number='20261026.2MV', offerDate='2026-10-26', customer='BETA SRL', customerCode='C000003', projectTitle='Progetto ECM', contactRef='Giulia Neri', owner='Maria Verdi', status='BOZZA', totalLicense=12000, totalMaintenance=2160, discountPct=5, margin=13452, updatedAt='2026-10-26T09:00:00'),
]
rows = [dict(rows0[i%5], id=i+1, number='2026%04d' % (i+1)) for i in range(40)]
C = lambda k, l, t='text', w=140, e=False, **kw: dict(key=k, label=l, type=t, width=w, editable=e, required=False, maxLen=None, min=None, max=None, agg=None, options=None, hidden=False, creatable=False, **kw)
cols = [C('number','Numero',w=130), C('offerDate','Data','date',110), C('customer','Cliente',w=200), C('projectTitle','Progetto',e=True,w=240), C('owner','Creata da',w=140),
        dict(C('status','Stato','enum',100,True), options=[dict(value='BOZZA',label='Bozza'),dict(value='EMESSA',label='Emessa')]),
        dict(C('totalLicense','Licenze','money',120), agg='sum'), dict(C('totalMaintenance','Manutenzione','money',120), agg='sum'),
        dict(C('discountPct','Sconto %','percent',90,True), agg='avg', min=0, max=100), dict(C('margin','Margine comm.','money',130), agg='sum')]
meta = dict(key='offers', title='Offerte', columns=cols, canCreate=False, canDelete=True, newHref='offers/new', newLabel='Nuova offerta',
            actions=[dict(name='docx',label='Scarica Word',icon='download',href='offers/{id}/docx',server=False,confirm=None,style=None), dict(name='edit',label='Modifica',icon='edit',href='offers/{id}/edit',server=False,confirm=None,style=None)])
def page(name, cfg, extra_js=''):
    css = rd('static/css/m3.css'); core = rd('static/js/grid-core.js'); grid = rd('static/js/grid.js')
    layouts = [dict(id=1, name='Per cliente e stato', config=json.dumps(cfg), isDefault=True)] if cfg else []
    html = f'''<!DOCTYPE html><html lang="it"><head><meta charset="utf-8"><style>{css}</style></head><body>
<svg width="0" height="0" style="position:absolute">{sprite}</svg>
<main class="content" style="margin:0"><div class="inner"><div class="pagehead"><h1>Offerte</h1></div>
<div class="cpgrid" data-grid="offers"></div></div></main>
<script>
window.toast=function(m,e){{console.log(m)}};
window.CPQ={{esc:s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}}[c])),url:p=>p,
 api:async(m,p,b)=>{{ if(p.endsWith('/layouts')) return {json.dumps(layouts)}; return {{meta:{json.dumps(meta)},rows:{json.dumps(rows)}}}; }}}};
</script><script>{core}</script><script>{grid}</script><script>{extra_js}</script></body></html>'''
    open(os.path.join(sys.argv[1], name), 'w', encoding='utf-8').write(html)

test_js = """
setTimeout(function(){
  var out=[]; var rows=function(){return document.querySelectorAll('tbody tr[data-id]').length};
  out.push('iniziale='+rows());
  var sel=document.querySelector('.cg-ppsel'); out.push('select='+(sel?sel.value:'NA'));
  sel.value='10'; sel.dispatchEvent(new Event('change',{bubbles:true}));
  setTimeout(function(){ out.push('dopo10='+rows()); document.title=out.join(' '); var d=document.createElement('pre'); d.textContent=out.join('\\n'); document.body.prepend(d); },400);
},600);
"""
page('grid-test.html', None, test_js)
