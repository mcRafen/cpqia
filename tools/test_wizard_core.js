// Test della logica pura del wizard (node tools/test_wizard_core.js)
const assert = require('assert');
const path = require('path');
const C = require(path.join(__dirname, '..', 'src/main/resources/static/js/wizard-core.js'));
const cat = require(path.join(__dirname, '..', 'src/test/resources/catalog-api.json'));
const app = (code) => cat.find((a) => a.code === code);

// numero offerta
assert.strictEqual(C.offerNumber('2026-10-22', 3, 'er'), '20261022.3ER');

// nuova riga ICM: utenti 1, nessun modulo preselezionato, nessuna modalita'
const icm = app('ICM'); const l = C.newLine(icm);
assert.strictEqual(l.modeCode, null);
assert.deepStrictEqual(Object.values(l.drivers), [1]);

// SMIWCO richiede SMIWCP (bloccante) che richiede SMIWPS4: selezione automatica dei prerequisiti
const wco = icm.modules.find((m) => m.code === 'SMIWCO');
const added = C.setModule(icm, l, wco.id, true);
assert.ok(added.includes('SMIWCP') && added.includes('SMIWPS4'), 'prerequisiti auto: ' + added);
assert.ok(l.modules[wco.id] === 1);
// il prerequisito "SMIWOR" (altra applicazione, avviso) NON viene aggiunto
assert.ok(!added.includes('SMIWOR'));

// alternative: APS Standard/Professional/Advanced
const aps = app('APS'); const la = C.newLine(aps);
const [s, p] = ['SMIJAPS', 'SMIJAPSP'].map((c) => aps.modules.find((m) => m.code === c));
C.setModule(aps, la, s.id, true); C.setModule(aps, la, p.id, true);
assert.ok(!la.modules[s.id] && la.modules[p.id], 'solo un modulo per gruppo alternative');
// driver liberi (visualizzatori) partono da 0, utenti da 1
assert.deepStrictEqual(aps.drivers.map((d) => la.drivers[d.id]), [1, 0]);
// JMES: utenti 1 e macchine 0
const jm = app('JMES'); const lj = C.newLine(jm);
assert.deepStrictEqual(jm.drivers.map((d) => [d.code, lj.drivers[d.id]]), [['UTENTI', 1], ['MACCHINE', 0]]);
// moduli "inclusi" e link non selezionabili
const b2b = app('B2B'); const lb = C.newLine(b2b);
const inc = b2b.modules.find((m) => m.included);
C.setModule(b2b, lb, inc.id, true);
assert.ok(!lb.modules[inc.id]);
// default mode
assert.strictEqual(C.newLine(app('JPA3')).modeCode, 'NEW');
// richiesta al server
const req = C.buildRequest({ offerId: null, date: '2026-10-22', progressive: '3', customer: { id: 7 }, title: '', contactRef: '', discountPct: '20', lines: [l] });
assert.strictEqual(req.customerId, 7); assert.strictEqual(req.progressive, 3); assert.strictEqual(req.discountPct, 20);
assert.ok(req.lines[0].modules.length >= 3);
// prezzo per modalita'
const eng = app('JPA3').modules.find((m) => m.code === 'SMIJPA');
assert.strictEqual(C.modulePrice(eng, 'NEW'), 6000); assert.strictEqual(C.modulePrice(eng, 'MIGR'), 3000); assert.strictEqual(C.modulePrice(eng, 'ADD'), 0);

// ---- dipendenze (selectModule) ----
const sel = (code, appCode) => app(appCode).modules.find((m) => m.code === code);
const has = (lines, appCode, code) => { const l = lines.find((x) => x.applicationId === app(appCode).id); return !!(l && l.modules[sel(code, appCode).id]); };

// SMIWCXS (App CRM smartphone) richiede SMIWPS4: stesso listino, selezione automatica
let lines = [C.newLine(app('CRMJ'))];
let r = C.selectModule(cat, lines, app('CRMJ').id, sel('SMIWCXS', 'CRMJ').id, true);
assert.ok(has(lines, 'CRMJ', 'SMIWCXS') && has(lines, 'CRMJ', 'SMIWPS4'), 'CRMJ: SMIWCXS -> SMIWPS4');
assert.deepStrictEqual(r.addedModules, ['SMIWPS4']);
// deselezionando il prerequisito si deseleziona anche chi lo richiede
r = C.selectModule(cat, lines, app('CRMJ').id, sel('SMIWPS4', 'CRMJ').id, false);
assert.ok(!has(lines, 'CRMJ', 'SMIWPS4') && !has(lines, 'CRMJ', 'SMIWCXS'));
assert.deepStrictEqual(r.removed, ['SMIWPS4', 'SMIWCXS']);

// catena: ICM SMIWCO -> SMIWCP -> SMIWPS4
lines = [C.newLine(app('ICM'))];
C.selectModule(cat, lines, app('ICM').id, sel('SMIWCO', 'ICM').id, true);
assert.ok(has(lines, 'ICM', 'SMIWCO') && has(lines, 'ICM', 'SMIWCP') && has(lines, 'ICM', 'SMIWPS4'));

// piu' prerequisiti: JTCE SMITC3D -> SMITCE + SMITWEB
lines = [C.newLine(app('JTCE'))];
C.selectModule(cat, lines, app('JTCE').id, sel('SMITC3D', 'JTCE').id, true);
assert.ok(has(lines, 'JTCE', 'SMITCE') && has(lines, 'JTCE', 'SMITWEB') && has(lines, 'JTCE', 'SMITC3D'));

// BPB SMIBPINV -> SMIBPBDG + SMIBPSPG
lines = [C.newLine(app('BPB'))];
C.selectModule(cat, lines, app('BPB').id, sel('SMIBPINV', 'BPB').id, true);
assert.ok(has(lines, 'BPB', 'SMIBPBDG') && has(lines, 'BPB', 'SMIBPSPG'));

// tra applicazioni: iOC SMIWCC richiede SMIWOR (solo B2B lo fornisce -> aggiunge B2B) e SMIWPS4 (piu' app -> segnalato, non scelto a caso)
lines = [C.newLine(app('IOC'))];
r = C.selectModule(cat, lines, app('IOC').id, sel('SMIWCC', 'IOC').id, true);
assert.ok(lines.some((l) => l.applicationId === app('B2B').id), 'B2B aggiunta');
assert.ok(has(lines, 'B2B', 'SMIWOR'));
assert.deepStrictEqual(r.addedApps, [app('B2B').name]);
assert.strictEqual(r.ambiguous.length, 1); assert.strictEqual(r.ambiguous[0].code, 'SMIWPS4');
assert.ok(r.ambiguous[0].apps.length >= 3);
// se un'applicazione che fornisce SMIWPS4 e' gia' in offerta, il prerequisito viene preso li'
lines = [C.newLine(app('TV')), C.newLine(app('IOC'))];
r = C.selectModule(cat, lines, app('IOC').id, sel('SMIWCC', 'IOC').id, true);
assert.ok(has(lines, 'TV', 'SMIWPS4') && r.ambiguous.length === 0, 'WPS4 preso da TV gia in offerta');
// i prerequisiti solo "avviso" (es. SMIWOR per ICM SMIWCO) non vengono aggiunti
lines = [C.newLine(app('ICM'))];
C.selectModule(cat, lines, app('ICM').id, sel('SMIWCO', 'ICM').id, true);
assert.ok(!lines.some((l) => l.applicationId === app('B2B').id));
// ---- richiesta con servizi accessori e flag
const rq = C.buildRequest({ offerId: null, date: '2026-10-26', progressive: 1, customer: { id: 1 }, discountPct: 0, lines: [],
  optMaint: true, optServices: true, optSummary: false, xSel: { services: new Set([2]), options: { 5: 1, 40: 3 } } });
assert.strictEqual(rq.optMaintenanceAdditional, true); assert.strictEqual(rq.optExtraServices, true); assert.strictEqual(rq.optFinalSummary, false);
assert.deepStrictEqual(rq.extras, { services: [2], options: [{ optionId: 5, qty: 1 }, { optionId: 40, qty: 3 }] });
const rq2 = C.buildRequest({ offerId: null, date: '2026-10-26', progressive: 1, customer: { id: 1 }, lines: [], optServices: false, xSel: { services: new Set([2]), options: {} } });
assert.strictEqual(rq2.extras, null);
// ---- modalita' di fatturazione e pagamento
const cfg = require(path.join(__dirname, '..', 'src/main/resources/offer-terms.json'));
let tp = C.defaultTerms(cfg, 'PROSPECT');
assert.strictEqual(tp.billLic, 'DAYS30'); assert.strictEqual(tp.billFee, 'NEXT_MONTH'); assert.strictEqual(tp.billAct, 'CONSUNTIVO');
assert.deepStrictEqual([tp.payLic, tp.payFee, tp.payAct], ['RIBA30', 'RIBA30', 'RIBA30']);
assert.deepStrictEqual(C.termsErrors(cfg, tp), []);
const tc = C.defaultTerms(cfg, 'CLIENTE');
assert.deepStrictEqual([tc.payLic, tc.payFee, tc.payAct], ['USUAL', 'USUAL', 'USUAL']);
// data obbligatoria se si sceglie "Data"
tp.billLic = 'DATE'; tp.billFee = 'DATE';
assert.deepStrictEqual(C.termsErrors(cfg, tp), ['Fatturazione licenze: indica la data', 'Fatturazione canoni: indica la data']);
tp.billLicDate = '2026-12-15'; tp.billFeeDate = '2027-01-31'; assert.deepStrictEqual(C.termsErrors(cfg, tp), []);
// "altro": testo libero obbligatorio
tp.payFee = 'OTHER'; assert.deepStrictEqual(C.termsErrors(cfg, tp), ['Pagamento canoni: scrivi la modalità']);
tp.payFeeOther = '60 gg data fattura'; assert.deepStrictEqual(C.termsErrors(cfg, tp), []);
// attivita': le opzioni di pagamento dipendono dalla fatturazione
assert.deepStrictEqual(C.allowedPay(cfg, 'act', 'CONSUNTIVO', 'CLIENTE'), ['USUAL', 'RIBA30', 'IVA_30_60', 'OTHER']);
// per un prospect "Solito in uso" non compare tra le opzioni (licenze, canoni, attivita')
assert.deepStrictEqual(C.allowedPay(cfg, 'act', 'CONSUNTIVO', 'PROSPECT'), ['RIBA30', 'IVA_30_60', 'OTHER']);
['lic', 'fee'].forEach((c) => assert.ok(!C.allowedPay(cfg, c, null, 'PROSPECT').includes('USUAL') && C.allowedPay(cfg, c, null, 'CLIENTE').includes('USUAL')));
assert.ok(!C.allowedPay(cfg, 'act', 'PACCHETTO', 'PROSPECT').includes('USUAL'));
assert.ok(!C.allowedPay(cfg, 'act', 'PACCHETTO', 'CLIENTE').includes('RIBA30') && C.allowedPay(cfg, 'act', 'PACCHETTO', 'CLIENTE').includes('IVA_60_90_120_150'));
tp.billAct = 'PACCHETTO'; C.fixActPay(cfg, tp, 'PROSPECT');   // RIBA30 non ammesso a pacchetto: un prospect passa alla prima opzione ammessa
assert.strictEqual(tp.payAct, 'IVA_30_60');
const tcl = C.defaultTerms(cfg, 'CLIENTE'); tcl.payAct = 'RIBA30'; tcl.billAct = 'PACCHETTO'; C.fixActPay(cfg, tcl, 'CLIENTE'); assert.strictEqual(tcl.payAct, 'USUAL');
tp.payAct = 'IVA_30_60_90'; tp.billAct = 'CONSUNTIVO'; C.fixActPay(cfg, tp, 'PROSPECT'); assert.strictEqual(tp.payAct, 'RIBA30');
// "Solito in uso" scelto per un cliente non e' valido se l'account diventa un prospect
const t6 = C.defaultTerms(cfg, 'CLIENTE'); assert.ok(C.termsErrors(cfg, t6, 'PROSPECT').length === 3);
C.normalizePay(cfg, t6, 'PROSPECT'); assert.deepStrictEqual([t6.payLic, t6.payFee, t6.payAct], ['RIBA30', 'RIBA30', 'RIBA30']);
// predefiniti per tipo di account: ogni riga di pagamento segue l'account finche' non e' modificata a mano
const tt = C.defaultTerms(cfg, 'CLIENTE'); const touched = { lic: false, fee: false, act: false };
C.applyDefaultPay(cfg, tt, 'PROSPECT', touched);
assert.deepStrictEqual([tt.payLic, tt.payFee, tt.payAct], ['RIBA30', 'RIBA30', 'RIBA30'], 'prospect: sempre RI.BA. 30 gg dffm');
tt.payFee = 'IVA_60_90'; touched.fee = true;                 // modificato a mano: non viene piu' toccato
C.applyDefaultPay(cfg, tt, 'CLIENTE', touched);
assert.deepStrictEqual([tt.payLic, tt.payFee, tt.payAct], ['USUAL', 'IVA_60_90', 'USUAL']);
C.applyDefaultPay(cfg, tt, 'PROSPECT', touched);
assert.deepStrictEqual([tt.payLic, tt.payFee, tt.payAct], ['RIBA30', 'IVA_60_90', 'RIBA30']);
// cambiare la fatturazione (data, semestrale, ...) non blocca i predefiniti dei pagamenti
const t5 = C.defaultTerms(cfg, 'CLIENTE'); t5.billLic = 'DATE'; t5.feeSemiannual = true;
C.applyDefaultPay(cfg, t5, 'PROSPECT', { lic: false, fee: false, act: false });
assert.strictEqual(t5.payLic, 'RIBA30');
// la richiesta contiene le date vuote come null
const rq3 = C.buildRequest({ offerId: null, date: '2026-10-26', progressive: 1, customer: { id: 1 }, lines: [], terms: C.defaultTerms(cfg, 'PROSPECT'), xSel: { services: new Set(), options: {} } });
assert.strictEqual(rq3.terms.billLicDate, null); assert.strictEqual(rq3.terms.payLic, 'RIBA30');
// fascia di trasferta suggerita dai km
const tr = [{ id: 1, name: 'Italia Distanza 0-25 km' }, { id: 2, name: 'Italia Distanza 25-100km' }, { id: 3, name: 'Italia Distanza 100-200 km' }, { id: 4, name: 'Italia Distanza 200-400 km' }, { id: 5, name: 'Italia Distanza > 400 km' }, { id: 6, name: 'Italia Isole' }];
[[0, 1], [25, 1], [25.1, 2], [100, 2], [150, 3], [400, 4], [400.1, 5], [900, 5]].forEach(([km, id]) => assert.strictEqual(C.travelBandFor(tr, km).id, id, 'km ' + km));
assert.strictEqual(C.travelBandFor(tr, null), null);
assert.strictEqual(C.buildRequest({ offerId: null, date: '2026-10-26', progressive: 1, customer: { id: 1 }, lines: [], terms: null, xSel: null, distanceKm: 12.5, nearestSede: 'Trento', travelRateId: '3' }).travelRateId, 3);
// attivita' sistemistiche
const L = [{ applicationId: 1 }, { applicationId: 2 }];
const acts = [
  { appId: 1, desc: 'Installazione', profileId: 5, hours: '7,5', rate: 85 },
  { appId: 1, desc: 'Formazione', profileId: 4, hours: 4, rate: 90 },
  { appId: 2, desc: 'Analisi', profileId: 3, hours: '10', rate: 95 },
  { appId: 9, desc: 'Applicazione non in offerta', profileId: 3, hours: 5, rate: 95 },
  { appId: 1, desc: '', profileId: null, hours: '', rate: 0 },
];
let t = C.activityTotals(acts, L, 0);
assert.strictEqual(t.list, 637.5 + 360 + 950); assert.strictEqual(t.net, t.list); assert.strictEqual(t.hours, 21.5);
t = C.activityTotals(acts, L, 20);                      // tariffe: 68, 72, 76
assert.strictEqual(C.discountedRate(85, 20), 68);
assert.strictEqual(t.net, 510 + 288 + 760); assert.strictEqual(t.perApp[1].list, 997.5); assert.strictEqual(t.perApp[1].net, 798);
assert.strictEqual(C.discountedRate(33.33, 10), 30);    // 29.997 -> 30,00
assert.strictEqual(C.activityRows(acts, L).length, 3);  // fuori offerta e vuota escluse
assert.deepStrictEqual(C.activityErrors(acts, L), []);
assert.strictEqual(C.activityErrors([{ appId: 1, desc: 'X', profileId: null, hours: 2 }], L).length, 1);
assert.strictEqual(C.activityErrors([{ appId: 1, desc: 'X', profileId: 3, hours: 0 }], L).length, 1);
const rq4 = C.buildRequest({ offerId: null, date: '2026-10-26', progressive: 1, customer: { id: 1 }, lines: L.map((l) => Object.assign({ modeCode: null, drivers: {}, modules: {} }, l)), terms: null, xSel: null, activities: acts, actDiscount: 20 });
assert.strictEqual(rq4.activities.length, 3); assert.strictEqual(rq4.activities[0].hours, 7.5); assert.strictEqual(rq4.activityDiscountPct, 20);
console.log('wizard-core: tutti i test OK');
