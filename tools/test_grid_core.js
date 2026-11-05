const G = require('../src/main/resources/static/js/grid-core.js');
let fail = 0;
const eq = (a, b, m) => { const x = JSON.stringify(a), y = JSON.stringify(b); if (x !== y) { fail++; console.log('FAIL', m, '\n  got', x, '\n  exp', y); } };
const cols = [
  { key: 'name', label: 'Nome', type: 'text' },
  { key: 'type', label: 'Tipo', type: 'enum', options: [{ value: 'CLIENTE', label: 'Cliente' }, { value: 'PROSPECT', label: 'Prospect' }] },
  { key: 'amt', label: 'Importo', type: 'money', agg: 'sum' },
  { key: 'pct', label: 'Sconto', type: 'percent' },
  { key: 'd', label: 'Data', type: 'date' },
  { key: 'ok', label: 'Attivo', type: 'bool' },
];
const rows = [
  { id: 1, name: 'Zeta', type: 'CLIENTE', amt: 100, pct: 10, d: '2026-10-01', ok: true },
  { id: 2, name: 'alfa', type: 'PROSPECT', amt: 250.5, pct: 0, d: '2026-11-15', ok: false },
  { id: 3, name: 'Èpsilon', type: 'CLIENTE', amt: 50, pct: 20, d: '2025-01-31', ok: true },
  { id: 4, name: 'beta', type: 'CLIENTE', amt: null, pct: 5, d: null, ok: true },
];
const ids = (rs) => rs.map((r) => r.id);
// formati
eq(G.format(1234.5, cols[2]), '€ 1.234,50', 'money'); eq(G.format('2026-10-26', cols[4]), '26/10/2026', 'date');
eq(G.format('CLIENTE', cols[1]), 'Cliente', 'enum'); eq(G.format(true, cols[5]), 'Sì', 'bool');
// filtri
eq(ids(G.applyFilters(rows, cols, { name: 'ALF' })), [2], 'text contains ci');
eq(ids(G.applyFilters(rows, cols, { name: 'epsilon' })), [3], 'accenti');
eq(ids(G.applyFilters(rows, cols, { name: '=beta' })), [4], 'esatto');
eq(ids(G.applyFilters(rows, cols, { name: '!a' })), [3], 'non contiene a');
eq(ids(G.applyFilters(rows, cols, { name: 'zeta|beta' })), [1, 4], 'uno tra');
eq(ids(G.applyFilters(rows, cols, { name: '^b' })), [4], 'inizia con');
eq(ids(G.applyFilters(rows, cols, { type: 'cliente' })), [1, 3, 4], 'enum etichetta');
eq(ids(G.applyFilters(rows, cols, { amt: '>=100' })), [1, 2], 'num >=');
eq(ids(G.applyFilters(rows, cols, { amt: '50..120' })), [1, 3], 'num intervallo');
eq(ids(G.applyFilters(rows, cols, { amt: '250,5' })), [2], 'num virgola');
eq(ids(G.applyFilters(rows, cols, { amt: 'vuoto' })), [4], 'vuoto');
eq(ids(G.applyFilters(rows, cols, { pct: '!=0' })), [1, 3, 4], 'num !=');
eq(ids(G.applyFilters(rows, cols, { d: '2026' })), [1, 2], 'anno');
eq(ids(G.applyFilters(rows, cols, { d: '10/2026' })), [1], 'mese');
eq(ids(G.applyFilters(rows, cols, { d: '>=01/11/2026' })), [2], 'data >=');
eq(ids(G.applyFilters(rows, cols, { d: '01/01/2025..31/12/2025' })), [3], 'data intervallo');
eq(ids(G.applyFilters(rows, cols, { ok: 'no' })), [2], 'bool no');
eq(ids(G.applyFilters(rows, cols, { ok: 'si', type: 'cliente' })), [1, 3, 4], 'filtri combinati');
eq(ids(G.applyFilters(rows, cols, {}, 'prospect')), [2], 'ricerca libera');
// ordinamenti
eq(ids(G.multiSort(rows, cols, [{ key: 'name', dir: 'asc' }])), [2, 4, 3, 1], 'sort testo it');
eq(ids(G.multiSort(rows, cols, [{ key: 'amt', dir: 'desc' }])), [2, 1, 3, 4], 'sort num desc, vuoti in fondo');
eq(ids(G.multiSort(rows, cols, [{ key: 'amt', dir: 'asc' }])), [3, 1, 2, 4], 'sort num asc, vuoti in fondo');
eq(ids(G.multiSort(rows, cols, [{ key: 'type', dir: 'asc' }, { key: 'amt', dir: 'desc' }])), [1, 3, 4, 2], 'sort in serie');
eq(G.toggleSort([], 'a', false), [{ key: 'a', dir: 'asc' }], 'toggle 1');
eq(G.toggleSort([{ key: 'a', dir: 'asc' }], 'a', false), [{ key: 'a', dir: 'desc' }], 'toggle 2');
eq(G.toggleSort([{ key: 'a', dir: 'desc' }], 'a', false), [], 'toggle 3');
eq(G.toggleSort([{ key: 'a', dir: 'asc' }], 'b', true), [{ key: 'a', dir: 'asc' }, { key: 'b', dir: 'asc' }], 'shift aggiunge');
eq(G.toggleSort([{ key: 'a', dir: 'asc' }, { key: 'b', dir: 'asc' }], 'a', false), [{ key: 'a', dir: 'desc' }], 'senza shift sostituisce');
// totali / pivot
eq(G.totals(rows, cols, {}).amt, { fn: 'sum', value: 400.5 }, 'totale somma');
eq(G.totals(rows, cols, { pct: 'avg' }).pct, { fn: 'avg', value: 8.75 }, 'media');
eq(G.totals(rows, cols, { amt: 'count' }).amt.value, 3, 'conteggio non vuoti');
eq(G.totals(rows, cols, { amt: 'max' }).amt.value, 250.5, 'max');
const tree = G.group(rows, cols, ['type', 'ok'], {}, []);
eq(tree.map((n) => [n.label, n.count, n.totals.amt.value]), [['Cliente', 3, 150], ['Prospect', 1, 250.5]], 'gruppo livello 1');
eq(tree[0].children.map((n) => [n.label, n.count, n.totals.amt.value]), [['Sì', 3, 150]], 'gruppo livello 2');
eq(G.flatten(tree, {}).filter((x) => x.kind === 'row').length, 4, 'flatten righe');
const first = G.allPaths(tree)[0];
eq(G.flatten(tree, { [first]: true }).filter((x) => x.kind === 'row').length, 1, 'gruppo compresso');
eq(G.group(rows, cols, [], {}, []), null, 'nessun gruppo');
eq(G.group(rows, cols, ['ok', 'type'], {}, []).map((n) => n.label), ['No', 'Sì'], 'pivot ordine colonne');
// configurazione
const cfg = G.normalizeConfig({ order: ['amt', 'zzz'], sorts: [{ key: 'zzz', dir: 'asc' }, { key: 'amt', dir: 'asc' }], filters: { name: 'a', qq: 'b' }, groups: ['type', 'x'] }, cols);
eq(cfg.order, ['amt', 'name', 'type', 'pct', 'd', 'ok'], 'config ordine'); eq(cfg.sorts, [{ key: 'amt', dir: 'asc' }], 'config sort');
eq(cfg.filters, { name: 'a' }, 'config filtri'); eq(cfg.groups, ['type'], 'config gruppi');
// csv
eq(G.toCsv([rows[1]], cols).split('\r\n')[1], 'alfa;Prospect;250,5;0 %;15/11/2026;No'.replace('0 %', '0'), 'csv');
console.log(fail ? fail + ' FALLITI' : 'grid-core: tutti i test OK');
process.exit(fail ? 1 : 0);
