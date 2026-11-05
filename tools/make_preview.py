#!/usr/bin/env python3
"""
Anteprima statica del wizard (senza server): inline di CSS/JS reali + API simulate (fetch stub) con il catalogo vero.
Uso: python3 -I tools/make_preview.py <cartella_output>   -> preview-step1.html, preview-step2.html, preview-step3.html
(solo per verifica grafica: il motore del calcolo reale e' quello Java)
"""
import json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources")
OUT = sys.argv[1]
rd = lambda p: open(os.path.join(ROOT, p), encoding="utf8").read()
catalog = open(os.path.join(HERE, "..", "src", "test", "resources", "catalog-api.json"), encoding="utf8").read()
terms = open(os.path.join(HERE, "..", "src", "main", "resources", "offer-terms.json"), encoding="utf8").read()
extras = open(os.path.join(HERE, "..", "src", "test", "resources", "extras-api.json"), encoding="utf8").read()

wizard = rd("templates/offers/wizard.html")
layout = rd("templates/fragments/layout.html")
start = wizard.index('<div id="wizard"')
end = wizard.index("</dialog>") + len("</dialog>")
body = wizard[start:end]
body = re.sub(r'\s+th:[\w:-]+="[^"]*"', "", body)
body = re.sub(r"\s+sec:[\w:-]+=\"[^\"]*\"", "", body)
body = re.sub(r'<div id="wizard"[^>]*>', '<div id="wizard" data-today="2026-10-25" data-next-progressive="1" data-initials="ER" data-prices="true" data-docx="true">', body, count=1)
sprite = layout[layout.index('<svg width="0"'):layout.index("</svg>", layout.index('<svg width="0"')) + 6]
css = rd("static/css/m3.css")

shell = '''
<header class="appbar"><a class="brand"><span class="logo">Q</span><span>CPQIA <span class="muted" style="font-size:14px">Configuratore offerte</span></span></a><span class="spacer"></span>
<div class="usermenu"><button type="button" id="usermenu-btn"><span class="avatar">ER</span><span style="text-align:left;line-height:1.2"><span>Enrico Rossi</span><br><small class="muted">Administrator</small></span></button>
<div class="menu" id="usermenu" hidden></div></div></header>
<nav class="drawer"><a class="active"><svg class="ic lg"><use href="#i-doc"/></svg>Offerte</a><a><svg class="ic lg"><use href="#i-business"/></svg>Anagrafica clienti</a>
<div class="section">Amministrazione</div><a><svg class="ic lg"><use href="#i-apps"/></svg>Applicazioni e moduli</a><a><svg class="ic lg"><use href="#i-group"/></svg>Utenti e permessi</a></nav>
'''

stub = '''
const CATALOG = __CATALOG__;
const EXTRAS = __EXTRAS__;
const TERMS = __TERMS__;
const CUSTOMERS = [
 {id:1,code:"C000001",type:"CLIENTE",companyName:"EFORMA SRL",streetType:"Via",streetName:"dell’Elettronica",streetNumber:"9",postalCode:"36061",city:"Thiene",province:"VI",vatNumber:"04133090243",taxCode:"04133090243"},
 {id:2,code:"C000002",type:"PROSPECT",companyName:"Rossi Meccanica SpA",streetType:"Viale",streetName:"Europa",streetNumber:"12/A",postalCode:"35100",city:"Padova",province:"PD",vatNumber:"01234567890",taxCode:""}];
const INF = 1e12;
function price(m, mode){ const p=m.prices||{}; return (mode in p)? p[mode] : (p.STD||0); }
function tierAmount(g, base, qty, mode){
  if(qty<=0) return 0; const t=g.tiers;
  if(g.baseMode==='LINEAR'){ const u=t[0].unitByMode||{}; return qty*((mode in u)?u[mode]:(u.STD||0)); }
  if(base===0) return 0; let amt=base;
  for(let k=1;k<t.length;k++){ const to=t[k].toQty==null?INF:t[k].toQty, pto=t[k-1].toQty==null?INF:t[k-1].toQty;
    const n = qty>=to ? to-pto : (qty>t[k].fromQty-1 ? qty-t[k].fromQty+1 : 0); if(n<=0) continue;
    const u=t[k].unitByMode||{}; const unit=(mode in u)?u[mode]:(('STD' in u)?u.STD:base*(t[k].pct||0)/100); amt+=unit*n; }
  return amt; }
function calcLine(app, line){
  const mode=line.modeCode||((app.modes.find(m=>m.preferred)||app.modes[0]||{}).code); let lic=0, man=0, ann=0, one=0; const sums={};
  (line.modules||[]).forEach(mq=>{ const m=app.modules.find(x=>x.id===mq.moduleId); if(!m||m.section==='LINK'||m.included) return; const q=m.qtyEnabled?mq.qty:1; const t=price(m,mode)*q;
    if(m.groupId!=null){ sums[m.groupId]=(sums[m.groupId]||0)+t; return; }
    const pct=m.maintenancePct!=null?m.maintenancePct:app.maintenancePct;
    if(m.kind==='ANNUAL') ann+=t; else if(m.kind==='ONETIME') one+=t; else { lic+=t; man+=t*pct/100; }
    one+=(m.onetimePrice||0)*q; });
  app.groups.forEach(g=>{ const qty=(line.drivers||{})[g.driverId]||0; const fb=g.fixedBase&&Object.keys(g.fixedBase).length?((mode in g.fixedBase)?g.fixedBase[mode]:(g.fixedBase.STD||0)):null;
    const base=fb!=null?fb:(sums[g.id]||0); const a=tierAmount(g,base,qty,mode); lic+=a; man+=a*app.maintenancePct/100; });
  return {applicationId:app.id, license:lic, onetime:one, licenseTotal:lic+one, maintenance:man+ann, rentMonthly:0, rows:[]};
}
function violations(req){
  const sel=new Set(); req.lines.forEach(l=>{const a=CATALOG.find(x=>x.id===l.applicationId); (l.modules||[]).forEach(mq=>{const m=a.modules.find(x=>x.id===mq.moduleId); if(m) sel.add(m.code);});});
  const out=[]; req.lines.forEach(l=>{const a=CATALOG.find(x=>x.id===l.applicationId); (l.modules||[]).forEach(mq=>{const m=a.modules.find(x=>x.id===mq.moduleId); if(!m) return;
    (m.rules||[]).forEach(r=>{ if(r.type==='INFO') out.push({applicationId:a.id,moduleId:m.id,severity:'INFO',message:m.code+': '+r.message});
      else if(r.type==='REQUIRES' && !(r.targetCodes||'').split(',').some(c=>sel.has(c.trim()))) out.push({applicationId:a.id,moduleId:m.id,severity:r.severity,message:m.code+': '+r.message}); }); }); });
  return out; }
window.fetch = async (url, opts) => {
  const u = String(url); const method=(opts&&opts.method)||'GET'; let data;
  if (u.includes('/offers/api/catalog')) data = CATALOG;
  else if (u.includes('/offers/api/extras')) data = EXTRAS;
  else if (u.includes('/offers/api/terms')) data = TERMS;
  else if (u.includes('/customers/api/search')) data = CUSTOMERS;
  else if (u.includes('/customers/api/vat-lookup')) data = {result:{valid:true,dataAvailable:true,vat:'04133090243',name:'EFORMA SRL',streetType:'Via',streetName:'Dell’Elettronica',streetNumber:'9',postalCode:'36061',city:'Thiene',province:'VI',message:'Dati recuperati da VIES: verificali prima di salvare'}, existing:CUSTOMERS[0]};
  else if (u.includes('/offers/api/next-progressive')) data = {progressive:1, initials:'ER'};
  else if (u.includes('/offers/api/calc')) { const req=JSON.parse(opts.body); const lines=req.lines.map(l=>calcLine(CATALOG.find(a=>a.id===l.applicationId), l));
    const tl=lines.reduce((s,x)=>s+x.licenseTotal,0), tm=lines.reduce((s,x)=>s+x.maintenance,0), d=req.discountPct||0;
    data={lines, totalLicense:tl, totalMaintenance:tm, discountPct:d, netLicense:tl*(1-d/100), netMaintenance:tm*(1-d/100), violations:violations(req)}; }
  else data = {};
  return new Response(JSON.stringify(data), {status:200, headers:{'content-type':'application/json'}});
};
'''.replace("__CATALOG__", catalog).replace("__EXTRAS__", extras).replace("__TERMS__", terms)

def page(demo):
    js_core = rd("static/js/wizard-core.js")
    js_app = rd("static/js/app.js")
    js_wiz = rd("static/js/wizard.js")
    return f'''<!DOCTYPE html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="ctx" content="/"><title>Anteprima wizard</title><style>{css}
body{{zoom:.6}}#dbg{{position:fixed;top:0;left:0;right:0;background:#fee;color:#900;z-index:999;font:12px monospace;white-space:pre-wrap}}</style></head><body><div id='dbg'></div><script>window.onerror=(m,u,l)=>{{document.getElementById('dbg').textContent+=m+' @'+l+' | '}};window.addEventListener('unhandledrejection',e=>{{document.getElementById('dbg').textContent+='promise: '+e.reason+' | '}});</script>
{shell}
<main class="content"><div class="inner">{body}</div></main>{sprite}<div class="snackbar" id="snackbar"></div>
<script>{stub}</script><script>{js_app}</script><script>{js_core}</script><script>{js_wiz}</script>
<script>
(async function(){{
  const wait = (ms)=>new Promise(r=>setTimeout(r,ms));
  await wait(1500);
  const $=(s)=>document.querySelector(s);
  const demo = "{demo}";
  if(demo==="1"){{ return; }}
  // cliente
  $('#f-cust').dispatchEvent(new Event('focus')); await wait(500);
  document.querySelector('#cust-list .item[data-id]').click(); await wait(100);
  $('#f-ref').value='Enrico Strobbe'; $('#f-ref').dispatchEvent(new Event('input'));
  if(demo==="v"){{ $('#btn-newcust').click(); await wait(200); const f=$('#cust-dialog-form'); f.elements.vatNumber.value='04133090243'; $('#cust-vat-btn').click(); await wait(600); return; }}
  if(demo==="d"){{ $('#btn-newcust').click(); await wait(200); $('#cust-dialog-form').elements.companyName.value='Nuova Srl'; $('#cust-dialog-form').dispatchEvent(new Event('submit',{{cancelable:true}})); return; }}
  if(demo==="x"||demo==="y"){{ const o=$('#opt-services'); o.checked=true; o.dispatchEvent(new Event('change',{{bubbles:true}})); const m=$('#opt-maint'); m.checked=true; m.dispatchEvent(new Event('change',{{bubbles:true}})); const sm=$('#opt-summary'); sm.checked=true; sm.dispatchEvent(new Event('change',{{bubbles:true}})); }}
  if(demo==="f"){{ const o=$('#opt-services'); o.checked=true; o.dispatchEvent(new Event('change',{{bubbles:true}})); const sm=$('#opt-summary'); sm.checked=true; sm.dispatchEvent(new Event('change',{{bubbles:true}})); return; }}
  $('#btn-to2').click(); await wait(200);
  const tick=async(sel)=>{{ const el=document.querySelector(sel); el.checked=!el.checked; el.dispatchEvent(new Event('change',{{bubbles:true}})); await wait(120); }};
  const byName=(n)=>[...document.querySelectorAll('.app-check')].find(a=>a.closest('tr').querySelector('.appname').textContent.trim()===n);
  async function pick(n, users){{ const a=byName(n); a.checked=true; a.dispatchEvent(new Event('change',{{bubbles:true}})); await wait(150);
    const inp=document.querySelector('tr.app-row[data-app="'+a.dataset.app+'"] .drv-in'); if(inp&&users){{ inp.value=users; inp.dispatchEvent(new Event('input',{{bubbles:true}})); }} await wait(150); }}
  await pick('Discovery Quality', 10);
  const mod=(code)=>{{ const row=[...document.querySelectorAll('table.mods tr')].find(r=>r.querySelector('.mono')&&r.querySelector('.mono').textContent===code); return row.querySelector('.mod-check'); }};
  for(const c of ['SMIDBAS','SMIDQRNC','SMIDQRCL']){{ const el=mod(c); el.checked=true; el.dispatchEvent(new Event('change',{{bubbles:true}})); await wait(120); }}
  await pick('JPA3 (JPA Synergy V300)', 10);
  await wait(500);
  if(demo==="x"||demo==="y"){{
    $('#btn-next2').click(); await wait(300);
    const pickOpt=async(grp,key)=>{{ const g=EXTRAS.flatMap(s=>s.groups).find(x=>x.code===grp); const o=g.options.find(x=>x.rowKey===key); const el=document.querySelector('.x-opt[data-opt="'+o.id+'"]'); el.checked=true; el.dispatchEvent(new Event('change',{{bubbles:true}})); await wait(150); }};
    await pickOpt('DXC_BAND','C'); await pickOpt('FIRMAE_BAND','E'); await pickOpt('CRC_BAND','D');
    const au=document.querySelector('.x-svc-check[data-svc="1"]'); au.checked=true; au.dispatchEvent(new Event('change',{{bubbles:true}})); await wait(300);
    if(demo==="y"){{ $('#btn-next3').click(); await wait(400); $('#sum-disc').value='20'; $('#sum-disc').dispatchEvent(new Event('input')); await wait(200); }}
    return;
  }}
  if(demo==="3"){{ $('#btn-next2').click(); await wait(400); $('#sum-disc').value='20'; $('#sum-disc').dispatchEvent(new Event('input')); await wait(200); }}
  else {{ const r=document.querySelector('tr.app-row.sel'); if(r) window.scrollTo(0, r.getBoundingClientRect().top*0.6 + window.scrollY - 150); }}
}})();
</script></body></html>'''

os.makedirs(OUT, exist_ok=True)
for name, demo in (("preview-step1.html", "1"), ("preview-step2.html", "2"), ("preview-step3.html", "3"), ("preview-dialog.html", "d"), ("preview-vat.html", "v"), ("preview-extras.html", "x"), ("preview-extras-sum.html", "y"), ("preview-flags.html", "f")):
    open(os.path.join(OUT, name), "w", encoding="utf8").write(page(demo))
print("scritti", OUT)
