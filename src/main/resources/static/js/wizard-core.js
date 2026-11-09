/* Logica pura del wizard "Nuova offerta" (senza DOM, testabile con node: tools/test_wizard_core.js) */
(function (root) {
  'use strict';

  /** prezzo del modulo nella modalita' indicata (fallback STD) */
  function modulePrice(m, mode) {
    const p = m.prices || {};
    if (mode && Object.prototype.hasOwnProperty.call(p, mode)) return p[mode] || 0;
    return p.STD || 0;
  }

  /** numero offerta: YYYYMMDD.<progressivo><iniziali>  (es. 20261022.3ER) */
  function offerNumber(dateIso, progressive, initials) {
    if (!dateIso) return '';
    const d = dateIso.replace(/-/g, '');
    return d + '.' + (progressive || '') + (initials || '').toUpperCase();
  }

  /** gruppo di prezzo (con base fissa?) per driver */
  function driverIsFree(app, driver) {
    // un driver "libero" (macchine, visualizzatori, utenti User) ha base fissa o calcolo lineare: parte da 0
    const groups = app.groups.filter((g) => g.driverId === driver.id);
    if (!groups.length) return true;
    return groups.every((g) => g.baseMode === 'LINEAR' || (g.fixedBase && Object.keys(g.fixedBase).length > 0));
  }

  /** stato iniziale di una riga quando si seleziona un'applicazione */
  function newLine(app) {
    const line = { applicationId: app.id, modeCode: null, drivers: {}, modules: {} };
    const dm = app.modes.find((m) => m.preferred) || app.modes[0];
    line.modeCode = dm ? dm.code : null;
    app.drivers.forEach((d) => { line.drivers[d.id] = 0; });          // utenti e macchine partono da 0: i campi obbligatori vanno compilati
    app.modules.forEach((m) => {
      if (m.defaultSelected && m.section !== 'LINK' && !m.included) line.modules[m.id] = 1;
    });
    return line;
  }

  function codesOf(targetCodes) {
    return (targetCodes || '').split(',').map((s) => s.trim()).filter(Boolean);
  }

  /** seleziona/deseleziona un modulo: gestisce alternative (excl_group) e prerequisiti dello stesso listino */
  function setModule(app, line, moduleId, on, qty) {
    const m = app.modules.find((x) => x.id === moduleId);
    if (!m || m.section === 'LINK' || m.included) return [];
    const added = [];
    if (!on) { delete line.modules[moduleId]; return added; }
    line.modules[moduleId] = m.qtyEnabled ? Math.max(1, qty || line.modules[moduleId] || 1) : 1;
    if (m.exclGroup) {
      app.modules.forEach((o) => {
        if (o.id !== m.id && o.exclGroup === m.exclGroup && line.modules[o.id]) delete line.modules[o.id];
      });
    }
    // prerequisiti obbligatori dello stesso listino: selezione automatica (ricorsiva)
    (m.rules || []).forEach((r) => {
      if (r.type !== 'REQUIRES' || r.severity !== 'ERROR') return;
      codesOf(r.targetCodes).forEach((code) => {
        const target = app.modules.find((x) => x.code === code && x.section !== 'LINK' && !x.included);
        if (target && !line.modules[target.id]) {
          added.push(target.code);
          setModule(app, line, target.id, true, 1).forEach((c) => added.push(c));
        }
      });
    });
    return added;
  }


  // ------------------------------------------------------------------ dipendenze tra moduli (anche tra applicazioni)
  function isCodeSelected(catalog, lines, code) {
    return lines.some((l) => {
      const a = catalog.find((x) => x.id === l.applicationId);
      return a && a.modules.some((m) => m.code === code && m.section !== 'LINK' && l.modules[m.id] > 0);
    });
  }

  /** applicazioni che offrono il modulo come voce vera (non come semplice rimando ad altro listino) */
  function providers(catalog, code) {
    const res = [];
    catalog.forEach((a) => {
      const m = a.modules.find((x) => x.code === code && x.section !== 'LINK' && !x.included);
      if (m) res.push({ app: a, module: m });
    });
    return res;
  }

  /**
   * Seleziona/deseleziona un modulo risolvendo le dipendenze obbligatorie (REQUIRES bloccanti):
   *  - prerequisiti nello stesso listino: selezionati in automatico (a catena);
   *  - prerequisiti in un'altra applicazione: se l'applicazione e' gia' in offerta si seleziona li';
   *    altrimenti, se un'unica applicazione li fornisce, viene aggiunta all'offerta;
   *    se piu' applicazioni li forniscono (es. SMIWPS4) non se ne sceglie una a caso: lo si segnala;
   *  - deselezionando un modulo si deselezionano anche quelli che lo richiedono (stesso listino).
   * Modifica `lines` sul posto e restituisce { addedModules, addedApps, removed, ambiguous }.
   */
  function selectModule(catalog, lines, appId, moduleId, on) {
    const out = { addedModules: [], addedApps: [], removed: [], ambiguous: [] };
    const app0 = catalog.find((a) => a.id === appId);
    const line0 = lines.find((l) => l.applicationId === appId);
    if (!app0 || !line0) return out;
    if (!on) { removeWithDependents(app0, line0, moduleId, out); return out; }

    const queue = [{ app: app0, line: line0, moduleId: moduleId }];
    const seen = new Set();
    while (queue.length) {
      const cur = queue.shift();
      const key = cur.app.id + ':' + cur.moduleId;
      if (seen.has(key)) continue;
      seen.add(key);
      const wasOn = !!cur.line.modules[cur.moduleId];
      const sameApp = setModule(cur.app, cur.line, cur.moduleId, true);
      sameApp.forEach((code) => {
        out.addedModules.push(code);
        const m = cur.app.modules.find((x) => x.code === code && cur.line.modules[x.id]);
        if (m) queue.push({ app: cur.app, line: cur.line, moduleId: m.id });
      });
      const mod = cur.app.modules.find((x) => x.id === cur.moduleId);
      (mod.rules || []).forEach((r) => {
        if (r.type !== 'REQUIRES' || r.severity !== 'ERROR') return;
        codesOf(r.targetCodes).forEach((code) => {
          if (isCodeSelected(catalog, lines, code)) return;
          const prov = providers(catalog, code);
          const inOffer = prov.find((p) => lines.some((l) => l.applicationId === p.app.id));
          let pick = inOffer;
          if (!pick) {
            if (prov.length === 1) pick = prov[0];
            else { if (prov.length) out.ambiguous.push({ code: code, apps: prov.map((p) => p.app.name) }); return; }
          }
          let pl = lines.find((l) => l.applicationId === pick.app.id);
          if (!pl) { pl = newLine(pick.app); lines.push(pl); out.addedApps.push(pick.app.name); }
          if (pick.app.id !== cur.app.id) out.addedModules.push(code);
          queue.push({ app: pick.app, line: pl, moduleId: pick.module.id });
        });
      });
      if (!wasOn && cur.moduleId !== moduleId) { /* gia' registrato sopra */ }
    }
    out.addedModules = Array.from(new Set(out.addedModules));
    return out;
  }

  function removeWithDependents(app, line, moduleId, out) {
    const m = app.modules.find((x) => x.id === moduleId);
    if (!m || !line.modules[moduleId]) return;
    delete line.modules[moduleId];
    out.removed.push(m.code);
    app.modules.forEach((d) => {
      if (!line.modules[d.id]) return;
      const needs = (d.rules || []).some((r) => r.type === 'REQUIRES' && r.severity === 'ERROR' && codesOf(r.targetCodes).includes(m.code));
      if (needs) removeWithDependents(app, line, d.id, out);
    });
  }

  // ------------------------------------------------------------------ modalita' di fatturazione e pagamento (offer-terms.json)
  /** predefiniti: pagamento RI.BA. 30 gg dffm per un prospect, "solito in uso" per un cliente */
  function defaultTerms(cfg, customerType) {
    const pay = customerType === 'CLIENTE' ? cfg.defaults.payCustomer : cfg.defaults.payProspect;
    return { billLic: cfg.defaults.billLic, billLicDate: '', billFee: cfg.defaults.billFee, billFeeDate: '', feeSemiannual: false,
      billAct: cfg.defaults.billAct, payLic: pay, payLicOther: '', payFee: pay, payFeeOther: '', payAct: pay, payActOther: '' };
  }

  const payDefault = (cfg, type) => (type === 'CLIENTE' ? cfg.defaults.payCustomer : cfg.defaults.payProspect);

  /** codici di pagamento ammessi per categoria ('lic' | 'fee' | 'act'); "Solito in uso" solo per i clienti, mai per i prospect */
  function allowedPay(cfg, cat, billAct, type) {
    const list = cat === 'lic' ? cfg.payLic : (cat === 'fee' ? cfg.payFee : (cfg.payAct[billAct] || []));
    return type === 'CLIENTE' ? list : list.filter((c) => c !== 'USUAL');
  }

  /** riporta al predefinito (o alla prima opzione ammessa) i pagamenti non piu' validi: cambio di fatturazione o di tipo account */
  function normalizePay(cfg, t, type) {
    [['lic', 'payLic', 'payLicOther'], ['fee', 'payFee', 'payFeeOther'], ['act', 'payAct', 'payActOther']].forEach(([cat, k, other]) => {
      const al = allowedPay(cfg, cat, t.billAct, type);
      if (!al.includes(t[k])) {
        const d = payDefault(cfg, type);
        t[k] = al.includes(d) ? d : al.find((c) => c !== 'OTHER');
        t[other] = '';
      }
    });
    return t;
  }

  /** dopo un cambio di fatturazione delle attivita': se il pagamento scelto non e' piu' ammesso torna al predefinito */
  function fixActPay(cfg, t, type) {
    return normalizePay(cfg, t, type);
  }

  /**
   * Riapplica i pagamenti predefiniti in base al tipo di account (prospect = RI.BA. 30 gg dffm, cliente = solito in uso),
   * ma solo per le righe che l'utente non ha modificato a mano (touched: { lic, fee, act }).
   */
  function applyDefaultPay(cfg, t, customerType, touched) {
    const d = defaultTerms(cfg, customerType);
    if (!touched.lic) { t.payLic = d.payLic; t.payLicOther = ''; }
    if (!touched.fee) { t.payFee = d.payFee; t.payFeeOther = ''; }
    if (!touched.act) { t.payAct = d.payAct; t.payActOther = ''; }
    return normalizePay(cfg, t, customerType);
  }

  function termsErrors(cfg, t, type) {
    const e = [];
    const bill = (list, code) => list.find((o) => o.code === code);
    const lic = bill(cfg.licBill, t.billLic); if (lic && lic.date && !t.billLicDate) e.push('Fatturazione licenze: indica la data');
    const fee = bill(cfg.feeBill, t.billFee); if (fee && fee.date && !t.billFeeDate) e.push('Fatturazione canoni: indica la data');
    [['lic', 'licenze', t.payLic, t.payLicOther], ['fee', 'canoni', t.payFee, t.payFeeOther], ['act', 'attività sistemistiche', t.payAct, t.payActOther]].forEach(([cat, name, code, other]) => {
      if (code === 'USUAL' && type !== 'CLIENTE') e.push('Pagamento ' + name + ': "Solito in uso" non è ammesso per un prospect');
      const o = cfg.pay.find((x) => x.code === code);
      if (o && o.free && !(other || '').trim()) e.push('Pagamento ' + name + ': scrivi la modalità');
    });
    return e;
  }

  /** richiesta per il server (calcolo/salvataggio) */
  function buildRequest(state) {
    const lines = state.lines.map((l) => ({
      applicationId: l.applicationId,
      modeCode: l.modeCode,
      drivers: l.drivers,
      modules: Object.keys(l.modules).map((id) => ({ moduleId: Number(id), qty: l.modules[id] })),
    }));
    return {
      id: state.offerId || null,
      offerDate: state.date,
      progressive: state.progressive ? Number(state.progressive) : null,
      customerId: state.customer ? state.customer.id : null,
      projectTitle: state.title || null,
      contactRef: state.contactRef || null,
      discountPct: Number(state.discountPct || 0),
      terms: state.terms ? Object.assign({}, state.terms, { billLicDate: state.terms.billLicDate || null, billFeeDate: state.terms.billFeeDate || null }) : null,
      optMaintenanceAdditional: !!state.optMaint,
      optExtraServices: !!state.optServices,
      optFinalSummary: !!state.optSummary,
      extras: state.optServices && state.xSel ? {
        services: Array.from(state.xSel.services),
        options: Object.keys(state.xSel.options).map((id) => ({ optionId: Number(id), qty: state.xSel.options[id] })),
      } : null,
      distanceKm: state.distanceKm == null || state.distanceKm === '' ? null : Number(state.distanceKm),
      nearestSede: state.nearestSede || null,
      travelRateId: state.travelRateId ? Number(state.travelRateId) : null,
      activities: (state.servicesMode === 'NONE' ? [] : activityRows(state.activities || [], state.lines)).map((a) => ({ applicationId: a.appId, description: a.desc.trim(), profileId: Number(a.profileId), hours: parseHours(a.hours), rfqImport: !!a.rfq, rfqGroup: (a.rfq || a.gid) ? (a.group || null) : null, days: a.rfq && a.days != null && a.days !== '' ? Number(a.days) : null })),
      activityDiscountPct: Number(state.actDiscount || 0),
      activityDiscountUntil: Number(state.actDiscount || 0) > 0 && state.actUntil ? state.actUntil : null,
      optActivityDetail: !!state.actDetail,
      optHoursDetail: !!state.hoursDetail,
      maintOnNet: state.maintOnNet !== false,
      optPowerSystem: !!state.optPower,
      powerModelId: state.optPower ? (state.powerModelId || null) : null,
      powerRamGb: state.optPower && state.powerRam ? Number(state.powerRam) : null,
      powerDisk: state.optPower && state.powerDisk ? state.powerDisk : null,
      powerCable: state.optPower && state.powerCable ? state.powerCable : null,
      powerTransfer: state.optPower ? (state.powerTransfer || 'NO') : 'NO',
      powerTransferRef: state.optPower && state.powerTransfer && state.powerTransfer !== 'NO' ? (state.powerTransferRef || '') : null,
      powerEdition: state.optPower ? (state.powerEdition || 'SOLUTION') : 'SOLUTION',
      powerOs: state.optPower ? (state.powerOs || '7.5') : '7.5',
      powerMaintMonths: state.optPower ? Number((state.powerMaintList && state.powerMaintList.length ? Math.min.apply(null, state.powerMaintList) : state.powerMaint) || 36) : 36,
      powerMaint: state.optPower ? (state.powerMaintLines ? state.powerMaintLines(state) : []) : [],
      powerDiskCount: state.optPower ? Math.max(1, Number(state.powerDisks || 2)) : 2,
      powerProducts: state.optPower ? Object.keys(state.powerProducts || {}).filter((k) => state.powerProducts[k]).map((k) => ({ productId: Number(k), value: state.powerProducts[k] })) : [],
      powerListPrice: state.optPower && state.powerMaintTotals ? state.powerMaintTotals(state).list : null,
      powerNetPrice: state.optPower && state.powerMaintTotals ? state.powerMaintTotals(state).net : null,
      powerLto8: !!(state.optPower && state.powerLto8),
      powerEth10: !!(state.optPower && state.powerEth10),
      powerIbmiUsers: state.optPower && !state.powerUnlimited && state.powerUsers !== '' && state.powerUsers != null ? Number(state.powerUsers) : null,
      powerIbmiUnlimited: !!(state.optPower && state.powerUnlimited),
      galileoApplicationId: state.galileoId || null,
      galileo: (state.optGalileo && state.galileoId && state.gal && state.gal.profileId) ? {
        profileId: state.gal.profileId, release: state.gal.release || '', users: Number(state.gal.users) || 0, addUsers: Number(state.gal.addUsers) || 0,
        source: Number(state.gal.source) || 0, aggiunta: !!state.gal.aggiunta,
        selections: Object.keys(state.gal.sel || {}).filter((k) => state.gal.sel[k]).map((k) => ({ moduleId: Number(k), state: state.gal.sel[k] })),
      } : null,
      optSmitechServices: !!state.optSmitech,
      optDepartmental: state.optDept !== false,
      optUseRfq: state.servicesMode === 'RFQ',
      servicesMode: state.servicesMode || 'MANUAL',
      profileRates: Object.keys(state.profileEdits || {}).map((k) => ({ profileId: Number(k), amount: Number(state.profileEdits[k]) })),
      optServicesDays: state.optDays !== false,
      appTotalsMode: state.optSummary ? (state.appTotalsMode || 'NO') : 'NO',
      rfqSheetTotal: state.servicesMode === 'RFQ' && state.rfqSheetTotal != null ? Number(state.rfqSheetTotal) : null,
      lines: lines,
    };
  }

  /** titolo suggerito: "Progetto <app1> - <app2>" */
  function suggestTitle(catalog, lines) {
    const names = lines.map((l) => (catalog.find((a) => a.id === l.applicationId) || {}).name).filter(Boolean);
    return names.length ? 'Progetto ' + names.join(' - ') : '';
  }

  /** ricostruisce lo stato righe da una richiesta salvata */
  function linesFromRequest(req) {
    return (req.lines || []).map((l) => {
      const mods = {};
      (l.modules || []).forEach((m) => { mods[m.moduleId] = m.qty; });
      return { applicationId: l.applicationId, modeCode: l.modeCode, drivers: Object.assign({}, l.drivers || {}), modules: mods };
    });
  }

  /**
   * Fascia di trasferta suggerita per i km: dai nomi della tabella "Trasferte Italia" ("... 0-25 km", "... 25-100km", "... > 400 km").
   * Intervalli (da, a]; il primo parte da 0 incluso. Le voci senza intervallo (es. Isole) non sono mai suggerite.
   */
  function travelBandFor(rates, km) {
    if (km == null || !isFinite(km)) return null;
    let best = null;
    (rates || []).forEach((r) => {
      let m = /(\d+)\s*-\s*(\d+)\s*km/i.exec(r.name);
      if (m) { const a = Number(m[1]), b = Number(m[2]); if ((km > a || (a === 0 && km >= 0)) && km <= b) best = r; return; }
      m = />\s*(\d+)\s*km/i.exec(r.name);
      if (m && km > Number(m[1])) best = r;
    });
    return best;
  }

  // ------------------------------------------------------------------ attivita' sistemistiche (sezione Servizi)
  const r2 = (x) => Math.round(x * 100) / 100;
  /** ore digitate ("7,5" / "7.5") -> numero (0 se non valido) */
  function parseHours(v) { const n = Number(String(v == null ? '' : v).replace(',', '.')); return isFinite(n) && n > 0 ? n : 0; }
  /** importo scritto in formato italiano ("1.234,50", "1234.5", "€ 99") -> numero; null se vuoto o non valido */
  function parseMoney(v) {
    let t = String(v == null ? '' : v).replace(/[€\s]/g, '');
    if (!t) return null;
    if (t.indexOf(',') >= 0) t = t.replace(/\./g, '').replace(',', '.');
    else if (/^\d{1,3}(\.\d{3})+$/.test(t)) t = t.replace(/\./g, '');
    const n = Number(t);
    return isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : null;
  }
  /** tariffa oraria scontata, arrotondata al centesimo */
  function discountedRate(rate, discPct) { return r2(Number(rate || 0) * (1 - Math.min(Math.max(Number(discPct || 0), 0), 100) / 100)); }
  const rowEmpty = (a) => !(a.desc || '').trim() && !(parseHours(a.hours) > 0);
  /** righe da considerare: applicazione presente nell'offerta e riga non completamente vuota */
  function activityRows(acts, lines) {
    return (acts || []).filter((a) => (a.rfq || a.gid || (lines || []).some((l) => l.applicationId === a.appId)) && !rowEmpty(a));
  }
  /** totali a listino e scontati (tariffa scontata x ore), complessivi e per applicazione */
  function activityTotals(acts, lines, discPct) {
    const out = { list: 0, net: 0, hours: 0, perApp: {} };
    (acts || []).filter((a) => a.rfq || a.gid || (lines || []).some((l) => l.applicationId === a.appId)).forEach((a) => {
      const h = parseHours(a.hours), rate = Number(a.rate || 0);
      if (!a.profileId || !(h > 0)) return;
      const list = r2(h * rate), net = r2(h * discountedRate(rate, discPct));
      const p = out.perApp[a.appId] || (out.perApp[a.appId] = { list: 0, net: 0, hours: 0 });
      p.list = r2(p.list + list); p.net = r2(p.net + net); p.hours = r2(p.hours + h);
      out.list = r2(out.list + list); out.net = r2(out.net + net); out.hours = r2(out.hours + h);
    });
    return out;
  }
  /** righe compilate a meta' */
  function activityErrors(acts, lines) {
    const e = [];
    activityRows(acts, lines).forEach((a, i) => {
      const name = (a.desc || '').trim() || ('riga ' + (i + 1));
      if (!(a.desc || '').trim()) e.push('Attività: manca la descrizione');
      else if (!a.profileId) e.push('Attività «' + name + '»: scegli il profilo professionale');
      else if (!(parseHours(a.hours) > 0)) e.push('Attività «' + name + '»: indica le ore');
      else if (parseHours(a.hours) > 9999) e.push('Attività «' + name + '»: massimo 9999 ore');
    });
    return Array.from(new Set(e));
  }

  const api = { parseMoney, activityRows, activityTotals, activityErrors, discountedRate, parseHours, travelBandFor, normalizePay, applyDefaultPay, defaultTerms, allowedPay, fixActPay, termsErrors, modulePrice, offerNumber, newLine, setModule, selectModule, isCodeSelected, providers, buildRequest, suggestTitle, linesFromRequest, driverIsFree };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.WizardCore = api;
})(typeof window !== 'undefined' ? window : globalThis);
