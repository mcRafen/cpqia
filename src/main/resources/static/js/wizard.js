/* Wizard "Nuova offerta": 1) testata e cliente  2) configurazione applicazioni e moduli  3) riepilogo e Word */
(function () {
  'use strict';
  const C = window.WizardCore;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const esc = CPQ.esc;

  const root = $('#wizard');
  if (!root) return;
  const canPrices = root.dataset.prices !== 'false';
  if (!canPrices) document.body.classList.add('noprice');

  const S = {
    offerId: root.dataset.offerId ? Number(root.dataset.offerId) : null,
    date: root.dataset.today,
    progressive: Number(root.dataset.nextProgressive || 1),
    initials: root.dataset.initials || '',
    powerModelId: null, powerRam: '', powerDisk: '', powerLto8: false, powerTs4300: false, powerEth10: false, powerUsers: '', powerUnlimited: false, powerCable: '', powerTransfer: 'NO', powerTransferRef: '', powerEdition: 'SOLUTION', powerOs: '7.5', powerMaint: '36', powerMaintList: [36], powerMaintAmt: {}, powerList: '', powerNet: '', powerDisks: '2', powerProducts: {}, ibmiProducts: [], powerModels: [],
    optRfq: false, svcGroups: [], profileEdits: {}, servicesMode: 'NONE', rfqEditGroups: new Set(), optDays: true, appTotalsMode: 'NO', rfqSheetTotal: null, optPower: false, optSmitech: false, optDept: false, optGalileo: false, galileoId: null,
    gal: { profileId: null, release: '', users: '0', addUsers: '', source: 0, aggiunta: false, sel: {}, calc: null },
    customer: null, title: '', contactRef: '', discountPct: 0, optMaint: false, optServices: false, optSummary: false,
    xSel: { services: new Set(), options: {} },
    accountTravelRateId: null, activities: [], actDiscount: 0, actUntil: '', actDetail: false, hoursDetail: false, maintOnNet: true, profiles: [],
    distanceKm: null, nearestSede: '', distMethod: '', distFor: null, travelRateId: null, travelRates: [],
    terms: null, payTouched: { lic: false, fee: false, act: false, dri: false },
    lines: [], step: 1, saved: null,
  };
  let catalog = [];
  let extrasCatalog = [];
  let termsCfg = null;
  let calc = null;
  const expanded = new Set();
  const filter = { text: '', family: '', onlySel: false };

  // BU: nessun colore proprio per famiglia, i chip seguono i colori del tema / palette selezionati
  const famClass = () => 'bu';
  const appOf = (id) => catalog.find((a) => a.id === id);
  const lineOf = (id) => S.lines.find((l) => l.applicationId === id);

  // ---------------------------------------------------------------- Power System: manutenzione SW/HW a periodi e importi
  const PW_PERIODS = [36, 48, 60];
  const amtKey = (m, kind) => m + '|' + kind;
  /** sezioni di importi di una configurazione Power: Power e, se scelti, LTO8 e/o TS4300 */
  const maintKinds = (o) => ['POWER'].concat(o.powerLto8 ? ['LTO8'] : [], o.powerTs4300 ? ['TS4300'] : []);
  const kindLabel = (k) => (k === 'LTO8' ? 'LTO8' : k === 'TS4300' ? 'TS4300' : 'Power');
  /** sezioni di importi richieste: per ogni periodo selezionato Power e, con LTO8 e/o TS4300, le relative sezioni */
  function maintSlots() {
    const slots = [];
    S.powerMaintList.slice().sort((a, b) => a - b).forEach((m) => { maintKinds(S).forEach((k) => slots.push({ months: m, kind: k })); });
    return slots;
  }
  function slotAmt(sl) {
    const v = S.powerMaintAmt[amtKey(sl.months, sl.kind)] || {};
    return { list: C.parseMoney(v.list), net: C.parseMoney(v.net) };
  }
  S.powerMaintLines = () => maintSlots().map((sl) => { const a = slotAmt(sl); return { months: sl.months, kind: sl.kind, listPrice: a.list, netPrice: a.net }; });
  S.powerMaintTotals = () => maintSlots().reduce((t, sl) => { const a = slotAmt(sl); return { list: t.list + (a.list || 0), net: t.net + (a.net || 0) }; }, { list: 0, net: 0 });
  /** periodo di manutenzione con il totale (Power + LTO8) piu' alto tra quelli selezionati: listino e netto di quel periodo */
  S.powerMaintMax = () => {
    const by = {};
    maintSlots().forEach((sl) => { const a = slotAmt(sl); const o = by[sl.months] || (by[sl.months] = { list: 0, net: 0 }); o.list += a.list || 0; o.net += a.net || 0; });
    return Object.keys(by).map((k) => by[k]).reduce((m, o) => (o.list > m.list || (o.list === m.list && o.net > m.net) ? o : m), { list: 0, net: 0 });
  };
  /** Power + LTO8 piu' alto tra le durate della scheda indicata (es. S.pwOther = Power CBU); null se gli importi non sono completi */
  function cbuMaintMax(o) {
    const by = {};
    for (const m of (o.powerMaintList || [])) {
      for (const kind of maintKinds(o)) {
        const v = (o.powerMaintAmt || {})[amtKey(m, kind)] || {}, l = C.parseMoney(v.list), n = C.parseMoney(v.net);
        if (l == null || n == null) return null;
        const t = by[m] || (by[m] = { list: 0, net: 0 }); t.list += l; t.net += n;
      }
    }
    const all = Object.keys(by).map((k) => by[k]);
    return all.length ? all.reduce((a, t) => (t.list > a.list || (t.list === a.list && t.net > a.net) ? t : a), { list: 0, net: 0 }) : null;
  }
  const maintComplete = () => S.powerMaintList.length > 0 && maintSlots().every((sl) => { const a = slotAmt(sl); return a.list != null && a.net != null; });

  // ---------------------------------------------------------------- avvio
  async function init() {
    try {
      catalog = await CPQ.api('GET', '/offers/api/catalog');
      extrasCatalog = await CPQ.api('GET', '/offers/api/extras');
      termsCfg = await CPQ.api('GET', '/offers/api/terms');
      await loadRates(null);
      try { S.powerModels = await CPQ.api('GET', '/offers/api/power-models'); } catch (e) { S.powerModels = []; }
      try { S.ibmiProducts = await CPQ.api('GET', '/offers/api/ibmi-products'); } catch (e) { S.ibmiProducts = []; }
      try { G.info = await CPQ.api('GET', '/offers/api/galileo/markets'); } catch (e) { G.info = { markets: [] }; }
      S.terms = C.defaultTerms(termsCfg, 'PROSPECT');
      if (S.offerId) {
        const data = await CPQ.api('GET', '/offers/api/' + S.offerId);
        const h = data.header;
        S.date = h.offerDate; S.progressive = h.progressive; S.initials = h.initials;
        S.title = h.projectTitle || ''; S.contactRef = h.contactRef || ''; S.discountPct = h.discountPct || 0;
        S.optPower = !!h.optPowerSystem; S.powerModelId = h.powerModelId || null; S.powerRam = h.powerRamGb ? String(h.powerRamGb) : ''; S.powerDisk = h.powerDisk || '';
        S.powerLto8 = !!h.powerLto8; S.powerTs4300 = !!h.powerTs4300; S.powerEth10 = !!h.powerEth10; S.powerUsers = h.powerIbmiUsers == null ? '' : String(h.powerIbmiUsers); S.powerUnlimited = !!h.powerIbmiUnlimited;
        S.powerCable = h.powerCable || ''; S.powerTransfer = h.powerTransfer || 'NO'; S.powerTransferRef = h.powerTransferRef || ''; S.powerEdition = h.powerEdition || 'SOLUTION'; S.powerOs = h.powerOs || '7.5'; S.powerMaint = String(h.powerMaintMonths || 36); S.powerMaintList = [Number(h.powerMaintMonths || 36)]; S.powerDisks = String(h.powerDiskCount || 2);
        S.powerMaintAmt = {}; S.optSmitech = !!h.optSmitechServices; S.optDept = h.optDepartmental !== false; S.galileoId = h.galileoApplicationId || null; S.optGalileo = !!S.galileoId;
        S.servicesMode = h.servicesMode || (h.optUseRfq ? 'RFQ' : 'MANUAL'); S.optRfq = S.servicesMode === 'RFQ'; S.optDays = h.optServicesDays !== false; S.appTotalsMode = h.appTotalsMode || 'NO'; S.rfqSheetTotal = h.rfqSheetTotal == null ? null : Number(h.rfqSheetTotal); S.optMaint = !!h.optMaintenanceAdditional; S.optServices = !!h.optExtraServices; S.optSummary = !!h.optFinalSummary;
        S.customer = await CPQ.api('GET', '/customers/api/' + h.customerId);
        const rq = data.request;
        if ((rq.powerMaint || []).length) {
          S.powerMaintList = Array.from(new Set(rq.powerMaint.map((x) => x.months))).sort((a, b) => a - b); S.powerMaint = String(S.powerMaintList[0]);
          rq.powerMaint.forEach((x) => { S.powerMaintAmt[amtKey(x.months, x.kind)] = { list: CPQ.num(x.listPrice, 2), net: CPQ.num(x.netPrice, 2) }; });
        } else if (h.powerListPrice != null) {           // offerta salvata prima dei periodi: importi storici sulla sezione Power
          S.powerMaintAmt[amtKey(S.powerMaintList[0], 'POWER')] = { list: CPQ.num(h.powerListPrice, 2), net: CPQ.num(h.powerNetPrice || 0, 2) };
        }
        S.powerProducts = {}; (rq.powerProducts || []).forEach((p) => { S.powerProducts[p.productId] = p.value; });
        S.powerKind = rq.powerKind || 'NEW'; S.hadrSolution = rq.powerHadrSolution || 'NONE'; S.pwTab = 'PROD';
        S.smidriPrice = rq.powerSmidriPrice == null ? '' : CPQ.num(rq.powerSmidriPrice, 2); S.smidriSmismp = !!rq.powerSmidriSmismp;
        S.smidriSmismpPrice = rq.powerSmidriSmismpPrice == null ? '' : CPQ.num(rq.powerSmidriSmismpPrice, 2);
        S.pwOther = S.powerKind === 'HADR' && rq.powerCbu ? cbuSlotFromRequest(rq.powerCbu) : pwFresh();      // scheda «Power CBU» (HA/DR)
        if (rq.galileo) {
          const g = rq.galileo; S.gal = { profileId: g.profileId, release: g.release || 'V9', users: g.users || '', addUsers: g.addUsers || '', source: g.source || 0, aggiunta: !!g.aggiunta, sel: {}, calc: null };
          (g.selections || []).forEach((x) => { S.gal.sel[x.moduleId] = x.state; });
        }
        S.actDiscount = rq.activityDiscountPct || 0; S.actUntil = rq.activityDiscountUntil || ''; S.actDetail = !!rq.optActivityDetail; S.hoursDetail = !!rq.optHoursDetail; S.maintOnNet = rq.maintOnNet !== false;
        S.activities = (rq.activities || []).map((a) => ({ appId: a.applicationId, desc: a.description, profileId: a.profileId, profileName: a.profileName, hours: a.hours, rate: a.rate, rfq: !!a.rfqImport, group: a.rfqGroup || '', days: a.days }));
        S.profileEdits = {}; (rq.profileRates || []).forEach((r) => { S.profileEdits[r.profileId] = Number(r.amount); });
        if (S.servicesMode !== 'RFQ') buildGroupsFromRows();
        S.distanceKm = rq.distanceKm; S.nearestSede = rq.nearestSede || ''; S.travelRateId = rq.travelRateId || null;
        if (S.distanceKm != null) S.distFor = h.customerId;
        S.lines = C.linesFromRequest(data.request);
        if (data.request.terms) { S.terms = Object.assign({}, data.request.terms); S.terms.billLicDate = S.terms.billLicDate || ''; S.terms.billFeeDate = S.terms.billFeeDate || ''; S.terms.payLicOther = S.terms.payLicOther || ''; S.terms.payFeeOther = S.terms.payFeeOther || ''; S.terms.payActOther = S.terms.payActOther || ''; S.terms.billDri = S.terms.billDri || 'INSTALL'; S.terms.billDriDate = S.terms.billDriDate || ''; S.terms.payDri = S.terms.payDri || S.terms.payFee; S.terms.payDriOther = S.terms.payDriOther || ''; S.payTouched = { lic: true, fee: true, act: true, dri: true }; }
        const ex = data.request.extras;
        if (ex) { S.xSel.services = new Set(ex.services || []); (ex.options || []).forEach((o) => { S.xSel.options[o.optionId] = o.qty; }); }
        S.lines.forEach((l) => expanded.add(l.applicationId));
        S.saved = h.number;
      } else if (root.dataset.customerId) {
        // nuova offerta avviata dall'elenco Account: cliente gia' selezionato
        try { S.customer = await CPQ.api('GET', '/customers/api/' + root.dataset.customerId); } catch (e) { toast('Account non trovato', true); }
      }
    } catch (e) { toast(e.message, true); }
    $('#maint-on-net').checked = S.maintOnNet;
    bindStep1();
    syncStep1();
    renderCustomer();
    renderFamilies();
    renderApps();
    refreshStepper();
    showStep(1);
    // nuova offerta: il cursore parte dalla ricerca dell'account per ragione sociale
    if (!S.offerId && !S.customer) setTimeout(() => { const f = $('#f-cust'); if (f) { f.focus({ preventScroll: true }); } }, 0);
    if (S.lines.length) scheduleCalc();
    if (S.gal.profileId && S.optGalileo && S.galileoId) galLoadProfile(S.gal.profileId, true);
  }

  // ---------------------------------------------------------------- step 1
  function syncStep1() {
    $('#f-date').value = S.date;
    $('#f-prog').value = S.progressive;
    $('#f-title').value = S.title;
    $('#f-ref').value = S.contactRef;
    $('#opt-maint').checked = S.optMaint; $('#opt-services').checked = S.optServices; $('#opt-summary').checked = S.optSummary;
    $('#opt-galileo').checked = S.optGalileo; $('#galileo-box').hidden = !S.optGalileo;
    renderPower();
    syncServicesUi(); $('#opt-days').checked = S.optDays; $('#opt-apptot').value = S.appTotalsMode; $('#svc-detail').checked = S.actDetail; $('#opt-hours-detail').checked = S.hoursDetail; $('#opt-power').checked = S.optPower; renderPwKind(); $('#opt-smitech').checked = S.optSmitech; $('#opt-dept').checked = S.optDept;
    renderGalileo();
    loadRevisions();
    refreshDocOptions();
    updateNumber();
  }

  /** revisione corrente (sola lettura) e storia delle revisioni dell'offerta, con i collegamenti alle altre revisioni */
  async function loadRevisions() {
    if (!S.offerId) return;
    try {
      const r = await CPQ.api('GET', '/offers/api/' + S.offerId + '/revisions');
      $('#rev-superseded').hidden = !r.superseded;
      const box = $('#rev-history');
      box.hidden = !r.history.length;
      box.classList.add('keep');
      const latest = r.history.length ? r.history[0].id : null;
      const chipStyle = 'border:0;cursor:pointer;font:inherit;height:auto;padding:4px 12px';          // stessa dimensione del carattere e stesso colore per tutte le revisioni
      const when = (x) => { const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(x.createdAt || ''); return m ? m[3] + '/' + m[2] + '/' + m[1] + ' ' + m[4] + ':' + m[5] : ''; };
      box.innerHTML = '<div style="font-weight:500;margin-bottom:6px">Storico revisioni <span class="muted" style="font-weight:400">(dalla più recente)</span></div><div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">' + r.history.map((x) => {
        const label = 'Rev. ' + x.revision + (x.id === latest ? ' · attuale' : '') + ' · ' + when(x) + (x.author ? ' - ' + x.author : '') + (x.changes ? ' · ' + x.changes + (x.changes === 1 ? ' modifica' : ' modifiche') : '');
        const tip = esc((x.author || '') + (x.createdAt ? ' – ' + when(x) : ''));
        // l'ultima revisione e' un collegamento; sulle precedenti il clic apre il menu (Visualizza revisione / Promuovi ad attuale)
        if (x.current) return '<span class="chip rev-now" title="' + tip + '" aria-current="true" style="' + chipStyle.replace('cursor:pointer', 'cursor:default') + '">' + esc(label) + '</span>';          // revisione aperta: non cliccabile
        return x.id === latest
          ? '<a class="chip blue" href="' + esc(CPQ.url('/offers/' + x.id + '/edit')) + '" title="' + tip + '" style="' + chipStyle + '">' + esc(label) + '</a>'
          : '<button type="button" class="chip blue keep rev-chip" data-id="' + x.id + '" data-rev="' + x.revision + '" title="' + tip + '" aria-haspopup="menu" style="' + chipStyle + '">' + esc(label) + ' ▾</button>';
      }).join('') + (r.history.length > 1 ? '<button type="button" class="btn tonal sm keep rev-compare">Confronta revisioni</button>' : '') + '</div>';
      const cmpBtn = box.querySelector('.rev-compare');
      if (cmpBtn) cmpBtn.addEventListener('click', () => openRevCompare(r.history));
      let menu = null;
      const closeMenu = () => { if (menu) { menu.remove(); menu = null; document.removeEventListener('click', onDoc, true); document.removeEventListener('keydown', onKey); } };
      const onDoc = (e) => { if (menu && !menu.contains(e.target)) closeMenu(); };
      const onKey = (e) => { if (e.key === 'Escape') closeMenu(); };
      box.querySelectorAll('.rev-chip').forEach((b) => b.addEventListener('click', (e) => {
        e.stopPropagation();
        const same = menu && menu.dataset.for === b.dataset.id; closeMenu(); if (same) return;
        menu = document.createElement('div'); menu.className = 'cg-rowmenu'; menu.setAttribute('role', 'menu'); menu.dataset.for = b.dataset.id;
        menu.innerHTML = '<button type="button" role="menuitem" data-m="view"><svg class="ic"><use href="#i-view"/></svg>Visualizza revisione</button>' +
          '<button type="button" role="menuitem" data-m="promote"><svg class="ic"><use href="#i-restore"/></svg>Promuovi ad attuale</button>';
        document.body.appendChild(menu);
        const rc = b.getBoundingClientRect();
        menu.style.left = Math.max(8, Math.min(window.innerWidth - menu.offsetWidth - 8, rc.left)) + 'px'; menu.style.top = (rc.bottom + 4) + 'px';
        menu.addEventListener('click', async (ev) => {
          const m = ev.target.closest('[data-m]'); if (!m) return;
          closeMenu();
          if (m.dataset.m === 'view') { window.location.href = CPQ.url('/offers/' + b.dataset.id + '/edit'); return; }
          if (!(await CPQ.confirm('Promuovere la revisione ' + b.dataset.rev + ' ad attuale? Verrà creata una nuova revisione, copia della ' + b.dataset.rev + ', che diventa quella in gestione.', { title: 'Promuovi revisione', yes: 'Sì, promuovi' }))) return;
          try { const res = await CPQ.api('POST', '/offers/api/' + b.dataset.id + '/promote', {}); CPQ.clean(); window.location.href = CPQ.url('/offers/' + res.id + '/edit'); }
          catch (er) { toast(er.message, true); }
        });
        setTimeout(() => { document.addEventListener('click', onDoc, true); document.addEventListener('keydown', onKey); }, 0);
      }));
      if (r.superseded) setReadOnly();
    } catch (_) { /* storico non critico */ }
  }

  /** finestra modale: scelta di N revisioni (almeno 2), tabella di confronto (colonne = revisioni, righe = campi) e modifiche memorizzate di ogni revisione */
  function openRevCompare(history) {
    const dlg = document.createElement('dialog');
    dlg.className = 'rev-dlg keep';
    const when = (x) => { const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(x.createdAt || ''); return m ? m[3] + '/' + m[2] + '/' + m[1] + ' ' + m[4] + ':' + m[5] : ''; };
    dlg.innerHTML = '<div class="dlg-head"><h3 style="margin:0">Confronto revisioni</h3><div class="muted">Scegli due o più revisioni da confrontare</div></div>' +
      '<div class="dlg-body"><div class="rev-pick">' + history.slice().reverse().map((x) =>
        '<label class="check"><input type="checkbox" class="rev-sel" value="' + x.id + '"> Rev. ' + x.revision + ' <span class="muted">' + esc(when(x)) + (x.author ? ' - ' + esc(x.author) : '') + '</span></label>').join('') + '</div>' +
      '<div class="rev-bar"><label class="check"><input type="checkbox" id="rev-only" checked> Mostra solo le differenze</label>' +
      '<span class="grow"></span><button type="button" class="btn text sm" id="rev-tab-cmp">Confronto</button><button type="button" class="btn text sm" id="rev-tab-chg">Modifiche per revisione</button></div>' +
      '<div id="rev-out" class="tablewrap"></div></div>' +
      '<div class="dlg-foot"><button type="button" class="btn" id="rev-close">Chiudi</button></div>';
    document.body.appendChild(dlg);
    const out = dlg.querySelector('#rev-out');
    let tab = 'cmp';
    const val = (v) => (v == null || v === '' ? '<span class="muted">—</span>' : esc(v));
    async function run() {
      const ids = Array.from(dlg.querySelectorAll('.rev-sel:checked')).map((i) => i.value);
      if (tab === 'chg') {
        if (!ids.length) { out.innerHTML = ''; return; }
        out.innerHTML = 'Caricamento…';
        const parts = [];
        for (const x of history.slice().sort((a, b) => b.revision - a.revision)) {
          if (!ids.includes(String(x.id))) continue;
          const ch = await CPQ.api('GET', '/offers/api/' + x.id + '/changes');
          parts.push('<h4 style="margin:14px 0 6px">Rev. ' + x.revision + ' rispetto alla precedente</h4>' + (ch.length
            ? '<table class="t"><thead><tr><th>Sezione</th><th>Campo</th><th>Prima</th><th>Dopo</th></tr></thead><tbody>' + ch.map((c) => '<tr><td>' + esc(c.section) + '</td><td>' + esc(c.field) + '</td><td>' + val(c.oldValue) + '</td><td><b>' + val(c.newValue) + '</b></td></tr>').join('') + '</tbody></table>'
            : '<div class="muted">' + (x.revision === 0 || x === history[history.length - 1] ? 'Revisione iniziale: nessun confronto.' : 'Nessuna modifica rispetto alla revisione precedente.') + '</div>'));
        }
        out.innerHTML = parts.join('');
        return;
      }
      if (!ids.length) { out.innerHTML = ''; return; }                       // nessuna revisione scelta: tabella vuota
      if (ids.length < 2) { out.innerHTML = '<div class="muted">Seleziona almeno un\'altra revisione da confrontare.</div>'; return; }
      out.innerHTML = 'Caricamento…';
      try {
        const c = await CPQ.api('GET', '/offers/api/' + S.offerId + '/compare?ids=' + ids.join(','));
        const only = dlg.querySelector('#rev-only').checked;
        let h = '<table class="t rev-cmp"><thead><tr><th>Campo</th>' + c.columns.map((x) => '<th>' + esc(x.label) + '</th>').join('') + '</tr></thead><tbody>';
        let any = false;
        c.sections.forEach((sec) => {
          const rows = sec.rows.filter((r) => !only || r.changed);
          if (!rows.length) return;
          any = true;
          h += '<tr class="rev-sec"><td colspan="' + (c.columns.length + 1) + '"><b>' + esc(sec.name) + '</b></td></tr>' +
            rows.map((r) => '<tr class="' + (r.changed ? 'rev-diff' : '') + '"><td>' + esc(r.field) + '</td>' + r.values.map((v) => '<td>' + val(v) + '</td>').join('') + '</tr>').join('');
        });
        out.innerHTML = any ? h + '</tbody></table>' : '<div class="muted">Nessuna differenza tra le revisioni scelte.</div>';
      } catch (e) { out.innerHTML = '<div class="alert err"><span>' + esc(e.message) + '</span></div>'; }
    }
    dlg.addEventListener('change', (e) => { if (e.target.matches('.rev-sel, #rev-only')) run(); });
    dlg.querySelector('#rev-tab-cmp').addEventListener('click', () => { tab = 'cmp'; run(); });
    dlg.querySelector('#rev-tab-chg').addEventListener('click', () => { tab = 'chg'; run(); });
    dlg.querySelector('#rev-close').addEventListener('click', () => dlg.close());
    dlg.addEventListener('close', () => dlg.remove());
    dlg.showModal();
    run();
  }

  /** revisione superata: l'offerta e' di sola consultazione (campi e pulsanti di modifica disabilitati, resta la navigazione tra le pagine) */
  function setReadOnly() {
    if (S.readOnly) return;
    S.readOnly = true;
    const nav = /^btn-(next|back|to)\d*$/;
    const lock = () => {
      root.querySelectorAll('[data-step] input, [data-step] select, [data-step] textarea, [data-step] button, #btn-save').forEach((el) => {
        if (el.closest('.keep') || el.classList.contains('keep') || nav.test(el.id) || el.id === 'btn-save-docx') return;
        if (!el.disabled) el.disabled = true;
      });
    };
    lock();
    let t = null;
    new MutationObserver(() => { clearTimeout(t); t = setTimeout(lock, 30); }).observe(root, { childList: true, subtree: true });
    document.body.classList.add('wiz-ro');
    refreshSaveState();
  }

  function updateNumber() {
    $('#f-number').value = C.offerNumber(S.date, S.progressive, S.initials);
  }

  function bindStep1() {
    $('#f-date').addEventListener('change', async (e) => {
      S.date = e.target.value; updateNumber();
      if (!S.offerId && S.date) {
        try {
          const r = await CPQ.api('GET', '/offers/api/next-progressive?date=' + encodeURIComponent(S.date) + (S.customer ? '&customerId=' + encodeURIComponent(S.customer.id) : ''));
          S.progressive = r.progressive; $('#f-prog').value = S.progressive; updateNumber();
        } catch (_) { /* suggerimento non critico */ }
      }
    });
    $('#f-prog').addEventListener('input', (e) => { S.progressive = e.target.value; updateNumber(); });
    $('#f-title').addEventListener('input', (e) => { S.title = e.target.value; });
    $('#f-ref').addEventListener('input', (e) => { S.contactRef = e.target.value; });

    // selezione cliente
    const input = $('#f-cust'); const list = $('#cust-list');
    let timer;
    const search = () => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        try {
          const rows = await CPQ.api('GET', '/customers/api/search?q=' + encodeURIComponent(input.value));
          list.innerHTML = rows.length ? rows.map((c) =>
            '<div class="item" data-id="' + c.id + '"><b>' + esc(c.companyName) + '</b><span class="sub">' + esc(c.code) + ' · ' + esc(c.city) +
            ' · P.IVA ' + esc(c.vatNumber) + ' · ' + (c.type === 'CLIENTE' ? 'Cliente' : 'Prospect') + '</span></div>').join('')
            : '<div class="item muted">Nessun account trovato: usa "Nuovo account"</div>';
          list.hidden = false;
          list._rows = rows;
        } catch (e) { toast(e.message, true); }
      }, 200);
    };
    input.addEventListener('input', search);
    input.addEventListener('focus', search);
    document.addEventListener('click', (e) => { if (!$('.combo').contains(e.target)) list.hidden = true; });
    list.addEventListener('click', (e) => {
      const it = e.target.closest('.item[data-id]'); if (!it) return;
      S.customer = (list._rows || []).find((c) => String(c.id) === it.dataset.id);
      list.hidden = true; input.value = ''; renderCustomer();
    });

    $('#btn-newcust').addEventListener('click', openCustomerDialog);
    $('#cust-dialog-form').addEventListener('submit', submitCustomer);
    $('#cust-cancel').addEventListener('click', () => $('#cust-dialog').close());
    CPQ.bindVatLookup($('#cust-dialog-form'), $('#cust-vat-btn'), $('#cust-vatmsg'), (c) => {
      S.customer = c; $('#cust-dialog').close(); renderCustomer(); toast('Selezionato l\'account ' + c.code);
    });
  }

  // tariffe applicabili: listino oppure tariffe dedicate dell'account (profili professionali e trasferte)
  let ratesFor = undefined;
  async function loadRates(customerId) {
    try {
      const rt = await CPQ.api('GET', '/offers/api/rates' + (customerId ? '?customerId=' + customerId : ''));
      S.travelRates = rt.travel || []; S.profiles = rt.profiles || []; S.accountTravelRateId = rt.accountTravelRateId || null;
      S.profilesBase = {}; S.profiles.forEach((p) => { S.profilesBase[p.id] = p.amount; });
    } catch (e) { S.travelRates = []; S.profiles = []; }
    ratesFor = customerId || null;
  }
  // ---------------------------------------------------------------- tariffe per profilo professionale (pagina Contenuti)
  /** sovrappone alle tariffe di partenza (account o listino) quelle modificate nell'offerta */
  function applyProfileEdits() {
    S.profiles.forEach((p) => { if (S.profileEdits[p.id] != null) p.amount = S.profileEdits[p.id]; });
  }
  function repriceActivities() {
    S.activities.forEach((a) => { const p = S.profiles.find((x) => x.id === Number(a.profileId)); if (p) a.rate = p.amount; });
  }
  function renderProfileRates() {
    const body = $('#rates-body'); if (!body) return;
    const rows = S.profiles || [];
    body.innerHTML = rows.length ? rows.map((p) => {
      const baseAmt = S.profilesBase && S.profilesBase[p.id] != null ? S.profilesBase[p.id] : p.amount, edited = S.profileEdits[p.id] != null;
      return '<tr data-pid="' + p.id + '"><td>' + esc(p.name) + (p.custom ? ' <span class="chip blue">tariffa account</span>' : '') + (edited ? ' <span class="chip warn">modificata</span>' : '') + '</td>' +
        '<td class="num">' + CPQ.eur(baseAmt) + '</td>' +
        '<td class="num"><input class="rate-in" inputmode="decimal" value="' + esc(Number(p.amount || 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })) + '" style="width:110px;text-align:right;height:36px;border:1px solid var(--outline);border-radius:6px;padding:0 8px;font:inherit;background:var(--surface);color:var(--on-surface)"></td></tr>';
    }).join('') : '<tr><td colspan="3" class="muted">Nessun profilo professionale in anagrafica.</td></tr>';
    $('#rates-reset').disabled = !Object.keys(S.profileEdits).length;
    $('#rates-hint').textContent = S.customer && S.profiles.some((p) => p.custom) ? 'Listino del cliente' : 'Listino';
  }
  const fmtRate = (v) => Number(v || 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  /** aggiorna la riga senza ridisegnare la tabella: il campo mantiene il fuoco e Tab passa al campo successivo */
  function updateRateRow(tr, p) {
    const edited = S.profileEdits[p.id] != null;
    tr.firstElementChild.innerHTML = esc(p.name) + (p.custom ? ' <span class="chip blue">tariffa account</span>' : '') + (edited ? ' <span class="chip warn">modificata</span>' : '');
    tr.querySelector('.rate-in').value = fmtRate(p.amount);
    $('#rates-reset').disabled = !Object.keys(S.profileEdits).length;
  }
  function bindProfileRates() {
    $('#rates-body').addEventListener('change', (e) => {
      const tr = e.target.closest('tr[data-pid]'); if (!tr || !e.target.classList.contains('rate-in')) return;
      const id = Number(tr.dataset.pid), p = S.profiles.find((x) => x.id === id); if (!p) return;
      const v = C.parseMoney(e.target.value);
      if (v == null || !isFinite(v) || v < 0 || v > 99999.99) { toast('Tariffa oraria non valida', true); e.target.value = fmtRate(p.amount); return; }
      const baseAmt = S.profilesBase[id];
      if (Math.abs(v - baseAmt) < 0.005) delete S.profileEdits[id]; else S.profileEdits[id] = Math.round(v * 100) / 100;
      p.amount = S.profileEdits[id] != null ? S.profileEdits[id] : baseAmt;
      repriceActivities(); updateRateRow(tr, p); CPQ.markDirty('page', root); updateAmounts(); refreshSaveState();
    });
    $('#rates-reset').addEventListener('click', () => {
      S.profileEdits = {}; S.profiles.forEach((p) => { if (S.profilesBase[p.id] != null) p.amount = S.profilesBase[p.id]; });
      repriceActivities(); renderProfileRates(); CPQ.markDirty('page', root); updateAmounts(); refreshSaveState();
    });
  }

  /** al cambio di account ricarica le tariffe e riprezza le righe di attività e la trasferta suggerita */
  async function refreshRates() {
    const id = S.customer ? S.customer.id : null;
    if (id === ratesFor) return;
    const changedAccount = ratesFor !== undefined && ratesFor !== null && id !== ratesFor;
    if (changedAccount) S.profileEdits = {};          // altro account: valgono le sue tariffe
    await loadRates(id);
    applyProfileEdits();
    // spese di trasferta assegnate all'account: proposte se non c'e' ancora una scelta o se cambia account
    if (changedAccount) { S.travelRateId = S.accountTravelRateId || null; S.distanceKm = null; S.nearestSede = ''; S.distFor = null; }
    else if (S.accountTravelRateId && !S.travelRateId) S.travelRateId = S.accountTravelRateId;
    S.activities.forEach((a) => { const p = S.profiles.find((x) => x.id === Number(a.profileId)); if (p) { a.rate = p.amount; a.profileName = p.name; } });
    renderTravel(); renderProfileRates();
    if (S.step === 5) renderServices(); else updateAmounts();
  }

  /** agente dell'account (sola lettura): nome decodificato dall'anagrafica; in una nuova offerta le sue iniziali compongono il numero */
  async function loadAgent() {
    const c = S.customer;
    const show = (t) => { S.agentText = t; const dd = $('#cust-agent'); if (dd) dd.textContent = t; };
    if (!c) { show(''); return; }
    show('…');
    try {
      const r = await CPQ.api('GET', '/offers/api/agent?customerId=' + encodeURIComponent(c.id));
      if (!S.customer || S.customer.id !== c.id) return;
      show(r.name ? r.name + (r.code ? ' (' + r.code + ')' : '') : (r.code || '— nessun agente —'));
      if (!S.offerId) {
        S.initials = r.initials || S.initials;
        if (S.date) {
          const n = await CPQ.api('GET', '/offers/api/next-progressive?date=' + encodeURIComponent(S.date) + '&customerId=' + encodeURIComponent(c.id));
          S.progressive = n.progressive; $('#f-prog').value = S.progressive; S.initials = n.initials || S.initials;
        }
        updateNumber();
      }
    } catch (_) { show(''); }
  }

  function renderCustomer() {
    refreshRates();
    applyDefaultPay();
    loadAgent();
    HW.cust = null; if (S.powerTransfer !== 'NO') pwHwLoad();
    const box = $('#custbox'); const c = S.customer;
    if (!c) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML =
      '<dt>Codice</dt><dd><b>' + esc(c.code) + '</b> <span class="chip ' + (c.type === 'CLIENTE' ? 'ok' : 'blue') + '">' + (c.type === 'CLIENTE' ? 'Cliente' : 'Prospect') + '</span></dd>' +
      '<dt>Ragione sociale</dt><dd>' + esc(c.companyName) + '</dd>' +
      '<dt>Indirizzo</dt><dd>' + esc(c.streetType + ' ' + c.streetName + ', ' + c.streetNumber) + ' – ' + esc(c.postalCode + ' ' + c.city + (c.province ? ' (' + c.province.toUpperCase() + ')' : '')) + '</dd>' +
      '<dt>P.IVA / C.F.</dt><dd><a href="#" class="atoka-vat" data-vat="' + esc(c.vatNumber) + '" title="Apri i dettagli su ATOKA">' + esc(c.vatNumber) + '</a>' + (c.taxCode ? ' / ' + esc(c.taxCode) : '') + '</dd>' +
      '<dt>Agente</dt><dd id="cust-agent">' + esc(S.agentText || '') + '</dd>';
  }

  // nuova anagrafica dal wizard
  function openCustomerDialog() {
    const f = $('#cust-dialog-form'); f.reset();
    $$('.field', f).forEach((x) => x.classList.remove('invalid'));
    $$('.field .err', f).forEach((x) => { x.textContent = ''; });
    $('#cust-formerr').hidden = true; $('#cust-vatmsg').hidden = true;
    setTimeout(() => f.elements.vatNumber.focus(), 50);
    $('#cust-dialog').showModal();
  }

  async function submitCustomer(e) {
    e.preventDefault();
    const f = e.target; const body = { type: 'PROSPECT' };
    ['companyName', 'streetType', 'streetName', 'streetNumber', 'postalCode', 'city', 'province', 'vatNumber', 'taxCode'].forEach((k) => { body[k] = f.elements[k].value.trim(); });
    $$('.field', f).forEach((x) => x.classList.remove('invalid'));
    $$('.field .err', f).forEach((x) => { x.textContent = ''; });
    // validazione lato client dei campi obbligatori (ripetuta lato server)
    const req = { companyName: 'Ragione sociale', streetType: 'Via', streetName: 'Indirizzo', streetNumber: 'Numero civico', postalCode: 'CAP', city: 'Città', vatNumber: 'Partita IVA' };
    let ko = false;
    Object.keys(req).forEach((k) => { if (!body[k]) { fieldError(f, k, req[k] + ' obbligatorio/a'); ko = true; } });
    if (ko) return;
    try {
      S.customer = await CPQ.api('POST', '/customers/api', body);
      $('#cust-dialog').close(); renderCustomer();
      toast('Account creato: codice ' + S.customer.code);
    } catch (err) {
      if (err.errors) Object.keys(err.errors).forEach((k) => fieldError(f, k, err.errors[k]));
      else { const b = $('#cust-formerr'); b.hidden = false; b.querySelector('span').textContent = err.message; }
    }
  }
  function fieldError(form, name, msg) {
    const input = form.elements[name]; if (!input) return;
    const wrap = input.closest('.field'); wrap.classList.add('invalid');
    wrap.querySelector('.err').textContent = msg;
  }

  // ---------------------------------------------------------------- navigazione
  /** il passo "Servizi accessori" c'e' solo se richiesto nella testata: rinumera i passi visibili */
  // passi e visibilita': Dipartimentali (2) e Servizi (5) solo con «Dipartimentali» e almeno un'applicazione; Servizi accessori (3) solo se richiesti
  const ORDER_NORMAL = [1, 6, 8, 7, 9, 2, 5, 3, 4], ORDER_RFQ = [1, 6, 5, 8, 7, 9, 2, 3, 4];     // con «Usa RFQ» i Servizi seguono subito gli Elementi
  const orderAll = () => (S.optRfq ? ORDER_RFQ : ORDER_NORMAL);
  function stepVisible(n) {
    if (n === 7) return S.optPower;
    if (n === 9) return S.optSmitech;
    if (n === 8) return galHasListino();
    if (n === 2) return S.optDept;
    if (n === 5) return S.servicesMode !== 'NONE';
    if (n === 3) return S.optServices;
    return true;
  }
  /** c'e' qualcosa da offrire: applicazioni (con Dipartimentali) oppure altri elementi senza Dipartimentali */
  function hasContent() {
    return S.lines.length > 0 || (S.servicesMode !== 'NONE' && C.activityRows(S.activities, S.lines).length > 0) || (!S.optDept && ((S.optPower && !!S.powerModelId) || S.optSmitech || (S.optGalileo && !!S.galileoId)));
  }
  const nextStep = (n) => orderAll().slice(orderAll().indexOf(n) + 1).find(stepVisible) || n;
  const prevStep = (n) => orderAll().slice(0, orderAll().indexOf(n)).reverse().find(stepVisible) || n;

  function refreshStepper() {
    $('#st-extras').hidden = !S.optServices; $('#bar-extras').hidden = !S.optServices;
    $('#st-power').hidden = !stepVisible(7); $('#bar-power').hidden = !stepVisible(7);
    $('#st-smitech').hidden = !stepVisible(9); $('#bar-smitech').hidden = !stepVisible(9);
    $('#st-galileo').hidden = !stepVisible(8); $('#bar-galileo').hidden = !stepVisible(8);
    $('#st-dept').hidden = !stepVisible(2); $('#bar-dept').hidden = !stepVisible(2);
    $('#st-services').hidden = !stepVisible(5); $('#bar-services').hidden = !stepVisible(5);
    // «Usa RFQ»: il passo Servizi si sposta subito dopo Elementi (la barra precede sempre il passo)
    const barS = $('#bar-services'), stS = $('#st-services'), stEl = $('.step[data-go="6"]'), stDept = $('#st-dept');
    if (barS && stS && stEl && stDept) {
      if (S.optRfq) { stEl.after(barS); barS.after(stS); }
      else { const anchor = $('#bar-extras'); if (anchor) { anchor.before(barS); barS.after(stS); } }
    }
    updateHeaderNav();
    let n = 0;
    $$('.step').forEach((s) => { if (!s.hidden) { n++; s.querySelector('.dot').textContent = n; } });
  }

  function showStep(n) {
    if (n !== 7 && S.pwTab === 'CBU') pwSwitchTab('PROD', false);      // fuori dalla pagina Power lo stato attivo e' sempre quello di produzione
    S.step = n;
    $$('[data-step]').forEach((p) => { p.hidden = Number(p.dataset.step) !== n; });
    const ORDER = orderAll();                                // ordine di navigazione (5 = Servizi, dopo i Dipartimentali)
    $$('.step').forEach((s) => {
      const k = Number(s.dataset.go);
      s.classList.toggle('active', k === n); s.classList.toggle('done', ORDER.indexOf(k) < ORDER.indexOf(n));
    });
    $('#totalbar').hidden = n === 1 || n === 6;
    if (n === 5) renderServices();
    if (n === 9) renderSmitech();
    if (n === 6) renderProfileRates();
    if (n === 8) galEnter();
    if (n === 3) renderExtras();
    if (n === 4) renderSummary();
    updateHeaderNav();
    window.scrollTo({ top: 0 });
  }

  /** pulsanti pagina precedente / successiva in alto a destra */
  function updateHeaderNav() {
    const p = $('#hd-prev'), nx = $('#hd-next'); if (!p || !nx) return;
    p.disabled = prevStep(S.step) === S.step; nx.disabled = nextStep(S.step) === S.step;
  }

  /** mercati Galileo: applicazioni della famiglia GALILEO (non compaiono nell'elenco delle applicazioni) */
  const GALILEO = 'GALILEO';
  /** scenario MODULI AGGIUNTIVI: gli altri contenuti sono oscurati, il Modello Light e' obbligatorio, niente totali per applicazione né manutenzione «in aggiunta» */
  const galAgg = () => { const a = S.optGalileo && S.galileoId ? appOf(S.galileoId) : null; return !!a && a.code === GAL_AGG_CODE; };
  function refreshDocOptions() {
    const agg = galAgg();
    $('#opt-maint-hint').hidden = agg || !S.optSummary || !S.optMaint;
    $('#row-apptot').hidden = agg || !S.optSummary;
    $('#row-detail').hidden = !S.optSummary;
    const rh = $('#row-hours-detail'); if (rh) rh.hidden = S.optSummary;          // «Dettaglio ore»: solo senza Modello Light
    const mw = $('#opt-maint').closest('label'); if (mw) mw.hidden = agg;
    const sum = $('#opt-summary'); sum.checked = S.optSummary; sum.disabled = agg;
    const sm = $('#sel-svc-mode'); if (sm) { sm.disabled = agg; if (agg) sm.value = 'MANUAL'; }          // MODULI AGGIUNTIVI: Servizi solo in modalità Manuale
    ['#opt-power', '#opt-smitech', '#opt-dept', '#opt-services'].forEach((id) => {          // «Servizi» resta utilizzabile
      const el = $(id); if (!el) return;
      el.disabled = agg;
      const w = el.closest('label.check') || el.closest('.field'); if (w) w.classList.toggle('opt-dim', agg);
    });
  }
  /** entrando in MODULI AGGIUNTIVI: toglie le altre componenti (previa conferma se ci sono dati) e imposta Modello Light, senza totali applicazioni */
  async function enterAggMode() {
    const has = S.lines.length || S.activities.some((a) => !a.rfq && a.appId) || S.optPower || S.optSmitech || S.optServices || S.optMaint;
    if (has && !(await CPQ.confirm('Con «MODULI AGGIUNTIVI» le altre componenti dell\'offerta (Dipartimentali, Power System, SMITECH, Servizi accessori, e le attività legate alle applicazioni) vengono tolte. Continuare?',
      { title: 'Moduli aggiuntivi', yes: 'Sì, togli', no: 'No, mantieni', danger: true }))) return false;
    S.lines = []; calc = null; expanded.clear();
    if (S.servicesMode === 'RFQ') { S.servicesMode = 'MANUAL'; S.optRfq = false; S.activities = S.activities.filter((a) => !a.rfq); S.rfqSheetTotal = null; }      // solo modalità Manuale
    S.activities = S.activities.filter((a) => a.rfq || !a.appId);          // «Servizi» resta: si tengono le attività non legate a un'applicazione
    S.svcGroups = (S.svcGroups || []).filter((g) => !g.appId);
    S.optDept = false; S.optServices = false; S.optMaint = false;
    S.optPower = false; S.powerModelId = null; S.powerRam = ''; S.powerDisk = ''; S.optSmitech = false;
    S.optSummary = true; S.appTotalsMode = 'NO';
    if (pwIsCbu()) pwSwap();
    S.powerKind = 'NEW'; S.pwOther = pwFresh(); renderPwKind();
    $('#opt-power').checked = false; $('#opt-smitech').checked = false; $('#opt-dept').checked = false; $('#opt-services').checked = false; $('#opt-maint').checked = false;
    $('#opt-apptot').value = 'NO';
    renderPower(); syncServicesUi(); renderApps(); scheduleCalc();
    return true;
  }
  const GAL_AGG_CODE = 'CRM_JGAL_MODULI';            // applicazione «AGGIUNTA MODULI»: compare in fondo all'elenco come «MODULI AGGIUNTIVI»
  const galAppName = (a) => (a && a.code === GAL_AGG_CODE ? 'MODULI AGGIUNTIVI' : (a ? a.name : ''));
  function renderGalileo() {
    const list = catalog.filter((a) => a.family === GALILEO);
    const opt = (a) => '<option value="' + a.id + '"' + (S.galileoId === a.id ? ' selected' : '') + '>' + esc(galAppName(a)) + '</option>';
    const agg = list.filter((a) => a.code === GAL_AGG_CODE);
    $('#sel-galileo').innerHTML = '<option value="">— seleziona —</option>' + list.filter((a) => a.code !== GAL_AGG_CODE).map(opt).join('') +
      (agg.length ? '<option value="" disabled>──────────</option>' + agg.map(opt).join('') : '');
  }

  // ---------------------------------------------------------------- IBM Power System (elemento + pagina di configurazione)
  const powerModelOf = (id) => S.powerModels.find((m) => m.id === Number(id));
  const powerModelLabel = (m) => m.version + ' – ' + m.description + ' (' + m.type + (m.processorGroup ? ', ' + m.processorGroup : '') + ', CPW ' + CPQ.num(m.cpw) + ')';
  const RAM_ALL = ['32', '64', '96', '128', '256', '384', '512'], DISK_ALL = ['1.6TB', '3.2TB', '6.4TB'];
  /** tagli supportati dal modello (elenco vuoto in anagrafica = tutti) */
  const supported = (csv, all) => { const l = String(csv || '').split(',').filter(Boolean); return l.length ? all.filter((x) => l.includes(x)) : all; };

  /** menu RAM e disco con i soli tagli supportati dal modello; azzera la scelta se non e' piu' valida */
  function renderPowerOptions() {
    const m = powerModelOf(S.powerModelId);
    const rams = supported(m && m.ramOptions, RAM_ALL), disks = supported(m && m.diskOptions, DISK_ALL);
    if (S.powerRam && !rams.includes(S.powerRam)) S.powerRam = '';
    if (S.powerDisk && !disks.includes(S.powerDisk)) S.powerDisk = '';
    // una sola opzione disponibile: selezione automatica
    if (m && !S.powerRam && rams.length === 1) S.powerRam = rams[0];
    if (m && !S.powerDisk && disks.length === 1) S.powerDisk = disks[0];
    // senza modello si propongono tutti i tagli; scelto il modello restano solo quelli supportati
    const ph = '<option value="">— seleziona —</option>';
    $('#pw-ram').innerHTML = ph + rams.map((r) => '<option value="' + r + '">' + r + ' GB</option>').join('');
    $('#pw-disk').innerHTML = ph + disks.map((d) => '<option value="' + d + '">' + d.replace('TB', ' TB') + '</option>').join('');
    $('#pw-ram').value = S.powerRam; $('#pw-disk').value = S.powerDisk;
    $('#pw-ram').disabled = false; $('#pw-disk').disabled = false;
  }

  // ---- tipo di Power: «Nuovo Power» / «Power HA/DR» (due schede: Produzione e CBU).
  // La pagina lavora sempre sui campi S.power*: la scheda inattiva tiene i suoi valori in S.pwOther e le schede si scambiano.
  const PW_KEYS = ['powerModelId', 'powerRam', 'powerDisk', 'powerLto8', 'powerTs4300', 'powerEth10', 'powerUsers', 'powerUnlimited', 'powerCable', 'powerTransfer', 'powerTransferRef', 'powerEdition', 'powerOs', 'powerMaint', 'powerMaintList', 'powerMaintAmt', 'powerDisks', 'powerProducts'];
  const pwFresh = () => ({ powerModelId: null, powerRam: '', powerDisk: '', powerLto8: false, powerTs4300: false, powerEth10: false, powerUsers: '', powerUnlimited: false, powerCable: '', powerTransfer: 'NO', powerTransferRef: '', powerEdition: 'SOLUTION', powerOs: '7.5', powerMaint: '36', powerMaintList: [36], powerMaintAmt: {}, powerDisks: '2', powerProducts: {} });
  const pwPick = () => { const o = {}; PW_KEYS.forEach((k) => { o[k] = S[k]; }); return o; };
  const pwLoad = (o) => { PW_KEYS.forEach((k) => { S[k] = o[k]; }); };
  S.powerKind = 'NEW'; S.pwTab = 'PROD'; S.pwOther = pwFresh();
  S.hadrSolution = 'NONE'; S.smidriPrice = ''; S.smidriSmismp = false; S.smidriSmismpPrice = '';
  const pwIsCbu = () => S.pwTab === 'CBU';
  function pwSwap() { const cur = pwPick(); pwLoad(S.pwOther); S.pwOther = cur; S.pwTab = S.pwTab === 'PROD' ? 'CBU' : 'PROD'; }
  /** esegue fn con i dati di produzione attivi (salvataggio, calcolo): la scheda CBU, se aperta, viene scambiata e poi ripristinata */
  function withProd(fn) {
    if (!pwIsCbu()) return fn();
    pwSwap();
    try { return fn(); } finally { pwSwap(); }
  }
  /** utenti IBM i della scheda CBU imposti dai core del modello scelto in Produzione: 8 core -> 10, 4 core -> 5 (altri core: null, campo libero) */
  const CBU_USERS_BY_CORES = { 8: 10, 4: 5 };
  function cbuAutoUsers() {
    if (S.powerKind !== 'HADR') return null;
    const prodId = pwIsCbu() ? S.pwOther.powerModelId : S.powerModelId;
    const m = prodId ? powerModelOf(prodId) : null;
    return m && CBU_USERS_BY_CORES[Number(m.cores)] != null ? CBU_USERS_BY_CORES[Number(m.cores)] : null;
  }
  /** allinea gli utenti salvati della scheda CBU (anche se inattiva) alla regola sopra */
  function applyCbuUsers() {
    if (S.powerKind !== 'HADR') return;
    const slot = pwIsCbu() ? S : S.pwOther;
    slot.powerUnlimited = false;                         // nella scheda CBU gli utenti ILLIMITATI non esistono
    const n = cbuAutoUsers(); if (n == null) return;
    slot.powerUsers = String(n);
  }

  /** richiesta al server: la Produzione e, con Power HA/DR, la scheda CBU (campi tipizzati come la Produzione, senza i prodotti IBM i) */
  function buildReq() {
    applyCbuUsers();
    return withProd(() => {
      const r = C.buildRequest(S);
      r.powerCbu = null;
      if (S.optPower && S.powerKind === 'HADR') {
        pwSwap();                                              // i campi S.power* sono quelli della CBU
        try {
          r.powerCbu = {
            modelId: S.powerModelId || null, ramGb: S.powerRam ? Number(S.powerRam) : null, disk: S.powerDisk || null,
            diskCount: Math.max(1, Number(S.powerDisks || 2)), lto8: !!S.powerLto8, ts4300: !!S.powerTs4300, eth10: !!S.powerEth10,
            ibmiUsers: !S.powerUnlimited && S.powerUsers !== '' && S.powerUsers != null ? Number(S.powerUsers) : null, ibmiUnlimited: !!S.powerUnlimited,
            cable: S.powerCable || null, transfer: S.powerTransfer || 'NO', transferRef: S.powerTransfer && S.powerTransfer !== 'NO' ? (S.powerTransferRef || '') : null,
            edition: S.powerEdition || 'SOLUTION', os: S.powerOs || '7.5', maint: S.powerMaintLines(),
          };
        } finally { pwSwap(); }
      }
      return r;
    });
  }
  /** scheda CBU salvata -> campi di stato (stessa forma di pwFresh) */
  function cbuSlotFromRequest(c) {
    const o = pwFresh(), ms = c.maint || [];
    o.powerModelId = c.modelId || null; o.powerRam = c.ramGb ? String(c.ramGb) : ''; o.powerDisk = c.disk || ''; o.powerDisks = String(c.diskCount || 2);
    o.powerLto8 = !!c.lto8; o.powerTs4300 = !!c.ts4300; o.powerEth10 = !!c.eth10; o.powerUsers = c.ibmiUsers == null ? '' : String(c.ibmiUsers); o.powerUnlimited = !!c.ibmiUnlimited;
    o.powerCable = c.cable || ''; o.powerTransfer = c.transfer || 'NO'; o.powerTransferRef = c.transferRef || ''; o.powerEdition = c.edition || 'SOLUTION'; o.powerOs = c.os || '7.5';
    if (ms.length) { o.powerMaintList = Array.from(new Set(ms.map((x) => x.months))).sort((a, b) => a - b); o.powerMaint = String(o.powerMaintList[0]); }
    ms.forEach((x) => { o.powerMaintAmt[amtKey(x.months, x.kind)] = { list: CPQ.num(x.listPrice, 2), net: CPQ.num(x.netPrice, 2) }; });
    return o;
  }
  function pwSwitchTab(tab, render) {
    if (S.pwTab === tab) return;
    closeMaintPanel(); pwSwap();
    if (render !== false) { renderPower(); renderPwKind(); }
  }
  /** SMIDRI (soluzione HA/DR): listino per gruppo processore del Power di Produzione, valore al cliente e SMISMP abbinato */
  function smidriListino() {
    const app = smitechApp(), prodId = pwIsCbu() ? S.pwOther.powerModelId : S.powerModelId;
    const cls = String((powerModelOf(prodId) || {}).processorGroup || '').toUpperCase();
    const norm = (c) => String(c || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    const mods = app ? app.modules.filter((m) => m.active !== false) : [];
    const byCode = (...codes) => { for (const c of codes) { const m = mods.find((x) => norm(x.code) === norm(c)); if (m) return m; } return null; };
    // SMIDRI: solo il modulo «SMIDRI»
    const mDri = mods.find((x) => String(x.code).trim() === 'SMIDRI') || null;          // codice esatto
    // SMISMP abbinato a SMIDRI: solo il modulo con codice esatto «SMISMP-SMIDRI»
    const mSm = mods.find((x) => String(x.code).trim() === 'SMISMP-SMIDRI') || null;          // codice esatto
    const price = (m) => { if (!m || !cls) return null; const p = m.prices || {}; return p[cls] != null ? p[cls] : (p.STD != null && p.STD > 0 ? p.STD : null); };
    return { cls: cls, smidri: price(mDri), smismp: price(mSm), dri: mDri ? mDri.code : null, sm: mSm ? mSm.code : null };
  }
  function renderSmidri() {
    const on = S.optPower && S.powerKind === 'HADR' && S.hadrSolution === 'SMIDRI' && !pwIsCbu();          // solo nella pagina «Power Produzione»
    const card = $('#pw-card-smidri'); if (!card) return;
    card.hidden = !on;
    if (!on) return;
    const L = smidriListino();
    $('#smidri-class').textContent = L.cls ? '(gruppo processore ' + L.cls + ')' : '';
    $('#smidri-list').textContent = L.smidri != null ? CPQ.eur(L.smidri) : '—';
    $('#smidri-list-note').textContent = !L.cls ? 'Scegli prima il modello Power di Produzione'
      : (L.smidri != null ? 'Modulo SMITECH ' + L.dri : (L.dri ? 'Modulo ' + L.dri + ': nessun prezzo per ' + L.cls : 'Modulo SMIDRI non trovato nell\'applicazione SMITECH'));
    $('#smidri-sm-list').textContent = L.smismp != null ? CPQ.eur(L.smismp) : '—';
    $('#smidri-sm-note').textContent = !L.cls ? '' : (L.smismp != null ? 'Modulo SMITECH ' + L.sm : (L.sm ? 'Modulo ' + L.sm + ': nessun prezzo per ' + L.cls : 'Modulo SMISMP con SMIDRI non trovato nell\'applicazione SMITECH'));
    if (!S.smidriPrice && L.smidri != null) S.smidriPrice = CPQ.num(L.smidri, 2);          // proposto il listino, modificabile
    if (S.smidriSmismp && !S.smidriSmismpPrice && L.smismp != null) S.smidriSmismpPrice = CPQ.num(L.smismp, 2);
    if (document.activeElement !== $('#smidri-price')) $('#smidri-price').value = S.smidriPrice || '';
    $('#smidri-smismp').checked = !!S.smidriSmismp;
    $('#smidri-sm-box').hidden = !S.smidriSmismp;
    if (document.activeElement !== $('#smidri-sm-price')) $('#smidri-sm-price').value = S.smidriSmismpPrice || '';
  }

  /** menu «Tipo di Power», schede e sezione «Prodotti IBM i» (assente nella scheda CBU) */
  function renderPwKind() {
    $('#power-kind-box').hidden = !S.optPower;
    $('#sel-power-kind').value = S.powerKind;
    $('#hadr-sol-box').hidden = !(S.optPower && S.powerKind === 'HADR');
    // con Power HA/DR il flag «Servizi SMITECH» non c'e' (e i servizi eventualmente scelti vengono tolti)
    const hadrOn = S.optPower && S.powerKind === 'HADR', smW = $('#opt-smitech').closest('label');
    if (smW) smW.hidden = hadrOn;
    if (hadrOn && S.optSmitech) { const a = smitechApp(), l = a && lineOf(a.id); S.optSmitech = false; $('#opt-smitech').checked = false; if (l) { S.lines = S.lines.filter((x) => x !== l); scheduleCalc(); } refreshStepper(); }          // «Soluzione HA/DR» sulla destra, solo con Power HA/DR
    $('#sel-hadr-sol').value = S.hadrSolution || 'NONE';
    renderSmidri();
    const hadr = S.optPower && S.powerKind === 'HADR';
    $('#pw-tabs').hidden = !hadr;
    $$('.pw-tab').forEach((b) => { const on = b.dataset.pwtab === S.pwTab; b.classList.toggle('active', on); b.setAttribute('aria-selected', on); });
    $('#pw-prod-wrap').hidden = hadr && pwIsCbu();
    $('#pw-copy-prod').hidden = !(hadr && pwIsCbu());
    $('#pw-unlimited-box').hidden = hadr && pwIsCbu();               // la scheda CBU non ha gli utenti ILLIMITATI
  }

  /** «Copia da Produzione»: porta nella scheda CBU le impostazioni della Produzione (esclusi i prodotti IBM i, gli importi economici, Transfer-Conversion e riferimenti Power); gli utenti seguono la regola dei core */
  async function copyProdToCbu() {
    if (!pwIsCbu()) return;
    const prod = S.pwOther;
    if (!prod.powerModelId) { toast('Power Produzione non ha ancora un modello da copiare', true); return; }
    if (S.powerModelId && !(await CPQ.confirm('La configurazione di Power CBU verrà sostituita con quella di Power Produzione. Continuare?',
      { title: 'Copia da Produzione', yes: 'Sì, copia', no: 'No' }))) return;
    const copy = JSON.parse(JSON.stringify(prod));          // copia profonda: le due schede non devono condividere gli stessi oggetti
    copy.powerProducts = {};
    copy.powerMaintAmt = {};                               // gli importi economici (listino e netto della manutenzione) NON si copiano: si indicano per la CBU
    pwLoad(copy);
    // Transfer-Conversion e riferimenti Power (tipo macchina, modello, matricola) NON si copiano: la CBU parte da «No» e senza riferimenti
    S.powerTransfer = 'NO'; S.powerTransferRef = '';
    pwRenderTransfer();
    applyCbuUsers();                                       // 8 core -> 10 utenti, 4 core -> 5; con altri core restano quelli copiati
    renderPower(); refreshStepper(); CPQ.markDirty('page', root); refreshSaveState();
  }

  /** Transfer-Conversion e riga dei riferimenti Power (elenco schede hardware + tipo macchina, modello, matricola) */
  function pwRenderTransfer() {
    $('#pw-transfer').value = S.powerTransfer;
    pwRenderRef();
    $('#pw-tref-box').hidden = S.powerTransfer === 'NO';
    if (S.powerTransfer !== 'NO') pwHwLoad();
  }

  function renderPower() {
    pwRenderTransfer();                                       // per primo: non dipende dal resto della pagina
    applyCbuUsers();
    renderPowerProducts();
    refreshPowerModelList(false);
    const m = powerModelOf(S.powerModelId);
    $('#power-model-info').textContent = m ? m.type + ' · ' + m.cores + ' core · ' + (m.mounting === 'RACK' ? 'Rack' : 'Desk') + ' · ' + m.version + (m.processorGroup ? ' · gruppo processore ' + m.processorGroup : '') + (m.ghz ? ' · ' + m.ghz + ' GHz' : '') : '';
    renderPowerOptions();
    $('#pw-users').value = S.powerUsers; $('#pw-unlimited').checked = S.powerUnlimited; $('#pw-users').disabled = S.powerUnlimited;
    $('#pw-lto8').checked = S.powerLto8; $('#pw-ts4300').checked = S.powerTs4300; syncTape(); $('#pw-eth10').checked = S.powerEth10;
    syncPowerUsers(); syncSmitechMode(false);
    $('#pw-cable').value = S.powerCable; $('#pw-edition').value = S.powerEdition; $('#pw-os').value = S.powerOs; $('#pw-disks').value = S.powerDisks; renderMaintBtn(); renderAmtSummary(); $('#pw-transfer').value = S.powerTransfer;
    pwRenderRef(); $('#pw-tref-box').hidden = S.powerTransfer === 'NO';
    $('#pw-cable-box').hidden = S.powerLto8 || S.powerTs4300;                  // con TAPE (LTO8 e/o TS4300) il cavo di collegamento non si sceglie
    renderPwKind();
  }

  /** sezione «Selezione del modello» completa: modello, RAM, disco e utenti IBM i (numero oppure ILLIMITATI) */
  function powerTopDone() {
    return !!S.powerModelId && !!S.powerRam && !!S.powerDisk && Number(S.powerDisks) >= 1 && (S.powerUnlimited || Number(S.powerUsers) >= 1);
  }

  /** elenco dei prodotti IBM i (anagrafica) con un selettore ciascuno: nessun valore predefinito, tutti obbligatori */
  const PRODUCT_VALUES = [['MIGRATO', 'Migrato'], ['1', '1'], ['2', '2'], ['3', '3'], ['4', '4']];
  function renderPowerProducts() {
    const box = $('#pw-products');
    if (!S.ibmiProducts.length) { box.innerHTML = '<div class="muted">Nessun prodotto IBM i attivo in anagrafica.</div>'; return; }
    box.innerHTML = S.ibmiProducts.map((p) => '<div class="pw-prod" data-pid="' + p.id + '"><div class="pw-prod-name"><span class="mono">' + esc(p.code) + '</span> ' + esc(p.description) + '</div>' +
      '<div class="field sm"><select data-pid="' + p.id + '"><option value="">— seleziona —</option>' +
      PRODUCT_VALUES.map(([v, l]) => '<option value="' + v + '"' + (S.powerProducts[p.id] === v ? ' selected' : '') + '>' + l + '</option>').join('') + '</select></div></div>').join('');
  }

  /** campo «Manutenzione SW/HW»: selezione multipla dei periodi */
  function renderMaintBtn() {
    const v = $('#pw-maint-val'); if (!v) return;
    const list = S.powerMaintList.slice().sort((a, b) => a - b);
    v.textContent = list.length ? list.map((m) => m + ' mesi').join(', ') : 'Seleziona…';
    v.classList.toggle('cs-ph', !list.length);
  }
  let maintPanel = null;
  function closeMaintPanel() {
    if (!maintPanel) return;
    maintPanel.remove(); maintPanel = null; $('#pw-maint-btn').setAttribute('aria-expanded', 'false'); $('#pw-maint-box .cs').classList.remove('open');
    document.removeEventListener('mousedown', maintOutside, true); window.removeEventListener('resize', closeMaintPanel);
  }
  const maintOutside = (e) => { if (maintPanel && !maintPanel.contains(e.target) && !$('#pw-maint-btn').contains(e.target)) closeMaintPanel(); };
  function paintMaintPanel() {
    maintPanel.innerHTML = PW_PERIODS.map((m) => '<div class="cs-opt' + (S.powerMaintList.includes(m) ? ' sel' : '') + '" role="option" data-m="' + m + '" aria-selected="' + S.powerMaintList.includes(m) + '"><span>' + m + ' mesi</span><svg class="cs-tick" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg></div>').join('');
  }
  function openMaintPanel() {
    if (maintPanel) { closeMaintPanel(); return; }
    const btn = $('#pw-maint-btn');
    maintPanel = document.createElement('div'); maintPanel.className = 'cs-panel'; maintPanel.setAttribute('role', 'listbox'); maintPanel.setAttribute('aria-multiselectable', 'true');
    paintMaintPanel(); document.body.appendChild(maintPanel);
    const r = btn.getBoundingClientRect();
    maintPanel.style.minWidth = Math.max(r.width, 160) + 'px'; maintPanel.style.left = Math.max(8, r.left) + 'px';
    const h = maintPanel.offsetHeight, below = window.innerHeight - r.bottom;
    maintPanel.style.top = (below < h + 12 && r.top > below ? Math.max(8, r.top - h - 4) : r.bottom + 4) + 'px';
    btn.setAttribute('aria-expanded', 'true'); $('#pw-maint-box .cs').classList.add('open');
    maintPanel.addEventListener('mousedown', (e) => {
      e.preventDefault();
      const it = e.target.closest('.cs-opt'); if (!it) return;
      const m = Number(it.dataset.m), i = S.powerMaintList.indexOf(m);
      if (i >= 0) S.powerMaintList.splice(i, 1); else S.powerMaintList.push(m);
      S.powerMaintList.sort((a, b) => a - b); S.powerMaint = String(S.powerMaintList[0] || 36);
      paintMaintPanel(); renderMaintBtn(); renderAmtSummary(); CPQ.markDirty('page', root); refreshSaveState();
    });
    document.addEventListener('mousedown', maintOutside, true); window.addEventListener('resize', closeMaintPanel);
  }

  /** testo sotto il pulsante «Importi manutenzione SW/HW» */
  function renderAmtSummary() {
    const el = $('#pw-amt-sum'); if (!el) return;
    if (!S.powerMaintList.length) { el.textContent = 'Seleziona prima la manutenzione SW/HW'; return; }
    if (!maintComplete()) { el.textContent = 'Da compilare: ' + S.powerMaintList.length + (S.powerMaintList.length === 1 ? ' periodo' : ' periodi') + ' selezionati'; return; }
    const t = S.powerMaintTotals();
    el.textContent = 'Listino ' + CPQ.eur(t.list) + ' · netto ' + CPQ.eur(t.net) + (t.list > 0 ? ' (sconto ' + CPQ.num((1 - t.net / t.list) * 100, 1) + ' %)' : '');
  }

  /** finestra «Importi manutenzione SW/HW»: lavora su una copia, che passa allo stato solo con «Conferma» */
  let amtDraft = null;
  function openAmtDialog() {
    if (!S.powerMaintList.length) { CPQ.fieldError($('#pw-maint-btn'), 'Seleziona prima almeno un periodo di manutenzione SW/HW'); return; }
    amtDraft = JSON.parse(JSON.stringify(S.powerMaintAmt));
    const sub = (m, kind, label) => {
      const v = amtDraft[amtKey(m, kind)] || {};
      return '<div class="pa-sub"><div class="pa-sub-h">' + label + '<span class="pa-disc" data-disc="' + m + '|' + kind + '"></span></div><div class="pa-row">' +
        '<div class="field"><input type="text" inputmode="decimal" placeholder=" " autocomplete="off" data-k="' + m + '|' + kind + '" data-f="list" id="pa-' + m + '-' + kind + '-list" value="' + esc(v.list || '') + '"><label for="pa-' + m + '-' + kind + '-list">Prezzo di listino (€) *</label></div>' +
        '<div class="field"><input type="text" inputmode="decimal" placeholder=" " autocomplete="off" data-k="' + m + '|' + kind + '" data-f="net" id="pa-' + m + '-' + kind + '-net" value="' + esc(v.net || '') + '"><label for="pa-' + m + '-' + kind + '-net">Netto scontato (€) *</label></div></div></div>';
    };
    $('#pw-amt-body').innerHTML = S.powerMaintList.slice().sort((a, b) => a - b).map((m) =>
      '<section class="pa-period"><h3>Manutenzione SW/HW – ' + m + ' mesi</h3>' + maintKinds(S).map((k) => sub(m, k, kindLabel(k))).join('') + '</section>').join('');
    $$('#pw-amt-body input').forEach((i) => amtDisc(i));
    $('#pw-amt-dialog').showModal();
    const f = $('#pw-amt-body input'); if (f) f.focus();
  }
  function amtDisc(inp) {
    const k = inp.dataset.k, v = amtDraft[k.replace('|', '|')] || {}, l = C.parseMoney(v.list), n = C.parseMoney(v.net);
    const el = document.querySelector('[data-disc="' + k + '"]');
    if (el) el.textContent = l > 0 && n != null ? 'sconto ' + CPQ.num((1 - n / l) * 100, 1) + ' %' : '';
  }
  function bindAmtDialog() {
    $('#pw-maint-btn').addEventListener('click', openMaintPanel);
    $('#pw-maint-btn').addEventListener('keydown', (e) => { if (e.key === 'Escape') closeMaintPanel(); if ((e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') && !maintPanel) { e.preventDefault(); openMaintPanel(); } });
    $('#pw-amt-open').addEventListener('click', openAmtDialog);
    $('#pw-amt-cancel').addEventListener('click', () => $('#pw-amt-dialog').close());
    const body = $('#pw-amt-body');
    body.addEventListener('input', (e) => {
      const i = e.target.closest('input[data-k]'); if (!i) return;
      const k = i.dataset.k; (amtDraft[k] = amtDraft[k] || {})[i.dataset.f] = i.value; amtDisc(i);
    });
    body.addEventListener('blur', (e) => {
      const i = e.target.closest('input[data-k]'); if (!i) return;
      const v = C.parseMoney(i.value); if (v != null) { i.value = CPQ.num(v, 2); amtDraft[i.dataset.k][i.dataset.f] = i.value; amtDisc(i); }
    }, true);
    $('#pw-amt-ok').addEventListener('click', () => {
      const bad = Array.from(body.querySelectorAll('input[data-k]')).find((i) => C.parseMoney(i.value) == null);
      if (bad) {
        const [m, kind] = bad.dataset.k.split('|');
        CPQ.fieldError(bad, 'Manutenzione ' + m + ' mesi – ' + kindLabel(kind) + ': indica il ' + (bad.dataset.f === 'list' ? 'prezzo di listino' : 'prezzo netto scontato'));
        return;
      }
      // il prezzo (di listino e netto scontato) di un periodo piu' breve non puo' superare quello di un periodo piu' lungo (Power e LTO8): 36 <= 48 <= 60 mesi
      const months = S.powerMaintList.slice().sort((a, b) => a - b);
      for (const kind of maintKinds(S)) {
        for (const f of ['list', 'net']) {
          for (let i = 0; i + 1 < months.length; i++) {
            const a = C.parseMoney((amtDraft[amtKey(months[i], kind)] || {})[f]), b = C.parseMoney((amtDraft[amtKey(months[i + 1], kind)] || {})[f]);
            if (a != null && b != null && a > b) {
              const el = body.querySelector('input[data-k="' + months[i] + '|' + kind + '"][data-f="' + f + '"]');
              CPQ.fieldError(el, 'Manutenzione ' + kindLabel(kind) + ': il prezzo ' + (f === 'list' ? 'di listino' : 'netto scontato') + ' a ' + months[i] + ' mesi (' + CPQ.eur(a) + ') non può essere maggiore di quello a ' + months[i + 1] + ' mesi (' + CPQ.eur(b) + ')');
              return;
            }
          }
        }
      }
      S.powerMaintAmt = amtDraft; amtDraft = null;
      $('#pw-amt-dialog').close(); renderAmtSummary(); CPQ.markDirty('page', root); refreshSaveState();
    });
  }

  /** le sezioni Opzioni Hardware / Software restano disabilitate (stile "bloccato") finche' la sezione superiore non e' compilata */
  function updatePowerLock() {
    const open = powerTopDone();
    ['#pw-card-hw', '#pw-card-amt', '#pw-card-sw'].forEach((id) => {
      const card = $(id);
      card.classList.toggle('locked', !open);
      card.querySelector('fieldset').disabled = !open;
      card.querySelector('.pw-lock').textContent = open ? '' : '🔒';
    });
  }

  /** con 40 o piu' utenti (anche ILLIMITATI) l'elenco dei Power System mostra solo il gruppo processore P10 */
  const POWER_P10_USERS = 40;
  const powerUsersNum = () => (S.powerUnlimited ? 1e9 : (Number(S.powerUsers) || 0));
  const isP10 = (m) => String(m && m.processorGroup || '').toUpperCase() === 'P10';
  function refreshPowerModelList(userChange) {
    const only = powerUsersNum() >= POWER_P10_USERS;
    if (userChange && only) {
      const cur = powerModelOf(S.powerModelId);
      if (cur && !isP10(cur)) {
        S.powerModelId = null; S.powerRam = ''; S.powerDisk = '';
        toast('Con ' + POWER_P10_USERS + ' o più utenti sono ammessi solo i Power System con gruppo processore P10: modello deselezionato', true);
        renderPower(); return;
      }
    }
    const list = S.powerModels.filter((m) => !only || isP10(m) || m.id === S.powerModelId);        // un modello gia' scelto in un'offerta salvata resta visibile
    $('#sel-power').innerHTML = '<option value="">— seleziona —</option>' + list.map((m) => '<option value="' + m.id + '"' + (S.powerModelId === m.id ? ' selected' : '') + '>' + esc(powerModelLabel(m)) + '</option>').join('');
  }

  /** con gli utenti di Galileo indicati, gli utenti IBM i del Power System sono gli stessi e il campo e' protetto */
  const galUsersLinked = () => !!(S.optGalileo && S.galileoId && Number(S.gal.users) >= 1);
  function syncPowerUsers(userChange) {
    const u = $('#pw-users'), un = $('#pw-unlimited'), h = $('#pw-users-link'); if (!u || !un) return;
    // scheda CBU: gli utenti derivano dai core del Power Produzione (8 core -> 10, 4 core -> 5); altrimenti restano liberi
    const auto = pwIsCbu() ? cbuAutoUsers() : null;
    const linked = auto == null && !pwIsCbu() && galUsersLinked();
    if (auto != null) { S.powerUsers = String(auto); S.powerUnlimited = false; u.value = S.powerUsers; un.checked = false; }
    else if (linked && S.powerUnlimited) { S.powerUsers = ''; u.value = ''; un.checked = true; }          // ILLIMITATI sempre selezionabile, anche con gli utenti di Galileo
    else if (linked) { S.powerUsers = String(Number(S.gal.users)); u.value = S.powerUsers; un.checked = false; }
    u.disabled = linked || auto != null || S.powerUnlimited; un.disabled = auto != null;          // solo nella scheda CBU gli utenti ILLIMITATI non esistono
    if (h) { h.hidden = (!linked && auto == null) || (linked && S.powerUnlimited); h.textContent = auto != null ? 'Utenti minimi - Gli altri sono ereditati da Power Produzione' : 'Collegato agli utenti di Galileo'; }
    refreshPowerModelList(!!userChange);
    updatePowerLock();
    smitechUsersChanged();
  }

  function powerText() {
    const m = powerModelOf(S.powerModelId);
    if (!m) return 'Power System';
    const parts = ['Power System – ' + m.description + (m.processorGroup ? ' (gruppo ' + m.processorGroup + ')' : '')];
    if (S.powerRam) parts.push('RAM ' + S.powerRam + ' GB');
    if (S.powerDisk) parts.push(S.powerDisks + ' dischi da ' + S.powerDisk.replace('TB', ' TB'));
    if (S.powerLto8) parts.push('LTO8');
    if (S.powerTs4300) parts.push('TS4300 - TAPE LIBRARY');
    if (S.powerEth10) parts.push('ethernet 10 Gbit');
    parts.push('manutenzione SW/HW ' + (S.powerMaintList.length ? S.powerMaintList.slice().sort((a, b) => a - b).join(', ') : S.powerMaint) + ' mesi');
    parts.push('IBM i ' + S.powerOs);
    if (S.powerCable) parts.push('cavo ' + S.powerCable);
    parts.push('edition ' + (S.powerEdition === 'EXPRESS' ? 'Express' : 'Solution'));
    if (S.powerTransfer !== 'NO') parts.push((S.powerTransfer === 'TRANSFER' ? 'Transfer' : 'Conversion') + (trDisplay(S.powerTransferRef) ? ' da ' + trDisplay(S.powerTransferRef) : ''));
    if (S.powerUnlimited) parts.push('utenti IBM i ILLIMITATI'); else if (S.powerUsers !== '') parts.push(CPQ.num(S.powerUsers, 0) + ' utenti IBM i');
    return parts.join(', ');
  }

  /** errori della configurazione Power System (vuoto se l'elemento non e' selezionato) */
  /** errori della pagina Power System, ciascuno con il campo da evidenziare */
  function powerErrorList() {
    if (!S.optPower) return [];
    if (S.powerKind !== 'HADR') return powerErrorsOfTab('Power System', null);
    // HA/DR: si controllano entrambe le schede; la scheda inattiva si scambia solo per il controllo
    const cur = pwIsCbu();
    const label = (c) => (c ? 'Power CBU' : 'Power Produzione');
    const tabOf = (c) => (c ? 'CBU' : 'PROD');
    // ogni errore porta la sua scheda: revealError apre quella giusta prima di segnalare il campo
    const a = powerErrorsOfTab(label(cur), tabOf(cur));
    pwSwap();
    let b; try { b = powerErrorsOfTab(label(!cur), tabOf(!cur)); } finally { pwSwap(); }
    // prima gli errori della Produzione, poi quelli della CBU
    return cur ? b.concat(a) : a.concat(b);
  }
  function powerErrorsOfTab(prefix, tab) {
    const e = [];
    const add = (sel, msg) => e.push({ step: 7, kind: 'power', tab: tab, sel: sel, msg: msg.replace(/^Power System/, prefix) });
    if (!S.powerModelId) add('#sel-power', 'Power System: scegli il modello');
    else {
      if (!S.powerRam) add('#pw-ram', 'Power System: scegli la RAM');
      if (!S.powerDisk) add('#pw-disk', 'Power System: scegli il disco');
      if (!(Number(S.powerDisks) >= 1)) add('#pw-disks', 'Power System: indica il numero di dischi');
      const noProd = pwIsCbu() ? null : S.ibmiProducts.find((p) => !S.powerProducts[p.id]);       // la scheda CBU non ha i «Prodotti IBM i»
      if (noProd) add('#pw-products select[data-pid="' + noProd.id + '"]', 'Power System: seleziona il valore di ogni prodotto IBM i');
      if (!S.powerMaintList.length) add('#pw-maint-btn', 'Power System: seleziona la manutenzione SW/HW');
      else if (!maintComplete()) add('#pw-amt-open', 'Power System: indica gli importi (listino e netto scontato) di ogni periodo di manutenzione SW/HW');
      // SMIDRI (solo Power Produzione): valore al cliente obbligatorio, anche per SMISMP se attivato
      if (tab === 'PROD' && S.hadrSolution === 'SMIDRI') {
        if (C.parseMoney(S.smidriPrice) == null) add('#smidri-price', 'Power System: indica il valore al cliente di SMIDRI');
        if (S.smidriSmismp && C.parseMoney(S.smidriSmismpPrice) == null) add('#smidri-sm-price', 'Power System: indica il valore al cliente di SMISMP con SMIDRI');
      }
      // Power CBU: il riferimento (tipo macchina, modello, matricola) non puo' essere quello della Produzione
      if (tab === 'CBU' && S.powerTransfer !== 'NO' && S.pwOther && S.pwOther.powerTransfer !== 'NO') {
        const a = trParse(S.powerTransferRef), b = trParse(S.pwOther.powerTransferRef), n = (x) => x.trim().toLowerCase();
        if (a.t.trim() && a.m.trim() && a.n.trim() && n(a.t) === n(b.t) && n(a.m) === n(b.m) && n(a.n) === n(b.n)) add('#pw-tr-serial', 'Power System: tipo macchina, modello e matricola non possono essere uguali a quelli di Power Produzione');
      }
      if (S.powerTransfer !== 'NO') { const r = trParse(S.powerTransferRef); if (!r.t.trim() || !r.m.trim() || !r.n.trim()) add(!r.t.trim() ? '#pw-tr-type' : (!r.m.trim() ? '#pw-tr-model' : '#pw-tr-serial'), 'Power System: indica tipo macchina, modello e matricola del Power da cui trasferire le licenze'); }
      if (!S.powerUnlimited && !(Number(S.powerUsers) >= 1)) add('#pw-users', 'Power System: indica gli utenti IBM i licenziati' + (pwIsCbu() ? '' : ' o ILLIMITATI'));
    }
    return e;
  }
  function powerErrors() { return powerErrorList().map((x) => x.msg); }

  function elementsText() {
    const el = [];
    if (S.optDept) el.push('Dipartimentali');
    if (S.optPower) el.push(powerText());
    if (S.optGalileo && S.galileoId) el.push('Galileo – ' + ((appOf(S.galileoId) || {}).name || ''));
    if (S.optSmitech) el.push('Servizi SMITECH');
    if (S.optServices) el.push('Servizi accessori');
    return el.join(' · ') || '—';
  }

  function validateStep1() {
    const miss = [];
    if (!S.date) miss.push('data offerta');
    if (!S.progressive || Number(S.progressive) < 1) miss.push('numero progressivo');
    if (!S.customer) miss.push('cliente');
    return miss;
  }

  /** tutti gli errori dell'offerta nell'ordine delle pagine, ciascuno con pagina e campo da evidenziare */
  function collectErrors() {
    const out = [];
    const add = (step, kind, sel, msg, prep) => out.push({ step: step, kind: kind, sel: sel, msg: msg, prep: prep });
    if (!S.date) add(1, 'base', '#f-date', 'Indica la data dell\'offerta');
    if (!S.progressive || Number(S.progressive) < 1) add(1, 'base', '#f-prog', 'Indica il numero progressivo');
    if (!S.customer) add(1, 'base', '#f-cust', 'Seleziona il cliente o il prospect');
    if (S.optGalileo && !S.galileoId) add(6, 'type', '#sel-galileo', 'Seleziona la tipologia Galileo');
    powerErrorList().forEach((x) => out.push(x));
    galileoErrorList().forEach((x) => out.push(x));
    // Dipartimentali: gli utenti delle applicazioni a scaglioni sono obbligatori (>= 1); partono da 0
    S.lines.forEach((l) => {
      const ap = appOf(l.applicationId); if (!ap) return;
      ap.drivers.forEach((d) => {
        const needed = ap.family !== 'SMITECH' || ap.groups.filter((g) => g.driverId === d.id).some((g) => ap.modules.some((m) => m.groupId === g.id && l.modules[m.id] > 0));
        if (needed && ap.family === 'SMITECH' && !((l.drivers[d.id] || 0) >= 1)) {
          if (!(S.optPower && S.powerUnlimited)) add(9, 'users', '#sm-body', 'Servizi SMITECH: indica gli utenti nella configurazione Power System o Galileo (' + (d.unitLabel || 'utenti') + ' del servizio SMISMP)');
        }
      });
      // Dipartimentali: utenti partono da 0; con un solo tipo va indicato, con piu' tipi (es. utenti e macchine) basta almeno uno dei due
      if (ap.family !== 'SMITECH' && ap.drivers.length && perUserSelected(ap, l) && !ap.drivers.some((d) => (l.drivers[d.id] || 0) >= 1)) {
        const d0 = ap.drivers[0];
        const what = ap.drivers.length === 1 ? 'il numero di ' + (d0.unitLabel || d0.label || 'utenti').toLowerCase()
          : 'almeno uno tra ' + ap.drivers.map((d) => (d.label || d.unitLabel || '').toLowerCase()).join(' e ');
        add(2, 'users', '#apps-body .drv-in[data-app="' + ap.id + '"][data-drv="' + d0.id + '"]', ap.name + ': indica ' + what, () => { expanded.add(ap.id); });
      }
    });
    if (!hasContent()) {
      if (S.optDept) add(2, 'content', '#app-search', 'Seleziona almeno un\'applicazione');
      else add(6, 'content', '#opt-power', 'Seleziona almeno un elemento');
    }
    const viol = violationList().find((v) => v.severity === 'ERROR');
    if (viol) add(2, 'viol', '#violations', viol.message || 'Risolvi i vincoli in rosso');
    // servizi: righe di attivita' incomplete
    if (S.servicesMode !== 'NONE') {
      const rows = new Set(C.activityRows(S.activities, S.lines));
      // «Servizi» attivi: serve almeno un servizio specificato prima di proseguire
      if (!rows.size) add(5, 'svc', S.optRfq ? '#btn-rfq-import' : '#svc-body', S.optRfq ? 'Servizi: importa le attività dal foglio RFQ oppure disattiva «Servizi»' : 'Servizi: inserisci almeno un servizio (descrizione, profilo professionale e ore) oppure disattiva «Servizi»');
      for (let i = 0; i < S.activities.length; i++) {
        const a = S.activities[i];
        if (!rows.has(a)) continue;
        const where = (a.rfq ? '#svc-rfq-body' : '#svc-body') + ' tr[data-i="' + i + '"] ';
        const prep = a.rfq ? () => { a._edit = true; } : null;
        const name = (a.desc || '').trim();
        if (!name) add(5, 'act', where + '.svc-desc', 'Attività: manca la descrizione', prep);
        else if (!a.profileId) add(5, 'act', where + '.svc-prof', 'Attività «' + name + '»: scegli il profilo professionale', prep);
        else if (!(C.parseHours(a.hours) > 0)) add(5, 'act', where + '.svc-hours', 'Attività «' + name + '»: indica le ore', prep);
        else if (C.parseHours(a.hours) > 9999) add(5, 'act', where + '.svc-hours', 'Attività «' + name + '»: massimo 9999 ore', prep);
      }
    }
    if (Number(S.actDiscount) > 0 && !S.actUntil) add(4, 'until', '#svc-until', 'Attività sistemistiche: indica la data di validità dello sconto');
    // titolo del progetto: obbligatorio quando non si puo' calcolare in automatico dalle applicazioni scelte
    if (!(S.title || '').trim() && !C.suggestTitle(catalog, S.lines)) add(4, 'title', '#sum-title', 'Indica il titolo del progetto (non si può calcolare in automatico)');
    // modalita' di fatturazione e pagamento
    const TS = [['Fatturazione licenze', '#t-bill-lic-date'], ['Fatturazione canoni', '#t-bill-fee-date'],
      ['Pagamento licenze: "Solito', '#t-pay-lic'], ['Pagamento canoni: "Solito', '#t-pay-fee'], ['Pagamento attività sistemistiche: "Solito', '#t-pay-act'],
      ['Fatturazione canone SMIDRI', '#t-bill-dri-date'], ['Pagamento canone SMIDRI/SMISMP: "Solito', '#t-pay-dri'], ['Pagamento canone SMIDRI/SMISMP: scrivi', '#t-pay-dri-other'],
      ['Pagamento licenze: scrivi', '#t-pay-lic-other'], ['Pagamento canoni: scrivi', '#t-pay-fee-other'], ['Pagamento attività sistemistiche: scrivi', '#t-pay-act-other']];
    (termsCfg ? C.termsErrors(termsCfg, S.terms, custType(), !!driLabel()) : []).forEach((m) => {
      const t = TS.find((x) => m.indexOf(x[0]) === 0);
      add(4, 'terms', t ? t[1] : '#terms-body', m);
    });
    return out;
  }
  /** porta l'utente alla pagina dell'errore e mostra il popup vicino al campo */
  function revealError(err) {
    if (err.prep) err.prep();
    showStep(err.step);
    if (err.tab && S.pwTab !== err.tab) pwSwitchTab(err.tab);          // Power HA/DR: apre la scheda (Produzione / CBU) a cui appartiene il campo
    requestAnimationFrame(() => {
      const el = err.sel ? $(err.sel) : null;
      if (el) CPQ.fieldError(el, err.msg); else toast(err.msg, true);
    });
  }

  // ---- Riferimenti Power (Transfer / Conversion): scheda hardware AS400 del cliente + tipo macchina, modello e matricola modificabili.
  // Nell'offerta il riferimento e' salvato come «tipo|modello|matricola».
  const HW = { cards: [], cust: null };
  const trParse = (s) => { s = String(s || ''); if (s.indexOf('|') < 0) return { t: '', m: s, n: '' }; const p = s.split('|'); return { t: p[0] || '', m: p[1] || '', n: p.slice(2).join('|') }; };
  const trJoin = (t, m, n) => ((t || '').trim() || (m || '').trim() || (n || '').trim() ? (t || '').trim() + '|' + (m || '').trim() + '|' + (n || '').trim() : '');
  /** testo leggibile: tipo-modello matricola (i riferimenti scritti a mano in passato restano com'erano) */
  const trDisplay = (s) => { s = String(s || ''); if (s.indexOf('|') < 0) return s.trim(); const r = trParse(s); return ((r.t ? r.t + '-' : '') + r.m + (r.n ? ' ' + r.n : '')).trim(); };
  /** voce dell'elenco: tipo macchina-modello s/n matricola - note (note e matricola solo se presenti) */
  const hwLabel = (c) => (c.machineType ? c.machineType + '-' : '') + c.model + (c.serial ? ' s/n ' + c.serial : '') + ((c.notes || '').trim() ? ' - ' + c.notes.trim().replace(/\s+/g, ' ') : '');
  /** allinea l'elenco a discesa alla scheda che corrisponde ai tre campi (nessuna = voce vuota) */
  function pwSyncHwSel() {
    const r = trParse(S.powerTransferRef);
    const i = HW.cards.findIndex((c) => c.machineType === r.t.trim() && c.model === r.m.trim() && c.serial === r.n.trim());
    $('#pw-hw').value = i >= 0 ? String(i) : '';
    const sel = $('#pw-hw'); if (sel.refresh) sel.refresh();
  }
  function pwRenderRef(fromSel) {
    const r = trParse(S.powerTransferRef);
    $('#pw-tr-type').value = r.t; $('#pw-tr-model').value = r.m; $('#pw-tr-serial').value = r.n;
    $('#pw-hw').innerHTML = '<option value="">' + (HW.cards.length ? '— seleziona una scheda hardware —' : '— nessuna scheda AS400 per il cliente —') + '</option>' +
      HW.cards.map((c, i) => '<option value="' + i + '">' + esc(hwLabel(c)) + '</option>').join('');
    if (!fromSel) pwSyncHwSel(); else { const i = HW.cards.findIndex((c) => c.machineType === r.t && c.model === r.m && c.serial === r.n); $('#pw-hw').value = i >= 0 ? String(i) : ''; }
  }
  /** carica le schede hardware (tipo AS400) del cliente dell'offerta */
  async function pwHwLoad() {
    const c = S.customer;
    if (!c) { HW.cards = []; HW.cust = null; pwRenderRef(); return; }
    if (HW.cust === c.id) { pwRenderRef(); return; }
    try {
      const rows = await CPQ.api('GET', '/offers/api/hw-cards?customerId=' + encodeURIComponent(c.id));
      if (!S.customer || S.customer.id !== c.id) return;
      HW.cards = rows; HW.cust = c.id;
    } catch (_) { HW.cards = []; }
    pwRenderRef();
  }

  /** vero se nella riga e' selezionato almeno un modulo con gruppo di prezzo (moduli per utente): solo allora si mostrano e si chiedono gli utenti */
  function perUserSelected(a, line) {
    return !!line && a.modules.some((m) => m.groupId != null && m.section !== 'LINK' && !m.included && line.modules[m.id] > 0);
  }

  /** «Data validità» dello sconto sulle attività: compare solo se c'e' uno sconto ed e' obbligatoria */
  function syncUntil() {
    const box = $('#svc-until-box'); if (!box) return;
    box.hidden = !(Number(S.actDiscount) > 0);
    const i = $('#svc-until'); if (i && document.activeElement !== i) i.value = S.actUntil || '';
  }

  /** applicazioni Dipartimentali (non SMITECH) senza utenti: un solo tipo da indicare, con piu' tipi almeno uno */
  function usersMissing() {
    return S.lines.filter((l) => { const ap = appOf(l.applicationId); return ap && ap.family !== 'SMITECH' && ap.drivers.length && perUserSelected(ap, l) && !ap.drivers.some((d) => (l.drivers[d.id] || 0) >= 1); })
      .map((l) => l.applicationId);
  }
  /** evidenzia (o toglie l'evidenza a) i campi utenti delle applicazioni ancora da compilare */
  function markUsers() {
    const miss = new Set(S.usersMark ? usersMissing() : []);
    $$('#apps-body .drv-in').forEach((i) => i.classList.toggle('drv-miss', miss.has(Number(i.dataset.app))));
    if (S.usersMark && !miss.size) S.usersMark = false;
  }

  function go(n) {
    const order = orderAll(), idx = (k) => order.indexOf(k);
    // dalla pagina Dipartimentali non si esce finche' gli utenti di ogni applicazione non sono compilati: si evidenziano tutti i campi mancanti
    if (S.step === 2 && n !== 2) {
      const miss = usersMissing();
      if (miss.length) {
        S.usersMark = true; markUsers();
        const err = collectErrors().find((e) => e.kind === 'users' && e.step === 2);
        if (err) revealError(err);
        if (miss.length > 1) toast('Indica gli utenti di ' + miss.length + ' applicazioni (campi evidenziati in rosso)', true);
        return;
      }
    }
    const first = collectErrors().find((e) => (e.kind === 'base' && n > 1) || (e.kind === 'type' && idx(n) > idx(6))
      || (e.kind === 'power' && idx(n) > idx(7)) || (e.kind === 'gal' && idx(n) > idx(8))
      || ((e.kind === 'svc' || e.kind === 'act') && idx(n) > idx(5)));          // con «Servizi» attivi non si va avanti senza servizi completi
    if (first) { revealError(first); return; }
    const after6 = idx(n) > idx(6);
    while (!stepVisible(n) && n !== 4) n = nextStep(n);        // salta i passi non presenti nell'offerta
    if (after6 && n !== 2 && n !== 7 && n !== 9 && n !== 5 && !hasContent()) {      // Dipartimentali e Power System si possono sempre aprire per completare l'offerta
      revealError(collectErrors().find((e) => e.kind === 'content'));
      return;
    }
    showStep(n);
  }

  // ---------------------------------------------------------------- step 2: tabella applicazioni
  /** BU (famiglie) che hanno almeno un modulo selezionato in offerta */
  function famsWithModules() {
    const set = new Set();
    S.lines.forEach((l) => { const a = appOf(l.applicationId); if (a && a.family && Object.keys(l.modules).length) set.add(a.family); });
    return set;
  }

  /** selettore delle BU: tutte quelle del catalogo, con numero di applicazioni e di moduli scelti; filtro «solo BU con moduli selezionati» */
  function renderFamilies() {
    const fams = Array.from(new Set(catalog.map((a) => a.family).filter((f) => f && f !== GALILEO)))
      .sort((x, y) => x.localeCompare(y, 'it', { sensitivity: 'base', numeric: true }));          // BU in ordine alfabetico
    const withSel = famsWithModules();
    const shown = filter.onlySel ? fams.filter((f) => withSel.has(f)) : fams;
    const modCount = (f) => S.lines.reduce((n, l) => { const a = appOf(l.applicationId); return n + (a && a.family === f ? Object.keys(l.modules).length : 0); }, 0);
    $('#families').innerHTML =
      '<span class="chip filter ' + (filter.onlySel ? 'on' : '') + '" data-onlysel="1" title="Mostra solo le BU in cui hai selezionato dei moduli">Solo BU con moduli selezionati' +
      (withSel.size ? ' (' + withSel.size + ')' : '') + '</span>' +
      '<span class="chip filter ' + (filter.family ? '' : 'on') + '" data-fam="">Tutte' + (filter.onlySel ? ' le selezionate' : ' le BU (' + fams.length + ')') + '</span>' +
      shown.map((f) => {
        const n = catalog.filter((a) => a.family === f).length, m = modCount(f);
        return '<span class="chip filter ' + famClass(f) + (filter.family === f ? ' on' : '') + '" data-fam="' + esc(f) + '">' + esc(f) +
          ' <span class="muted" style="font-size:11px">' + n + (m ? ' · <b>' + m + ' moduli</b>' : '') + '</span></span>';
      }).join('');
  }

  function renderApps() {
    const tb = $('#apps-body');
    const t = filter.text.toLowerCase();
    const rows = [];
    const withSel = famsWithModules();
    if (filter.onlySel && filter.family && !withSel.has(filter.family)) filter.family = '';
    renderFamilies();
    catalog.forEach((a) => {
      if (a.family === GALILEO || a.family === 'SMITECH') return;                // i mercati Galileo si scelgono nella pagina Contenuti, i servizi SMITECH nella loro pagina
      const sel = lineOf(a.id);
      if (filter.family && a.family !== filter.family && !sel) return;
      if (filter.onlySel && !withSel.has(a.family)) return;
      if (t && !(a.name + ' ' + (a.family || '') + ' ' + a.modules.map((m) => m.code + ' ' + m.description).join(' ')).toLowerCase().includes(t) && !sel) return;
      rows.push(appRow(a, sel));
      if (expanded.has(a.id)) rows.push('<tr class="detail" data-detail="' + a.id + '"><td colspan="7">' + appDetail(a, sel) + '</td></tr>');
    });
    tb.innerHTML = rows.join('') || '<tr><td colspan="7" class="muted" style="padding:24px">Nessuna applicazione corrisponde alla ricerca.</td></tr>';
    updateAmounts();
    if (S.usersMark) markUsers();
  }

  function appRow(a, line) {
    const modeSel = a.modes.length
      ? '<div class="mini"><select class="mode-sel" data-app="' + a.id + '"' + (line ? '' : ' disabled') + '>' +
        a.modes.map((m) => '<option value="' + esc(m.code) + '"' + (line && line.modeCode === m.code ? ' selected' : (!line && m.preferred ? ' selected' : '')) + '>' + esc(m.label) + '</option>').join('') +
        '</select></div>'
      : '<span class="muted">—</span>';
    // campo utenti solo con moduli per utente selezionati; con soli moduli a prezzo fisso vale il prezzo del modulo
    const drv = !a.drivers.length ? '<span class="muted">non a utenti</span>'
      : (line && !perUserSelected(a, line)) ? '<span class="muted" title="Sono selezionati solo moduli a prezzo fisso">prezzo fisso</span>'
      : !line ? '<span class="muted">—</span>'
      : a.drivers.map((d) => {
        const v = line ? (line.drivers[d.id] || 0) : '';
        return '<div class="mini" style="margin-bottom:' + (a.drivers.length > 1 ? '4px' : '0') + '"><input type="number" min="0" step="1" class="drv-in" data-app="' + a.id + '" data-drv="' + d.id + '" value="' + v +
          '"' + (line ? '' : ' disabled') + ' title="' + esc(d.label) + '" aria-label="' + esc(d.label) + '"><label>' + esc(a.drivers.length > 1 ? d.label : d.unitLabel) + '</label></div>';
      }).join('');
    return '<tr class="app-row' + (line ? ' sel' : '') + '" data-app="' + a.id + '">' +
      '<td><label class="check"><input type="checkbox" class="app-check" data-app="' + a.id + '"' + (line ? ' checked' : '') + ' aria-label="Seleziona ' + esc(a.name) + '"></label></td>' +
      '<td class="name"><div class="appname-row"><div class="appname" title="' + esc(a.name) + '">' + esc(a.name) + '</div>' +
        (hasTiers(a) ? '<button type="button" class="btn icon sm tiers-info" data-app="' + a.id + '" title="Progressione delle licenze per scaglioni di utenti" aria-label="Scaglioni di utenti"><svg class="ic"><use href="#i-info"/></svg></button>' : '') + '</div><div style="margin-top:3px"><span class="chip ' + famClass(a.family) + '">' + esc(a.family || '') + '</span>' +
      (a.needsReview ? ' <span class="chip warn" title="Logica del listino da verificare">da verificare</span>' : '') +
      ' <span class="muted">' + a.modules.filter((m) => m.section !== 'LINK').length + ' moduli</span></div></td>' +
      '<td>' + modeSel + '</td><td>' + drv + '</td>' +
      '<td class="num pr" data-amt="lic:' + a.id + '">' + (line ? '…' : '') + '</td>' +
      '<td class="num pr" data-amt="man:' + a.id + '">' + (line ? '…' : '') + '</td>' +
      '<td><button type="button" class="btn icon sm toggle" data-app="' + a.id + '" aria-label="Dettaglio moduli"><svg class="ic" style="transform:rotate(' + (expanded.has(a.id) ? 180 : 0) + 'deg)"><use href="#i-expand"/></svg></button></td></tr>';
  }

  function isCodeSelected(code) {
    return S.lines.some((l) => {
      const a = appOf(l.applicationId);
      return a.modules.some((m) => m.code === code && l.modules[m.id] > 0 && m.section !== 'LINK');
    });
  }

  function ruleChip(m, r, selected) {
    const targets = (r.targetCodes || '').split(',').map((s) => s.trim()).filter(Boolean);
    let cls = 'blue'; let txt = r.message;
    if (r.type === 'INFO') { cls = 'warn'; }
    else if (r.type === 'REQUIRES' || r.type === 'REQUIRES_ANY') {
      const ok = r.type === 'REQUIRES' ? targets.every(isCodeSelected) : targets.some(isCodeSelected);
      txt = (r.type === 'REQUIRES_ANY' ? 'Richiede uno tra ' : 'Richiede ') + targets.join(', ');
      cls = !selected ? 'blue' : (ok ? 'ok' : (r.severity === 'ERROR' ? 'err' : 'warn'));
    }
    return '<span class="chip ' + cls + '" title="' + esc(r.message) + '"><svg class="ic" style="width:14px;height:14px"><use href="#i-' + (cls === 'err' || cls === 'warn' ? 'warning' : 'info') + '"/></svg>' + esc(txt.length > 90 ? txt.slice(0, 88) + '…' : txt) + '</span>';
  }

  /** prezzi unitari effettivi di un modulo selezionato: listino (catalogo o modificato) e importo a cliente (uguale al listino finche' non modificato) */
  function modPrices(line, m, mode) {
    const mp = (line && line.modPrice && line.modPrice[m.id]) || {};
    const cat = C.modulePrice(m, mode);
    const list = mp.list == null ? cat : mp.list;
    return { cat: cat, list: list, cust: mp.cust == null ? list : mp.cust, listEdited: mp.list != null, custEdited: mp.cust != null };
  }
  const MOD_HEAD = '<thead><tr><th></th><th>Codice</th><th>Modulo</th><th class="num">Listino</th><th class="num">Importo a cliente</th><th>Q.tà</th></tr></thead>';
  function modRow(a, line, m) {
    const on = !!(line && line.modules[m.id] > 0);
    const mode = line ? line.modeCode : (a.modes.find((x) => x.preferred) || a.modes[0] || {}).code;
    const pr = modPrices(line, m, mode);
    const kind = m.kind === 'ANNUAL' ? '<span class="chip">canone annuo</span>' : (m.kind === 'MONTHLY' ? '<span class="chip">canone mensile</span>' : (m.kind === 'ONETIME' ? '<span class="chip">una tantum</span>' : ''));
    const onetime = m.onetimePrice ? ' <span class="muted">+ ' + CPQ.eur(m.onetimePrice) + ' att.</span>' : '';
    const editable = on && !m.included && m.section !== 'LINK';
    const inp = (cls, v, edited, label) => '<input type="text" inputmode="decimal" class="mod-amt ' + cls + (edited ? ' edited' : '') + '" data-app="' + a.id + '" data-mod="' + m.id + '" value="' + esc(CPQ.num(v, 2)) +
      '" aria-label="' + label + ' ' + esc(m.code) + '" title="' + (edited ? 'Modificato a mano: svuota il campo per tornare al valore di default' : 'Modificabile') + '" style="width:104px;height:32px;text-align:right;border:1px solid var(--outline);border-radius:6px;padding:0 8px;font:inherit;background:var(--surface);color:var(--on-surface)">';
    const listCell = m.included ? '<span class="chip ok">incluso</span>' : (editable ? inp('mod-list', pr.list, pr.listEdited, 'Listino') : CPQ.eur(pr.list)) + onetime + ' ' + kind;
    const custCell = m.included ? '' : (editable ? inp('mod-cust', pr.cust, pr.custEdited, 'Importo a cliente') : CPQ.eur(pr.list));
    const qty = m.qtyEnabled && on ? '<input type="number" min="1" class="mod-qty qty" data-app="' + a.id + '" data-mod="' + m.id + '" value="' + line.modules[m.id] + '" style="width:64px;height:32px;border:1px solid var(--outline);border-radius:6px;padding:0 6px"' + '>' : '';
    const rules = (m.rules || []).map((r) => ruleChip(m, r, on)).join('');
    return '<tr class="' + (on ? 'on' : '') + (m.included ? ' dis' : '') + '"><td style="width:34px"><label class="check"><input type="checkbox" class="mod-check" data-app="' + a.id + '" data-mod="' + m.id + '"' +
      (on || m.included ? ' checked' : '') + (m.included ? ' disabled' : '') + ' aria-label="' + esc(m.code) + '"></label></td>' +
      '<td class="mono nowrap">' + esc(m.code) + '</td><td>' + esc(m.description) + (m.exclGroup ? ' <span class="chip" title="Alternativo agli altri moduli dello stesso gruppo">alternativo</span>' : '') +
      (m.note && !m.rules.length ? '<div class="hint">' + esc(m.note.length > 160 ? m.note.slice(0, 158) + '…' : m.note) + '</div>' : '') +
      (rules ? '<div class="rules">' + rules + '</div>' : '') + '</td>' +
      '<td class="num pr nowrap">' + listCell + '</td><td class="num pr nowrap">' + custCell + '</td><td style="width:80px">' + qty + '</td></tr>';
  }

  /** l'applicazione ha una progressione delle licenze per scaglioni (gruppi di prezzo con piu' fasce; sono esclusi i gruppi lineari «per unita'») */
  const tierGroups = (a) => a.groups.filter((g) => g.baseMode !== 'LINEAR' && g.tiers.length > 1);
  const hasTiers = (a) => tierGroups(a).length > 0;

  /** finestra «centro informazioni»: tabella degli scaglioni di utenti (Da, A, costo) per ogni gruppo di prezzo dell'applicazione */
  function openTiersInfo(appId) {
    const a = appOf(appId); if (!a) return;
    const line = lineOf(appId);
    const mode = line ? line.modeCode : (a.modes.find((x) => x.preferred) || a.modes[0] || {}).code;
    const dlg = document.createElement('dialog');
    dlg.className = 'tiers-dlg';
    /** valore base del gruppo sul quale si applicano le percentuali: base fissa, altrimenti somma dei moduli selezionati del gruppo (x quantita') */
    const baseOf = (g) => {
      if (g.fixedBase && Object.keys(g.fixedBase).length) return Number(g.fixedBase[mode] != null ? g.fixedBase[mode] : (g.fixedBase.STD || 0));
      return a.modules.filter((m) => m.groupId === g.id && line && line.modules[m.id] > 0 && m.section !== 'LINK' && !m.included)
        .reduce((n, m) => n + C.modulePrice(m, mode) * (m.qtyEnabled ? line.modules[m.id] : 1), 0);
    };
    let needModules = false;
    const money = (t, g, i) => {
      const sing = (w) => { w = String(w || 'unità'); return /[^aeiou]i$/.test(w) ? w.slice(0, -1) + 'e' : (/ne$/.test(w) && !/zione$/.test(w) ? w.slice(0, -1) + 'a' : w); };      // utenti → utente, macchine → macchina
      const u = t.unitByMode || {}, d = a.drivers.find((x) => x.id === g.driverId) || {}, unit = esc(sing(d.unitLabel));
      const v = u[mode] != null ? u[mode] : u.STD;
      if (g.baseMode === 'STEP') return v != null ? '<b>' + CPQ.eur(v) + '</b> canone annuo della fascia' : '<span class="muted">valutazione ad hoc</span>';
      if (i === 0) return (g.fixedBase && Object.keys(g.fixedBase).length) ? '<b>' + CPQ.eur(g.fixedBase[mode] != null ? g.fixedBase[mode] : (g.fixedBase.STD || 0)) + '</b> (base inclusa)' : 'inclusa nel prezzo dei moduli';
      if (v != null) return '<b>' + CPQ.eur(v) + '</b> per ' + unit;
      if (t.pct == null) return '—';
      const base = baseOf(g);
      if (!(base > 0)) { needModules = true; return '<span class="muted">seleziona i moduli</span>'; }
      return '<b>' + CPQ.eur(base * t.pct / 100) + '</b> per ' + unit;          // controvalore in euro della percentuale sul valore dei moduli selezionati
    };
    const body = tierGroups(a).map((g) => {
      const d = a.drivers.find((x) => x.id === g.driverId) || {};
      const users = line ? (line.drivers[g.driverId] || 0) : 0;
      const rows = g.tiers.map((t, i) => {
        const cur = users >= 1 && users >= t.fromQty && (t.toQty == null || users <= t.toQty);
        return '<tr class="' + (cur ? 'cur' : '') + '"><td>' + esc(t.label) + (cur ? ' <span class="chip blue">' + users + ' ' + esc(d.unitLabel || 'unità') + '</span>' : '') + '</td><td class="num">' + t.fromQty + '</td><td class="num">' + (t.toQty == null ? '∞' : t.toQty) + '</td><td>' + money(t, g, i) + '</td></tr>';
      }).join('');
      return '<h4 style="margin:14px 0 6px">' + esc(g.label) + ' <span class="muted" style="font-weight:400">– ' + esc(d.label || 'utenti') + '</span></h4>' +
        '<table class="t"><thead><tr><th>Fascia</th><th class="num">Da</th><th class="num">A</th><th>Costo</th></tr></thead><tbody>' + rows + '</tbody></table>';
    }).join('');
    dlg.innerHTML = '<div class="dlg-head"><h3 style="margin:0">' + esc(a.name) + '</h3><div class="muted">Progressione delle licenze per scaglioni di utenti' + (mode ? ' · listino ' + esc(mode) : '') + '</div></div>' +
      '<div class="dlg-body">' + body + '<div class="hint" style="margin-top:12px">Il prezzo dei moduli è moltiplicato dal numero di utenti secondo gli scaglioni: i primi sono inclusi, gli ulteriori si pagano per ogni utente della fascia.' +
        (needModules ? ' Gli importi per utente si calcolano sul valore dei moduli selezionati: spunta i moduli dell\'applicazione per vederli.' : '') + '</div></div>' +
      '<div class="dlg-foot"><button type="button" class="btn" data-close>Chiudi</button></div>';
    document.body.appendChild(dlg);
    dlg.addEventListener('click', (e) => { if (e.target.closest('[data-close]') || e.target === dlg) dlg.close(); });
    dlg.addEventListener('close', () => dlg.remove());
    dlg.showModal();
  }

  function tiersText(a, g, mode) {
    const d = a.drivers.find((x) => x.id === g.driverId) || {};
    if (g.baseMode === 'LINEAR') {
      const t = g.tiers[0]; const u = (t.unitByMode || {});
      return esc(g.label) + ': ' + CPQ.eur(u[mode] != null ? u[mode] : (u.STD || 0)) + ' per ' + esc(d.unitLabel || 'unità');
    }
    const parts = g.tiers.map((t, i) => {
      const u = t.unitByMode || {};
      let s = esc(t.label) + ': ';
      if (i === 0) {
        s += (g.fixedBase && Object.keys(g.fixedBase).length) ? CPQ.eur(g.fixedBase[mode] != null ? g.fixedBase[mode] : (g.fixedBase.STD || 0)) + ' (inclusa)' : 'inclusa nel prezzo dei moduli';
      } else if (u[mode] != null || u.STD != null) s += CPQ.eur(u[mode] != null ? u[mode] : u.STD) + ' / ' + esc(d.unitLabel || 'unità');
      else s += (t.pct != null ? t.pct + '% del valore moduli per ' + esc(d.unitLabel || 'unità') : '—');
      return s;
    });
    return '<b>' + esc(g.label) + '</b> – ' + parts.join(' · ');
  }

  function appDetail(a, line) {
    const mode = line ? line.modeCode : (a.modes.find((x) => x.preferred) || a.modes[0] || {}).code;
    let h = '<div class="detail-inner">';
    if (!line) h += '<div class="alert info" style="margin-bottom:10px"><svg class="ic"><use href="#i-info"/></svg><span>Spuntando un modulo l\'applicazione viene aggiunta automaticamente all\'offerta.</span></div>';
    if (a.notes) h += '<div class="hint" style="margin-bottom:8px">' + esc(a.notes) + '</div>';
    const byGroup = [];
    // Dipartimentali: niente titolo e niente riga delle fasce per il gruppo «Moduli per utente» (i moduli restano)
    const hideGroup = (g) => /^moduli per utente$/i.test(String(g.label || '').trim());
    a.groups.forEach((g) => {
      const mods = a.modules.filter((m) => m.groupId === g.id && m.section !== 'LINK');
      if (mods.length) byGroup.push({ label: hideGroup(g) ? '' : g.label + ' – il prezzo è moltiplicato dal numero di ' + ((a.drivers.find((d) => d.id === g.driverId) || {}).unitLabel || 'utenti'), mods: mods });
    });
    byGroup.forEach((bg) => { h += (bg.label ? '<h4>' + esc(bg.label) + '</h4>' : '') + '<table class="mods">' + MOD_HEAD + '<tbody>' + bg.mods.map((m) => modRow(a, line, m)).join('') + '</tbody></table>'; });
    const fixed = a.modules.filter((m) => m.groupId == null && m.section !== 'LINK');
    if (fixed.length) h += '<h4>Moduli a prezzo fisso</h4><table class="mods">' + MOD_HEAD + '<tbody>' + fixed.map((m) => modRow(a, line, m)).join('') + '</tbody></table>';
    const links = a.modules.filter((m) => m.section === 'LINK');
    if (links.length) h += '<h4>Moduli presenti in altri listini</h4><div class="hint">' + links.map((m) => '<b>' + esc(m.code) + '</b> ' + esc(m.description) + (m.linkText ? ' (' + esc(m.linkText) + ')' : '')).join(' · ') + '. Aggiungere l\'applicazione corrispondente all\'offerta.</div>';
    const tg = a.groups.filter((g) => !hideGroup(g));
    if (tg.length) h += '<div class="tiers">' + tg.map((g) => tiersText(a, g, mode)).join('<br>') + '</div>';
    return h + '</div>';
  }

  // ---------------------------------------------------------------- eventi tabella
  function bindStep2() {
    $('#maint-on-net').addEventListener('change', (e) => { S.maintOnNet = e.target.checked; updateSummaryTotals(); });
    const body = $('#apps-body');
    body.addEventListener('change', (e) => {
      const t = e.target; const id = Number(t.dataset.app); const a = appOf(id);
      if (t.classList.contains('app-check')) {
        if (t.checked) { S.lines.push(C.newLine(a)); expanded.add(id); } else { S.lines = S.lines.filter((l) => l.applicationId !== id); }
        renderApps(); scheduleCalc();
      } else if (t.classList.contains('mode-sel')) {
        lineOf(id).modeCode = t.value; renderApps(); scheduleCalc();
      } else if (t.classList.contains('mod-check')) {
        // modulo spuntato in un'applicazione non ancora selezionata: si seleziona anche l'applicazione
        const appAdded = t.checked && !lineOf(id);
        if (appAdded) { S.lines.push(C.newLine(a)); expanded.add(id); }
        const r = C.selectModule(catalog, S.lines, id, Number(t.dataset.mod), t.checked);
        r.addedApps.forEach((n) => { const ap = catalog.find((x) => x.name === n); if (ap) expanded.add(ap.id); });
        const msgs = [];
        if (appAdded) msgs.push('Aggiunta all\'offerta l\'applicazione ' + a.name);
        if (r.addedModules.length) msgs.push('Aggiunti i prerequisiti: ' + r.addedModules.join(', '));
        if (r.addedApps.length) msgs.push('Aggiunta all\'offerta anche: ' + r.addedApps.join(', '));
        if (r.removed.length > 1) msgs.push('Rimossi anche i moduli che li richiedevano: ' + r.removed.slice(1).join(', '));
        r.ambiguous.forEach((a) => msgs.push('Prerequisito ' + a.code + ': aggiungi una di queste applicazioni (' + a.apps.join(', ') + ')'));
        if (msgs.length) toast(msgs.join(' · '), r.ambiguous.length > 0);
        renderApps(); scheduleCalc();
      }
    });
    body.addEventListener('click', (e) => { const b = e.target.closest('.tiers-info'); if (b) { e.stopPropagation(); openTiersInfo(Number(b.dataset.app)); } });
    body.addEventListener('input', (e) => {
      const t = e.target; const id = Number(t.dataset.app);
      if (t.classList.contains('drv-in')) { lineOf(id).drivers[Number(t.dataset.drv)] = Math.max(0, parseInt(t.value || '0', 10) || 0); scheduleCalc(); if (S.usersMark) markUsers(); }
      else if (t.classList.contains('mod-qty')) { lineOf(id).modules[Number(t.dataset.mod)] = Math.max(1, parseInt(t.value || '1', 10) || 1); scheduleCalc(); }
    });
    // listino / importo a cliente di un modulo selezionato: alla conferma del campo (campo vuoto = torna al valore di default)
    body.addEventListener('change', (e) => {
      const t = e.target; if (!t.classList.contains('mod-amt')) return;
      const id = Number(t.dataset.app), modId = Number(t.dataset.mod), line = lineOf(id), a = appOf(id), m = a && a.modules.find((x) => x.id === modId);
      if (!line || !m) return;
      const isList = t.classList.contains('mod-list'), raw = String(t.value || '').trim(), v = raw === '' ? null : C.parseMoney(raw);
      if (raw !== '' && (v == null || v < 0)) { CPQ.fieldError(t, 'Indica un importo valido (non negativo)'); const p0 = modPrices(line, m, line.modeCode); t.value = CPQ.num(isList ? p0.list : p0.cust, 2); return; }
      line.modPrice = line.modPrice || {};
      const cur = line.modPrice[modId] || (line.modPrice[modId] = { list: null, cust: null });
      const cat = C.modulePrice(m, line.modeCode);
      if (isList) cur.list = v == null || v === cat ? null : v; else cur.cust = v;
      if (cur.list == null && cur.cust == null) delete line.modPrice[modId];
      const p = modPrices(line, m, line.modeCode), row = t.closest('tr');
      const l = row.querySelector('.mod-list'), c = row.querySelector('.mod-cust');
      if (l) { l.value = CPQ.num(p.list, 2); l.classList.toggle('edited', p.listEdited); }
      if (c) { c.value = CPQ.num(p.cust, 2); c.classList.toggle('edited', p.custEdited); }
      CPQ.markDirty('page', root); scheduleCalc();
    });
    body.addEventListener('click', (e) => {
      if (e.target.closest('.tiers-info')) return;                  // icona scaglioni: apre solo la finestra informativa
      const tg = e.target.closest('.toggle');
      if (tg) { const id = Number(tg.dataset.app); expanded.has(id) ? expanded.delete(id) : expanded.add(id); renderApps(); return; }
      const nameCell = e.target.closest('td.name');
      if (nameCell) { const id = Number(nameCell.parentElement.dataset.app); expanded.has(id) ? expanded.delete(id) : expanded.add(id); renderApps(); }
    });
    $('#app-search').addEventListener('input', (e) => { filter.text = e.target.value; renderApps(); });
    $('#families').addEventListener('click', (e) => {
      const c = e.target.closest('.chip.filter'); if (!c) return;
      if (c.dataset.onlysel) filter.onlySel = !filter.onlySel; else filter.family = c.dataset.fam;
      renderApps();
    });
  }

  // ---------------------------------------------------------------- calcolo (server)
  let calcTimer; let calcSeq = 0;
  function scheduleCalc() {
    syncSmitechUsers();
    clearTimeout(calcTimer);
    calcTimer = setTimeout(async () => {
      const seq = ++calcSeq;
      try {
        const res = await CPQ.api('POST', '/offers/api/calc', buildReq());
        if (seq !== calcSeq) return;
        calc = res; updateAmounts();
        if (S.step === 4) renderSummary();
        if (S.step === 3) renderViolations('#x-violations');
      } catch (e) { toast(e.message, true); }
    }, 220);
  }

  function lineCalc(appId) {
    if (!calc) return null;
    const idx = S.lines.findIndex((l) => l.applicationId === appId);
    return idx >= 0 ? calc.lines[idx] : null;
  }

  /** netto e margine calcolati subito nel browser (il server li ricalcola al salvataggio) */
  const r2 = (v) => Math.round((v + Number.EPSILON) * 100) / 100;
  /** listino di vendita e manutenzione annua della configurazione Galileo (se presente e valida) */
  /** moduli Galileo selezionati (testo breve per la tabella del riepilogo) */
  function galSelectedText() {
    if (!G.profile) return '';
    const sel = G.profile.modules.filter((m) => S.gal.sel[m.moduleId] && m.flag !== 'CB' && m.flag !== 'V11').map((m) => m.code);
    return sel.length ? sel.join(', ') : 'Moduli base';
  }
  function galileoTotals() {
    const r = stepVisible(8) && S.gal.calc && S.gal.calc.result && S.gal.calc.result.valid ? S.gal.calc.result : null;
    return r ? { list: Number(r.list) || 0, maint: Number(r.maintenance) || 0 } : { list: 0, maint: 0 };
  }
  function nets() {
    // licenze e manutenzione: somma delle applicazioni Dipartimentali e della configurazione Galileo
    const g = galileoTotals();
    const tl = r2((calc ? calc.totalLicense : 0) + g.list); const tm = r2((calc ? calc.totalMaintenance : 0) + g.maint);
    const d = Math.min(100, Math.max(0, Number(S.discountPct) || 0));
    // «Manutenzione su netto scontato»: la manutenzione segue lo sconto; altrimenti resta sul valore di listino
    // base dello sconto: importi calcolati con i prezzi a cliente dei moduli (uguali al listino se non modificati)
    const cl = calc ? calc.lines.reduce((a, l) => a + (l.customerLicenseTotal != null ? l.customerLicenseTotal : l.licenseTotal), 0) : 0;
    const cm = calc ? calc.lines.reduce((a, l) => a + (l.customerMaintenance != null ? l.customerMaintenance : l.maintenance), 0) : 0;
    const nl = r2((cl + g.list) * (1 - d / 100)); const nm = S.maintOnNet ? r2((cm + g.maint) * (1 - d / 100)) : tm;
    return { tl: tl, tm: tm, d: d, nl: nl, nm: nm, margin: r2(nl + nm) };
  }

  function updateAmounts() {
    $$('[data-amt]').forEach((td) => {
      const [kind, id] = td.dataset.amt.split(':'); const lc = lineCalc(Number(id));
      td.textContent = lc ? CPQ.eur(kind === 'lic' ? lc.licenseTotal : lc.maintenance) : '';
    });
    // «Totali Dipartimentali»: solo le applicazioni a catalogo (senza Galileo)
    const dl = calc ? calc.totalLicense : 0, dm = calc ? calc.totalMaintenance : 0;
    $('#dip-t-list').textContent = CPQ.eur(dl);
    $('#dip-t-maint').textContent = CPQ.eur(dm);
    $('#dip-t-note').textContent = S.lines.length ? S.lines.length + (S.lines.length === 1 ? ' applicazione' : ' applicazioni') : '';
    $('#dip-lines').innerHTML = S.lines.map((l) => {
      const lc = lineCalc(l.applicationId), ap = appOf(l.applicationId);
      return '<tr class="gal-lines"><td>' + esc(ap ? ap.name : '') + '</td><td class="num pr">' + (lc ? CPQ.eur(lc.licenseTotal) : '') + '</td><td class="num pr">' + (lc ? CPQ.eur(lc.maintenance) : '') + '</td></tr>';
    }).join('');
    const n = nets();
    $('#t-lic').textContent = CPQ.eur(n.tl);
    $('#t-man').textContent = CPQ.eur(n.tm);
    const hasDisc = n.d > 0 || n.nl !== n.tl || n.nm !== n.tm;          // netto visibile con sconto o con importi a cliente modificati
    $('#t-net-wrap').hidden = !hasDisc;
    if (hasDisc) { $('#t-net-lic').textContent = CPQ.eur(n.nl); $('#t-net-man').textContent = CPQ.eur(n.nm); }
    $('#t-margin').textContent = CPQ.eur(n.margin);
    const at = C.activityTotals(S.activities, S.lines, S.actDiscount);
    $('#t-act-list').textContent = CPQ.eur(at.list); $('#t-act-net').textContent = CPQ.eur(at.net);
    $('#t-count').textContent = S.lines.length + (S.lines.length === 1 ? ' applicazione' : ' applicazioni');
    renderViolations('#violations');
    $('#btn-next2').disabled = false;
    refreshStepper();
  }

  function violationList() {
    return (calc && calc.violations) ? calc.violations : [];
  }

  function renderViolations(sel) {
    const box = $(sel); if (!box) return;
    let vs = violationList().filter((v) => v.severity !== 'INFO' || sel === '#violations');
    // applicationId 0 = servizi accessori: solo nel loro passo e nel riepilogo
    if (sel === '#x-violations') vs = vs.filter((v) => v.applicationId === 0);
    if (sel === '#violations') vs = vs.filter((v) => v.applicationId !== 0);
    if (!vs.length) { box.innerHTML = ''; return; }
    const name = (id) => (appOf(id) || {}).name || '';
    box.innerHTML = vs.map((v) => '<div class="alert ' + (v.severity === 'ERROR' ? 'err' : (v.severity === 'WARNING' ? 'warn' : 'info')) + '"><svg class="ic"><use href="#i-' + (v.severity === 'INFO' ? 'info' : 'warning') + '"/></svg><span>' + (name(v.applicationId) ? '<b>' + esc(name(v.applicationId)) + '</b> – ' : '') + esc(v.message) + '</span></div>').join('');
  }


  // ---------------------------------------------------------------- step 3: servizi accessori
  const svcOf = (id) => extrasCatalog.find((s) => s.id === id);

  function xServiceTotals(s) {
    let one = 0; let ann = 0; let adHoc = false;
    s.groups.forEach((g) => g.options.forEach((o) => {
      const q = g.selection === 'FIXED' ? 1 : S.xSel.options[o.id];
      if (!q) return;
      const k = o.qtyEnabled ? q : 1;
      one += (o.onetime || 0) * k; ann += (o.annual || 0) * k; if (o.adHoc) adHoc = true;
    }));
    return { one: one, ann: ann, adHoc: adHoc };
  }

  function xTotals() {
    let one = 0; let ann = 0;
    extrasCatalog.forEach((s) => { if (S.xSel.services.has(s.id)) { const t = xServiceTotals(s); one += t.one; ann += t.ann; } });
    return { one: one, ann: ann };
  }

  function optAmounts(o) {
    const parts = [];
    if (o.onetime) parts.push(CPQ.eur(o.onetime) + ' una tantum');
    if (o.annual) parts.push(CPQ.eur(o.annual) + ' / anno');
    if (o.adHoc) parts.push(esc(o.adHocText || 'ad hoc'));
    return parts.join(' · ');
  }

  function renderExtras() {
    const box = $('#extras-body');
    if (!box) return;
    box.innerHTML = extrasCatalog.map((s) => {
      const on = S.xSel.services.has(s.id);
      const t = xServiceTotals(s);
      const chips = on ? (s.includedText ? '<span class="chip ok pr">' + esc(s.includedText) + '</span>' :
        '<span class="chip blue pr">una tantum ' + CPQ.eur(t.one) + '</span> <span class="chip blue pr">canone ' + CPQ.eur(t.ann) + (t.adHoc ? ' + ad hoc' : '') + '</span>') : '';
      let body = '';
      if (s.includedText) body = '<div class="hint">Servizio compreso nel contratto, senza costi aggiuntivi.</div>';
      s.groups.forEach((g) => {
        if (g.selection === 'FIXED') {
          body += '<h4>' + esc(g.title) + ' <span class="hint">(inclusi con il servizio)</span></h4><table class="mods"><tbody>' +
            g.options.map((o) => '<tr class="' + (on ? 'on' : '') + '"><td class="mono nowrap" style="width:130px">' + esc(o.articleCode || o.rowKey) + '</td><td>' + esc(o.label) + '</td><td class="num pr nowrap">' + optAmounts(o) + '</td></tr>').join('') + '</tbody></table>';
          return;
        }
        const one = g.selection === 'ONE';
        const missing = on && g.required && !g.options.some((o) => S.xSel.options[o.id]);
        body += '<h4>' + esc(g.title) + ' <span class="hint">' + (one ? '(scegli una riga' + (g.required ? ', obbligatorio' : '') + ')' : '(opzionale, anche più righe)') + '</span>' +
          (missing ? ' <span class="chip err">da scegliere</span>' : '') +
          (one && g.options.some((o) => S.xSel.options[o.id]) ? ' <button type="button" class="btn text sm x-clear" data-grp="' + g.id + '">Annulla scelta</button>' : '') + '</h4>' +
          '<table class="mods"><tbody>' + g.options.map((o) => {
            const q = S.xSel.options[o.id]; const picked = !!q;
            const input = one ? '<input type="radio" class="x-opt" name="xg' + g.id + '" data-grp="' + g.id + '" data-opt="' + o.id + '"' + (picked ? ' checked' : '') + '>' :
              '<input type="checkbox" class="x-opt" data-grp="' + g.id + '" data-opt="' + o.id + '"' + (picked ? ' checked' : '') + '>';
            const qty = o.qtyEnabled && picked ? '<input type="number" min="1" class="x-qty qty" data-opt="' + o.id + '" value="' + q + '" style="width:72px;height:32px;border:1px solid var(--outline);border-radius:6px;padding:0 6px;text-align:right" title="Quantità">' : '';
            return '<tr class="' + (picked ? 'on' : '') + '"><td style="width:34px"><label class="check">' + input + '</label></td>' +
              '<td class="mono nowrap" style="width:60px">' + esc(o.rowKey.length <= 2 ? o.rowKey : (o.articleCode || o.rowKey)) + '</td><td>' + esc(o.label) + (o.qtyEnabled ? ' <span class="chip">a quantità</span>' : '') + '</td>' +
              '<td class="num pr nowrap">' + optAmounts(o) + (o.extraUnit ? '<div class="hint">oltre soglia ' + CPQ.eur(o.extraUnit) + ' cad.</div>' : '') + '</td><td style="width:90px">' + qty + '</td></tr>';
          }).join('') + '</tbody></table>';
      });
      return '<div class="card x-svc" data-svc="' + s.id + '"><div class="cardhead"><label class="check"><input type="checkbox" class="x-svc-check" data-svc="' + s.id + '"' + (on ? ' checked' : '') + '><b style="font-size:16px">' + esc(s.title) + '</b></label><span class="grow"></span>' + chips + '</div>' +
        '<div class="detail-inner" style="padding:6px 0 0 4px;border-left:0">' + body + '</div></div>';
    }).join('');
    updateExtrasUi();
  }

  function updateExtrasUi() {
    const t = xTotals();
    const el = $('#x-total'); if (el) el.textContent = 'una tantum ' + CPQ.eur(t.one) + ' · canone ' + CPQ.eur(t.ann);
    renderViolations('#x-violations');
  }

  function bindExtras() {
    const box = $('#extras-body');
    box.addEventListener('change', (e) => {
      const t = e.target;
      if (t.classList.contains('x-svc-check')) {
        const sid = Number(t.dataset.svc); const s = svcOf(sid);
        if (t.checked) S.xSel.services.add(sid);
        else { S.xSel.services.delete(sid); s.groups.forEach((g) => g.options.forEach((o) => { delete S.xSel.options[o.id]; })); }
      } else if (t.classList.contains('x-opt')) {
        const g = extrasCatalog.flatMap((s) => s.groups).find((x) => x.id === Number(t.dataset.grp));
        if (g.selection === 'ONE') g.options.forEach((o) => { delete S.xSel.options[o.id]; });
        if (t.checked) S.xSel.options[Number(t.dataset.opt)] = 1; else delete S.xSel.options[Number(t.dataset.opt)];
        S.xSel.services.add(g.serviceId);        // scegliendo una riga si seleziona anche il servizio
      } else return;
      renderExtras(); scheduleCalc();
    });
    box.addEventListener('input', (e) => {
      const t = e.target;
      if (t.classList.contains('x-qty')) { S.xSel.options[Number(t.dataset.opt)] = Math.max(1, parseInt(t.value || '1', 10) || 1); updateExtrasUi(); scheduleCalc(); }
    });
    box.addEventListener('click', (e) => {
      const b = e.target.closest('.x-clear'); if (!b) return;
      const g = extrasCatalog.flatMap((s) => s.groups).find((x) => x.id === Number(b.dataset.grp));
      g.options.forEach((o) => { delete S.xSel.options[o.id]; });
      renderExtras(); scheduleCalc();
    });
  }

  function renderSummaryExtras() {
    const card = $('#sum-extras-card');
    if (!card) return;
    card.hidden = !S.optServices;
    if (!S.optServices) return;
    const rows = extrasCatalog.filter((s) => S.xSel.services.has(s.id)).map((s) => {
      const t = xServiceTotals(s);
      const picks = [];
      s.groups.forEach((g) => { if (g.selection === 'FIXED') return; g.options.forEach((o) => { const q = S.xSel.options[o.id]; if (q) picks.push(esc(o.rowKey.length <= 2 ? 'Riga ' + o.rowKey + ' – ' + o.label : o.label) + (o.qtyEnabled && q > 1 ? ' × ' + q : '')); }); });
      return '<tr><td><b>' + esc(s.title) + '</b></td><td>' + (picks.join('<br>') || '<span class="muted">—</span>') + '</td>' +
        '<td class="num pr">' + (s.includedText ? esc(s.includedText) : CPQ.eur(t.one)) + '</td><td class="num pr">' + (s.includedText ? '—' : CPQ.eur(t.ann) + (t.adHoc ? '<div class="hint">+ ad hoc</div>' : '')) + '</td></tr>';
    });
    $('#sum-extras').innerHTML = rows.join('') || '<tr><td colspan="4" class="muted">Nessun servizio selezionato.</td></tr>';
  }


  // ---------------------------------------------------------------- modalita' di fatturazione e pagamento
  /** finche' l'utente non le modifica, il pagamento segue il tipo di account: prospect = RI.BA. 30 gg dffm, cliente = solito in uso */
  const custType = () => (S.customer ? S.customer.type : 'PROSPECT');

  function applyDefaultPay() {
    if (!termsCfg || S.offerId) return;
    C.applyDefaultPay(termsCfg, S.terms, custType(), S.payTouched);
  }

  function optionsHtml(list, sel) {
    return list.map((o) => '<option value="' + esc(o.code) + '"' + (o.code === sel ? ' selected' : '') + '>' + esc(o.label) + '</option>').join('');
  }

  function payOptionsHtml(allowed, sel) {
    return allowed.map((code) => { const o = termsCfg.pay.find((x) => x.code === code); return '<option value="' + esc(code) + '"' + (code === sel ? ' selected' : '') + '>' + esc(o.label) + '</option>'; }).join('');
  }

  function renderTerms() {
    const box = $('#terms-body');
    if (!box || !termsCfg || !S.terms) return;
    C.normalizePay(termsCfg, S.terms, custType());
    const t = S.terms;
    const isDate = (list, code) => (list.find((o) => o.code === code) || {}).date;
    const isFree = (code) => (termsCfg.pay.find((o) => o.code === code) || {}).free;
    const dateIn = (id, val) => '<input type="date" id="' + id + '" value="' + esc(val) + '" class="' + (val ? '' : 'bad') + '" aria-label="Data" required>';
    const otherIn = (id, val) => '<input type="text" id="' + id + '" value="' + esc(val) + '" maxlength="250" placeholder="Scrivi la modalità di pagamento" class="' + (val.trim() ? '' : 'bad') + '">';
    box.innerHTML = '<div class="terms-grid">' +
      '<div></div><div class="th">Fatturazione</div><div class="th">Pagamento</div>' +
      // licenze
      '<div class="rowlabel">Licenze software</div>' +
      '<div class="tcell"><span class="mlabel">Fatturazione</span><select id="t-bill-lic">' + optionsHtml(termsCfg.licBill, t.billLic) + '</select>' + (isDate(termsCfg.licBill, t.billLic) ? dateIn('t-bill-lic-date', t.billLicDate) : '') + '</div>' +
      '<div class="tcell"><span class="mlabel">Pagamento</span><select id="t-pay-lic">' + payOptionsHtml(C.allowedPay(termsCfg, 'lic', null, custType()), t.payLic) + '</select>' + (isFree(t.payLic) ? otherIn('t-pay-lic-other', t.payLicOther) : '') + '</div>' +
      // canoni
      '<div class="rowlabel">Canone di manutenzione<br><label class="check" style="margin-top:6px;font-weight:400"><input type="checkbox" id="t-semi"' + (t.feeSemiannual ? ' checked' : '') + '> Semestrale</label></div>' +
      '<div class="tcell"><span class="mlabel">Fatturazione</span><select id="t-bill-fee">' + optionsHtml(termsCfg.feeBill, t.billFee) + '</select>' + (isDate(termsCfg.feeBill, t.billFee) ? dateIn('t-bill-fee-date', t.billFeeDate) : '') + '</div>' +
      '<div class="tcell"><span class="mlabel">Pagamento</span><select id="t-pay-fee">' + payOptionsHtml(C.allowedPay(termsCfg, 'fee', null, custType()), t.payFee) + '</select>' + (isFree(t.payFee) ? otherIn('t-pay-fee-other', t.payFeeOther) : '') + '</div>' +
      // attivita' sistemistiche
      '<div class="rowlabel">Attività sistemistiche</div>' +
      '<div class="tcell"><span class="mlabel">Fatturazione</span><select id="t-bill-act">' + optionsHtml(termsCfg.actBill, t.billAct) + '</select></div>' +
      '<div class="tcell"><span class="mlabel">Pagamento</span><select id="t-pay-act">' + payOptionsHtml(C.allowedPay(termsCfg, 'act', t.billAct, custType()), t.payAct) + '</select>' + (isFree(t.payAct) ? otherIn('t-pay-act-other', t.payActOther) : '') + '</div>' +
      // canone SMIDRI / SMISMP: solo se l'offerta comprende SMIDRI e/o SMISMP
      (driLabel() ? '<div class="rowlabel">Canone ' + esc(driLabel()) + '</div>' +
        '<div class="tcell"><span class="mlabel">Fatturazione</span><select id="t-bill-dri">' + optionsHtml(termsCfg.driBill, t.billDri) + '</select>' + (isDate(termsCfg.driBill, t.billDri) ? dateIn('t-bill-dri-date', t.billDriDate) : '') + '</div>' +
        '<div class="tcell"><span class="mlabel">Pagamento</span><select id="t-pay-dri">' + payOptionsHtml(C.allowedPay(termsCfg, 'dri', null, custType()), t.payDri) + '</select>' + (isFree(t.payDri) ? otherIn('t-pay-dri-other', t.payDriOther) : '') + '</div>' : '') +
      '</div>';
  }

  /** SMIDRI / SMISMP presenti in offerta: «SMIDRI», «SMISMP», «SMIDRI/SMISMP» oppure '' (nessuno) */
  function driLabel() {
    let dri = false, smp = false;
    if (S.optPower && S.powerKind === 'HADR' && S.hadrSolution === 'SMIDRI') { dri = true; smp = !!S.smidriSmismp; }
    const app = smitechApp();
    if (app && S.optSmitech !== false) {
      const l = lineOf(app.id);
      if (l) app.modules.forEach((m) => { if (l.modules[m.id] > 0) { const c = String(m.code).toUpperCase(); dri = dri || c.indexOf('SMIDRI') >= 0; smp = smp || c.indexOf('SMISMP') >= 0; } });
    }
    return dri && smp ? 'SMIDRI/SMISMP' : (dri ? 'SMIDRI' : (smp ? 'SMISMP' : ''));
  }

  function bindTerms() {
    const box = $('#terms-body');
    box.addEventListener('change', (e) => {
      const id = e.target.id; const t = S.terms; const v = e.target.value;
      if (id === 't-bill-lic') t.billLic = v;
      else if (id === 't-bill-lic-date') t.billLicDate = v;
      else if (id === 't-bill-fee') t.billFee = v;
      else if (id === 't-bill-fee-date') t.billFeeDate = v;
      else if (id === 't-semi') t.feeSemiannual = e.target.checked;
      else if (id === 't-bill-act') { t.billAct = v; C.fixActPay(termsCfg, t, custType()); }
      else if (id === 't-pay-lic') { t.payLic = v; S.payTouched.lic = true; }
      else if (id === 't-pay-fee') { t.payFee = v; S.payTouched.fee = true; }
      else if (id === 't-pay-act') { t.payAct = v; S.payTouched.act = true; }
      else if (id === 't-bill-dri') t.billDri = v;
      else if (id === 't-bill-dri-date') t.billDriDate = v;
      else if (id === 't-pay-dri') { t.payDri = v; S.payTouched.dri = true; }
      else return;
      renderTerms(); refreshSaveState();
    });
    box.addEventListener('input', (e) => {
      const id = e.target.id; const t = S.terms; const v = e.target.value;
      if (id === 't-pay-lic-other') { t.payLicOther = v; S.payTouched.lic = true; }
      else if (id === 't-pay-fee-other') { t.payFeeOther = v; S.payTouched.fee = true; }
      else if (id === 't-pay-act-other') { t.payActOther = v; S.payTouched.act = true; }
      else if (id === 't-pay-dri-other') { t.payDriOther = v; S.payTouched.dri = true; }
      else return;
      e.target.classList.toggle('bad', !v.trim()); refreshSaveState();
    });
  }

  // ---------------------------------------------------------------- step 4: riepilogo
  function renderSummary() {
    $('#sum-number').textContent = C.offerNumber(S.date, S.progressive, S.initials);
    const c = S.customer;
    $('#sum-customer').textContent = c ? c.companyName + ' (' + c.code + ')' : '—';
    $('#sum-elements').textContent = elementsText();
    $('#sum-title').value = S.title || C.suggestTitle(catalog, S.lines);
    const tl = document.querySelector('label[for="sum-title"]');
    if (tl) tl.textContent = 'Titolo del progetto (copertina)' + (C.suggestTitle(catalog, S.lines) ? '' : ' *');          // obbligatorio se non calcolato in automatico
    if (document.activeElement !== $('#svc-disc')) $('#svc-disc').value = S.actDiscount || '';
    syncUntil();
    if (document.activeElement !== $('#sum-disc')) $('#sum-disc').value = S.discountPct || '';
    // le applicazioni si dividono in due tabelle: licenze una tantum con manutenzione (%) e servizi a canone annuo (moduli ANNUAL / MONTHLY)
    const isFee = (m) => m && (m.kind === 'ANNUAL' || m.kind === 'MONTHLY');
    const rows = []; const feeRows = [];
    S.lines.forEach((l, i) => {
      const a = appOf(l.applicationId); const lc = calc ? calc.lines[i] : null;
      const modHtml = (pred) => Object.keys(l.modules).map((id) => { const m = a.modules.find((x) => x.id === Number(id)); return m && l.modules[id] > 0 && pred(m) ? '<div><span class="mono">' + esc(m.code) + '</span> ' + esc(m.description) + (l.modules[id] > 1 ? ' <b>× ' + l.modules[id] + '</b>' : '') + '</div>' : ''; }).join('');
      const seats = perUserSelected(a, l) ? a.drivers.filter((d) => l.drivers[d.id] > 0).map((d) => esc(d.label) + ': <b>' + CPQ.num(l.drivers[d.id], 0) + '</b>').join(' · ') : '';
      const mode = a.modes.length ? '<div class="hint">' + esc((a.modes.find((m) => m.code === l.modeCode) || {}).label || '') + '</div>' : '';
      const fee = lc ? Number(lc.annual) || 0 : 0;
      const licMods = modHtml((m) => !isFee(m)), feeMods = modHtml(isFee);
      if (licMods || !feeMods) rows.push('<tr><td><b>' + esc(a.name) + '</b>' + mode + '</td><td>' + licMods + '</td><td>' + seats + '</td><td class="num pr">' + (lc ? CPQ.eur(lc.licenseTotal) : '') + '</td><td class="num pr">' + (lc ? CPQ.eur(r2(lc.maintenance - fee)) : '') + '</td></tr>');
      if (feeMods || fee > 0) feeRows.push('<tr><td><b>' + esc(a.name) + '</b>' + mode + '</td><td>' + feeMods + '</td><td>' + seats + '</td><td class="num pr">' + (lc ? CPQ.eur(fee) : '') + '</td></tr>');
    });
    const gt = galileoTotals();
    const galRow = stepVisible(8) && S.gal.calc && S.gal.calc.result && S.gal.calc.result.valid
      ? '<tr><td><b>Galileo</b><div class="hint">' + esc(galProfileLabel()) + '</div></td><td>' + esc(galMarketName()) + '</td><td>Utenti: <b>' + CPQ.num(Number(S.gal.users) || 0, 0) + '</b></td><td class="num pr">' + CPQ.eur(gt.list) + '</td><td class="num pr">' + CPQ.eur(gt.maint) + '</td></tr>' : '';
    $('#sum-body').innerHTML = galRow + rows.join('');                       // Galileo, se presente, e' sempre la prima riga
    $('#sum-annual-wrap').hidden = !feeRows.length;
    $('#sum-annual-body').innerHTML = feeRows.join('');
    renderSummaryExtras();
    updateSummaryTotals();
    renderViolations('#sum-violations');
    renderTerms();
    renderTravel();
    if (S.customer && S.distFor !== S.customer.id && travelAuto()) loadDistance(S.distFor !== null);
    refreshSaveState();
  }

  // ---------------------------------------------------------------- servizi: attivita' sistemistiche per applicazione
  const profileOf = (id) => S.profiles.find((p) => p.id === Number(id));

  // ---------------------------------------------------------------- servizi manuali: gruppi di righe (riordinabili con il trascinamento)
  let gidSeq = 1;
  function newGroup(name, appId) { const g = { gid: 'g' + (gidSeq++), name: name || '', appId: appId || null }; S.svcGroups.push(g); return g; }
  const groupOf = (gid) => S.svcGroups.find((g) => g.gid === gid);
  /** dalle righe salvate (applicazione + titolo) ai gruppi: righe consecutive con stessa applicazione e stesso titolo */
  function buildGroupsFromRows() {
    S.svcGroups = []; let cur = null;
    S.activities.forEach((r) => {
      if (r.rfq) return;
      const app = r.appId || null, grp = r.group || '';
      if (!cur || cur.appId !== app || cur.name !== grp) cur = newGroup(grp, app);
      r.gid = cur.gid; r.appId = app;
    });
  }
  /** le righe seguono l'ordine dei gruppi (e' l'ordine del Word) */
  function syncActivityOrder() {
    const out = [];
    S.svcGroups.forEach((g) => S.activities.forEach((r) => { if (r.gid === g.gid) out.push(r); }));
    S.activities.forEach((r) => { if (!r.gid) out.push(r); });
    S.activities = out;
  }
  function syncRowsFromGroup(g) { S.activities.forEach((r) => { if (r.gid === g.gid) { r.group = g.name; r.appId = g.appId; } }); }
  function addManualRow(gid) {
    const g = groupOf(gid); if (!g) return;
    const row = { appId: g.appId, group: g.name, gid: gid, desc: '', profileId: null, hours: '', rate: 0 };
    S.activities.push(row); syncActivityOrder(); renderServices(); CPQ.markDirty('page', root);
    const tr = $('#svc-body tr[data-i="' + S.activities.indexOf(row) + '"]'); if (tr) tr.querySelector('.svc-desc').focus();
  }
  function renderManualGroups() {
    if (!S.svcGroups.length && !S.activities.some((r) => !r.rfq)) newGroup('', null);
    const apps = S.lines.map((l) => appOf(l.applicationId)).filter(Boolean);
    const IC = (n) => '<svg class="ic"><use href="#i-' + n + '"/></svg>';
    return '<div class="mg-bar"><button type="button" class="btn tonal sm" data-mnew>' + IC('add') + 'Aggiungi gruppo</button>' +
      '<span class="hint">Il gruppo è facoltativo: puoi scrivere le righe senza titolo. Per cambiare l\'ordine dei gruppi trascina l\'icona a sinistra del titolo.</span></div>' +
      S.svcGroups.map((g) => {
        const rows = S.activities.map((r, i) => [r, i]).filter(([r]) => r.gid === g.gid);
        return '<div class="mg" data-gid="' + g.gid + '"><div class="mg-head"><span class="mg-handle" draggable="true" title="Trascina per spostare il gruppo" aria-label="Sposta il gruppo">' + IC('drag') + '</span>' +
          '<input class="mg-name" data-gid="' + g.gid + '" maxlength="250" placeholder="Titolo del gruppo (facoltativo)" value="' + esc(g.name) + '">' +
          (apps.length ? '<select class="mg-app" data-gid="' + g.gid + '" title="Applicazione collegata (facoltativa)"><option value="">Nessuna applicazione</option>' +
            apps.map((a) => '<option value="' + a.id + '"' + (g.appId === a.id ? ' selected' : '') + '>' + esc(a.name) + '</option>').join('') + '</select>' : '') +
          '<button type="button" class="btn tonal sm" data-mgadd="' + g.gid + '">' + IC('add') + 'Aggiungi riga</button>' +
          '<button type="button" class="btn icon sm" data-mgdel="' + g.gid + '" title="Elimina il gruppo e le sue righe">' + IC('delete') + '</button></div>' +
          (rows.length ? '<div class="tablewrap"><table class="t svc-t"><thead><tr><th>Descrizione</th><th>Profilo professionale</th><th class="num">gg</th><th class="num">Ore</th><th class="num pr">Tariffa oraria</th><th class="num pr">Totale riga</th><th></th></tr></thead><tbody>' +
            rows.map(([r, i]) => svcRow(r, i)).join('') + '</tbody><tfoot><tr><td colspan="2" class="right"><b>Totale</b></td><td></td><td class="num" data-gsub-h="' + g.gid + '"></td><td class="pr"></td><td class="num pr" data-gsub="' + g.gid + '"></td><td></td></tr></tfoot></table></div>'
            : '<div class="hint" style="padding:8px 0 4px">Nessuna riga: usa «Aggiungi riga».</div>') + '</div>';
      }).join('');
  }
  function bindManualGroups() {
    const body = $('#svc-body');
    let dragging = null;
    body.addEventListener('click', async (e) => {
      const nw = e.target.closest('[data-mnew]'), add = e.target.closest('[data-mgadd]'), del = e.target.closest('[data-mgdel]');
      if (nw) {
        const g = newGroup('', null); renderServices(); CPQ.markDirty('page', root);
        const inp = $('#svc-body .mg-name[data-gid="' + g.gid + '"]'); if (inp) inp.focus();
      } else if (add) addManualRow(add.dataset.mgadd);
      else if (del) {
        const g = groupOf(del.dataset.mgdel); if (!g) return;
        if (S.activities.some((r) => r.gid === g.gid) && !(await CPQ.confirm('Eliminare il gruppo' + (g.name ? ' «' + g.name + '»' : '') + ' e tutte le sue righe?', { title: 'Elimina gruppo', yes: 'Sì, elimina', no: 'No', danger: true }))) return;
        S.activities = S.activities.filter((r) => r.gid !== g.gid); S.svcGroups = S.svcGroups.filter((x) => x.gid !== g.gid);
        renderServices(); CPQ.markDirty('page', root);
      }
    });
    body.addEventListener('input', (e) => {
      const n = e.target.closest('.mg-name'); if (!n) return;
      const g = groupOf(n.dataset.gid); if (g) { g.name = n.value; syncRowsFromGroup(g); CPQ.markDirty('page', root); }
    });
    body.addEventListener('change', (e) => {
      const a = e.target.closest('.mg-app'); if (!a) return;
      const g = groupOf(a.dataset.gid); if (g) { g.appId = a.value ? Number(a.value) : null; syncRowsFromGroup(g); CPQ.markDirty('page', root); updateServiceTotals(); }
    });
    // trascinamento dei gruppi
    const clearMarks = () => $$('#svc-body .mg').forEach((b) => b.classList.remove('mg-drop-before', 'mg-drop-after', 'mg-dragging'));
    body.addEventListener('dragstart', (e) => {
      const h = e.target.closest('.mg-handle'); if (!h) return;
      const blk = h.closest('.mg'); dragging = blk.dataset.gid;
      e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', dragging);
      try { e.dataTransfer.setDragImage(blk, 24, 20); } catch (er) { /* senza anteprima */ }
      setTimeout(() => blk.classList.add('mg-dragging'), 0);
    });
    body.addEventListener('dragover', (e) => {
      if (!dragging) return;
      const blk = e.target.closest('.mg'); if (!blk || blk.dataset.gid === dragging) return;
      e.preventDefault(); e.dataTransfer.dropEffect = 'move';
      const r = blk.getBoundingClientRect(), after = e.clientY > r.top + r.height / 2;
      $$('#svc-body .mg').forEach((b) => b.classList.remove('mg-drop-before', 'mg-drop-after'));
      blk.classList.add(after ? 'mg-drop-after' : 'mg-drop-before');
    });
    body.addEventListener('drop', (e) => {
      if (!dragging) return;
      const blk = e.target.closest('.mg'); if (!blk || blk.dataset.gid === dragging) { clearMarks(); dragging = null; return; }
      e.preventDefault();
      const r = blk.getBoundingClientRect(), after = e.clientY > r.top + r.height / 2;
      const moved = groupOf(dragging); S.svcGroups = S.svcGroups.filter((g) => g !== moved);
      let at = S.svcGroups.findIndex((g) => g.gid === blk.dataset.gid); if (after) at += 1;
      S.svcGroups.splice(at, 0, moved);
      dragging = null; syncActivityOrder(); renderServices(); CPQ.markDirty('page', root);
    });
    body.addEventListener('dragend', () => { clearMarks(); dragging = null; });
  }

  function renderServices() {
    $('#svc-manual-card').hidden = S.optRfq; $('#svc-rfq-card').hidden = !S.optRfq;
    if (S.optRfq) renderRfq();
    $('#svc-body').innerHTML = S.optRfq ? '' : renderManualGroups();
    if (document.activeElement !== $('#svc-disc')) $('#svc-disc').value = S.actDiscount || '';
    syncUntil();
    $('#svc-detail').checked = S.actDetail;
    updateServiceTotals();
  }

  /** attivita' importate da RFQ: applicazione > titolo > righe (sola lettura) */
  function renderRfq() {
    const rows = S.activities.map((r, i) => [r, i]).filter(([r]) => r.rfq);
    const apps = [];
    rows.forEach(([r, i]) => {
      let a = apps.find((x) => x.id === r.appId); if (!a) { a = { id: r.appId, groups: [] }; apps.push(a); }
      let g = a.groups.find((x) => x.name === (r.group || '')); if (!g) { g = { name: r.group || '', rows: [] }; a.groups.push(g); }
      g.rows.push([r, i]);
    });
    $('#btn-rfq-import').disabled = rows.length > 0; $('#btn-rfq-clear').disabled = rows.length === 0;
    updateRfqCheck();
    const IC = (n) => '<svg class="ic"><use href="#i-' + n + '"/></svg>';
    const lead = (editBtn, moreBtn) => '<td class="actions nowrap" style="white-space:nowrap;padding-left:6px;padding-right:4px;width:76px">' + editBtn + moreBtn + '</td>';
    $('#svc-rfq-body').innerHTML = apps.length ? apps.map((a) => {
      const ap0 = a.id === 0 ? null : appOf(a.id);
      const ap = a.id === 0 ? { name: 'NON TROVATO' } : (ap0 ? { name: ap0.family === 'GALILEO' ? 'GALILEO' : ap0.name } : { name: 'Applicazione ' + a.id });      // Galileo: titolo «GALILEO», non la tipologia
      return '<div class="svc-app" data-app="' + a.id + '"><div class="svc-head"><b>' + esc(ap.name) + '</b><span class="grow"></span>' +
        '<button type="button" class="btn tonal sm" data-gadd="' + a.id + '">' + IC('add') + 'Aggiungi sottogruppo</button>' +
        '<button type="button" class="btn tonal sm" data-radd="' + a.id + '" data-group="">' + IC('add') + 'Aggiungi riga</button></div>' +
        '<div class="tablewrap"><table class="t svc-t"><thead><tr><th>Descrizione</th><th>Profilo professionale</th><th class="num">gg</th><th class="num">Ore</th><th class="num pr">Tariffa oraria</th><th class="num pr">Totale riga</th><th></th></tr></thead><tbody>' +
        a.groups.map((g) => {
          const gk = a.id + '|' + g.name, ged = S.rfqEditGroups.has(gk);
          return (g.name ? '<tr class="svc-grp"><td colspan="2">' + (ged ? '<input class="svc-gname" data-gapp="' + a.id + '" data-gold="' + esc(g.name) + '" maxlength="250" value="' + esc(g.name) + '">' : '<b>' + esc(g.name) + '</b>') + '</td>' +
            '<td colspan="4" class="num pr" data-gk="' + esc(gk) + '"></td>' +
            lead('<button type="button" class="btn icon sm" data-gedit="' + esc(gk) + '" title="' + (ged ? 'Fine modifica' : 'Modifica il titolo') + '">' + IC(ged ? 'check' : 'edit') + '</button>',
                 '<button type="button" class="btn icon sm rfq-more" data-kind="group" data-app="' + a.id + '" data-group="' + esc(g.name) + '" title="Altre azioni" aria-haspopup="menu">' + IC('more') + '</button>') + '</tr>' : '') +
          g.rows.map(([r, i]) => rfqRow(r, i, g.name ? 'padding-left:24px' : '', lead)).join('');
        }).join('') +
        '</tbody><tfoot><tr><td colspan="2" class="right"><b>Totale</b></td><td class="num" data-sub-d="' + a.id + '"></td><td class="num" data-sub-h="' + a.id + '"></td><td class="pr"></td><td class="num pr" data-sub="' + a.id + '"></td><td></td></tr></tfoot></table></div></div>';
    }).join('') : '<div class="hint">Nessuna attività importata. Usa «Importa da RFQ» per leggere il foglio Google.</div>';
  }

  /** riga RFQ: in sola lettura (come l'import); con la matita diventa modificabile. Il menu a 3 puntini contiene «Elimina» */
  function rfqRow(r, i, indent, lead) {
    const IC = (n) => '<svg class="ic"><use href="#i-' + n + '"/></svg>';
    const head = lead('<button type="button" class="btn icon sm" data-redit="' + i + '" title="' + (r._edit ? 'Fine modifica' : 'Modifica la riga') + '">' + IC(r._edit ? 'check' : 'edit') + '</button>',
                      '<button type="button" class="btn icon sm rfq-more" data-kind="row" data-i="' + i + '" title="Altre azioni" aria-haspopup="menu">' + IC('more') + '</button>');
    if (r._edit) return svcRow(r, i, indent).replace(/<td class="actions">.*<\/td><\/tr>$/, head + '</tr>');
    const days = r.days != null && r.days !== '' ? Number(r.days) : C.parseHours(r.hours) / 8;
    return '<tr data-i="' + i + '"><td' + (indent ? ' style="' + indent + '"' : '') + '>' + esc(r.desc) + '</td><td>' + esc(r.profileName || '') + '</td><td class="num">' + esc(CPQ.num(days)) + '</td><td class="num">' + esc(CPQ.num(C.parseHours(r.hours))) + '</td>' +
      '<td class="num pr" data-rate="' + i + '"></td><td class="num pr" data-line="' + i + '"></td>' + head + '</tr>';
  }

  /** inserisce una riga vuota dopo l'ultima riga RFQ del gruppo (o dell'applicazione), cosi' l'ordine del documento resta quello a video */
  function rfqInsert(appId, group, focusGroupName) {
    let at = -1;
    S.activities.forEach((a, i) => { if (a.rfq && a.appId === appId && (group == null || (a.group || '') === group)) at = i; });
    if (at < 0) S.activities.forEach((a, i) => { if (a.rfq && a.appId === appId) at = i; });
    const row = { appId: appId, desc: '', profileId: null, hours: '', rate: 0, rfq: true, group: group || '', days: null, _edit: true };
    if (focusGroupName) S.rfqEditGroups.add(appId + '|' + group);
    S.activities.splice(at + 1, 0, row);
    renderServices(); CPQ.markDirty('page', root);
    const tr = $('#svc-rfq-body tr[data-i="' + (at + 1) + '"]');
    if (focusGroupName) { const g = $('#svc-rfq-body .svc-gname[data-gold="' + CSS.escape(group) + '"][data-gapp="' + appId + '"]'); if (g) { g.focus(); g.select(); } }
    else if (tr) tr.querySelector('.svc-desc').focus();
  }
  function rfqNewGroup(appId) {
    const names = new Set(S.activities.filter((a) => a.rfq && a.appId === appId).map((a) => a.group || ''));
    let n = 'Nuovo gruppo', k = 1; while (names.has(n)) { k += 1; n = 'Nuovo gruppo ' + k; }
    rfqInsert(appId, n, true);
  }
  function bindRfqEdit() {
    const body = $('#svc-rfq-body');
    body.addEventListener('click', (e) => {
      const gadd = e.target.closest('[data-gadd]'), radd = e.target.closest('[data-radd]'), redit = e.target.closest('[data-redit]'), gedit = e.target.closest('[data-gedit]');
      if (gadd) rfqNewGroup(Number(gadd.dataset.gadd));
      else if (radd) rfqInsert(Number(radd.dataset.radd), radd.dataset.group || '', false);
      else if (redit) { const r = S.activities[Number(redit.dataset.redit)]; r._edit = !r._edit; renderServices(); }
      else if (gedit) { const k = gedit.dataset.gedit; if (S.rfqEditGroups.has(k)) S.rfqEditGroups.delete(k); else S.rfqEditGroups.add(k); renderServices(); }
    });
    // menu a 3 puntini (riga o gruppo)
    let rmenu = null;
    const rclose = () => { if (rmenu) { rmenu.remove(); rmenu = null; } };
    document.addEventListener('click', (e) => {
      const b = e.target.closest ? e.target.closest('.rfq-more') : null;
      if (!b || !body.contains(b)) { rclose(); return; }
      e.stopPropagation(); const key = b.dataset.kind + '|' + (b.dataset.i || b.dataset.app + '|' + b.dataset.group); const same = rmenu && rmenu.dataset.for === key; rclose(); if (same) return;
      rmenu = document.createElement('div'); rmenu.className = 'cg-rowmenu'; rmenu.setAttribute('role', 'menu'); rmenu.dataset.for = key;
      const isGroup = b.dataset.kind === 'group';
      rmenu.innerHTML = (isGroup ? '<button type="button" role="menuitem" data-m="add"><svg class="ic"><use href="#i-add"/></svg>Aggiungi riga</button>' : '') +
        '<button type="button" role="menuitem" class="danger" data-m="del"><svg class="ic"><use href="#i-delete"/></svg>' + (isGroup ? 'Elimina gruppo' : 'Elimina') + '</button>';
      document.body.appendChild(rmenu);
      const rc = b.getBoundingClientRect();
      rmenu.style.position = 'fixed'; rmenu.style.top = (rc.bottom + 4) + 'px'; rmenu.style.left = Math.max(8, rc.left) + 'px';
      rmenu.addEventListener('click', async (ev) => {
        const m = ev.target.closest('[data-m]'); if (!m) return; rclose();
        if (m.dataset.m === 'add') { rfqInsert(Number(b.dataset.app), b.dataset.group, false); return; }
        if (isGroup) {
          const id = Number(b.dataset.app), g = b.dataset.group;
          if (!(await CPQ.confirm('Eliminare il gruppo «' + g + '» e tutte le sue righe?', { title: 'Elimina gruppo', yes: 'Sì, elimina', no: 'No', danger: true }))) return;
          S.activities = S.activities.filter((a) => !(a.rfq && a.appId === id && (a.group || '') === g));
        } else {
          const r = S.activities[Number(b.dataset.i)];
          if (!(await CPQ.confirm('Eliminare la riga «' + ((r && r.desc) || 'senza descrizione') + '»?', { title: 'Elimina riga', yes: 'Sì, elimina', no: 'No', danger: true }))) return;
          S.activities.splice(Number(b.dataset.i), 1);
        }
        renderServices(); CPQ.markDirty('page', root);
      });
    });
    body.addEventListener('input', (e) => {
      const tr = e.target.closest('tr[data-i]'); if (!tr) return;
      const r = S.activities[Number(tr.dataset.i)], c = e.target.classList;
      if (c.contains('svc-desc')) r.desc = e.target.value;
      if (c.contains('svc-hours')) r.hours = e.target.value;
      if (c.contains('svc-days')) r.days = e.target.value === '' ? null : Number(String(e.target.value).replace(',', '.'));
      CPQ.markDirty('page', root); updateServiceTotals();
    });
    body.addEventListener('change', (e) => {
      if (e.target.classList.contains('svc-gname')) {            // rinomina il gruppo (anche su tutte le sue righe)
        const id = Number(e.target.dataset.gapp), old = e.target.dataset.gold, nu = e.target.value.trim() || old;
        S.activities.forEach((a) => { if (a.rfq && a.appId === id && (a.group || '') === old) a.group = nu; });
        S.rfqEditGroups.delete(id + '|' + old);
        renderServices(); CPQ.markDirty('page', root); return;
      }
      const tr = e.target.closest('tr[data-i]'); if (!tr || !e.target.classList.contains('svc-prof')) return;
      const r = S.activities[Number(tr.dataset.i)];
      r.profileId = e.target.value ? Number(e.target.value) : null;
      const p = profileOf(r.profileId); if (p) { r.rate = p.amount; r.profileName = p.name; }
      CPQ.markDirty('page', root); updateServiceTotals();
    });
  }

  /** controllo di coerenza: totale servizi calcolato (listino, tariffe dell'account) contro quello del foglio (colonna H, «TOTALE GENERALE PROGETTO») */
  function rfqMismatch() {
    const acts = S.activities.filter((a) => a.rfq);
    if (!acts.length || S.rfqSheetTotal == null) return null;
    const mine = C.activityTotals(acts, S.lines, 0).list;
    return Math.abs(mine - S.rfqSheetTotal) > 0.05 ? { mine: mine, sheet: S.rfqSheetTotal } : null;
  }
  function updateRfqCheck() {
    const box = $('#rfq-mismatch'); if (!box) return;
    const m = rfqMismatch();
    box.hidden = !m;
    // verde (secondo il tema) se il totale calcolato e' inferiore a quello dell'RFQ, arancio se e' superiore
    if (m) { const lower = m.mine < m.sheet; box.classList.toggle('ok', lower); box.classList.toggle('warn', !lower); }
    if (m) box.querySelector('span').textContent = 'Attenzione: i totali non corrispondono. Totale servizi calcolato ' + CPQ.eur(m.mine) + ' (tariffe dell\'account), totale del foglio RFQ ' + CPQ.eur(m.sheet) + ' (colonna H, «TOTALE GENERALE PROGETTO»), differenza ' + CPQ.eur(m.mine - m.sheet) + '.';
  }

  // ---- importazione da RFQ (foglio Google)
  let rfqPendingUrl = '';
  function rfqMsg(text, isErr) {
    const e = $('#rfq-err'), st = $('#rfq-status');
    e.hidden = !isErr || !text; if (isErr) e.querySelector('span').textContent = text || '';
    st.hidden = isErr || !text; if (!isErr) st.textContent = text || '';
  }
  function openRfqDialog() {
    $('#rfq-url').value = rfqPendingUrl; rfqMsg('', false); $('#rfq-reauth').hidden = true; $('#rfq-go').disabled = false;
    $('#rfq-dialog').showModal(); $('#rfq-url').focus();
  }
  function applyRfqItems(items) {
    S.activities = S.activities.filter((a) => !a.rfq);
    items.forEach((it) => S.activities.push({ appId: it.applicationId, desc: it.description, profileId: it.profileId, profileName: it.profileName,
      hours: String(it.hours), days: it.days, rate: it.rate, rfq: true, group: it.group || '' }));
    repriceActivities();
    S.rfqSheetTotal = null;
    CPQ.markDirty('page', root);
  }
  // ---------------------------------------------------------------- RFQ: attivazione dei moduli elencati nel foglio «moduli relativi alle attività» (colonna C)
  const normMod = (t) => String(t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  /** matrice di transcodifica: codice del modulo nell'RFQ -> codice del modulo nei listini */
  const RFQ_MODULE_ALIAS = { 'SMIJPM-WEB': 'SMIJPM', 'SMIBK': 'SMIBKIG', 'CRMSYN': 'CRM' };
  /** moduli dell'RFQ esclusi dalle verifiche e non importati */
  const RFQ_MODULE_SKIP = ['SMIJLIC'];
  /** applica la transcodifica / l'esclusione al codice iniziale della voce (anche "CODICE - descrizione"); restituisce null se la voce va esclusa */
  function canonRfqModule(text, notes) {
    const t = String(text).trim(), u = t.toUpperCase();
    const starts = (code) => u === code || (u.startsWith(code) && /^[\s:–—-]/.test(u.slice(code.length)) && !(code === 'SMIJPM' && u.startsWith('SMIJPM-WEB')));
    if (RFQ_MODULE_SKIP.some((c) => u === c || (u.startsWith(c) && /^[\s:–—-]/.test(u.slice(c.length))))) return null;
    const keys = Object.keys(RFQ_MODULE_ALIAS).sort((a, b) => b.length - a.length);
    for (const k of keys) {
      if (starts(k)) { const out = RFQ_MODULE_ALIAS[k] + t.slice(k.length); notes.push(k + ' → ' + RFQ_MODULE_ALIAS[k]); return out; }
    }
    return t;
  }
  /** voci dell'elenco: una cella puo' contenere piu' moduli (a capo, «;» oppure codici separati da virgola) */
  function expandRfqModules(list, maps) {
    const out = [], seen = new Set();
    const add = (t) => { t = canonRfqModule(String(t || ''), maps || []); if (t && !seen.has(normMod(t))) { seen.add(normMod(t)); out.push(t); } };
    list.forEach((raw) => String(raw).split(/[\r\n;]+/).forEach((part) => {
      const codes = part.split(/\s*,\s*/);
      if (codes.length > 1 && codes.every((c) => /^[A-Za-z0-9_.-]{3,}$/.test(c))) codes.forEach(add); else add(part);
    }));
    return out;
  }
  /** "CODICE - descrizione", "CODICE descrizione", solo codice, oppure solo descrizione (anche con maiuscole diverse) */
  function rfqModuleKey(raw) {
    const t = String(raw).trim();
    const codes = [];
    let m = /^([A-Za-z0-9_.]{3,})\s*[-–—:]\s*(.+)$/.exec(t);
    if (m) { codes.push(m[1].toUpperCase()); return { codes: codes, desc: normMod(m[2]) }; }
    m = /^([A-Za-z0-9_.]{3,})(\s+.*)?$/.exec(t);
    if (m) codes.push(m[1].toUpperCase());                              // primo termine: puo' essere il codice
    return { codes: codes, desc: normMod(t) };
  }
  const tokensOf = (t) => normMod(t).split(' ').filter((w) => w.length > 1);
  function modMatches(key, code, desc) {
    const c = String(code || '').toUpperCase();
    if (c && key.codes.includes(c)) return true;
    const d = normMod(desc);
    if (!key.desc || !d) return false;
    if (d === key.desc) return true;
    if (key.desc.length >= 8 && (d.includes(key.desc) || key.desc.includes(d)) && Math.min(d.length, key.desc.length) >= 8) return true;
    const a = tokensOf(d), b = tokensOf(key.desc);                      // stesse parole in ordine o descrizione piu' breve contenuta nell'altra
    if (a.length >= 2 && b.length >= 2) { const [small, big] = a.length <= b.length ? [a, b] : [b, a]; return small.every((w) => big.includes(w)); }
    return false;
  }
  /** senza scenario scelto si usa la nuova configurazione (se il mercato Galileo la prevede), altrimenti il primo scenario */
  async function ensureGalileoProfile(rep) {
    if (!(S.optGalileo && S.galileoId)) return false;
    if (!S.gal.profileId) {
      const mk = galMarket();
      const prof = mk && mk.profiles ? (mk.profiles.find((p) => p.kind === 'NEW') || mk.profiles[0]) : null;
      if (!prof) { rep.notes.push('Galileo: il mercato scelto non ha un listino configurato, nessun modulo Galileo attivato.'); return false; }
      S.gal.profileId = prof.id; await galLoadProfile(prof.id, false);
      rep.notes.push('Galileo: scenario non scelto, usato «' + (GAL_KIND[prof.kind] || prof.label) + '» (modificabile nella pagina Galileo).');
    } else if (!G.profile || G.profile.id !== S.gal.profileId) await galLoadProfile(S.gal.profileId, true);
    return !!(G.profile && G.profile.id === S.gal.profileId);
  }
  /** confronta i moduli dell'RFQ con quelli di Galileo (scenario scelto) e dei Dipartimentali (applicazioni nell'offerta) e li attiva; restituisce il resoconto */
  async function activateRfqModules(listRaw) {
    const maps = [];
    const list = expandRfqModules(listRaw || [], maps);
    const rep = { total: list.length, activated: [], already: [], unmatched: [], notes: [] };
    if (maps.length) rep.notes.push('Transcodifica applicata: ' + Array.from(new Set(maps)).join(', ') + '.');
    if (!list.length) return rep;
    const galOn = await ensureGalileoProfile(rep);
    const rfqApps = new Set(S.activities.filter((x) => x.rfq && x.appId).map((x) => x.appId));
    if (!S.optGalileo && !S.lines.length) rep.notes.push('Nell\'offerta non ci sono applicazioni (Dipartimentali) né Galileo: nessun modulo può essere attivato.');
    let dip = false, gal = false;
    list.forEach((raw) => {
      const key = rfqModuleKey(raw);
      let hit = false, did = false, needV11 = false;
      S.lines.forEach((l) => {                                                 // Dipartimentali
        const a = appOf(l.applicationId); if (!a) return;
        a.modules.filter((m) => modMatches(key, m.code, m.description)).forEach((m) => {
          hit = true;
          if (m.section === 'LINK' || m.included || l.modules[m.id]) return;
          C.selectModule(catalog, S.lines, a.id, m.id, true); dip = true; did = true;
        });
      });
      if (!hit) {                                                              // modulo di un'applicazione Dipartimentali non ancora nell'offerta: si attiva «Dipartimentali» e si aggiunge l'applicazione
        const cands = [];
        catalog.filter((x) => x.family !== GALILEO && !lineOf(x.id)).forEach((x) => x.modules.forEach((m) => { if (modMatches(key, m.code, m.description)) cands.push({ a: x, m: m }); }));
        const inRfq = cands.filter((c) => rfqApps.has(c.a.id));                  // si preferiscono le applicazioni citate nelle attivita' dell'RFQ
        const pick = inRfq.length ? inRfq : cands;
        const apps = Array.from(new Set(pick.map((c) => c.a.id)));
        if (apps.length === 1) {
          pick.forEach((c) => {
            hit = true;
            if (!S.optDept) { S.optDept = true; $('#opt-dept').checked = true; refreshStepper(); rep.notes.push('Dipartimentali attivato.'); }
            if (!lineOf(c.a.id)) { S.lines.push(C.newLine(c.a)); if (typeof expanded !== 'undefined') expanded.add(c.a.id); rep.notes.push('Aggiunta l\'applicazione ' + c.a.name + '.'); }
            if (c.m.section !== 'LINK' && !c.m.included) { C.selectModule(catalog, S.lines, c.a.id, c.m.id, true); did = true; dip = true; }
          });
        } else if (apps.length > 1) { hit = true; rep.unmatched.push(raw + '  (presente in più applicazioni: ' + pick.map((c) => c.a.name).filter((n, i, ar) => ar.indexOf(n) === i).join(', ') + ')'); return; }
      }
      if (galOn) {                                                             // Galileo
        G.profile.modules.filter((m) => modMatches(key, m.code, m.description)).forEach((m) => {
          hit = true;
          if (m.flag === 'V11') {                                                                                 // modulo della release V11 (SMIXW): imposta la release, il modulo e' incluso e conteggiato in automatico
            if (!G.profile.cfgV11) { needV11 = true; return; }
            if (S.gal.release !== 'V11') { S.gal.release = 'V11'; gal = true; did = true; rep.notes.push('Galileo: release impostata a V11 (modulo ' + m.code + ' presente nell\'RFQ).'); }
            return;
          }
          if (m.flag === 'CB' || G.profile.kind === 'IMMOB' || S.gal.sel[m.moduleId]) return;                  // gia' inclusi o gia' attivi
          S.gal.sel[m.moduleId] = G.profile.kind === 'NEW' ? 'D' : 'F'; gal = true; did = true;
        });
      }
      if (needV11 && !did) rep.unmatched.push(raw + '  (la release V11 non è prevista dallo scenario Galileo scelto)');
      else if (!hit) rep.unmatched.push(raw); else if (did) rep.activated.push(raw); else rep.already.push(raw);
    });
    if (dip) { renderApps(); scheduleCalc(); }
    if (gal) { S.gal.calc = null; if (S.step === 8) galRender(); scheduleGalCalc(); refreshSaveState(); }
    if (dip || gal) CPQ.markDirty('page', root);
    return rep;
  }
  /** finestra con il resoconto: moduli attivati e moduli dell'RFQ che non e' stato possibile attivare */
  function showModulesReport(rep, sheetFound) {
    if (!sheetFound) { toast('Foglio «moduli relativi alle attività» non trovato nel documento RFQ: nessun modulo attivato', true); return; }
    if (!rep.total) { toast('Il foglio «moduli relativi alle attività» non contiene moduli in colonna C'); return; }
    const lines = ['Moduli nel foglio RFQ: ' + rep.total, 'Attivati: ' + rep.activated.length, 'Già attivi o inclusi: ' + rep.already.length, 'NON attivati: ' + rep.unmatched.length];
    if (rep.unmatched.length) lines.push('', 'Moduli dell\'RFQ non attivati (nessuna corrispondenza con i moduli di Galileo / Dipartimentali):', ...rep.unmatched.map((m) => ' • ' + m));
    if (rep.activated.length) lines.push('', 'Moduli attivati:', ...rep.activated.map((m) => ' • ' + m));
    if (rep.notes.length) lines.push('', ...rep.notes);
    const dlg = document.createElement('dialog'); dlg.className = 'cpq-confirm'; dlg.style.width = 'min(640px, 94vw)';
    dlg.innerHTML = '<h3>Moduli dell\'RFQ</h3><textarea readonly style="width:100%;height:300px;font:12.5px ui-monospace,Menlo,monospace;border:1px solid var(--outline);border-radius:6px;padding:8px;background:var(--surface);color:var(--on-surface)"></textarea><div style="text-align:right;margin-top:10px"><button type="button" class="btn">Chiudi</button></div>';
    dlg.querySelector('textarea').value = lines.join('\n');
    dlg.querySelector('button').addEventListener('click', () => dlg.close()); dlg.addEventListener('close', () => dlg.remove());
    document.body.appendChild(dlg); dlg.showModal();
  }
  function openGoogleAuth(url) {
    const w = window.open(url, 'cpqia-rfq-auth', 'width=520,height=680');
    if (!w) { rfqMsg('Il browser ha bloccato la finestra di accesso a Google: consenti i popup per questo sito e riprova.', true); return false; }
    return true;
  }
  async function runRfqImport(url) {
    $('#rfq-go').disabled = true; rfqMsg('Lettura del foglio in corso…', false); $('#rfq-reauth').hidden = true;
    try {
      const r = await CPQ.api('POST', '/api/rfq/import', { url: url, customerId: S.customer ? S.customer.id : null, galileoId: S.optGalileo ? S.galileoId : null });
      if (r.mode === 'ok') {
        applyRfqItems(r.items); S.rfqSheetTotal = r.sheetTotal == null ? null : Number(r.sheetTotal); $('#rfq-dialog').close(); renderServices();
        if (S.rfqSheetTotal == null) toast('Nel foglio manca il totale (colonna H della riga «TOTALE GENERALE PROGETTO»): controllo dei totali non eseguito', true);
        else { const m = rfqMismatch(); if (m) CPQ.confirm('I totali non corrispondono.\n\nTotale servizi calcolato: ' + CPQ.eur(m.mine) + '\nTotale del foglio RFQ (colonna H): ' + CPQ.eur(m.sheet) + '\nDifferenza: ' + CPQ.eur(m.mine - m.sheet) + '\n\nControlla il foglio, le tariffe dell\'account e le figure professionali. L\'avviso resta visibile nella pagina Servizi.', { title: 'Totali RFQ non corrispondenti', yes: 'Ho capito', no: 'Chiudi' }); }
        toast('Importate ' + r.items.length + ' attività da RFQ' + ((r.notFound || []).length ? ' – applicazioni non trovate (gruppo NON TROVATO): ' + r.notFound.join(', ') : ''), (r.notFound || []).length > 0);
        activateRfqModules(r.modules || []).then((rep) => showModulesReport(rep, r.modulesSheet !== false)).catch((er) => toast('Moduli RFQ: ' + er.message, true));
      } else if (r.mode === 'noaccess') {
        rfqMsg(r.message, true); $('#rfq-go').disabled = false; $('#rfq-reauth').hidden = false; $('#rfq-reauth').dataset.url = r.authUrl;
      } else if (r.mode === 'auth') {
        rfqMsg('Autorizza l\'accesso in sola lettura ai fogli Google nella finestra che si è aperta. Se Google segnala un errore di URI di reindirizzamento, registra in Google Cloud Console (client OAuth ' + (r.clientId || '') + ') questo indirizzo: ' + (r.redirectUri || ''), false);
        if (!openGoogleAuth(r.authUrl)) $('#rfq-go').disabled = false;
      }
    } catch (e) { rfqMsg(e.message, true); $('#rfq-go').disabled = false; }
  }
  let lastServicesMode = 'MANUAL';
  /** allinea casella «Servizi» ed elenco a discesa allo stato */
  function syncServicesUi() {
    S.optRfq = S.servicesMode === 'RFQ';
    if (S.servicesMode !== 'NONE') lastServicesMode = S.servicesMode;
    const on = S.servicesMode !== 'NONE';
    $('#opt-services-on').checked = on; $('#sel-svc-mode').value = on ? S.servicesMode : lastServicesMode; $('#svc-mode-wrap').hidden = !on;
    const sc = $('#sum-act-card'); if (sc) sc.hidden = !on;
  }
  /** cambio modalita' dei servizi: le attivita' di un'altra modalita' vengono rimosse dopo conferma */
  async function applyServicesMode(next) {
    if (galAgg() && next === 'RFQ') next = 'MANUAL';          // MODULI AGGIUNTIVI: modalità fissa Manuale
    const cur = S.servicesMode;
    if (next === cur) { syncServicesUi(); return; }
    const hasManual = S.activities.some((a) => !a.rfq), hasRfq = S.activities.some((a) => a.rfq);
    let msg = null;
    if (next === 'NONE' && S.activities.length) msg = 'Senza servizi tutte le attività inserite vengono rimosse. Continuare?';
    else if (next === 'RFQ' && hasManual) msg = 'Con la modalità RFQ le attività inserite a mano vengono rimosse. Continuare?';
    else if (next === 'MANUAL' && hasRfq) msg = 'Con la modalità Manuale le righe importate dal foglio RFQ vengono rimosse. Continuare?';
    if (msg && !(await CPQ.confirm(msg, { title: 'Servizi', yes: 'Sì, rimuovi', no: 'No, mantieni', danger: true }))) { syncServicesUi(); return; }
    if (next === 'NONE') S.activities = [];
    else if (next === 'RFQ') S.activities = S.activities.filter((a) => a.rfq);
    else S.activities = S.activities.filter((a) => !a.rfq);
    if (next !== 'RFQ') S.rfqSheetTotal = null;
    S.svcGroups = [];
    S.servicesMode = next; syncServicesUi(); CPQ.markDirty('page', root); refreshStepper(); updateAmounts(); refreshSaveState();
  }
  function bindRfq() {
    $('#opt-services-on').addEventListener('change', (e) => applyServicesMode(e.target.checked ? (galAgg() ? 'MANUAL' : (lastServicesMode || 'MANUAL')) : 'NONE'));
    $('#sel-svc-mode').addEventListener('change', (e) => applyServicesMode(e.target.value));
    $('#opt-days').addEventListener('change', (e) => { S.optDays = e.target.checked; });
    $('#opt-hours-detail').addEventListener('change', (e) => { S.hoursDetail = e.target.checked; });
    $('#opt-apptot').addEventListener('change', (e) => { S.appTotalsMode = e.target.value; });
    $('#btn-rfq-import').addEventListener('click', openRfqDialog);
    $('#rfq-cancel').addEventListener('click', () => $('#rfq-dialog').close());
    $('#rfq-form').addEventListener('submit', (e) => {
      e.preventDefault();
      const url = $('#rfq-url').value.trim(); rfqPendingUrl = url;
      if (!url) { rfqMsg('Indica l\'indirizzo del documento Google Sheets', true); return; }
      runRfqImport(url);
    });
    $('#rfq-reauth').addEventListener('click', () => { const u = $('#rfq-reauth').dataset.url; if (u) { rfqMsg('Scegli l\'account Google nella finestra che si è aperta…', false); openGoogleAuth(u); } });
    // fine dell'autorizzazione (finestra di Google chiusa): si riprende l'importazione
    window.addEventListener('message', (e) => {
      if (e.origin !== window.location.origin || e.data !== 'cpqia-rfq-auth') return;
      if ($('#rfq-dialog').open && rfqPendingUrl) runRfqImport(rfqPendingUrl);
    });
    $('#btn-rfq-clear').addEventListener('click', async () => {
      const ok = await CPQ.confirm('Eliminare tutte le righe importate da RFQ?', { title: 'Elimina righe importate', yes: 'Sì, elimina', no: 'No', danger: true });
      if (!ok) return;
      S.activities = S.activities.filter((a) => !a.rfq); S.rfqSheetTotal = null; CPQ.markDirty('page', root); renderServices();
    });
  }

  function svcRow(r, i, indent) {
    const prof = S.profiles.slice();
    if (r.profileId && !profileOf(r.profileId)) prof.push({ id: r.profileId, name: r.profileName || 'Profilo non più attivo', amount: r.rate || 0 });
    return '<tr data-i="' + i + '"><td' + (indent ? ' style="' + indent + '"' : '') + '><input class="svc-desc" maxlength="250" value="' + esc(r.desc) + '" placeholder="Descrizione dell\'attività"></td>' +
      '<td><select class="svc-prof"><option value="">— scegli —</option>' + prof.map((p) => '<option value="' + p.id + '"' + (Number(r.profileId) === p.id ? ' selected' : '') + '>' + esc(p.name) + '</option>').join('') + '</select></td>' +
      (r.rfq ? '<td class="num"><input class="svc-days" inputmode="decimal" value="' + esc(r.days != null && r.days !== '' ? String(r.days).replace('.', ',') : (C.parseHours(r.hours) > 0 ? String(C.parseHours(r.hours) / 8).replace('.', ',') : '')) + '" style="width:64px;text-align:right"></td>' : '<td class="num muted" data-gg="' + i + '"></td>') +
      '<td class="num"><input class="svc-hours" inputmode="decimal" value="' + esc(r.hours === '' || r.hours == null ? '' : String(r.hours).replace('.', ',')) + '" style="width:80px;text-align:right"></td>' +
      '<td class="num pr" data-rate="' + i + '"></td><td class="num pr" data-line="' + i + '"></td>' +
      '<td class="actions"><button type="button" class="btn icon sm" data-del="' + i + '" title="Elimina la riga"><svg class="ic"><use href="#i-delete"/></svg></button></td></tr>';
  }

  /** aggiorna importi e totali senza ridisegnare le righe (cosi' non si perde il cursore mentre si scrive) */
  function updateServiceTotals() {
    const d = Number(S.actDiscount || 0), net = d > 0;
    S.activities.forEach((r, i) => {
      const rate = $('[data-rate="' + i + '"]'), line = $('[data-line="' + i + '"]');
      if (!rate) return;
      if (r.rfq && (r.days == null || r.days === '')) {        // gg derivati dalle ore finche' non vengono indicati a mano
        const di = $('tr[data-i="' + i + '"] .svc-days');
        if (di && document.activeElement !== di) di.value = C.parseHours(r.hours) > 0 ? String(C.parseHours(r.hours) / 8).replace('.', ',') : '';
      }
      const gg = $('[data-gg="' + i + '"]'); if (gg) gg.textContent = C.parseHours(r.hours) > 0 ? CPQ.num(C.parseHours(r.hours) / 8) : '';
      const t = C.activityTotals([r], S.lines, d);
      const rr = Number(r.rate || 0);
      rate.innerHTML = r.profileId ? CPQ.eur(rr) + ((profileOf(r.profileId) || {}).custom ? '<div class="hint">tariffe cliente</div>' : '') + (net ? '<div class="hint">scontata ' + CPQ.eur(C.discountedRate(rr, d)) + '</div>' : '') : '';
      line.innerHTML = r.profileId && C.parseHours(r.hours) > 0 ? CPQ.eur(t.list) + (net ? '<div class="hint">scontato ' + CPQ.eur(t.net) + '</div>' : '') : '';
    });
    const all = C.activityTotals(S.activities, S.lines, d);
    const appIds = new Set(S.lines.map((l) => l.applicationId));
    S.activities.forEach((a) => { if (a.rfq) appIds.add(a.appId); });
    appIds.forEach((id) => {
      const sub = $('[data-sub="' + id + '"]'), sh = $('[data-sub-h="' + id + '"]');
      const t = all.perApp[id] || { list: 0, net: 0, hours: 0 };
      if (sub) sub.innerHTML = '<b>' + CPQ.eur(t.list) + '</b>' + (net ? '<div class="hint">scontato ' + CPQ.eur(t.net) + '</div>' : '');
      if (sh) sh.innerHTML = '<b>' + CPQ.num(t.hours) + ' h</b>';
      const sd = $('[data-sub-d="' + id + '"]');
      if (sd) sd.innerHTML = '<b>' + CPQ.num(S.activities.filter((a) => a.rfq && a.appId === id).reduce((n, a) => n + (a.days != null && a.days !== '' ? Number(a.days) : C.parseHours(a.hours) / 8), 0)) + ' gg</b>';
    });
    S.svcGroups.forEach((g) => {                    // servizi manuali: totale di ogni gruppo
      const t = C.activityTotals(S.activities.filter((x) => x.gid === g.gid), S.lines, d);
      const h = $('[data-gsub-h="' + g.gid + '"]'), v = $('[data-gsub="' + g.gid + '"]');
      if (h) h.innerHTML = '<b>' + CPQ.num(t.hours) + ' h</b>';
      if (v) v.innerHTML = '<b>' + CPQ.eur(t.list) + '</b>' + (net ? '<div class="hint">scontato ' + CPQ.eur(t.net) + '</div>' : '');
    });
    $$('[data-gk]').forEach((el) => {      // subtotali dei titoli (righe da RFQ)
      const k = el.dataset.gk, cut = k.indexOf('|'), aid = k.slice(0, cut), grp = k.slice(cut + 1);
      const t = C.activityTotals(S.activities.filter((a) => a.rfq && String(a.appId) === aid && (a.group || '') === grp), S.lines, d);
      el.innerHTML = '<b>' + CPQ.eur(t.list) + '</b>' + (net ? '<span class="hint"> scontato ' + CPQ.eur(t.net) + '</span>' : '') + ' <span class="muted">· ' + CPQ.num(t.hours) + ' h</span>';
    });
    $('#svc-t-list').textContent = CPQ.eur(all.list); $('#svc-t-net').textContent = CPQ.eur(all.net);
    $('#svc-t-hours').textContent = CPQ.num(all.hours) + ' ore';
    $('#svc-t-save').textContent = net ? 'risparmio ' + CPQ.eur(all.list - all.net) : '';
    const errs = C.activityErrors(S.activities, S.lines);
    $('#svc-blockers').textContent = errs.join(' · ');
    updateAmounts();
    refreshSaveState();
  }

  /** aggiunge una riga vuota all'applicazione e porta il cursore sulla descrizione */
  function addActivity(appId) {
    S.activities.push({ appId: appId, desc: '', profileId: null, hours: '', rate: 0 });
    renderServices();
    const tr = $('#svc-body tr[data-i="' + (S.activities.length - 1) + '"]');
    if (tr) tr.querySelector('.svc-desc').focus();
  }

  // ---------------------------------------------------------------- incolla di un elenco di servizi (Word, Excel, Google Sheets) nel campo «Descrizione»
  /** testo copiato -> elenco di descrizioni: righe del testo, oppure prima colonna non vuota di ogni riga se arriva da un foglio (celle separate da tab) */
  function parsePastedServices(text) {
    const tsv = text.indexOf('\t') >= 0;
    const rows = []; let row = [], cell = '', q = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (q) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += c; }
      else if (tsv && c === '"' && cell === '') q = true;          // cella tra virgolette (a capo dentro la cella)
      else if (c === '\t') { row.push(cell); cell = ''; }
      else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); rows.push(row); row = []; cell = ''; }
      else cell += c;
    }
    if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
    return rows.map((r) => (r.find((x) => x.trim() !== '') || '').replace(/\s*[\r\n]+\s*/g, ' ').replace(/\u00a0/g, ' ').trim())
      .map((t) => t.replace(/^[\u2022\u00B7\u25AA\u25CF\u2023\u2043*\-–—]\s+/, '').trim())      // punti elenco di Word
      .filter(Boolean).map((t) => t.slice(0, 250));
  }
  /** testo copiato -> elenco di ore: una per riga; da un foglio si prende la prima cella numerica di ogni riga */
  function parsePastedHours(text) {
    const norm = (t) => { t = t.replace(/\u00a0|\s/g, ''); if (!t) return ''; if (t.indexOf(',') >= 0) t = t.replace(/\./g, '').replace(',', '.'); const n = Number(t); return isFinite(n) && n > 0 ? String(n).replace('.', ',') : ''; };
    const lines = text.replace(/\r\n?/g, '\n').replace(/\n+$/, '').split('\n');
    const out = lines.map((ln) => { const cells = ln.split('\t'); for (const c of cells) { const v = norm(c.replace(/^"|"$/g, '')); if (v) return v; } return ''; });
    while (out.length && !out[out.length - 1]) out.pop();
    return out;
  }
  /** incolla una colonna di ore: la prima va nella riga corrente, le altre nelle righe seguenti dello stesso gruppo; se non bastano si creano nuove righe */
  function pasteHours(e, inp) {
    const tr = inp.closest('tr[data-i]'); if (!tr) return;
    const text = (e.clipboardData || window.clipboardData).getData('text'); if (!text) return;
    const vals = parsePastedHours(text);
    const multi = /[\r\n\t]/.test(text.replace(/[\r\n\t]+$/, ''));
    if (!vals.length || (vals.length === 1 && !multi)) return;                // un solo valore: incolla normale
    e.preventDefault();
    const i = Number(tr.dataset.i), r = S.activities[i];
    const same = (x) => (r.gid ? x.gid === r.gid : r.rfq ? (x.rfq && x.appId === r.appId && (x.group || '') === (r.group || '')) : (!x.rfq && !x.gid && x.appId === r.appId));
    const sib = S.activities.filter(same), start = sib.indexOf(r);
    let created = 0;
    vals.forEach((v, k) => {
      let row = sib[start + k];
      if (!row) {
        row = { appId: r.appId, group: r.group, gid: r.gid, rfq: r.rfq, _edit: r.rfq ? true : undefined, desc: '', profileId: r.profileId, profileName: r.profileName, rate: r.rate, hours: '', days: null };
        const lastIdx = S.activities.indexOf(sib[sib.length - 1]);
        S.activities.splice(lastIdx + 1, 0, row); sib.push(row); created++;
      }
      row.hours = v;
    });
    if (r.gid) syncActivityOrder();
    CPQ.markDirty('page', root); renderServices();
    const box = $(r.rfq ? '#svc-rfq-body' : '#svc-body'), lastRow = sib[Math.min(sib.length - 1, start + vals.length - 1)], ti = S.activities.indexOf(lastRow);
    const f = box.querySelector('tr[data-i="' + ti + '"] .svc-hours'); if (f) f.focus();
    toast('Incollate ' + vals.length + ' ore' + (created ? ' (' + created + ' nuove righe: completa descrizione)' : ''));
  }
  function bindPasteRows(sel) {
    $(sel).addEventListener('paste', (e) => {
      const hrs = e.target.closest('input.svc-hours'); if (hrs) { pasteHours(e, hrs); return; }
      const inp = e.target.closest('input.svc-desc'); if (!inp) return;
      const tr = inp.closest('tr[data-i]'); if (!tr) return;
      const text = (e.clipboardData || window.clipboardData).getData('text'); if (!text) return;
      const items = parsePastedServices(text);
      const multi = /[\r\n\t]/.test(text.replace(/[\r\n\t]+$/, ''));
      if (!items.length || (items.length === 1 && !multi)) return;      // una sola riga di testo: incolla normale
      e.preventDefault();
      const i = Number(tr.dataset.i), r = S.activities[i];
      const a = inp.selectionStart == null ? inp.value.length : inp.selectionStart, b = inp.selectionEnd == null ? a : inp.selectionEnd;
      r.desc = (inp.value.slice(0, a) + items[0] + inp.value.slice(b)).slice(0, 250);       // la prima riga va nel campo, le altre in nuove righe
      // le righe seguenti: si riempiono prima le righe libere (descrizione vuota) dello stesso gruppo, poi se ne creano di nuove
      const same = (x) => (r.gid ? x.gid === r.gid : r.rfq ? (x.rfq && x.appId === r.appId && (x.group || '') === (r.group || '')) : (!x.rfq && !x.gid && x.appId === r.appId));
      const sib = S.activities.filter(same);
      let p = sib.indexOf(r), last = r, created = 0;
      items.slice(1).forEach((t) => {
        let k = p + 1;
        while (k < sib.length && (sib[k].desc || '').trim()) k++;
        let row = sib[k];
        if (row) { row.desc = t; p = k; }
        else {
          row = { appId: r.appId, group: r.group, gid: r.gid, rfq: r.rfq, _edit: r.rfq ? true : undefined, desc: t,
            profileId: r.profileId, profileName: r.profileName, rate: r.rate, hours: '', days: null };
          S.activities.splice(S.activities.indexOf(sib[sib.length - 1]) + 1, 0, row); sib.push(row); p = sib.length - 1; created++;
        }
        last = row;
      });
      if (r.gid) syncActivityOrder();
      CPQ.markDirty('page', root); renderServices();
      const ti = S.activities.indexOf(last);
      const box = $(r.rfq ? '#svc-rfq-body' : '#svc-body'), nt = box.querySelector('tr[data-i="' + ti + '"] .svc-hours') || box.querySelector('tr[data-i="' + ti + '"] .svc-desc');
      if (nt) nt.focus();
      toast(items.length > 1 ? 'Incollate ' + items.length + ' righe' + (created ? ' (' + created + ' nuove)' : ' nelle righe libere') + ': completa profilo e ore' : 'Testo incollato');
    });
  }

  function bindServices() {
    bindRfq(); bindRfqEdit(); bindManualGroups(); bindPasteRows('#svc-body'); bindPasteRows('#svc-rfq-body');
    const body = $('#svc-body');
    body.addEventListener('click', (e) => {
      const add = e.target.closest('[data-add]'), del = e.target.closest('[data-del]');
      if (add) addActivity(Number(add.dataset.add));
      if (del) { S.activities.splice(Number(del.dataset.del), 1); renderServices(); CPQ.markDirty('page', root); }
    });
    // Tab nella cella delle ore dell'ultima riga di un'applicazione: nuova riga (solo se la riga ha una descrizione)
    body.addEventListener('keydown', (e) => {
      if (e.key !== 'Tab' || e.shiftKey || !e.target.classList.contains('svc-hours')) return;
      const tr = e.target.closest('tr[data-i]'); if (!tr) return;
      const i = Number(tr.dataset.i), r = S.activities[i];
      const lastOfApp = r.gid ? S.activities.map((x, k) => [x, k]).filter(([x]) => x.gid === r.gid).pop()[1] === i : S.activities.map((x, k) => [x, k]).filter(([x]) => x.appId === r.appId).pop()[1] === i;
      if (!lastOfApp || !(r.desc || '').trim()) return;
      e.preventDefault();
      if (r.gid) addManualRow(r.gid); else addActivity(r.appId);
    });
    body.addEventListener('input', (e) => {
      const tr = e.target.closest('tr[data-i]'); if (!tr) return;
      const r = S.activities[Number(tr.dataset.i)];
      if (e.target.classList.contains('svc-desc')) r.desc = e.target.value;
      if (e.target.classList.contains('svc-hours')) r.hours = e.target.value;
      updateServiceTotals();
    });
    body.addEventListener('change', (e) => {
      const tr = e.target.closest('tr[data-i]'); if (!tr || !e.target.classList.contains('svc-prof')) return;
      const r = S.activities[Number(tr.dataset.i)];
      r.profileId = e.target.value ? Number(e.target.value) : null;
      const p = profileOf(r.profileId); if (p) { r.rate = p.amount; r.profileName = p.name; }
      updateServiceTotals();
    });
    $('#svc-disc').addEventListener('input', (e) => {
      let v = Number(String(e.target.value || 0).replace(',', '.'));
      if (v > 100) { v = 100; e.target.value = 100; } if (v < 0 || !isFinite(v)) { v = 0; e.target.value = 0; }
      S.actDiscount = v; syncUntil(); updateServiceTotals(); updateSummaryTotals();
    });
    $('#svc-detail').addEventListener('change', (e) => { S.actDetail = e.target.checked; });
    $('#btn-back5').addEventListener('click', () => go(prevStep(5)));
    $('#btn-next5').addEventListener('click', () => go(nextStep(5)));
  }

  // ---------------------------------------------------------------- Galileo: listino JGalileo per mercato e scenario
  const G = { info: { markets: [] }, profile: null, rules: [], timer: null, seq: 0, collapsed: new Set(), loading: false };
  const GAL_KIND = { NEW: 'Nuova configurazione', CONS: 'Consociata', MIG: 'Migrazione', MIGCONS: 'Migrazione consociata', IMMOB: 'Immobiliare (fino a 3 utenti)', AGG: 'Moduli aggiuntivi' };
  const galMarket = () => (G.info.markets || []).find((m) => m.applicationId === S.galileoId);
  /** la pagina Galileo c'e' solo se e' scelta una tipologia che ha un listino configurato */
  function galHasListino() { return !!(S.optGalileo && S.galileoId && galMarket()); }
  /** descrizione della configurazione Galileo scelta (tipologia: es. COMMESSA & MACHINERY, PRODUZIONE in SERIE, MODULI AGGIUNTIVI) */
  const galMarketName = () => { const m = galMarket(); return m ? (m.code === GAL_AGG_CODE ? 'MODULI AGGIUNTIVI' : m.name) : ''; };
  const galProfileLabel = () => (G.profile ? (GAL_KIND[G.profile.kind] || G.profile.label) : '');
  const galIsAgg = () => !!G.profile && G.profile.kind === 'AGG';
  const galIsMig = () => !!G.profile && (G.profile.kind === 'MIG' || G.profile.kind === 'MIGCONS');
  const galDate = (iso) => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || ''); return m ? m[3] + '/' + m[2] + '/' + m[1] : (iso || ''); };

  function galReset() {
    S.gal = { profileId: null, release: '', users: '0', addUsers: '', source: 0, aggiunta: false, sel: {}, calc: null };
    G.profile = null; G.rules = [];
  }

  /** all'apertura del passo: carica il profilo (scenario predefinito = nuova configurazione) e disegna la pagina */
  async function galEnter() {
    const m = galMarket(); if (!m) return;
    $('#gal-market').textContent = m.code === GAL_AGG_CODE ? 'MODULI AGGIUNTIVI' : m.name;
    const l = G.info.listino;
    $('#gal-listino').textContent = l ? 'Listino ' + l.name + ' ' + l.version + ' – valido fino al ' + galDate(l.validUntil) : '';
    const ex = $('#gal-expired'); ex.hidden = !G.info.expired;
    if (G.info.expired) ex.querySelector('span').textContent = 'Il listino è scaduto il ' + galDate(l.validUntil) + ': verifica con l\'amministrazione prima di emettere l\'offerta.';
    if (!S.gal.profileId || !m.profiles.some((p) => p.id === S.gal.profileId)) {
      const first = m.profiles.find((p) => p.kind === 'NEW') || m.profiles[0];
      await galLoadProfile(first.id, false);
    } else if (!G.profile || G.profile.id !== S.gal.profileId) {
      await galLoadProfile(S.gal.profileId, true);
    } else galRender();
  }

  /** carica il listino di un profilo; con keep mantiene le selezioni (per codice modulo) cambiando scenario */
  async function galLoadProfile(id, keep) {
    try {
      G.loading = true;
      const r = await CPQ.api('GET', '/offers/api/galileo/profile/' + id);
      const old = G.profile, oldSel = S.gal.sel;
      G.profile = r.profile; G.rules = r.rules || [];
      S.gal.profileId = id;
      if (keep && old && old.id !== id) {                 // cambio scenario: si riportano i moduli con lo stesso codice
        const byCode = {}; old.modules.forEach((m) => { byCode[m.moduleId] = m.code; });
        const ns = {};
        G.profile.modules.forEach((m) => { const was = Object.keys(oldSel).find((k) => byCode[k] === m.code); if (was && oldSel[was]) ns[m.moduleId] = oldSel[was]; });
        S.gal.sel = ns;
      } else if (!keep) S.gal.sel = {};
      else { const valid = new Set(G.profile.modules.map((m) => String(m.moduleId))); Object.keys(S.gal.sel).forEach((k) => { if (!valid.has(k)) delete S.gal.sel[k]; }); }
      if (!G.profile.cfgV11) S.gal.release = 'V9';
      if (G.profile.kind !== 'MIG' && G.profile.kind !== 'MIGCONS') { S.gal.source = 0; S.gal.addUsers = ''; }
      if (!G.profile.aggiuntaSupported) S.gal.aggiunta = false;
      S.gal.calc = null;
      G.loading = false;
      if (S.step === 8) galRender();
      scheduleGalCalc();
    } catch (e) { G.loading = false; toast(e.message, true); }
  }

  const galState = (m) => S.gal.sel[m.moduleId] || (m.flag === 'CB' ? 'D' : '');

  function galStateCtl(m) {
    const kind = G.profile.kind, st = galState(m);
    if (kind === 'IMMOB') return '<span class="muted">incluso</span>';
    if (m.flag === 'V11') return '<span class="muted">' + (S.gal.release === 'V11' ? 'incluso (V11)' : 'solo con V11') + '</span>';
    if (kind === 'NEW' || kind === 'AGG') {
      if (m.flag === 'CB') return '<span class="muted">incluso</span>';
      return '<input type="checkbox" class="gal-chk" data-m="' + m.moduleId + '"' + (st === 'D' ? ' checked' : '') + '>';
    }
    const lbl = galIsMig() ? 'Migrato' : 'In comune';
    const opts = m.flag === 'CB' ? [['D', lbl], ['F', 'Nuovo']] : [['', '—'], ['D', lbl], ['F', 'Nuovo']];
    return '<select data-native class="gal-st" data-m="' + m.moduleId + '">' + opts.map((o) => '<option value="' + o[0] + '"' + (st === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select>';
  }

  /** moduli senza effetto sul prezzo in questo scenario (formula assente nel foglio Excel del listino) */
  function galNoCharge(m) {
    if (m.flag !== 'FA') return false;
    return G.profile.kind === 'NEW' ? !m.eRule : (!m.eRule && !m.gCounted);
  }

  /** ricostruisce le opzioni di un menu (il selettore personalizzato si aggiorna cosi' alla modifica del contenuto) */
  function galFill(sel, options, value) {
    sel.innerHTML = options.map((o) => '<option value="' + esc(o[0]) + '"' + (String(o[0]) === String(value) ? ' selected' : '') + '>' + esc(o[1]) + '</option>').join('');
  }

  function galRender() {
    const P = G.profile; if (!P) return;
    const mk = galMarket();
    if (mk) galFill($('#gal-kind'), mk.profiles.map((p) => [p.id, GAL_KIND[p.kind] || p.label]), P.id);
    // MODULI AGGIUNTIVI: si sceglie solo il numero di utenti (nessuno scenario) e poi i moduli per area
    $('#gal-kind').closest('.field').hidden = galIsAgg();
    $('#gal-release-box').hidden = !P.cfgV11;
    galFill($('#gal-release'), [['', '— non selezionato —'], ['V9', 'V9'], ['V11', 'V11']], S.gal.release);
    $('#gal-users').value = S.gal.users;
    const ul = document.querySelector('label[for="gal-users"]'); if (ul) ul.textContent = galIsAgg() ? 'Numero utenti' : 'Numero utenti *';          // MODULI AGGIUNTIVI: facoltativo
    $('#gal-users-help').textContent = P.kind === 'IMMOB' ? 'da 1 a ' + P.maxUsers + ' utenti'
      : galIsAgg() ? 'Facoltativo. Fino a 10 utenti il prezzo è la somma dei moduli; oltre si aggiunge un importo per utente in base alla fascia' : '';
    $('#gal-source-box').hidden = !galIsMig();
    galFill($('#gal-source'), [[0, '— seleziona —'], [1, 'Carattere'], [2, 'Visuale'], [3, 'V9']], S.gal.source || 0);
    $('#gal-add-box').hidden = !galIsMig(); $('#gal-addusers').value = S.gal.addUsers;
    $('#gal-agg-box').hidden = !P.aggiuntaSupported; $('#gal-agg').checked = !!S.gal.aggiunta;
    $('#gal-mods-help').textContent = galIsAgg() ? 'Spunta i moduli da aggiungere, organizzati per area applicativa.'
      : P.kind === 'NEW' ? 'Spunta le funzioni aggiuntive (FA); i moduli base (CB) sono compresi nella configurazione.'
      : P.kind === 'IMMOB' ? 'Configurazione immobiliare: i moduli base sono inclusi nel prezzo fisso.'
      : 'Per ogni modulo indica se è ' + (galIsMig() ? 'già presente (migrato)' : 'in comune con la principale') + ' oppure nuovo; i moduli base (CB) sono ' + (galIsMig() ? 'migrati' : 'in comune') + ' salvo diversa scelta.';
    galRenderMods();
    galRenderTotals();
  }

  function galRenderMods() {
    const P = G.profile; if (!P) return;
    const q = ($('#gal-filter').value || '').trim().toLowerCase(), only = $('#gal-onlysel').checked;
    const groups = []; const idx = {};
    const seen = new Set();
    P.modules.forEach((m) => {
      if (m.flag === 'V11' && S.gal.release !== 'V11') return;      // il modulo della release V11 (SMIXW) compare, ed e' conteggiato in automatico, solo con la V11
      if (seen.has(m.code)) return;               // stesso modulo in due righe del foglio: una sola voce (il calcolo usa entrambe le righe)
      seen.add(m.code);
      const a = m.area || 'ALTRO';
      if (!(a in idx)) { idx[a] = groups.length; groups.push({ area: a, mods: [] }); }
      groups[idx[a]].mods.push(m);
    });
    const violSev = {}; ((S.gal.calc && S.gal.calc.violations) || []).forEach((v) => { violSev[v.moduleCode] = v.severity; });
    const viol = new Set(Object.keys(violSev));
    $('#gal-mods').innerHTML = groups.map((g) => {
      const rows = g.mods.filter((m) => (!q || (m.code + ' ' + m.description).toLowerCase().includes(q)) && (!only || galState(m)));
      if (!rows.length) return '';
      const closed = G.collapsed.has(g.area) && !q;
      const nsel = g.mods.filter((m) => galState(m) && m.flag !== 'CB' && m.flag !== 'V11').length;
      return '<div class="gal-area" data-area="' + esc(g.area) + '"><span>' + (closed ? '▸' : '▾') + '</span><span class="grow">' + esc(g.area) + '</span><span class="cnt">' + g.mods.length + ' moduli' + (nsel ? ' · ' + nsel + ' selezionati' : '') + '</span></div>' +
        (closed ? '' : '<div class="tablewrap"><table class="t gal-t"><tbody>' + rows.map((m) => {
          const st = galState(m);
          return '<tr data-m="' + m.moduleId + '" class="' + (st && m.flag !== 'CB' && m.flag !== 'V11' ? 'sel' : '') + '"><td style="width:150px">' + galStateCtl(m) + '</td><td class="mono"><b>' + esc(m.code) + '</b></td><td>' + esc(m.description) +
            (m.flag === 'CB' ? ' <span class="chip">base</span>' : '') + (viol.has(m.code) ? (violSev[m.code] === 'ERROR' ? ' <span class="chip err">vincolo non rispettato</span>' : ' <span class="chip warn" title="Dipendenza non vincolante: verifica con il cliente">dipendenza da verificare</span>') : '') +
            (galNoCharge(m) ? ' <span class="chip warn" title="Nel foglio Excel del listino questo modulo non ha la formula di addebito">non addebitato</span>' : '') + '</td>' +
            '<td class="num pr nowrap">' + (m.price == null ? '' : CPQ.eur(m.price)) + '</td></tr>';
        }).join('') + '</tbody></table></div>');
    }).join('') || '<div class="muted">Nessun modulo.</div>';
  }

  function galBody() {
    const g = S.gal;
    return { profileId: g.profileId, release: g.release, users: Number(g.users) || 0, addUsers: Number(g.addUsers) || 0, source: Number(g.source) || 0, aggiunta: !!g.aggiunta,
      selections: Object.keys(g.sel).filter((k) => g.sel[k]).map((k) => ({ moduleId: Number(k), state: g.sel[k] })) };
  }

  function scheduleGalCalc() {
    clearTimeout(G.timer);
    G.timer = setTimeout(galCalc, 150);
  }

  async function galCalc() {
    if (!S.gal.profileId) return;
    const seq = ++G.seq;
    S.gal.calc = null;
    try {
      const r = await CPQ.api('POST', '/offers/api/galileo/calc', galBody());
      if (seq !== G.seq) return;
      S.gal.calc = r; updateAmounts(); if (S.step === 4) updateSummaryTotals();
    } catch (e) { if (seq !== G.seq) return; S.gal.calc = { error: e.message }; }
    if (S.step === 8) { galRenderTotals(); galRenderMods(); }
    refreshSaveState();
    if (S.step === 4) updateSummaryTotals();
  }

  function galRenderTotals() {
    const c = S.gal.calc, need = galIsAgg() ? Object.keys(S.gal.sel).some((k) => S.gal.sel[k]) : Number(S.gal.users) >= 1;
    const inv = $('#gal-invalid');
    const r = c && c.result;
    const ok = !!(r && r.valid);
    inv.hidden = ok || !need || !c;
    if (c && c.error) { inv.hidden = false; inv.querySelector('span').textContent = c.error; }
    else if (r && !r.valid && need) inv.querySelector('span').textContent = r.message || 'Configurazione non valida';
    $('#gal-t-list').textContent = ok ? CPQ.eur(r.list) : '€ 0,00';
    $('#gal-t-maint').textContent = ok ? CPQ.eur(r.maintenance) : '€ 0,00';
    $('#gal-t-note').textContent = ok ? galProfileLabel() : '';
    $('#gal-lines').innerHTML = ok ? r.lines.map((l) => '<tr class="gal-lines"><td>' + esc(l.label) + '</td><td class="num pr">' + CPQ.eur(l.amount) + '</td></tr>').join('') : '';
    $('#gal-violations').innerHTML = ((c && c.violations) || []).map((v) => '<div class="gal-viol ' + (v.severity === 'ERROR' ? 'err' : 'warn') + '">' + esc(v.message) + '</div>').join('');
  }

  /** errori bloccanti della pagina Galileo (vuoto se l'elemento non e' selezionato o non ha listino) */
  function galileoErrorList() {
    if (!stepVisible(8)) return [];
    const g = S.gal, e = [];
    const add = (sel, msg) => e.push({ step: 8, kind: 'gal', sel: sel, msg: msg });
    if (!g.profileId) { const mk = galMarket(); return mk && mk.code === GAL_AGG_CODE ? [{ step: 8, kind: 'gal', sel: '#gal-mods', msg: 'Galileo: seleziona almeno un modulo' }] : [{ step: 8, kind: 'gal', sel: '#gal-kind', msg: 'Galileo: scegli lo scenario' }]; }
    if (G.profile && G.profile.id === g.profileId && G.profile.cfgV11 && !g.release) return [{ step: 8, kind: 'gal', sel: '#gal-release', msg: 'Galileo: seleziona la release (V9 o V11)' }];
    if (!(Number(g.users) >= 1) && !galIsAgg()) return [{ step: 8, kind: 'gal', sel: '#gal-users', msg: 'Galileo: indica il numero di utenti' }];          // MODULI AGGIUNTIVI: utenti facoltativi
    if (!g.calc) return [{ step: 8, kind: 'gal', sel: '#gal-users', msg: 'Galileo: calcolo in corso' }];
    if (g.calc.error) return [{ step: 8, kind: 'gal', sel: '#gal-card-tot', msg: 'Galileo: ' + g.calc.error }];
    if (!g.calc.result.valid) add('#gal-card-tot', 'Galileo: ' + g.calc.result.message);
    else (g.calc.violations || []).filter((v) => v.severity === 'ERROR').forEach((v) => add('#gal-violations', 'Galileo – ' + v.message));
    return e;
  }
  function galileoErrors() { return Array.from(new Set(galileoErrorList().map((x) => x.msg))); }

  function bindGalileo() {
    $('#gal-kind').addEventListener('change', (e) => { galLoadProfile(Number(e.target.value), true); });
    $('#gal-release').addEventListener('change', (e) => { S.gal.release = e.target.value; galRenderMods(); scheduleGalCalc(); });
    $('#gal-users').addEventListener('input', (e) => { S.gal.users = e.target.value.replace(/\D/g, ''); syncPowerUsers(true); scheduleGalCalc(); refreshSaveState(); });
    $('#gal-addusers').addEventListener('input', (e) => { S.gal.addUsers = e.target.value.replace(/\D/g, ''); scheduleGalCalc(); });
    $('#gal-source').addEventListener('change', (e) => { S.gal.source = Number(e.target.value); scheduleGalCalc(); });
    $('#gal-agg').addEventListener('change', (e) => { S.gal.aggiunta = e.target.checked; scheduleGalCalc(); });
    $('#gal-filter').addEventListener('input', galRenderMods);
    $('#gal-onlysel').addEventListener('change', galRenderMods);
    const box = $('#gal-mods');
    box.addEventListener('click', (e) => {
      const a = e.target.closest('.gal-area'); if (!a) return;
      const k = a.dataset.area; if (G.collapsed.has(k)) G.collapsed.delete(k); else G.collapsed.add(k);
      galRenderMods();
    });
    box.addEventListener('change', (e) => {
      const el = e.target.closest('[data-m]'); if (!el) return;
      const id = el.dataset.m, m = G.profile.modules.find((x) => String(x.moduleId) === id); if (!m) return;
      let v = el.classList.contains('gal-chk') ? (el.checked ? 'D' : '') : el.value;
      if (m.flag === 'CB' && v === 'D') v = '';             // moduli base: «in comune» e' lo stato predefinito
      if (v) S.gal.sel[id] = v; else delete S.gal.sel[id];
      const tr = el.closest('tr'); if (tr) tr.classList.toggle('sel', !!galState(m) && m.flag !== 'CB');
      scheduleGalCalc();
    });
  }

  // ---------------------------------------------------------------- Servizi SMITECH: moduli della famiglia SMITECH con Area applicativa = SMITECH
  const smitechApp = () => catalog.find((a) => a.code === 'SMITECH' || a.family === 'SMITECH');
  const SM_KIND = { LICENSE: 'Valore fisso', ANNUAL: 'Canone annuo', MONTHLY: 'Canone mensile', ONETIME: 'Una tantum' };
  /** con la sezione Power System la classe (P05 / P10) e' quella del gruppo processore del modello scelto, non modificabile */
  function smitechPowerClass(app) {
    if (!S.optPower || !S.powerModelId) return null;
    const cls = String((powerModelOf(S.powerModelId) || {}).processorGroup || '').toUpperCase();
    return app.modes.some((m) => m.code === cls) ? cls : null;
  }
  function syncSmitechMode(recalc) {
    const app = smitechApp(); if (!app) return;
    const cls = smitechPowerClass(app); if (!cls) { if (S.step === 9) renderSmitech(); return; }
    S.smitechMode = cls;
    const line = lineOf(app.id);
    if (line && line.modeCode !== cls) { line.modeCode = cls; if (recalc) scheduleCalc(); }
    if (S.step === 9) renderSmitech();
  }
  const smMode = (app, line) => smitechPowerClass(app) || (line && line.modeCode) || S.smitechMode || (app.modes.find((m) => m.preferred) || app.modes[0] || {}).code || null;
  /** utenti dei servizi SMITECH: quelli impostati su Power System e/o Galileo (se ci sono entrambi, il maggiore) */
  function smitechUsers() {
    const c = [];
    if (S.optPower && S.powerModelId && !S.powerUnlimited && Number(S.powerUsers) > 0) c.push({ n: Number(S.powerUsers), src: 'Power System' });
    if (S.optGalileo && Number(S.gal.users) > 0) c.push({ n: Number(S.gal.users), src: 'Galileo' });
    if (!c.length) return { n: 0, src: S.optPower && S.powerUnlimited ? 'Power System (utenti illimitati)' : '' };
    const max = Math.max.apply(null, c.map((x) => x.n));
    const srcs = c.filter((x) => x.n === max).map((x) => x.src);
    return { n: max, src: srcs.join(' e ') };
  }
  /** riporta gli utenti di Power / Galileo nelle grandezze (utenti) della riga SMITECH; vero se qualcosa e' cambiato */
  function syncSmitechUsers() {
    const app = smitechApp(), line = app && lineOf(app.id); if (!line) return false;
    const u = smitechUsers().n; let changed = false;
    app.drivers.forEach((d) => { if ((line.drivers[d.id] || 0) !== u) { line.drivers[d.id] = u; changed = true; } });
    return changed;
  }
  function smitechUsersChanged() { if (syncSmitechUsers()) scheduleCalc(); if (S.step === 9) renderSmitech(); }
  function smModules(app) { return app.modules.filter((m) => m.area === 'SMITECH' && m.section !== 'LINK'); }
  /** testo del prezzo: importo del modulo per la classe scelta, oppure «da …» per i moduli a fasce di utenti */
  function smPriceText(app, m, mode) {
    if (m.groupId) {
      const g = app.groups.find((x) => x.id === m.groupId);
      const prices = g ? g.tiers.map((t) => C.modulePrice({ prices: t.unitByMode || {} }, mode)).filter((v) => v > 0) : [];
      return prices.length ? 'da ' + CPQ.eur(Math.min.apply(null, prices)) + ' (a fasce di utenti)' : 'a fasce di utenti';
    }
    const p = C.modulePrice(m, mode);
    return m.included ? 'incluso' : (p > 0 ? CPQ.eur(p) : 'da definire');
  }
  function renderSmitech() {
    const app = smitechApp(), body = $('#sm-body'); if (!app || !body) { if (body) body.innerHTML = '<tr><td colspan="5" class="muted">Applicazione SMITECH non presente nell\'anagrafica.</td></tr>'; return; }
    syncSmitechUsers();
    const line = lineOf(app.id), mode = smMode(app, line);
    $('#sm-mode-box').hidden = !app.modes.length;
    const forced = smitechPowerClass(app);
    $('#sm-mode').disabled = !!forced;
    const help = $('#sm-mode-help'); help.hidden = !forced; help.textContent = forced ? 'Impostata dal Power System (gruppo processore ' + forced + ')' : '';
    $('#sm-mode').innerHTML = app.modes.map((m) => '<option value="' + esc(m.code) + '"' + (m.code === mode ? ' selected' : '') + '>' + esc(m.label) + '</option>').join('');
    const mods = smModules(app);
    body.innerHTML = mods.length ? mods.map((m) => {
      const on = !!(line && line.modules[m.id] > 0);
      const grp = m.groupId ? app.groups.find((x) => x.id === m.groupId) : null;
      const drv = grp ? app.drivers.find((d) => d.id === grp.driverId) : null;
      const su = smitechUsers();
      const users = on && drv ? '<div class="hint" style="margin-top:4px">' + esc(drv.label) + ': <b>' + (su.n > 0 ? CPQ.num(su.n, 0) + ' ' + esc(drv.unitLabel) : (su.src ? esc(su.src) + ': fascia da valutare ad hoc' : 'da impostare'))
        + '</b>' + (su.n > 0 ? ' · da ' + esc(su.src) : (su.src ? '' : ' in Power System o Galileo')) + '</div>' : '';
      return '<tr class="' + (on ? 'sel' : '') + '"><td><input type="checkbox" class="sm-chk" data-mod="' + m.id + '"' + (on ? ' checked' : '') + (m.included ? ' disabled' : '') + ' aria-label="Seleziona ' + esc(m.code) + '"></td>' +
        '<td class="mono"><b>' + esc(m.code) + '</b></td><td>' + esc(m.description) + (m.note ? '<div class="hint">' + esc(m.note) + '</div>' : '') + users + '</td>' +
        '<td><span class="chip ' + (m.kind === 'LICENSE' ? '' : 'blue') + '">' + esc(SM_KIND[m.kind] || m.kind) + '</span></td>' +
        '<td class="num pr">' + esc(smPriceText(app, m, mode)) + '</td></tr>';
    }).join('') : '<tr><td colspan="5" class="muted">Nessun modulo SMITECH con Area applicativa «SMITECH»: imposta l\'area nelle Impostazioni > Applicazioni e moduli.</td></tr>';
    const vs = violationList().filter((v) => v.applicationId === app.id && v.severity !== 'INFO');
    $('#sm-violations').innerHTML = vs.map((v) => '<div class="gal-viol ' + (v.severity === 'ERROR' ? 'err' : 'warn') + '">' + esc(v.message) + '</div>').join('');
  }
  function bindSmitech() {
    const body = $('#sm-body'); if (!body) return;
    body.addEventListener('change', (e) => {
      const app = smitechApp(); if (!app) return;
      const chk = e.target.closest('.sm-chk');
      if (chk) {
        let line = lineOf(app.id);
        if (chk.checked && !line) { line = C.newLine(app); line.modeCode = smitechPowerClass(app) || S.smitechMode || line.modeCode; S.lines.push(line); }
        const r = C.selectModule(catalog, S.lines, app.id, Number(chk.dataset.mod), chk.checked);
        const msgs = [];
        if (r.addedModules.length) msgs.push('Aggiunti i prerequisiti: ' + r.addedModules.join(', '));
        if (r.addedApps.length) msgs.push('Aggiunta all\'offerta anche: ' + r.addedApps.join(', '));
        if (r.removed.length > 1) msgs.push('Rimossi anche i servizi che li richiedevano: ' + r.removed.slice(1).join(', '));
        r.ambiguous.forEach((a) => msgs.push('Prerequisito ' + a.code + ': aggiungi una di queste applicazioni (' + a.apps.join(', ') + ')'));
        if (msgs.length) toast(msgs.join(' · '), r.ambiguous.length > 0);
        line = lineOf(app.id);
        syncSmitechUsers();
        if (line && !Object.keys(line.modules).some((k) => line.modules[k] > 0)) S.lines = S.lines.filter((l) => l !== line);          // nessun servizio: l'applicazione esce dall'offerta
        renderSmitech(); scheduleCalc(); CPQ.markDirty('page', root); refreshStepper(); refreshSaveState();
      }
    });
    $('#sm-mode').addEventListener('change', (e) => {
      const app = smitechApp(), line = app && lineOf(app.id);
      if (app && smitechPowerClass(app)) return;                         // classe fissata dal Power System
      S.smitechMode = e.target.value; if (line) line.modeCode = e.target.value;
      renderSmitech(); scheduleCalc(); CPQ.markDirty('page', root);
    });
  }

  // ---------------------------------------------------------------- trasferta: distanza dalla sede piu' vicina + tariffa
  function renderTravel() {
    $('#dist-km').textContent = S.distanceKm == null ? '—' : CPQ.num(S.distanceKm, 1) + ' km';
    $('#travel-help').textContent = travelSourceText() || 'Dalla tabella “Trasferte Italia”';
    $('#dist-sede').textContent = S.nearestSede ? 'Sede: ' + S.nearestSede + (S.distMethod === 'ESTIMATE' ? ' (stima)' : '') : '';
    const sel = $('#travel-rate');
    sel.innerHTML = '<option value="">— nessuna —</option>' + S.travelRates.map((r) => '<option value="' + r.id + '"' + (Number(S.travelRateId) === r.id ? ' selected' : '') + '>' + esc(r.name) + ' – ' + CPQ.eur(r.amount) + '</option>').join('');
  }

  /** trasferta calcolata in automatico (sede piu' vicina): solo prospect senza voce di trasferta in anagrafica */
  const travelAuto = () => !!S.customer && !S.accountTravelRateId;        // senza voce assegnata in anagrafica (prospect o cliente) la voce si seleziona dalla distanza

  function travelSourceText() {
    if (!S.customer) return '';
    if (custType() === 'CLIENTE') return S.accountTravelRateId ? 'Cliente: voce di trasferta presa dall\'anagrafica dell\'account (con «Ricalcola» si imposta la voce della distanza)' : 'Cliente: nessuna voce in anagrafica, la voce è selezionata in base alla distanza dalla sede Sanmarco più vicina (modificabile)';
    if (S.accountTravelRateId) return 'Prospect: voce di trasferta già registrata nell\'anagrafica';
    return 'Prospect: voce calcolata dalla sede Sanmarco più vicina; salvando l\'offerta con una trasferta viene registrata nell\'anagrafica del prospect';
  }

  async function loadDistance(manual) {
    if (!S.customer) return;
    const id = S.customer.id;
    S.distFor = id;
    $('#dist-km').textContent = '…'; $('#dist-note').textContent = 'Calcolo in corso (geocodifica e percorso)…';
    try {
      const r = await CPQ.api('GET', '/offers/api/distance?customerId=' + id);
      if (S.customer.id !== id) return;
      if (r.ok) {
        S.distanceKm = r.km; S.nearestSede = r.sede; S.distMethod = r.method;
        const band = C.travelBandFor(S.travelRates, Number(r.km));
        // il calcolo (manuale o automatico) imposta nell'elenco la voce di riferimento per la distanza
        if (band) S.travelRateId = band.id;
        $('#dist-note').textContent = (r.message || '') + (band ? '' : ' Nessuna voce di trasferta corrisponde a ' + CPQ.num(r.km, 1) + ' km: scegli la voce a mano.');
      } else {
        S.distanceKm = null; S.nearestSede = ''; $('#dist-note').textContent = r.message || 'Distanza non calcolabile';
      }
    } catch (e) { S.distanceKm = null; $('#dist-note').textContent = e.message; }
    renderTravel();
  }

  /** abilita il salvataggio solo se non ci sono vincoli bloccanti ne' campi obbligatori mancanti */
  function refreshSaveState() {
    const errs = violationList().filter((v) => v.severity === 'ERROR');
    const miss = validateStep1();
    const terr = termsCfg ? C.termsErrors(termsCfg, S.terms, custType(), !!driLabel()) : [];
    const aerr = C.activityErrors(S.activities, S.lines);
    $('#btn-save').disabled = !!S.readOnly;         // gli errori si segnalano sul campo, con un popup, al click; revisione superata: sola lettura
    $('#btn-save-docx').disabled = root.dataset.docx === 'false';
    const sd = $('#btn-save-docx'); if (sd && sd.lastChild) sd.lastChild.textContent = S.readOnly ? 'Genera Word' : 'Salva e genera Word';
    $('#sum-blockers').textContent = errs.length ? 'Risolvi i vincoli in rosso prima di salvare.' : aerr.concat(powerErrors(), galileoErrors(), terr).join(' · ');
  }

  /** tabella totali, netto e margine del riepilogo: si aggiornano mentre si digita lo sconto */
  function updateSummaryTotals() {
    const n = nets();
    const feeTot = r2((calc ? calc.lines : []).reduce((t, x) => t + (Number(x.annual) || 0), 0));
    let foot = '<tr><td colspan="3" class="right"><b>Totale</b></td><td class="num pr"><b>' + CPQ.eur(n.tl) + '</b></td><td class="num pr"><b>' + CPQ.eur(r2(n.tm - feeTot)) + '</b></td></tr>';
    $('#sum-annual-foot').innerHTML = '<tr><td colspan="3" class="right"><b>Totale canoni annui</b></td><td class="num pr"><b>' + CPQ.eur(feeTot) + '</b></td></tr>';
    $('#sum-foot').innerHTML = foot;
    $('#n-lic').textContent = CPQ.eur(n.nl); $('#n-man').textContent = CPQ.eur(n.nm);
    $('#n-lic-was').textContent = n.nl !== n.tl ? CPQ.eur(n.tl) : ''; $('#n-man-was').textContent = n.nm !== n.tm ? CPQ.eur(n.tm) : '';
    $('#n-margin').textContent = CPQ.eur(n.margin);
    const sat = C.activityTotals(S.activities, S.lines, S.actDiscount);
    $('#sum-act-list').textContent = CPQ.eur(sat.list); $('#sum-act-net').textContent = CPQ.eur(sat.net);
    $('#sum-act-save').textContent = CPQ.eur(r2(sat.list - sat.net));
    $('#sum-act-hours').textContent = sat.hours ? CPQ.num(sat.hours) + ' ore' : '';
    $('#sum-act-none').hidden = sat.list > 0;
    // importi del Power System (solo listino e netto)
    $('#sum-pw-card').hidden = !S.optPower;
    if (S.optPower) {
      const pt = S.powerMaintMax(), pl = maintComplete() ? pt.list : null, pn = maintComplete() ? pt.net : null, pm = powerModelOf(S.powerModelId);
      $('#sum-pw-list').textContent = pl == null ? '—' : CPQ.eur(pl); $('#sum-pw-net').textContent = pn == null ? '—' : CPQ.eur(pn);
      $('#sum-pw-model').textContent = pm ? pm.description : '';
      const rowsP = maintComplete() ? maintSlots().map((sl) => { const am = slotAmt(sl); return '<tr><td>' + sl.months + ' mesi</td><td>' + kindLabel(sl.kind) + '</td><td class="num">' + CPQ.eur(am.list) + '</td><td class="num">' + CPQ.eur(am.net) + '</td></tr>'; }) : [];
      $('#sum-pw-periods').innerHTML = rowsP.join(''); $('#sum-pw-periods-wrap').hidden = !rowsP.length;
      $('#sum-pw-disc').textContent = pl > 0 && pn != null ? 'sconto ' + CPQ.num((1 - pn / pl) * 100, 1) + ' %' : '';
      // Power CBU (solo Power HA/DR): listino e prezzo al cliente della scheda CBU, con il Power + LTO8 piu' alto tra le durate scelte (come per la Produzione)
      const hadr = S.powerKind === 'HADR' && S.pwOther && S.pwOther.powerModelId;          // solo se la scheda CBU ha un modello scelto
      $('#sum-cbu-list-box').hidden = !hadr; $('#sum-cbu-net-box').hidden = !hadr;
      if (hadr) {
        const cb = cbuMaintMax(S.pwOther), cm = powerModelOf(S.pwOther.powerModelId);
        $('#sum-cbu-list').textContent = cb == null ? '—' : CPQ.eur(cb.list); $('#sum-cbu-net').textContent = cb == null ? '—' : CPQ.eur(cb.net);
        $('#sum-cbu-model').textContent = cm ? cm.description : '';
        $('#sum-cbu-disc').textContent = cb && cb.list > 0 ? 'sconto ' + CPQ.num((1 - cb.net / cb.list) * 100, 1) + ' %' : '';
      }
    }
    updateAmounts();
  }

  async function save(thenDocx) {
    const firstErr = collectErrors()[0];
    if (firstErr) { revealError(firstErr); return; }
    const title = $('#sum-title').value.trim();
    S.title = title || C.suggestTitle(catalog, S.lines);
    try {
      const r = await CPQ.api('POST', '/offers/api/save', buildReq());
      S.offerId = r.id; S.saved = r.number; CPQ.clean();
      toast('Offerta ' + r.number + ' salvata');
      if (thenDocx) window.location = CPQ.url('/offers/' + r.id + '/docx');
      else window.location = CPQ.url('/offers');
    } catch (e) { toast(e.message, true); }
  }

  // ---------------------------------------------------------------- bind generale
  $$('.step').forEach((s) => s.addEventListener('click', () => go(Number(s.dataset.go))));
  $('#btn-to2').addEventListener('click', () => go(6));
  $('#btn-back6').addEventListener('click', () => go(1));
  $('#btn-next6').addEventListener('click', () => go(nextStep(6)));
  $('#opt-smitech').addEventListener('change', async (e) => {
    const app = smitechApp(), line = app && lineOf(app.id);
    if (!e.target.checked && line && Object.keys(line.modules).length && !(await CPQ.confirm('Senza i servizi SMITECH i servizi selezionati vengono tolti dall\'offerta. Continuare?', { title: 'Servizi SMITECH', yes: 'Sì, togli', no: 'No', danger: true }))) { e.target.checked = true; return; }
    S.optSmitech = e.target.checked;
    if (!S.optSmitech && line) { S.lines = S.lines.filter((l) => l !== line); scheduleCalc(); }
    refreshStepper(); refreshSaveState();
  });
  $('#opt-power').addEventListener('change', (e) => {
    S.optPower = e.target.checked;
    if (!S.optPower) {
      if (pwIsCbu()) pwSwap();
      S.powerModelId = null; S.powerRam = ''; S.powerDisk = ''; S.powerKind = 'NEW'; S.pwOther = pwFresh(); renderPower();
    }
    renderPwKind();
    refreshStepper(); syncSmitechMode(true);
  });
  $('#sel-power-kind').addEventListener('change', async (e) => {
    const k = e.target.value;
    if (k === 'HADR' && S.optSmitech) {
      const a = smitechApp(), l = a && lineOf(a.id);
      if (l && Object.keys(l.modules).length && !(await CPQ.confirm('Con Power HA/DR i servizi SMITECH non sono previsti: quelli selezionati vengono tolti dall\'offerta. Continuare?', { title: 'Power HA/DR', yes: 'Sì, togli', no: 'No', danger: true }))) { e.target.value = S.powerKind; return; }
    }
    if (k !== 'HADR' && pwIsCbu()) pwSwap();                // si esce da HA/DR: resta la configurazione di produzione
    S.powerKind = k;                                        // la scheda CBU resta in memoria (non viene inviata se il tipo non è HA/DR)
    renderPower(); CPQ.markDirty('page', root); refreshSaveState();
  });
  $('#sel-hadr-sol').addEventListener('change', (e) => { S.hadrSolution = e.target.value; renderSmidri(); CPQ.markDirty('page', root); });
  $('#smidri-price').addEventListener('input', (e) => { S.smidriPrice = e.target.value; CPQ.markDirty('page', root); });
  $('#smidri-price').addEventListener('blur', (e) => { const v = C.parseMoney(e.target.value); if (v != null) { S.smidriPrice = CPQ.num(v, 2); e.target.value = S.smidriPrice; } });
  $('#smidri-smismp').addEventListener('change', (e) => { S.smidriSmismp = e.target.checked; renderSmidri(); CPQ.markDirty('page', root); });
  $('#smidri-sm-price').addEventListener('input', (e) => { S.smidriSmismpPrice = e.target.value; CPQ.markDirty('page', root); });
  $('#smidri-sm-price').addEventListener('blur', (e) => { const v = C.parseMoney(e.target.value); if (v != null) { S.smidriSmismpPrice = CPQ.num(v, 2); e.target.value = S.smidriSmismpPrice; } });
  $('#pw-copy-prod').addEventListener('click', copyProdToCbu);
  $$('.pw-tab').forEach((b) => b.addEventListener('click', () => { pwSwitchTab(b.dataset.pwtab); CPQ.markDirty('page', root); }));
  $('#sel-power').addEventListener('change', (e) => { S.powerModelId = e.target.value ? Number(e.target.value) : null; renderPower(); refreshStepper(); syncSmitechMode(true); });
  $('#pw-ram').addEventListener('change', (e) => { S.powerRam = e.target.value; updatePowerLock(); });
  $('#pw-disks').addEventListener('input', (e) => { S.powerDisks = e.target.value.replace(/\D/g, ''); updatePowerLock(); });
  $('#pw-disk').addEventListener('change', (e) => { S.powerDisk = e.target.value; updatePowerLock(); });
  $('#pw-users').addEventListener('input', (e) => { const v = e.target.value.replace(/\D/g, ''); S.powerUsers = v; refreshPowerModelList(true); updatePowerLock(); smitechUsersChanged(); });
  $('#pw-unlimited').addEventListener('change', (e) => {
    S.powerUnlimited = e.target.checked;
    if (S.powerUnlimited) { S.powerUsers = ''; $('#pw-users').value = ''; }
    syncPowerUsers(true);                                   // deselezionando, con Galileo tornano gli utenti di Galileo
    setTimeout(updatePowerLock, 0); CPQ.markDirty('page', root);
  });
  // TAPE (LTO8 e/o TS4300) e cavo di collegamento (LTO6/LTO7) sono alternativi: scegliendone uno l'altro si disattiva
  $('#pw-cable').addEventListener('change', (e) => { S.powerCable = e.target.value; if (S.powerCable) { S.powerLto8 = false; S.powerTs4300 = false; $('#pw-lto8').checked = false; $('#pw-ts4300').checked = false; syncTape(); renderAmtSummary(); } });
  bindAmtDialog();
  $('#pw-copy-all').addEventListener('click', () => {
    const first = S.ibmiProducts[0], v = first ? S.powerProducts[first.id] : '';
    if (!v) { toast('Imposta prima il valore del primo prodotto', true); return; }
    S.ibmiProducts.forEach((p) => { S.powerProducts[p.id] = v; });
    renderPowerProducts(); CPQ.markDirty('page', root); refreshSaveState();
  });
  $('#pw-products').addEventListener('change', (e) => { const sel = e.target.closest('select[data-pid]'); if (sel) S.powerProducts[sel.dataset.pid] = sel.value; });
  $('#pw-os').addEventListener('change', (e) => { S.powerOs = e.target.value; });
  $('#pw-edition').addEventListener('change', (e) => { S.powerEdition = e.target.value; });
  $('#pw-transfer').addEventListener('change', (e) => {
    S.powerTransfer = e.target.value; $('#pw-tref-box').hidden = S.powerTransfer === 'NO';
    if (S.powerTransfer === 'NO') {
      S.powerTransferRef = ''; pwRenderRef();
      S.ibmiProducts.forEach((p) => { delete S.powerProducts[p.id]; });          // di nuovo «— seleziona —»
      renderPowerProducts(); refreshSaveState();
    } else {
      // Transfer / Conversion: tutti i prodotti IBM i risultano «Migrato» (modificabile prodotto per prodotto)
      S.ibmiProducts.forEach((p) => { S.powerProducts[p.id] = 'MIGRATO'; });
      renderPowerProducts(); refreshSaveState();
      pwHwLoad(); $('#pw-hw').focus();
    }
  });
  $('#pw-hw').addEventListener('change', (e) => {
    const c = HW.cards[Number(e.target.value)];
    if (c) { S.powerTransferRef = trJoin(c.machineType, c.model, c.serial); pwRenderRef(true); CPQ.markDirty('page', root); }
  });
  ['#pw-tr-type', '#pw-tr-model', '#pw-tr-serial'].forEach((id) => $(id).addEventListener('input', () => {
    S.powerTransferRef = trJoin($('#pw-tr-type').value, $('#pw-tr-model').value, $('#pw-tr-serial').value); pwSyncHwSel(); CPQ.markDirty('page', root);
  }));
  /** TAPE = LTO8 e/o TS4300 TAPE LIBRARY: la casella TAPE mostra le due scelte e con TAPE acceso almeno una e' attiva (LTO8 predefinita) */
  function syncTape() {
    const on = S.powerLto8 || S.powerTs4300; $('#pw-tape-on').checked = on; $('#pw-tape-sub').hidden = !on;
    $('#pw-cable-box').hidden = on; if (on) { S.powerCable = ''; $('#pw-cable').value = ''; }          // il cavo si sceglie solo con TAPE spento
  }
  $('#pw-tape-on').addEventListener('change', (e) => {
    S.powerLto8 = e.target.checked; S.powerTs4300 = false; $('#pw-lto8').checked = S.powerLto8; $('#pw-ts4300').checked = false;
    syncTape(); renderAmtSummary();
  });
  $('#pw-ts4300').addEventListener('change', (e) => {
    if (!e.target.checked && !S.powerLto8) { e.target.checked = true; window.toast('Con TAPE attivo seleziona almeno LTO8 o TS4300'); return; }
    S.powerTs4300 = e.target.checked; syncTape(); renderAmtSummary();
  });
  $('#pw-lto8').addEventListener('change', (e) => { if (!e.target.checked && !S.powerTs4300) { e.target.checked = true; window.toast('Con TAPE attivo seleziona almeno LTO8 o TS4300'); return; }
    S.powerLto8 = e.target.checked; syncTape(); renderAmtSummary(); refreshSaveState(); });
  $('#pw-eth10').addEventListener('change', (e) => { S.powerEth10 = e.target.checked; });
  $('#opt-galileo').addEventListener('change', (e) => {
    S.optGalileo = e.target.checked; $('#galileo-box').hidden = !S.optGalileo; syncPowerUsers();
    if (!S.optGalileo) { S.galileoId = null; $('#sel-galileo').value = ''; galReset(); }
    refreshDocOptions(); refreshStepper();
  });
  // «Dipartimentali»: se si toglie la spunta le applicazioni selezionate vengono rimosse (previa conferma, predefinito No)
  $('#opt-dept').addEventListener('change', async (e) => {
    if (e.target.checked) { S.optDept = true; refreshStepper(); return; }
    if (S.lines.length || S.activities.length) {
      const ok = await CPQ.confirm('Disattivando «Dipartimentali» le applicazioni e le attività già configurate verranno rimosse dall\'offerta. Continuare?',
        { title: 'Dipartimentali', yes: 'Sì, rimuovi', no: 'No, mantieni', danger: true });
      if (!ok) { e.target.checked = true; return; }
      S.lines = []; S.activities = []; calc = null; expanded.clear(); renderApps(); scheduleCalc();
    }
    S.optDept = false; refreshStepper();
  });
  $('#sel-galileo').addEventListener('change', async (e) => {
    const prev = S.galileoId;
    S.galileoId = e.target.value ? Number(e.target.value) : null;
    if (galAgg() && !(await enterAggMode())) { S.galileoId = prev; renderGalileo(); return; }
    galReset(); refreshDocOptions(); refreshStepper();
  });
  bindProfileRates();
  bindSmitech();
  $('#hd-prev').addEventListener('click', () => go(prevStep(S.step)));
  $('#hd-next').addEventListener('click', () => go(nextStep(S.step)));
  $('#btn-back1').addEventListener('click', () => go(prevStep(2)));
  $('#btn-back7').addEventListener('click', () => go(prevStep(7)));
  $('#btn-back9').addEventListener('click', () => go(prevStep(9)));
  $('#btn-next9').addEventListener('click', () => go(nextStep(9)));
  $('#btn-back8').addEventListener('click', () => go(prevStep(8)));
  $('#btn-next8').addEventListener('click', () => go(nextStep(8)));
  bindGalileo();
  $('#btn-next7').addEventListener('click', () => go(nextStep(7)));
  $('#btn-next2').addEventListener('click', () => go(nextStep(2)));
  $('#btn-back3').addEventListener('click', () => go(prevStep(3)));
  $('#btn-next3').addEventListener('click', () => go(4));
  $('#btn-back2').addEventListener('click', () => go(prevStep(4)));
  $('#btn-save').addEventListener('click', () => save(false));
  $('#btn-save-docx').addEventListener('click', () => { if (S.readOnly && S.offerId) window.location = CPQ.url('/offers/' + S.offerId + '/docx'); else save(true); });          // revisione superata: genera il Word senza salvare
  $('#travel-rate').addEventListener('change', (e) => { S.travelRateId = e.target.value ? Number(e.target.value) : null; });
  $('#dist-refresh').addEventListener('click', () => loadDistance(true));
  $('#sum-title').addEventListener('input', (e) => { S.title = e.target.value; });
  $('#svc-until').addEventListener('input', (e) => { S.actUntil = e.target.value; CPQ.markDirty('page', root); });
  [['opt-maint', 'optMaint'], ['opt-services', 'optServices'], ['opt-summary', 'optSummary']].forEach(([id, key]) => {
    $('#' + id).addEventListener('change', (e) => {
      S[key] = e.target.checked; refreshDocOptions();
      refreshStepper(); scheduleCalc();
    });
  });
  $('#sum-disc').addEventListener('input', (e) => {
    let v = Number(e.target.value || 0);
    if (v > 100) { v = 100; e.target.value = 100; } if (v < 0) { v = 0; e.target.value = 0; }
    S.discountPct = v; updateSummaryTotals();
  });
  bindStep2();
  bindServices();
  bindExtras();
  bindTerms();
  init();
})();
