/* Logica pura delle griglie (filtri, ordinamenti multipli, raggruppamento/pivot con totali, formati, CSV). Testata con node: tools/test_grid_core.js */
(function (root) {
  'use strict';

  const NUMERIC = { number: 1, money: 1, percent: 1, int: 1, coord: 1 };
  const isNumeric = (col) => !!NUMERIC[col.type];

  // ------------------------------------------------------------------ formati
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function fmtNumber(v, col) {
    if (v == null || v === '') return '';
    const n = Number(v);
    if (!isFinite(n)) return String(v);
    const dec = col.type === 'int' ? 0 : (col.type === 'coord' ? 6 : 2);
    const s = n.toLocaleString('it-IT', { minimumFractionDigits: dec, maximumFractionDigits: dec, useGrouping: col.type === 'coord' ? false : 'always' });
    if (col.type === 'money') return '€ ' + s;
    if (col.type === 'percent') return s + ' %';
    return s;
  }
  function fmtDate(v) {
    if (!v) return '';
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(v));
    return m ? m[3] + '/' + m[2] + '/' + m[1] : String(v);
  }
  function fmtDateTime(v) {
    if (!v) return '';
    const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(v));
    return m ? m[3] + '/' + m[2] + '/' + m[1] + ' ' + m[4] + ':' + m[5] + ':' + (m[6] || '00') : fmtDate(v);
  }
  function optionLabel(col, v) {
    const o = (col.options || []).find((x) => x.value === v);
    return o ? o.label : (v == null ? '' : String(v));
  }
  /** testo mostrato in cella */
  function format(v, col) {
    if (v == null || v === '') return '';
    if (isNumeric(col)) return fmtNumber(v, col);
    if (col.type === 'date') return fmtDate(v);
    if (col.type === 'datetime') return fmtDateTime(v);
    if (col.type === 'bool') return v ? 'Sì' : 'No';
    if (col.type === 'enum') return optionLabel(col, v);
    if (col.type === 'multi') return String(v).split(',').filter(Boolean).map((x) => optionLabel(col, x)).join(', ');
    return String(v);
  }

  // ------------------------------------------------------------------ filtri
  const stripAccents = (s) => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

  /** "1.234,50" | "1234.5" | "12 %" -> numero (null se non e' un numero) */
  function parseNum(s) {
    let t = String(s).trim().replace(/[€%\s]/g, '');
    if (!t) return null;
    if (t.indexOf(',') >= 0) t = t.replace(/\./g, '').replace(',', '.');
    const n = Number(t);
    return isFinite(n) ? n : null;
  }
  /** "26/10/2026" | "2026-10-26" -> "2026-10-26" ; "10/2026" -> "2026-10" ; altrimenti null */
  function parseDate(s) {
    const t = String(s).trim();
    let m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(t);
    if (m) return m[3] + '-' + pad(+m[2]) + '-' + pad(+m[1]);
    m = /^(\d{1,2})\/(\d{4})$/.exec(t);
    if (m) return m[2] + '-' + pad(+m[1]);
    m = /^\d{4}(-\d{2}(-\d{2})?)?$/.exec(t);
    return m ? t : null;
  }

  /**
   * Compila il testo di un filtro in una funzione (valore -> bool). Sintassi:
   *  testo: parte del testo | =esatto | !non contiene | ^inizia con | a|b (uno tra)
   *  numeri/date: 5 | >5 | >=5 | <5 | <=5 | !=5 | 5..10 | vuoto / !vuoto
   *  si/no (bool) ; elenchi: valore o etichetta
   * Restituisce null se il filtro e' vuoto.
   */
  function compileFilter(col, text) {
    const raw = (text == null ? '' : String(text)).trim();
    if (!raw) return null;
    const low = stripAccents(raw);
    if (low === 'vuoto') return (v) => v == null || v === '';
    if (low === '!vuoto') return (v) => !(v == null || v === '');
    if (col.type === 'bool') {
      const yes = ['si', 'true', '1', 'x'].includes(low), no = ['no', 'false', '0'].includes(low);
      return (v) => (yes ? !!v : no ? !v : true);
    }
    if (isNumeric(col)) {
      let m = /^(-?[\d.,]+)\s*\.\.\s*(-?[\d.,]+)$/.exec(raw);
      if (m) {
        const a = parseNum(m[1]), b = parseNum(m[2]);
        if (a != null && b != null) return (v) => v != null && v !== '' && Number(v) >= Math.min(a, b) && Number(v) <= Math.max(a, b);
      }
      m = /^(>=|<=|!=|=|>|<)?\s*(.+)$/.exec(raw);
      const n = parseNum(m[2]);
      if (n != null) {
        const op = m[1] || '=';
        return (v) => {
          if (v == null || v === '') return false;
          const x = Number(v);
          switch (op) {
            case '>': return x > n; case '<': return x < n; case '>=': return x >= n; case '<=': return x <= n;
            case '!=': return Math.abs(x - n) >= 0.005; default: return Math.abs(x - n) < 0.005;
          }
        };
      }
      // testo non numerico: confronto sul testo formattato (sotto)
    }
    if (col.type === 'date' || col.type === 'datetime') {
      const dateOf = (v) => (v == null ? '' : String(v).slice(0, 10));
      let m = /^(\d[\d/-]*)\s*\.\.\s*(\d[\d/-]*)$/.exec(raw);
      if (m) {
        const a = parseDate(m[1]), b = parseDate(m[2]);
        if (a && b) return (v) => { const d = dateOf(v); return !!d && d >= a && (d <= b || d.slice(0, b.length) === b); };
      }
      m = /^(>=|<=|!=|=|>|<)?\s*(.+)$/.exec(raw);
      const d0 = parseDate(m[2]);
      if (d0) {
        const op = m[1] || '=';
        return (v) => {
          const d = dateOf(v); if (!d) return false;
          const same = d.slice(0, d0.length) === d0;     // consente "2026-10" (mese) e "2026" (anno)
          switch (op) {
            case '>': return d > d0 && !same; case '<': return d < d0; case '>=': return d >= d0 || same;
            case '<=': return d <= d0 || same; case '!=': return !same; default: return same;
          }
        };
      }
    }
    // testo / elenchi / ripiego
    const shown = (v) => stripAccents(format(v, col));
    const rawv = (v) => stripAccents(v == null ? '' : v);
    if (raw[0] === '!') { const t = stripAccents(raw.slice(1).trim()); return (v) => !shown(v).includes(t) && !rawv(v).includes(t); }
    if (raw[0] === '=') { const t = stripAccents(raw.slice(1).trim()); return (v) => shown(v) === t || rawv(v) === t; }
    if (raw[0] === '^') { const t = stripAccents(raw.slice(1).trim()); return (v) => shown(v).startsWith(t); }
    const alts = low.split('|').map((x) => x.trim()).filter(Boolean);
    return (v) => { const s = shown(v), r = rawv(v); return alts.some((t) => s.includes(t) || (col.type === 'enum' && r === t)); };
  }

  /** filters: { colKey: testo } ; quick: ricerca libera su tutte le colonne indicate */
  function applyFilters(rows, cols, filters, quick) {
    const fs = [];
    Object.keys(filters || {}).forEach((k) => {
      const col = cols.find((c) => c.key === k); if (!col) return;
      const f = compileFilter(col, filters[k]);
      if (f) fs.push([k, f]);
    });
    const q = quick ? stripAccents(quick.trim()) : '';
    return rows.filter((r) => fs.every(([k, f]) => f(r[k])) &&
      (!q || cols.some((c) => stripAccents(format(r[c.key], c)).includes(q))));
  }

  // ------------------------------------------------------------------ ordinamento multiplo
  const isEmpty = (v) => v == null || v === '';
  function compareValues(a, b, col) {
    const ea = isEmpty(a), eb = isEmpty(b);
    if (ea || eb) return ea === eb ? 0 : (ea ? 1 : -1);
    if (isNumeric(col)) return Number(a) - Number(b);
    if (col.type === 'bool') return (a ? 1 : 0) - (b ? 1 : 0);
    if (col.type === 'date' || col.type === 'datetime') return String(a) < String(b) ? -1 : String(a) > String(b) ? 1 : 0;
    return format(a, col).localeCompare(format(b, col), 'it', { sensitivity: 'base', numeric: true });
  }
  /** sorts: [{key, dir:'asc'|'desc'}] in ordine di priorita'; ordinamento stabile, vuoti sempre in fondo */
  function multiSort(rows, cols, sorts) {
    const list = (sorts || []).map((s) => ({ col: cols.find((c) => c.key === s.key), dir: s.dir === 'desc' ? -1 : 1 })).filter((s) => s.col);
    if (!list.length) return rows.slice();
    return rows.map((r, i) => [r, i]).sort((x, y) => {
      for (const s of list) {
        const a = x[0][s.col.key], b = y[0][s.col.key];
        const ea = isEmpty(a), eb = isEmpty(b);
        let c;
        if (ea || eb) c = ea === eb ? 0 : (ea ? 1 : -1);
        else c = compareValues(a, b, s.col) * s.dir;
        if (c) return c;
      }
      return x[1] - y[1];
    }).map((p) => p[0]);
  }
  /** clic su intestazione: senza Maiusc sostituisce l'ordinamento; con Maiusc aggiunge in serie (poi inverte, poi toglie) */
  function toggleSort(sorts, key, additive) {
    const cur = sorts.find((s) => s.key === key);
    const next = !cur ? 'asc' : cur.dir === 'asc' ? 'desc' : null;
    if (!additive) return next ? [{ key: key, dir: next }] : [];
    const rest = sorts.filter((s) => s.key !== key);
    if (!cur) return rest.concat([{ key: key, dir: 'asc' }]);
    return next ? sorts.map((s) => (s.key === key ? { key: key, dir: next } : s)) : rest;
  }

  // ------------------------------------------------------------------ totali e pivot
  const AGGS = { sum: 'Somma', avg: 'Media', min: 'Min', max: 'Max', count: 'Conteggio' };
  function aggregate(rows, key, fn) {
    if (fn === 'count') return rows.filter((r) => !isEmpty(r[key])).length;
    const xs = rows.map((r) => r[key]).filter((v) => !isEmpty(v)).map(Number).filter(isFinite);
    if (!xs.length) return null;
    switch (fn) {
      case 'avg': return xs.reduce((a, b) => a + b, 0) / xs.length;
      case 'min': return Math.min.apply(null, xs);
      case 'max': return Math.max.apply(null, xs);
      default: return Math.round(xs.reduce((a, b) => a + b, 0) * 100) / 100;
    }
  }
  /** totali per ogni colonna numerica: aggs = { colKey: 'sum'|... } (predefinito: col.agg o somma) */
  function totals(rows, cols, aggs) {
    const out = {};
    cols.filter((c) => isNumeric(c) && c.type !== 'coord').forEach((c) => {
      const fn = (aggs && aggs[c.key]) || c.agg || 'sum';
      out[c.key] = { fn: fn, value: aggregate(rows, c.key, fn) };
    });
    return out;
  }
  /**
   * Raggruppa le righe per le colonne indicate (ordine = ordine dei livelli di pivot).
   * Restituisce un albero: { key, value, label, level, count, totals, children:[...] | rows:[...] }
   */
  function group(rows, cols, groupKeys, aggs, sorts) {
    function build(list, level) {
      const key = groupKeys[level];
      if (!key) return null;
      const col = cols.find((c) => c.key === key);
      const buckets = new Map();
      list.forEach((r) => {
        const v = isEmpty(r[key]) ? '' : r[key];
        if (!buckets.has(v)) buckets.set(v, []);
        buckets.get(v).push(r);
      });
      const nodes = Array.from(buckets.entries()).map(([v, rs]) => ({
        key: key, value: v, label: v === '' ? '(vuoto)' : format(v, col), level: level, count: rs.length, totals: totals(rs, cols, aggs),
        children: build(rs, level + 1), rows: level + 1 >= groupKeys.length ? multiSort(rs, cols, sorts) : null,
      }));
      const s = (sorts || []).find((x) => x.key === key);
      const dir = s && s.dir === 'desc' ? -1 : 1;
      nodes.sort((a, b) => dir * compareValues(a.value === '' ? null : a.value, b.value === '' ? null : b.value, col));
      return nodes;
    }
    return groupKeys.length ? build(rows, 0) : null;
  }
  /** elenco piatto da visualizzare: voci 'group' (con percorso per comprimere) e 'row' */
  function flatten(tree, collapsed, path) {
    const out = [];
    (tree || []).forEach((n) => {
      const p = (path ? path + '\u0001' : '') + n.key + '=' + n.value;
      const closed = !!(collapsed && collapsed[p]);
      out.push({ kind: 'group', node: n, path: p, level: n.level, open: !closed });
      if (closed) return;
      if (n.children) flatten(n.children, collapsed, p).forEach((x) => out.push(x));
      else (n.rows || []).forEach((r) => out.push({ kind: 'row', row: r, level: n.level + 1 }));
    });
    return out;
  }
  function allPaths(tree, path, acc) {
    acc = acc || [];
    (tree || []).forEach((n) => {
      const p = (path ? path + '\u0001' : '') + n.key + '=' + n.value;
      acc.push(p);
      allPaths(n.children, p, acc);
    });
    return acc;
  }

  // ------------------------------------------------------------------ configurazione salvabile
  function normalizeConfig(cfg, cols) {
    const keys = cols.map((c) => c.key);
    const c = Object.assign({ order: [], hidden: null, sorts: [], filters: {}, groups: [], aggs: {}, pageSize: 15 }, cfg || {});
    const order = (c.order || []).filter((k) => keys.includes(k));
    keys.forEach((k) => { if (!order.includes(k)) order.push(k); });
    c.order = order;
    c.hidden = Array.isArray(c.hidden) ? c.hidden.filter((k) => keys.includes(k)) : cols.filter((x) => x.hidden).map((x) => x.key);
    c.sorts = (c.sorts || []).filter((s) => keys.includes(s.key));
    c.groups = (c.groups || []).filter((k) => keys.includes(k));
    const f = {};
    Object.keys(c.filters || {}).forEach((k) => { if (keys.includes(k) && c.filters[k]) f[k] = c.filters[k]; });
    c.filters = f;
    return c;
  }

  function csvEscape(s) { s = String(s == null ? '' : s); return /[";\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; }
  /** CSV con separatore ';' (Excel italiano) */
  function toCsv(rows, cols) {
    const lines = [cols.map((c) => csvEscape(c.label)).join(';')];
    rows.forEach((r) => lines.push(cols.map((c) => csvEscape(isNumeric(c) && !isEmpty(r[c.key]) ? String(r[c.key]).replace('.', ',') : format(r[c.key], c))).join(';')));
    return '﻿' + lines.join('\r\n');
  }

  /** testo TSV da incollare in Google Sheets / Excel (numeri con virgola decimale, date gg/mm/aaaa) */
  function toTsv(rows, cols) {
    const cell = (s) => String(s == null ? '' : s).replace(/[\t\r\n]+/g, ' ');
    const lines = [cols.map((c) => cell(c.label)).join('\t')];
    rows.forEach((r) => lines.push(cols.map((c) => cell(isNumeric(c) && !isEmpty(r[c.key]) ? String(r[c.key]).replace('.', ',') : format(r[c.key], c))).join('\t')));
    return lines.join('\n');
  }

  const api = { toTsv, isNumeric, format, fmtNumber, parseNum, parseDate, compileFilter, applyFilters, compareValues, multiSort, toggleSort, aggregate, totals, group, flatten, allPaths, normalizeConfig, toCsv, AGGS };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.GridCore = api;
})(typeof window !== 'undefined' ? window : globalThis);
