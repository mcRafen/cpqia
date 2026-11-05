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
    powerModelId: null, powerRam: '', powerDisk: '', powerLto8: false, powerEth10: false, powerUsers: '', powerUnlimited: false, powerCable: '', powerTransfer: 'NO', powerTransferRef: '', powerEdition: 'SOLUTION', powerOs: '7.5', powerMaint: '36', powerMaintList: [36], powerMaintAmt: {}, powerList: '', powerNet: '', powerDisks: '2', powerProducts: {}, ibmiProducts: [], powerModels: [],
    optRfq: false, svcGroups: [], profileEdits: {}, servicesMode: 'NONE', rfqEditGroups: new Set(), optDays: true, appTotalsMode: 'NO', rfqSheetTotal: null, optPower: false, optSmitech: false, optDept: false, optGalileo: false, galileoId: null,
    gal: { profileId: null, release: '', users: '0', addUsers: '', source: 0, aggiunta: false, sel: {}, calc: null },
    customer: null, title: '', contactRef: '', discountPct: 0, optMaint: false, optServices: false, optSummary: false,
    xSel: { services: new Set(), options: {} },
    accountTravelRateId: null, activities: [], actDiscount: 0, actDetail: false, maintOnNet: true, profiles: [],
    distanceKm: null, nearestSede: '', distMethod: '', distFor: null, travelRateId: null, travelRates: [],
    terms: null, payTouched: { lic: false, fee: false, act: false },
    lines: [], step: 1, saved: null,
  };
  let catalog = [];
  let extrasCatalog = [];
  let termsCfg = null;
  let calc = null;
  const expanded = new Set();
  const filter = { text: '', family: '', onlySel: false };

  const famClass = (f) => { let h = 0; String(f || '').split('').forEach((c) => { h = (h * 31 + c.charCodeAt(0)) % 6; }); return 'f' + h; };
  const appOf = (id) => catalog.find((a) => a.id === id);
  const lineOf = (id) => S.lines.find((l) => l.applicationId === id);

  // ---------------------------------------------------------------- Power System: manutenzione SW/HW a periodi e importi
  const PW_PERIODS = [36, 48, 60];
  const amtKey = (m, kind) => m + '|' + kind;
  /** sezioni di importi richieste: per ogni periodo selezionato Power e, con LTO8, LTO8 */
  function maintSlots() {
    const slots = [];
    S.powerMaintList.slice().sort((a, b) => a - b).forEach((m) => { slots.push({ months: m, kind: 'POWER' }); if (S.powerLto8) slots.push({ months: m, kind: 'LTO8' }); });
    return slots;
  }
  function slotAmt(sl) {
    const v = S.powerMaintAmt[amtKey(sl.months, sl.kind)] || {};
    return { list: C.parseMoney(v.list), net: C.parseMoney(v.net) };
  }
  S.powerMaintLines = () => maintSlots().map((sl) => { const a = slotAmt(sl); return { months: sl.months, kind: sl.kind, listPrice: a.list, netPrice: a.net }; });
  S.powerMaintTotals = () => maintSlots().reduce((t, sl) => { const a = slotAmt(sl); return { list: t.list + (a.list || 0), net: t.net + (a.net || 0) }; }, { list: 0, net: 0 });
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
        S.powerLto8 = !!h.powerLto8; S.powerEth10 = !!h.powerEth10; S.powerUsers = h.powerIbmiUsers == null ? '' : String(h.powerIbmiUsers); S.powerUnlimited = !!h.powerIbmiUnlimited;
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
        if (rq.galileo) {
          const g = rq.galileo; S.gal = { profileId: g.profileId, release: g.release || 'V9', users: g.users || '', addUsers: g.addUsers || '', source: g.source || 0, aggiunta: !!g.aggiunta, sel: {}, calc: null };
          (g.selections || []).forEach((x) => { S.gal.sel[x.moduleId] = x.state; });
        }
        S.actDiscount = rq.activityDiscountPct || 0; S.actDetail = !!rq.optActivityDetail; S.maintOnNet = rq.maintOnNet !== false;
        S.activities = (rq.activities || []).map((a) => ({ appId: a.applicationId, desc: a.description, profileId: a.profileId, profileName: a.profileName, hours: a.hours, rate: a.rate, rfq: !!a.rfqImport, group: a.rfqGroup || '', days: a.days }));
        S.profileEdits = {}; (rq.profileRates || []).forEach((r) => { S.profileEdits[r.profileId] = Number(r.amount); });
        if (S.servicesMode !== 'RFQ') buildGroupsFromRows();
        S.distanceKm = rq.distanceKm; S.nearestSede = rq.nearestSede || ''; S.travelRateId = rq.travelRateId || null;
        if (S.distanceKm != null) S.distFor = h.customerId;
        S.lines = C.linesFromRequest(data.request);
        if (data.request.terms) { S.terms = Object.assign({}, data.request.terms); S.terms.billLicDate = S.terms.billLicDate || ''; S.terms.billFeeDate = S.terms.billFeeDate || ''; S.terms.payLicOther = S.terms.payLicOther || ''; S.terms.payFeeOther = S.terms.payFeeOther || ''; S.terms.payActOther = S.terms.payActOther || ''; S.payTouched = { lic: true, fee: true, act: true }; }
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
    syncServicesUi(); $('#opt-days').checked = S.optDays; $('#opt-apptot').value = S.appTotalsMode; $('#svc-detail').checked = S.actDetail; $('#opt-power').checked = S.optPower; $('#opt-smitech').checked = S.optSmitech; $('#opt-dept').checked = S.optDept;
    renderGalileo();
    $('#opt-maint-hint').hidden = S.optSummary || !S.optMaint; $('#row-apptot').hidden = !S.optSummary; $('#row-detail').hidden = !S.optSummary;
    updateNumber();
  }

  function updateNumber() {
    $('#f-number').value = C.offerNumber(S.date, S.progressive, S.initials);
  }

  function bindStep1() {
    $('#f-date').addEventListener('change', async (e) => {
      S.date = e.target.value; updateNumber();
      if (!S.offerId && S.date) {
        try {
          const r = await CPQ.api('GET', '/offers/api/next-progressive?date=' + encodeURIComponent(S.date));
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

  function renderCustomer() {
    refreshRates();
    applyDefaultPay();
    const box = $('#custbox'); const c = S.customer;
    if (!c) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML =
      '<dt>Codice</dt><dd><b>' + esc(c.code) + '</b> <span class="chip ' + (c.type === 'CLIENTE' ? 'ok' : 'blue') + '">' + (c.type === 'CLIENTE' ? 'Cliente' : 'Prospect') + '</span></dd>' +
      '<dt>Ragione sociale</dt><dd>' + esc(c.companyName) + '</dd>' +
      '<dt>Indirizzo</dt><dd>' + esc(c.streetType + ' ' + c.streetName + ', ' + c.streetNumber) + ' – ' + esc(c.postalCode + ' ' + c.city + (c.province ? ' (' + c.province.toUpperCase() + ')' : '')) + '</dd>' +
      '<dt>P.IVA / C.F.</dt><dd>' + esc(c.vatNumber) + (c.taxCode ? ' / ' + esc(c.taxCode) : '') + '</dd>';
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
  function renderGalileo() {
    const list = catalog.filter((a) => a.family === GALILEO);
    $('#sel-galileo').innerHTML = '<option value="">— seleziona —</option>' + list.map((a) => '<option value="' + a.id + '"' + (S.galileoId === a.id ? ' selected' : '') + '>' + esc(a.name) + '</option>').join('');
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

  function renderPower() {
    renderPowerProducts();
    refreshPowerModelList(false);
    const m = powerModelOf(S.powerModelId);
    $('#power-model-info').textContent = m ? m.type + ' · ' + m.cores + ' core · ' + (m.mounting === 'RACK' ? 'Rack' : 'Desk') + ' · ' + m.version + (m.processorGroup ? ' · gruppo processore ' + m.processorGroup : '') + (m.ghz ? ' · ' + m.ghz + ' GHz' : '') : '';
    renderPowerOptions();
    $('#pw-users').value = S.powerUsers; $('#pw-unlimited').checked = S.powerUnlimited; $('#pw-users').disabled = S.powerUnlimited;
    $('#pw-lto8').checked = S.powerLto8; $('#pw-eth10').checked = S.powerEth10;
    syncPowerUsers(); syncSmitechMode(false);
    $('#pw-cable').value = S.powerCable; $('#pw-edition').value = S.powerEdition; $('#pw-os').value = S.powerOs; $('#pw-disks').value = S.powerDisks; renderMaintBtn(); renderAmtSummary(); $('#pw-transfer').value = S.powerTransfer;
    $('#pw-tref').value = S.powerTransferRef; $('#pw-tref-box').hidden = S.powerTransfer === 'NO';
    $('#pw-cable-box').hidden = S.powerLto8;                  // con LTO8 il cavo di collegamento non si sceglie
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
      '<section class="pa-period"><h3>Manutenzione SW/HW – ' + m + ' mesi</h3>' + sub(m, 'POWER', 'Power') + (S.powerLto8 ? sub(m, 'LTO8', 'LTO8') : '') + '</section>').join('');
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
        CPQ.fieldError(bad, 'Manutenzione ' + m + ' mesi – ' + (kind === 'LTO8' ? 'LTO8' : 'Power') + ': indica il ' + (bad.dataset.f === 'list' ? 'prezzo di listino' : 'prezzo netto scontato'));
        return;
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
    const linked = galUsersLinked();
    if (linked) { S.powerUsers = String(Number(S.gal.users)); S.powerUnlimited = false; u.value = S.powerUsers; un.checked = false; }
    u.disabled = linked || S.powerUnlimited; un.disabled = linked;
    if (h) h.hidden = !linked;
    refreshPowerModelList(!!userChange);
    updatePowerLock();
  }

  function powerText() {
    const m = powerModelOf(S.powerModelId);
    if (!m) return 'Power System';
    const parts = ['Power System – ' + m.description + (m.processorGroup ? ' (gruppo ' + m.processorGroup + ')' : '')];
    if (S.powerRam) parts.push('RAM ' + S.powerRam + ' GB');
    if (S.powerDisk) parts.push(S.powerDisks + ' dischi da ' + S.powerDisk.replace('TB', ' TB'));
    if (S.powerLto8) parts.push('LTO8');
    if (S.powerEth10) parts.push('ethernet 10 Gbit');
    parts.push('manutenzione SW/HW ' + (S.powerMaintList.length ? S.powerMaintList.slice().sort((a, b) => a - b).join(', ') : S.powerMaint) + ' mesi');
    parts.push('IBM i ' + S.powerOs);
    if (S.powerCable) parts.push('cavo ' + S.powerCable);
    parts.push('edition ' + (S.powerEdition === 'EXPRESS' ? 'Express' : 'Solution'));
    if (S.powerTransfer !== 'NO') parts.push((S.powerTransfer === 'TRANSFER' ? 'Transfer' : 'Conversion') + (S.powerTransferRef.trim() ? ' da ' + S.powerTransferRef.trim() : ''));
    if (S.powerUnlimited) parts.push('utenti IBM i ILLIMITATI'); else if (S.powerUsers !== '') parts.push(CPQ.num(S.powerUsers, 0) + ' utenti IBM i');
    return parts.join(', ');
  }

  /** errori della configurazione Power System (vuoto se l'elemento non e' selezionato) */
  /** errori della pagina Power System, ciascuno con il campo da evidenziare */
  function powerErrorList() {
    if (!S.optPower) return [];
    const e = [];
    const add = (sel, msg) => e.push({ step: 7, kind: 'power', sel: sel, msg: msg });
    if (!S.powerModelId) add('#sel-power', 'Power System: scegli il modello');
    else {
      if (!S.powerRam) add('#pw-ram', 'Power System: scegli la RAM');
      if (!S.powerDisk) add('#pw-disk', 'Power System: scegli il disco');
      if (!(Number(S.powerDisks) >= 1)) add('#pw-disks', 'Power System: indica il numero di dischi');
      const noProd = S.ibmiProducts.find((p) => !S.powerProducts[p.id]);
      if (noProd) add('#pw-products select[data-pid="' + noProd.id + '"]', 'Power System: seleziona il valore di ogni prodotto IBM i');
      if (!S.powerMaintList.length) add('#pw-maint-btn', 'Power System: seleziona la manutenzione SW/HW');
      else if (!maintComplete()) add('#pw-amt-open', 'Power System: indica gli importi (listino e netto scontato) di ogni periodo di manutenzione SW/HW');
      if (S.powerTransfer !== 'NO' && !S.powerTransferRef.trim()) add('#pw-tref', 'Power System: indica i riferimenti del Power da cui trasferire le licenze');
      if (!S.powerUnlimited && !(Number(S.powerUsers) >= 1)) add('#pw-users', 'Power System: indica gli utenti IBM i licenziati o ILLIMITATI');
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
        if (needed && !C.driverIsFree(ap, d) && !((l.drivers[d.id] || 0) >= 1)) {
          add(2, 'users', '#apps-body .drv-in[data-drv="' + d.id + '"]', ap.name + ': indica il numero di ' + (d.unitLabel || d.label || 'utenti').toLowerCase(), () => { expanded.add(ap.id); });
        }
      });
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
    // modalita' di fatturazione e pagamento
    const TS = [['Fatturazione licenze', '#t-bill-lic-date'], ['Fatturazione canoni', '#t-bill-fee-date'],
      ['Pagamento licenze: "Solito', '#t-pay-lic'], ['Pagamento canoni: "Solito', '#t-pay-fee'], ['Pagamento attività sistemistiche: "Solito', '#t-pay-act'],
      ['Pagamento licenze: scrivi', '#t-pay-lic-other'], ['Pagamento canoni: scrivi', '#t-pay-fee-other'], ['Pagamento attività sistemistiche: scrivi', '#t-pay-act-other']];
    (termsCfg ? C.termsErrors(termsCfg, S.terms, custType()) : []).forEach((m) => {
      const t = TS.find((x) => m.indexOf(x[0]) === 0);
      add(4, 'terms', t ? t[1] : '#terms-body', m);
    });
    return out;
  }
  /** porta l'utente alla pagina dell'errore e mostra il popup vicino al campo */
  function revealError(err) {
    if (err.prep) err.prep();
    showStep(err.step);
    requestAnimationFrame(() => {
      const el = err.sel ? $(err.sel) : null;
      if (el) CPQ.fieldError(el, err.msg); else toast(err.msg, true);
    });
  }

  function go(n) {
    const order = orderAll(), idx = (k) => order.indexOf(k);
    const first = collectErrors().find((e) => (e.kind === 'base' && n > 1) || (e.kind === 'type' && idx(n) > idx(6))
      || (e.kind === 'power' && idx(n) > idx(7)) || (e.kind === 'gal' && idx(n) > idx(8)));
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
    const fams = Array.from(new Set(catalog.map((a) => a.family).filter((f) => f && f !== GALILEO)));
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
  }

  function appRow(a, line) {
    const modeSel = a.modes.length
      ? '<div class="mini"><select class="mode-sel" data-app="' + a.id + '"' + (line ? '' : ' disabled') + '>' +
        a.modes.map((m) => '<option value="' + esc(m.code) + '"' + (line && line.modeCode === m.code ? ' selected' : (!line && m.preferred ? ' selected' : '')) + '>' + esc(m.label) + '</option>').join('') +
        '</select></div>'
      : '<span class="muted">—</span>';
    const drv = a.drivers.length
      ? a.drivers.map((d) => {
        const v = line ? (line.drivers[d.id] || 0) : '';
        return '<div class="mini" style="margin-bottom:' + (a.drivers.length > 1 ? '4px' : '0') + '"><input type="number" min="0" step="1" class="drv-in" data-app="' + a.id + '" data-drv="' + d.id + '" value="' + v +
          '"' + (line ? '' : ' disabled') + ' title="' + esc(d.label) + '" aria-label="' + esc(d.label) + '"><label>' + esc(a.drivers.length > 1 ? d.label : d.unitLabel) + '</label></div>';
      }).join('')
      : '<span class="muted">non a utenti</span>';
    return '<tr class="app-row' + (line ? ' sel' : '') + '" data-app="' + a.id + '">' +
      '<td style="width:44px"><label class="check"><input type="checkbox" class="app-check" data-app="' + a.id + '"' + (line ? ' checked' : '') + ' aria-label="Seleziona ' + esc(a.name) + '"></label></td>' +
      '<td class="name"><div class="appname">' + esc(a.name) + '</div><div style="margin-top:3px"><span class="chip ' + famClass(a.family) + '">' + esc(a.family || '') + '</span>' +
      (a.needsReview ? ' <span class="chip warn" title="Logica del listino da verificare">da verificare</span>' : '') +
      ' <span class="muted">' + a.modules.filter((m) => m.section !== 'LINK').length + ' moduli</span></div></td>' +
      '<td>' + modeSel + '</td><td>' + drv + '</td>' +
      '<td class="num pr" data-amt="lic:' + a.id + '">' + (line ? '…' : '') + '</td>' +
      '<td class="num pr" data-amt="man:' + a.id + '">' + (line ? '…' : '') + '</td>' +
      '<td style="width:52px"><button type="button" class="btn icon sm toggle" data-app="' + a.id + '" aria-label="Dettaglio moduli"><svg class="ic" style="transform:rotate(' + (expanded.has(a.id) ? 180 : 0) + 'deg)"><use href="#i-expand"/></svg></button></td></tr>';
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

  function modRow(a, line, m) {
    const on = !!(line && line.modules[m.id] > 0);
    const price = C.modulePrice(m, line ? line.modeCode : (a.modes.find((x) => x.preferred) || a.modes[0] || {}).code);
    const kind = m.kind === 'ANNUAL' ? '<span class="chip">canone annuo</span>' : (m.kind === 'MONTHLY' ? '<span class="chip">canone mensile</span>' : (m.kind === 'ONETIME' ? '<span class="chip">una tantum</span>' : ''));
    const priceTxt = m.included ? '<span class="chip ok">incluso</span>' : CPQ.eur(price) + (m.onetimePrice ? ' <span class="muted">+ ' + CPQ.eur(m.onetimePrice) + ' att.</span>' : '');
    const qty = m.qtyEnabled && on ? '<input type="number" min="1" class="mod-qty qty" data-app="' + a.id + '" data-mod="' + m.id + '" value="' + line.modules[m.id] + '" style="width:64px;height:32px;border:1px solid var(--outline);border-radius:6px;padding:0 6px;text-align:right">' : '';
    const rules = (m.rules || []).map((r) => ruleChip(m, r, on)).join('');
    return '<tr class="' + (on ? 'on' : '') + (m.included ? ' dis' : '') + '"><td style="width:34px"><label class="check"><input type="checkbox" class="mod-check" data-app="' + a.id + '" data-mod="' + m.id + '"' +
      (on || m.included ? ' checked' : '') + (m.included ? ' disabled' : '') + ' aria-label="' + esc(m.code) + '"></label></td>' +
      '<td class="mono nowrap">' + esc(m.code) + '</td><td>' + esc(m.description) + (m.exclGroup ? ' <span class="chip" title="Alternativo agli altri moduli dello stesso gruppo">alternativo</span>' : '') +
      (m.note && !m.rules.length ? '<div class="hint">' + esc(m.note.length > 160 ? m.note.slice(0, 158) + '…' : m.note) + '</div>' : '') +
      (rules ? '<div class="rules">' + rules + '</div>' : '') + '</td>' +
      '<td class="num pr nowrap">' + priceTxt + ' ' + kind + '</td><td style="width:80px">' + qty + '</td></tr>';
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
    a.groups.forEach((g) => {
      const mods = a.modules.filter((m) => m.groupId === g.id && m.section !== 'LINK');
      if (mods.length) byGroup.push({ label: g.label + ' – il prezzo è moltiplicato dal numero di ' + ((a.drivers.find((d) => d.id === g.driverId) || {}).unitLabel || 'utenti'), mods: mods });
    });
    byGroup.forEach((bg) => { h += '<h4>' + esc(bg.label) + '</h4><table class="mods"><tbody>' + bg.mods.map((m) => modRow(a, line, m)).join('') + '</tbody></table>'; });
    const fixed = a.modules.filter((m) => m.groupId == null && m.section !== 'LINK');
    if (fixed.length) h += '<h4>Moduli a prezzo fisso</h4><table class="mods"><tbody>' + fixed.map((m) => modRow(a, line, m)).join('') + '</tbody></table>';
    const links = a.modules.filter((m) => m.section === 'LINK');
    if (links.length) h += '<h4>Moduli presenti in altri listini</h4><div class="hint">' + links.map((m) => '<b>' + esc(m.code) + '</b> ' + esc(m.description) + (m.linkText ? ' (' + esc(m.linkText) + ')' : '')).join(' · ') + '. Aggiungere l\'applicazione corrispondente all\'offerta.</div>';
    if (a.groups.length) h += '<div class="tiers">' + a.groups.map((g) => tiersText(a, g, mode)).join('<br>') + '</div>';
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
    body.addEventListener('input', (e) => {
      const t = e.target; const id = Number(t.dataset.app);
      if (t.classList.contains('drv-in')) { lineOf(id).drivers[Number(t.dataset.drv)] = Math.max(0, parseInt(t.value || '0', 10) || 0); scheduleCalc(); }
      else if (t.classList.contains('mod-qty')) { lineOf(id).modules[Number(t.dataset.mod)] = Math.max(1, parseInt(t.value || '1', 10) || 1); scheduleCalc(); }
    });
    body.addEventListener('click', (e) => {
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
    clearTimeout(calcTimer);
    calcTimer = setTimeout(async () => {
      const seq = ++calcSeq;
      try {
        const res = await CPQ.api('POST', '/offers/api/calc', C.buildRequest(S));
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
    const nl = r2(tl * (1 - d / 100)); const nm = S.maintOnNet ? r2(tm * (1 - d / 100)) : tm;
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
    const hasDisc = n.d > 0;
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
      '</div>';
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
      else return;
      renderTerms(); refreshSaveState();
    });
    box.addEventListener('input', (e) => {
      const id = e.target.id; const t = S.terms; const v = e.target.value;
      if (id === 't-pay-lic-other') { t.payLicOther = v; S.payTouched.lic = true; }
      else if (id === 't-pay-fee-other') { t.payFeeOther = v; S.payTouched.fee = true; }
      else if (id === 't-pay-act-other') { t.payActOther = v; S.payTouched.act = true; }
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
    if (document.activeElement !== $('#svc-disc')) $('#svc-disc').value = S.actDiscount || '';
    if (document.activeElement !== $('#sum-disc')) $('#sum-disc').value = S.discountPct || '';
    const rows = S.lines.map((l, i) => {
      const a = appOf(l.applicationId); const lc = calc ? calc.lines[i] : null;
      const mods = Object.keys(l.modules).map((id) => { const m = a.modules.find((x) => x.id === Number(id)); return m ? '<div><span class="mono">' + esc(m.code) + '</span> ' + esc(m.description) + (l.modules[id] > 1 ? ' <b>× ' + l.modules[id] + '</b>' : '') + '</div>' : ''; }).join('');
      const seats = a.drivers.filter((d) => l.drivers[d.id] > 0).map((d) => esc(d.label) + ': <b>' + CPQ.num(l.drivers[d.id], 0) + '</b>').join(' · ');
      const mode = a.modes.length ? '<div class="hint">' + esc((a.modes.find((m) => m.code === l.modeCode) || {}).label || '') + '</div>' : '';
      return '<tr><td><b>' + esc(a.name) + '</b>' + mode + '</td><td>' + mods + '</td><td>' + seats + '</td><td class="num pr">' + (lc ? CPQ.eur(lc.licenseTotal) : '') + '</td><td class="num pr">' + (lc ? CPQ.eur(lc.maintenance) : '') + '</td></tr>';
    }).join('');
    const gt = galileoTotals();
    const galRow = stepVisible(8) && S.gal.calc && S.gal.calc.result && S.gal.calc.result.valid
      ? '<tr><td><b>Galileo</b><div class="hint">' + esc(galProfileLabel()) + '</div></td><td>' + esc(galSelectedText()) + '</td><td>Utenti: <b>' + CPQ.num(Number(S.gal.users) || 0, 0) + '</b></td><td class="num pr">' + CPQ.eur(gt.list) + '</td><td class="num pr">' + CPQ.eur(gt.maint) + '</td></tr>' : '';
    $('#sum-body').innerHTML = galRow + rows;                                // Galileo, se presente, e' sempre la prima riga
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
    $('#opt-services-on').addEventListener('change', (e) => applyServicesMode(e.target.checked ? (lastServicesMode || 'MANUAL') : 'NONE'));
    $('#sel-svc-mode').addEventListener('change', (e) => applyServicesMode(e.target.value));
    $('#opt-days').addEventListener('change', (e) => { S.optDays = e.target.checked; });
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
      S.actDiscount = v; updateServiceTotals(); updateSummaryTotals();
    });
    $('#svc-detail').addEventListener('change', (e) => { S.actDetail = e.target.checked; });
    $('#btn-back5').addEventListener('click', () => go(prevStep(5)));
    $('#btn-next5').addEventListener('click', () => go(nextStep(5)));
  }

  // ---------------------------------------------------------------- Galileo: listino JGalileo per mercato e scenario
  const G = { info: { markets: [] }, profile: null, rules: [], timer: null, seq: 0, collapsed: new Set(), loading: false };
  const GAL_KIND = { NEW: 'Nuova configurazione', CONS: 'Consociata', MIG: 'Migrazione', MIGCONS: 'Migrazione consociata', IMMOB: 'Immobiliare (fino a 3 utenti)' };
  const galMarket = () => (G.info.markets || []).find((m) => m.applicationId === S.galileoId);
  /** la pagina Galileo c'e' solo se e' scelta una tipologia che ha un listino configurato */
  function galHasListino() { return !!(S.optGalileo && S.galileoId && galMarket()); }
  const galProfileLabel = () => (G.profile ? (GAL_KIND[G.profile.kind] || G.profile.label) : '');
  const galIsMig = () => !!G.profile && (G.profile.kind === 'MIG' || G.profile.kind === 'MIGCONS');
  const galDate = (iso) => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || ''); return m ? m[3] + '/' + m[2] + '/' + m[1] : (iso || ''); };

  function galReset() {
    S.gal = { profileId: null, release: '', users: '0', addUsers: '', source: 0, aggiunta: false, sel: {}, calc: null };
    G.profile = null; G.rules = [];
  }

  /** all'apertura del passo: carica il profilo (scenario predefinito = nuova configurazione) e disegna la pagina */
  async function galEnter() {
    const m = galMarket(); if (!m) return;
    $('#gal-market').textContent = m.name;
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
    if (kind === 'NEW') {
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
    $('#gal-release-box').hidden = !P.cfgV11;
    galFill($('#gal-release'), [['', '— non selezionato —'], ['V9', 'V9'], ['V11', 'V11']], S.gal.release);
    $('#gal-users').value = S.gal.users;
    $('#gal-users-help').textContent = P.kind === 'IMMOB' ? 'da 1 a ' + P.maxUsers + ' utenti' : '';
    $('#gal-source-box').hidden = !galIsMig();
    galFill($('#gal-source'), [[0, '— seleziona —'], [1, 'Carattere'], [2, 'Visuale'], [3, 'V9']], S.gal.source || 0);
    $('#gal-add-box').hidden = !galIsMig(); $('#gal-addusers').value = S.gal.addUsers;
    $('#gal-agg-box').hidden = !P.aggiuntaSupported; $('#gal-agg').checked = !!S.gal.aggiunta;
    $('#gal-mods-help').textContent = P.kind === 'NEW' ? 'Spunta le funzioni aggiuntive (FA); i moduli base (CB) sono compresi nella configurazione.'
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
    const viol = new Set(((S.gal.calc && S.gal.calc.violations) || []).map((v) => v.moduleCode));
    $('#gal-mods').innerHTML = groups.map((g) => {
      const rows = g.mods.filter((m) => (!q || (m.code + ' ' + m.description).toLowerCase().includes(q)) && (!only || galState(m)));
      if (!rows.length) return '';
      const closed = G.collapsed.has(g.area) && !q;
      const nsel = g.mods.filter((m) => galState(m) && m.flag !== 'CB' && m.flag !== 'V11').length;
      return '<div class="gal-area" data-area="' + esc(g.area) + '"><span>' + (closed ? '▸' : '▾') + '</span><span class="grow">' + esc(g.area) + '</span><span class="cnt">' + g.mods.length + ' moduli' + (nsel ? ' · ' + nsel + ' selezionati' : '') + '</span></div>' +
        (closed ? '' : '<div class="tablewrap"><table class="t gal-t"><tbody>' + rows.map((m) => {
          const st = galState(m);
          return '<tr data-m="' + m.moduleId + '" class="' + (st && m.flag !== 'CB' && m.flag !== 'V11' ? 'sel' : '') + '"><td style="width:150px">' + galStateCtl(m) + '</td><td class="mono"><b>' + esc(m.code) + '</b></td><td>' + esc(m.description) +
            (m.flag === 'CB' ? ' <span class="chip">base</span>' : '') + (viol.has(m.code) ? ' <span class="chip err">vincolo non rispettato</span>' : '') +
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
    const c = S.gal.calc, need = Number(S.gal.users) >= 1;
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
    if (!g.profileId) return [{ step: 8, kind: 'gal', sel: '#gal-kind', msg: 'Galileo: scegli lo scenario' }];
    if (G.profile && G.profile.id === g.profileId && G.profile.cfgV11 && !g.release) return [{ step: 8, kind: 'gal', sel: '#gal-release', msg: 'Galileo: seleziona la release (V9 o V11)' }];
    if (!(Number(g.users) >= 1)) return [{ step: 8, kind: 'gal', sel: '#gal-users', msg: 'Galileo: indica il numero di utenti' }];
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
      const users = on && drv ? '<div class="mini" style="margin-top:4px"><input type="number" min="0" step="1" class="sm-drv" data-drv="' + drv.id + '" value="' + (line.drivers[drv.id] || 0) + '" aria-label="' + esc(drv.label) + '" style="width:90px"><label>' + esc(drv.unitLabel) + '</label></div>' : '';
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
        if (line && !Object.keys(line.modules).some((k) => line.modules[k] > 0)) S.lines = S.lines.filter((l) => l !== line);          // nessun servizio: l'applicazione esce dall'offerta
        renderSmitech(); scheduleCalc(); CPQ.markDirty('page', root); refreshStepper(); refreshSaveState();
      }
    });
    body.addEventListener('input', (e) => {
      const d = e.target.closest('.sm-drv'); if (!d) return;
      const app = smitechApp(), line = app && lineOf(app.id); if (!line) return;
      line.drivers[Number(d.dataset.drv)] = Math.max(0, parseInt(d.value || '0', 10) || 0); scheduleCalc(); CPQ.markDirty('page', root);
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
    const terr = termsCfg ? C.termsErrors(termsCfg, S.terms, custType()) : [];
    const aerr = C.activityErrors(S.activities, S.lines);
    $('#btn-save').disabled = false;                // gli errori si segnalano sul campo, con un popup, al click
    $('#btn-save-docx').disabled = root.dataset.docx === 'false';
    $('#sum-blockers').textContent = errs.length ? 'Risolvi i vincoli in rosso prima di salvare.' : aerr.concat(powerErrors(), galileoErrors(), terr).join(' · ');
  }

  /** tabella totali, netto e margine del riepilogo: si aggiornano mentre si digita lo sconto */
  function updateSummaryTotals() {
    const n = nets();
    let foot = '<tr><td colspan="3" class="right"><b>Totale</b></td><td class="num pr"><b>' + CPQ.eur(n.tl) + '</b></td><td class="num pr"><b>' + CPQ.eur(n.tm) + '</b></td></tr>';
    if (n.d > 0) foot += '<tr><td colspan="3" class="right"><b>Netto a voi riservato</b> (sconto ' + n.d + '%)</td><td class="num pr"><b>' + CPQ.eur(n.nl) + '</b></td><td class="num pr"><b>' + CPQ.eur(n.nm) + '</b></td></tr>';
    $('#sum-foot').innerHTML = foot;
    $('#n-lic').textContent = CPQ.eur(n.nl); $('#n-man').textContent = CPQ.eur(n.nm);
    $('#n-lic-was').textContent = n.d > 0 ? CPQ.eur(n.tl) : ''; $('#n-man-was').textContent = n.d > 0 ? CPQ.eur(n.tm) : '';
    $('#n-margin').textContent = CPQ.eur(n.margin);
    const sat = C.activityTotals(S.activities, S.lines, S.actDiscount);
    $('#sum-act-list').textContent = CPQ.eur(sat.list); $('#sum-act-net').textContent = CPQ.eur(sat.net);
    $('#sum-act-save').textContent = CPQ.eur(r2(sat.list - sat.net));
    $('#sum-act-hours').textContent = sat.hours ? CPQ.num(sat.hours) + ' ore' : '';
    $('#sum-act-none').hidden = sat.list > 0;
    // importi del Power System (solo listino e netto)
    $('#sum-pw-card').hidden = !S.optPower;
    if (S.optPower) {
      const pt = S.powerMaintTotals(), pl = maintComplete() ? pt.list : null, pn = maintComplete() ? pt.net : null, pm = powerModelOf(S.powerModelId);
      $('#sum-pw-list').textContent = pl == null ? '—' : CPQ.eur(pl); $('#sum-pw-net').textContent = pn == null ? '—' : CPQ.eur(pn);
      $('#sum-pw-model').textContent = pm ? pm.description : '';
      const rowsP = maintComplete() ? maintSlots().map((sl) => { const am = slotAmt(sl); return '<tr><td>' + sl.months + ' mesi</td><td>' + (sl.kind === 'LTO8' ? 'LTO8' : 'Power') + '</td><td class="num">' + CPQ.eur(am.list) + '</td><td class="num">' + CPQ.eur(am.net) + '</td></tr>'; }) : [];
      $('#sum-pw-periods').innerHTML = rowsP.join(''); $('#sum-pw-periods-wrap').hidden = !rowsP.length;
      $('#sum-pw-disc').textContent = pl > 0 && pn != null ? 'sconto ' + CPQ.num((1 - pn / pl) * 100, 1) + ' %' : '';
    }
    updateAmounts();
  }

  async function save(thenDocx) {
    const firstErr = collectErrors()[0];
    if (firstErr) { revealError(firstErr); return; }
    const title = $('#sum-title').value.trim();
    S.title = title || C.suggestTitle(catalog, S.lines);
    try {
      const r = await CPQ.api('POST', '/offers/api/save', C.buildRequest(S));
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
    if (!S.optPower) { S.powerModelId = null; S.powerRam = ''; S.powerDisk = ''; renderPower(); }
    refreshStepper(); syncSmitechMode(true);
  });
  $('#sel-power').addEventListener('change', (e) => { S.powerModelId = e.target.value ? Number(e.target.value) : null; renderPower(); refreshStepper(); syncSmitechMode(true); });
  $('#pw-ram').addEventListener('change', (e) => { S.powerRam = e.target.value; updatePowerLock(); });
  $('#pw-disks').addEventListener('input', (e) => { S.powerDisks = e.target.value.replace(/\D/g, ''); updatePowerLock(); });
  $('#pw-disk').addEventListener('change', (e) => { S.powerDisk = e.target.value; updatePowerLock(); });
  $('#pw-users').addEventListener('input', (e) => { const v = e.target.value.replace(/\D/g, ''); S.powerUsers = v; refreshPowerModelList(true); updatePowerLock(); });
  $('#pw-unlimited').addEventListener('change', (e) => { S.powerUnlimited = e.target.checked; setTimeout(updatePowerLock, 0); $('#pw-users').disabled = S.powerUnlimited; if (S.powerUnlimited) { S.powerUsers = ''; $('#pw-users').value = ''; } refreshPowerModelList(true); });
  // LTO8 e cavo di collegamento (LTO6/LTO7) sono alternativi: scegliendone uno l'altro si disattiva
  $('#pw-cable').addEventListener('change', (e) => { S.powerCable = e.target.value; if (S.powerCable) { S.powerLto8 = false; $('#pw-lto8').checked = false; renderAmtSummary(); } });
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
      S.powerTransferRef = ''; $('#pw-tref').value = '';
      S.ibmiProducts.forEach((p) => { delete S.powerProducts[p.id]; });          // di nuovo «— seleziona —»
      renderPowerProducts(); refreshSaveState();
    } else {
      // Transfer / Conversion: tutti i prodotti IBM i risultano «Migrato» (modificabile prodotto per prodotto)
      S.ibmiProducts.forEach((p) => { S.powerProducts[p.id] = 'MIGRATO'; });
      renderPowerProducts(); refreshSaveState();
      $('#pw-tref').focus();
    }
  });
  $('#pw-tref').addEventListener('input', (e) => { S.powerTransferRef = e.target.value; });
  $('#pw-lto8').addEventListener('change', (e) => { S.powerLto8 = e.target.checked; $('#pw-cable-box').hidden = S.powerLto8; if (S.powerLto8) { S.powerCable = ''; $('#pw-cable').value = ''; } renderAmtSummary(); refreshSaveState(); });
  $('#pw-eth10').addEventListener('change', (e) => { S.powerEth10 = e.target.checked; });
  $('#opt-galileo').addEventListener('change', (e) => {
    S.optGalileo = e.target.checked; $('#galileo-box').hidden = !S.optGalileo; syncPowerUsers();
    if (!S.optGalileo) { S.galileoId = null; $('#sel-galileo').value = ''; galReset(); }
    refreshStepper();
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
  $('#sel-galileo').addEventListener('change', (e) => { S.galileoId = e.target.value ? Number(e.target.value) : null; galReset(); refreshStepper(); });
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
  $('#btn-save-docx').addEventListener('click', () => save(true));
  $('#travel-rate').addEventListener('change', (e) => { S.travelRateId = e.target.value ? Number(e.target.value) : null; });
  $('#dist-refresh').addEventListener('click', () => loadDistance(true));
  $('#sum-title').addEventListener('input', (e) => { S.title = e.target.value; });
  [['opt-maint', 'optMaint'], ['opt-services', 'optServices'], ['opt-summary', 'optSummary']].forEach(([id, key]) => {
    $('#' + id).addEventListener('change', (e) => {
      S[key] = e.target.checked; $('#opt-maint-hint').hidden = S.optSummary || !S.optMaint; $('#row-apptot').hidden = !S.optSummary; $('#row-detail').hidden = !S.optSummary;
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
