/* Griglia dati avanzata: modifica in cella, spostamento colonne, ordinamento multiplo, filtri per colonna,
   viste salvate, pivot (raggruppamento a piu' livelli trascinando le colonne) con totali. Logica pura in grid-core.js. */
(function () {
  'use strict';
  const G = window.GridCore;
  const esc = CPQ.esc;
  const icon = (n, cls) => '<svg class="ic ' + (cls || '') + '"><use href="#i-' + n + '"/></svg>';

  class Grid {
    constructor(el, key) {
      this.el = el; this.key = key;
      this.meta = null; this.rows = []; this.layouts = [];
      this.sel = new Set(); this.groupIds = {};
      this.cfg = null; this.quick = ''; this.collapsed = {}; this.page = 0; this.drag = null;
      this.base = '/api/grid/' + encodeURIComponent(key);
    }

    async init() {
      this.el.classList.add('cg'); this.el.innerHTML = '<div class="cg-loading">Caricamento…</div>';
      try {
        const [d, l, st] = await Promise.all([CPQ.api('GET', this.base), CPQ.api('GET', this.base + '/layouts').catch(() => []),
          CPQ.api('GET', this.base + '/settings').catch(() => ({ pageSize: 15 }))]);
        this.meta = d.meta; this.rows = d.rows; this.layouts = l || []; this.pageSize = st.pageSize == null ? 15 : st.pageSize;
      } catch (e) { this.el.innerHTML = '<div class="alert err"><span>' + esc(e.message) + '</span></div>'; return; }
      this.cols = this.meta.columns;
      // funzioni abilitate per questa griglia (Impostazioni > Comportamento griglie); senza indicazioni tutto e' abilitato
      this.bh = Object.assign({ configurable: true, moveColumns: true, sortColumns: true, pivot: true, totals: true, subtotals: true, inlineEdit: true }, this.meta.behavior || {});
      const def = this.bh.configurable ? this.layouts.find((x) => x.isDefault) : null;
      this.cfg = G.normalizeConfig(def ? safeParse(def.config) : null, this.cols);
      this.cfg.pageSize = this.pageSize;                 // righe per pagina: impostazione dell'utente, non della vista
      this.enforce();
      this.build();
    }

    /** toglie dalla configurazione (anche se proviene da una vista salvata) cio' che il comportamento della griglia non consente */
    enforce() {
      const b = this.bh, c = this.cfg;
      if (!b.configurable) { c.hidden = this.cols.filter((x) => x.hidden).map((x) => x.key); }
      if (!b.moveColumns) { c.order = this.cols.map((x) => x.key); }
      if (!b.sortColumns) c.sorts = [];
      if (!b.pivot) c.groups = [];
    }

    col(k) { return this.cols.find((c) => c.key === k); }
    visibleCols() { return this.cfg.order.filter((k) => !this.cfg.hidden.includes(k)).map((k) => this.col(k)); }

    // ------------------------------------------------------------------ scheletro
    build() {
      const m = this.meta;
      this.el.innerHTML =
        '<div class="cg-toolbar">' +
        '<div class="cg-search">' + icon('search') + '<input type="search" class="cg-quick" placeholder="Cerca in tutte le colonne…"></div>' +
        '<span class="cg-count muted"></span><span class="cg-selinfo"></span><span class="grow"></span>' +
        '<label class="cg-pp" title="Righe per pagina (impostazione personale di questa griglia)"><span class="muted">Righe</span> <select class="cg-ppsel">' + PAGE_SIZES.map((n) => '<option value="' + n + '">' + (n || 'Tutte') + '</option>').join('') + '</select></label>' +
        '<button type="button" class="btn tonal sm cg-clear" title="Azzera filtri, ordinamenti e raggruppamenti">Azzera</button>' +
        (this.bh.configurable ? '<span class="cg-pop-wrap"><button type="button" class="btn tonal sm cg-colsbtn">' + icon('view') + 'Colonne</button><div class="cg-pop cg-cols" hidden></div></span>' +
        '<span class="cg-pop-wrap"><button type="button" class="btn tonal sm cg-viewsbtn">' + icon('sort') + 'Viste</button><div class="cg-pop cg-views" hidden></div></span>' : '') +
        (m.canExport !== false ? '<span class="cg-pop-wrap"><button type="button" class="btn tonal sm cg-expbtn" title="Esporta le righe visualizzate">' + icon('download') + 'Esporta</button>' +
        '<div class="cg-pop cg-exp-menu" hidden><button type="button" data-ex="xlsx">Excel (.xlsx)</button><button type="button" data-ex="gsheet">Google Sheets</button><button type="button" data-ex="csv">CSV (.csv)</button></div></span>' : '') +
        (m.actions.some((a) => a.name === 'purge') ? '<button type="button" class="btn sm danger cg-empty-trash">' + icon('delete') + 'Svuota cestino</button>' : '') +
        (m.newHref ? '<a class="btn sm" href="' + CPQ.url('/' + m.newHref) + '">' + icon('add') + esc(m.newLabel || 'Nuovo') + '</a>'
          : (m.canCreate ? '<button type="button" class="btn sm cg-new">' + icon('add') + 'Aggiungi</button>' : '')) +
        '</div>' +
        '<div class="cg-groupbar"></div>' +
        '<div class="card cg-card" style="padding:0"><div class="tablewrap"><table class="t cg-table"><thead></thead><tbody></tbody><tfoot></tfoot></table></div>' +
        '<div class="cg-pager"></div></div>';
      const q = (s) => this.el.querySelector(s);
      q('.cg-quick').addEventListener('input', (e) => { this.quick = e.target.value; this.page = 0; this.renderBody(); });
      q('.cg-ppsel').value = String(this.cfg.pageSize);
      q('.cg-ppsel').addEventListener('change', (e) => this.setPageSize(Number(e.target.value)));
      q('.cg-clear').addEventListener('click', () => { this.quick = ''; q('.cg-quick').value = ''; this.cfg.filters = {}; this.cfg.sorts = []; this.cfg.groups = []; this.page = 0; this.renderAll(); });
      this.bindPop('.cg-expbtn', '.cg-exp-menu', () => {});
      q('.cg-exp-menu').addEventListener('click', (e) => {
        const b = e.target.closest('[data-ex]'); if (!b) return;
        q('.cg-exp-menu').hidden = true;
        const f = { xlsx: () => this.exportXlsx(), gsheet: () => this.exportGoogle(), csv: () => this.exportCsv() }[b.dataset.ex];
        f();
      });
      const et = q('.cg-empty-trash'); if (et) et.addEventListener('click', () => this.emptyTrash());
      const nb = q('.cg-new'); if (nb) nb.addEventListener('click', () => this.openCreate());
      if (this.bh.configurable) {
        this.bindPop('.cg-colsbtn', '.cg-cols', () => this.renderColsMenu());
        this.bindPop('.cg-viewsbtn', '.cg-views', () => this.renderViewsMenu());
      }
      document.addEventListener('click', (e) => { if (!this.el.contains(e.target)) this.el.querySelectorAll('.cg-pop').forEach((p) => { p.hidden = true; }); });
      this.renderAll();
    }

    bindPop(btnSel, popSel, fill) {
      const btn = this.el.querySelector(btnSel), pop = this.el.querySelector(popSel);
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const open = pop.hidden;
        this.el.querySelectorAll('.cg-pop').forEach((p) => { p.hidden = true; });
        if (open) { fill(); pop.hidden = false; }
      });
      pop.addEventListener('click', (e) => e.stopPropagation());
    }

    renderAll() { this.renderGroupBar(); this.renderHead(); this.renderBody(); }

    // ------------------------------------------------------------------ barra pivot
    renderGroupBar() {
      const bar = this.el.querySelector('.cg-groupbar');
      if (!this.bh.pivot) { bar.hidden = true; bar.style.display = 'none'; bar.innerHTML = ''; return; }
      const chips = this.cfg.groups.map((k, i) => {
        const c = this.col(k);
        return '<span class="cg-chip" draggable="true" data-gk="' + esc(k) + '">' + icon('group-by', 'sm') + '<b>' + (i + 1) + '</b> ' + esc(c.label) +
          '<button type="button" class="cg-x" data-rm="' + esc(k) + '" title="Togli dal raggruppamento">×</button></span>';
      }).join('<span class="cg-arrow">›</span>');
      bar.innerHTML = '<span class="cg-gl">Raggruppa per (pivot):</span>' +
        (chips || '<span class="cg-ghost">trascina qui una o più colonne dell’intestazione; l’ordine delle etichette è l’ordine dei livelli</span>') +
        (this.cfg.groups.length ? '<span class="grow"></span><button type="button" class="btn text sm cg-exp">Espandi tutto</button><button type="button" class="btn text sm cg-col">Comprimi tutto</button>' : '');
      bar.classList.toggle('on', this.cfg.groups.length > 0);
      bar.querySelectorAll('[data-rm]').forEach((b) => b.addEventListener('click', () => { this.cfg.groups = this.cfg.groups.filter((k) => k !== b.dataset.rm); this.collapsed = {}; this.renderAll(); }));
      const ex = bar.querySelector('.cg-exp'), co = bar.querySelector('.cg-col');
      if (ex) ex.addEventListener('click', () => { this.collapsed = {}; this.renderBody(); });
      if (co) co.addEventListener('click', () => { this.collapsed = {}; G.allPaths(this.currentTree()).forEach((p) => { this.collapsed[p] = true; }); this.renderBody(); });
      // trascinamento: i chip si riordinano, le colonne dell'intestazione si aggiungono
      bar.querySelectorAll('.cg-chip').forEach((chip) => {
        chip.addEventListener('dragstart', (e) => { this.drag = { kind: 'chip', key: chip.dataset.gk }; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', chip.dataset.gk); });
        chip.addEventListener('dragend', () => { this.drag = null; bar.classList.remove('over'); });
      });
      bar.ondragover = (e) => { if (this.drag) { e.preventDefault(); bar.classList.add('over'); } };
      bar.ondragleave = () => bar.classList.remove('over');
      bar.ondrop = (e) => {
        e.preventDefault(); bar.classList.remove('over');
        if (!this.drag) return;
        const k = this.drag.key; this.drag = null;
        // posizione di inserimento: prima del chip su cui si rilascia (metà sinistra), altrimenti in coda
        const target = e.target.closest ? e.target.closest('.cg-chip') : null;
        let list = this.cfg.groups.filter((x) => x !== k);
        let at = list.length;
        if (target) { const ti = list.indexOf(target.dataset.gk); const r = target.getBoundingClientRect(); at = ti < 0 ? list.length : (e.clientX < r.left + r.width / 2 ? ti : ti + 1); }
        list.splice(at, 0, k);
        this.cfg.groups = list; this.collapsed = {}; this.page = 0; this.renderAll();
      };
    }

    // ------------------------------------------------------------------ intestazione e filtri
    renderHead() {
      const cols = this.visibleCols(), grouped = this.cfg.groups.length > 0;
      const th = this.el.querySelector('thead');
      const acts = this.meta.actions.length || this.meta.canDelete;
      let h1 = '<tr class="cg-hr"><th class="cg-selcol"><input type="checkbox" class="cg-selall" title="Seleziona / deseleziona tutte le righe filtrate"></th>' + (acts ? '<th class="cg-actcol"></th>' : '') + (grouped ? '<th class="cg-lead">Raggruppamento</th>' : '');
      let h2 = '<tr class="cg-fr"><th></th>' + (acts ? '<th></th>' : '') + (grouped ? '<th></th>' : '');
      cols.forEach((c) => {
        const si = this.cfg.sorts.findIndex((s) => s.key === c.key), s = this.cfg.sorts[si];
        h1 += '<th' + (this.bh.moveColumns || this.bh.pivot ? ' draggable="true"' : '') + ' data-k="' + esc(c.key) + '" class="cg-h' + (G.isNumeric(c) ? ' num' : '') + (s ? ' sorted' : '') + '" style="min-width:' + (c.width || 120) + 'px">' +
          '<span class="cg-hl" title="' + (this.bh.sortColumns ? 'Clic: ordina · Maiusc+clic: ordina anche per questa colonna' : '') + (this.bh.moveColumns ? ' · trascina per spostare' : '') + (this.bh.pivot ? ' · trascina nella barra per raggruppare' : '') + '">' + esc(c.label) +
          (s ? '<span class="cg-sort">' + (s.dir === 'asc' ? '▲' : '▼') + (this.cfg.sorts.length > 1 ? '<sup>' + (si + 1) + '</sup>' : '') + '</span>' : '') + '</span>' +
          (this.bh.pivot ? '<button type="button" class="cg-gb" data-gb="' + esc(c.key) + '" title="Raggruppa per questa colonna">' + icon('group-by', 'sm') + '</button>' : '') + '</th>';
        const f = this.cfg.filters[c.key] || '';
        let inp;
        if (c.type === 'enum') inp = '<select data-f="' + esc(c.key) + '"><option value="">Tutti</option>' + c.options.map((o) => '<option value="' + esc(o.value) + '"' + (f === o.value ? ' selected' : '') + '>' + esc(o.label) + '</option>').join('') + '</select>';
        else if (c.type === 'bool') inp = '<select data-f="' + esc(c.key) + '"><option value="">Tutti</option><option value="si"' + (f === 'si' ? ' selected' : '') + '>Sì</option><option value="no"' + (f === 'no' ? ' selected' : '') + '>No</option></select>';
        else inp = '<input data-f="' + esc(c.key) + '" value="' + esc(f) + '" placeholder="filtro" title="' + esc(FILTER_HELP[G.isNumeric(c) ? 'n' : (c.type === 'date' || c.type === 'datetime') ? 'd' : 't']) + '">';
        h2 += '<th class="cg-f">' + inp + '</th>';
      });
      th.innerHTML = h1 + '</tr>' + h2 + '</tr>';

      th.querySelectorAll('th.cg-h').forEach((cell) => {
        const k = cell.dataset.k;
        if (this.bh.sortColumns) cell.querySelector('.cg-hl').addEventListener('click', (e) => { this.cfg.sorts = G.toggleSort(this.cfg.sorts, k, e.shiftKey); this.renderHead(); this.renderBody(); });
        else cell.querySelector('.cg-hl').style.cursor = 'default';
        const gbBtn = cell.querySelector('.cg-gb');
        if (gbBtn) gbBtn.addEventListener('click', (e) => { e.stopPropagation(); if (!this.cfg.groups.includes(k)) this.cfg.groups.push(k); this.collapsed = {}; this.renderAll(); });
        cell.addEventListener('dragstart', (e) => { this.drag = { kind: 'col', key: k }; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', k); cell.classList.add('dragging'); });
        cell.addEventListener('dragend', () => { this.drag = null; cell.classList.remove('dragging'); th.querySelectorAll('.dropl,.dropr').forEach((x) => x.classList.remove('dropl', 'dropr')); this.el.querySelector('.cg-groupbar').classList.remove('over'); });
        cell.addEventListener('dragover', (e) => {
          if (!this.bh.moveColumns || !this.drag || this.drag.kind !== 'col' || this.drag.key === k) return;
          e.preventDefault();
          const r = cell.getBoundingClientRect(), left = e.clientX < r.left + r.width / 2;
          cell.classList.toggle('dropl', left); cell.classList.toggle('dropr', !left);
        });
        cell.addEventListener('dragleave', () => cell.classList.remove('dropl', 'dropr'));
        cell.addEventListener('drop', (e) => {
          if (!this.bh.moveColumns || !this.drag || this.drag.kind !== 'col') return;
          e.preventDefault();
          const from = this.drag.key, r = cell.getBoundingClientRect(), left = e.clientX < r.left + r.width / 2;
          this.drag = null;
          const order = this.cfg.order.filter((x) => x !== from);
          let at = order.indexOf(k); if (!left) at += 1;
          order.splice(at, 0, from);
          this.cfg.order = order; this.renderHead(); this.renderBody();
        });
      });
      let timer;
      th.querySelectorAll('[data-f]').forEach((i) => {
        const apply = () => { const v = i.value.trim(); if (v) this.cfg.filters[i.dataset.f] = v; else delete this.cfg.filters[i.dataset.f]; this.page = 0; this.renderBody(); };
        if (i.tagName === 'SELECT') i.addEventListener('change', apply);
        else i.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(apply, 180); });
      });
    }

    // ------------------------------------------------------------------ dati visualizzati
    filtered() { return G.applyFilters(this.rows, this.visibleCols().length ? this.cols : this.cols, this.cfg.filters, this.quick); }
    currentTree() { return G.group(this.filtered(), this.cols, this.cfg.groups, this.cfg.aggs, this.cfg.sorts); }

    renderBody() {
      const cols = this.visibleCols(), grouped = this.cfg.groups.length > 0;
      const rows = this.filtered();
      const tb = this.el.querySelector('tbody'), tf = this.el.querySelector('tfoot');
      const acts = this.meta.actions.length || this.meta.canDelete;
      const span = cols.length + (grouped ? 1 : 0) + (acts ? 1 : 0) + 1;
      this.groupIds = {};
      let html = '';
      let shown;
      if (grouped) {
        const fullTree = G.group(rows, this.cols, this.cfg.groups, this.cfg.aggs, this.cfg.sorts);
        // con la pivot le «righe per pagina» contano i gruppi di primo livello
        const gsize = this.cfg.pageSize || 0, gpages = gsize ? Math.max(1, Math.ceil(fullTree.length / gsize)) : 1;
        if (this.page >= gpages) this.page = gpages - 1;
        const tree = gsize ? fullTree.slice(this.page * gsize, this.page * gsize + gsize) : fullTree;
        G.flatten(tree, this.collapsed).forEach((it) => {
          if (it.kind === 'group') {
            const n = it.node;
            const gids = collectIds(n); this.groupIds[it.path] = gids;
            const allIn = gids.length > 0 && gids.every((x) => this.sel.has(x));
            html += '<tr class="cg-grp l' + Math.min(it.level, 3) + '" data-path="' + esc(it.path) + '"><td class="cg-selcol"><input type="checkbox" class="cg-selgrp" data-path="' + esc(it.path) + '"' + (allIn ? ' checked' : '') + ' title="Seleziona le righe del gruppo"></td>' + (acts ? '<td></td>' : '') + '<td class="cg-lead" style="padding-left:' + (10 + it.level * 20) + 'px">' +
              '<span class="cg-tg">' + (it.open ? '▾' : '▸') + '</span> <b>' + esc(this.col(n.key).label) + ':</b> ' + esc(n.label) + ' <span class="muted">(' + n.count + ')</span></td>' +
              cols.map((c) => '<td class="' + (G.isNumeric(c) ? 'num' : '') + '">' + (this.bh.subtotals && G.isNumeric(c) && n.totals[c.key] ? '<b>' + esc(totalText(n.totals[c.key], c)) + '</b>' : '') + '</td>').join('') + '</tr>';
          } else {
            html += this.rowHtml(it.row, cols, true, acts, it.level);
          }
        });
        shown = rows.length;
        if (!rows.length) html = '<tr><td colspan="' + span + '" class="muted cg-empty">Nessun dato.</td></tr>';
        this.renderPager(fullTree.length, gpages, true);
      } else {
        const sorted = G.multiSort(rows, this.cols, this.cfg.sorts);
        const size = this.cfg.pageSize || 0, pages = size ? Math.max(1, Math.ceil(sorted.length / size)) : 1;
        if (this.page >= pages) this.page = pages - 1;
        const slice = size ? sorted.slice(this.page * size, this.page * size + size) : sorted;
        slice.forEach((r) => { html += this.rowHtml(r, cols, false, acts, 0); });
        if (!slice.length) html = '<tr><td colspan="' + span + '" class="muted cg-empty">Nessun dato.</td></tr>';
        shown = sorted.length;
        this.renderPager(sorted.length, pages);
      }
      tb.innerHTML = html;
      // totali generali (su tutte le righe filtrate)
      const tot = G.totals(rows, this.cols, this.cfg.aggs);
      const hasNum = cols.some((c) => G.isNumeric(c));
      tf.innerHTML = this.bh.totals && hasNum && rows.length ? '<tr class="cg-tot"><td></td>' + (acts ? '<td></td>' : '') + (grouped ? '<td class="cg-lead"><b>Totale generale</b> <span class="muted">(' + rows.length + ')</span></td>' : '') +
        cols.map((c, i) => {
          if (!G.isNumeric(c)) return '<td>' + (!grouped && i === 0 ? '<b>Totale</b> <span class="muted">(' + rows.length + ')</span>' : '') + '</td>';
          const t = tot[c.key];
          if (!t) return '<td></td>';
          return '<td class="num"><select class="cg-agg" data-agg="' + esc(c.key) + '" title="Funzione di totalizzazione">' +
            Object.keys(G.AGGS).map((f) => '<option value="' + f + '"' + (t.fn === f ? ' selected' : '') + '>' + G.AGGS[f] + '</option>').join('') + '</select> <b>' + esc(totalText(t, c)) + '</b></td>';
        }).join('') + '</tr>' : '';
      this.el.querySelector('.cg-count').textContent = shown === this.rows.length ? CPQ.num(shown, 0) + ' righe' : CPQ.num(shown, 0) + ' di ' + CPQ.num(this.rows.length, 0) + ' righe';
      this.bindBody();
      this.updateSelUi();
    }

    /**
     * Azioni di riga: le azioni «in linea» sono pulsanti; quelle marcate `menu` e (se prevista) l'eliminazione vanno nel menu a
     * tre puntini, con un separatore prima di «Sposta nel Cestino». Senza azioni di menu, l'eliminazione resta un pulsante.
     */
    /**
     * Azioni di riga: in vista solo quella principale (modifica; se la griglia non ha modifica, la prima azione, es. «Ripristina»);
     * le altre stanno nel menu a tre puntini, con le azioni pericolose e «Sposta nel Cestino» in fondo, dopo un separatore.
     */
    rowPlan() {
      const acts = this.meta.actions;
      const editAct = acts.find((a) => a.name === 'edit');
      let primary = null;
      if (editAct) primary = { act: editAct };
      else if (this.hasEditForm()) primary = { dialog: true };
      else if (acts.length) primary = { act: acts[0] };
      const rest = acts.filter((a) => !(primary && primary.act === a));
      return { primary: primary, normal: rest.filter((a) => a.style !== 'danger'), danger: rest.filter((a) => a.style === 'danger'), del: !!this.meta.canDelete };
    }

    actionsHtml(r) {
      const p = this.rowPlan();
      let h = '<td class="actions nowrap">';
      if (p.primary && p.primary.dialog) h += '<button type="button" class="btn icon sm" data-edit="1" title="Modifica il record">' + icon('edit') + '</button>';
      else if (p.primary) {
        const a = p.primary.act;
        if (a.server) h += '<button type="button" class="btn icon sm ' + (a.style === 'danger' ? 'danger' : '') + '" data-act="' + esc(a.name) + '" title="' + esc(a.label) + '">' + icon(a.icon) + '</button>';
        else h += '<a class="btn icon sm" title="' + esc(a.label) + '" href="' + esc(CPQ.url('/' + a.href.replace('{id}', encodeURIComponent(r.id)))) + '">' + icon(a.icon) + '</a>';
      }
      if (p.normal.length || p.danger.length || p.del) h += '<button type="button" class="btn icon sm" data-more="1" title="Altre azioni" aria-haspopup="menu">' + icon('more') + '</button>';
      h += '</td>';
      return h;
    }

    /** menu a tre puntini della riga (posizionato sul pulsante, fuori dal contenitore scorrevole della tabella) */
    openRowMenu(btn) {
      this.closeRowMenu();
      const tr = btn.closest('tr'), id = tr.dataset.id, row = this.rowById(id);
      const p = this.rowPlan();
      const item = (a) => a.server
        ? '<button type="button" role="menuitem" class="' + (a.style === 'danger' ? 'danger' : '') + '" data-mact="' + esc(a.name) + '">' + icon(a.icon) + esc(a.label) + '</button>'
        : '<a role="menuitem" href="' + esc(CPQ.url('/' + a.href.replace('{id}', encodeURIComponent(id)))) + '">' + icon(a.icon) + esc(a.label) + '</a>';
      const m = document.createElement('div'); m.className = 'cg-rowmenu'; m.setAttribute('role', 'menu');
      let html = p.normal.map(item).join('');
      const tail = p.danger.map(item).join('') + (p.del ? '<button type="button" role="menuitem" class="danger" data-mdel="1">' + icon('delete') + 'Sposta nel Cestino</button>' : '');
      if (tail) html += (p.normal.length ? '<div class="cg-msep" role="separator"></div>' : '') + tail;
      m.innerHTML = html;
      document.body.appendChild(m);
      const r = btn.getBoundingClientRect(), w = m.offsetWidth, hgt = m.offsetHeight;
      m.style.left = Math.max(8, Math.min(window.innerWidth - w - 8, r.right - w)) + 'px';
      m.style.top = (r.bottom + hgt + 8 > window.innerHeight ? Math.max(8, r.top - hgt - 4) : r.bottom + 4) + 'px';
      m.addEventListener('click', (e) => {
        const act = e.target.closest('[data-mact]'), del = e.target.closest('[data-mdel]');
        if (act) { this.closeRowMenu(); this.runAction(id, act.dataset.mact); }
        else if (del) { this.closeRowMenu(); this.remove(id); }
        else if (e.target.closest('a')) this.closeRowMenu();
      });
      this.rowMenu = m;
      this._menuClose = (e) => { if (e.type === 'keydown' ? e.key === 'Escape' : !m.contains(e.target)) this.closeRowMenu(); };
      setTimeout(() => { document.addEventListener('click', this._menuClose, true); document.addEventListener('keydown', this._menuClose); window.addEventListener('scroll', this._menuClose, true); }, 0);
    }

    closeRowMenu() {
      if (!this.rowMenu) return;
      this.rowMenu.remove(); this.rowMenu = null;
      document.removeEventListener('click', this._menuClose, true); document.removeEventListener('keydown', this._menuClose); window.removeEventListener('scroll', this._menuClose, true);
    }

    rowHtml(r, cols, grouped, acts, level) {
      let h = '<tr data-id="' + esc(r.id) + '"' + (this.sel.has(String(r.id)) ? ' class="cg-picked"' : '') + '><td class="cg-selcol"><input type="checkbox" class="cg-selrow"' + (this.sel.has(String(r.id)) ? ' checked' : '') + '></td>' + (acts ? this.actionsHtml(r) : '') + (grouped ? '<td class="cg-lead"></td>' : '');
      cols.forEach((c) => {
        const v = r[c.key], ed = c.editable && this.bh.inlineEdit;          // «Modifica in griglia» disattiva: celle in sola lettura (la scheda di modifica resta)
        let inner;
        if (c.type === 'bool') inner = ed ? '<input type="checkbox" class="cg-bool" data-k="' + esc(c.key) + '"' + (v ? ' checked' : '') + '>' : (v ? '✓' : '—');
        else if (c.upload) inner = v ? icon('doc') + ' ' + esc(v) : '<span class="muted">—</span>';
        else if (c.key === 'vatNumber' && v) inner = '<a href="#" class="atoka-vat" data-vat="' + esc(v) + '" title="Apri i dettagli su ATOKA">' + esc(v) + '</a>';
        else if (c.type === 'enum' && c.key === 'status') inner = '<span class="chip ' + (v === 'EMESSA' ? 'ok' : 'blue') + '">' + esc(G.format(v, c)) + '</span>';
        else inner = esc(G.format(v, c));
        h += '<td class="cg-c' + (G.isNumeric(c) ? ' num' : '') + (ed ? ' ed' : '') + (c.type === 'bool' ? ' ctr' : '') + '" data-k="' + esc(c.key) + '"' + (ed && c.type !== 'bool' ? ' title="Doppio clic per modificare"' : '') + '>' + inner + '</td>';
      });
      return h + '</tr>';
    }

    /** righe per pagina: si applica subito e si salva per l'utente e per questa griglia */
    async setPageSize(n) {
      this.cfg.pageSize = n; this.page = 0;
      const sel = this.el.querySelector('.cg-ppsel'); if (sel) sel.value = String(n);
      this.renderBody();
      try { await CPQ.api('POST', this.base + '/settings', { pageSize: n }); }
      catch (e) { window.toast('Impostazione non salvata: ' + e.message, true); }
    }

    renderPager(total, pages, grouped) {
      const p = this.el.querySelector('.cg-pager'), size = this.cfg.pageSize || 0;
      if (!size || total <= size) { p.innerHTML = ''; return; }          // una sola pagina: nessun paginatore
      p.innerHTML = '<span class="muted">' + (grouped ? 'Gruppi per pagina' : 'Righe per pagina') + '</span> <select class="cg-ps">' + PAGE_SIZES.map((n) => '<option value="' + n + '"' + (size === n ? ' selected' : '') + '>' + (n || 'Tutte') + '</option>').join('') + '</select>' +
        (size ? ' <button type="button" class="btn icon sm cg-prev"' + (this.page === 0 ? ' disabled' : '') + '>' + icon('back') + '</button><span>' + (this.page + 1) + ' / ' + pages + '</span><button type="button" class="btn icon sm cg-next"' + (this.page >= pages - 1 ? ' disabled' : '') + '>' + icon('forward') + '</button>' : '');
      p.querySelector('.cg-ps').addEventListener('change', (e) => this.setPageSize(Number(e.target.value)));
      const pv = p.querySelector('.cg-prev'), nx = p.querySelector('.cg-next');
      if (pv) pv.addEventListener('click', () => { this.page--; this.renderBody(); });
      if (nx) nx.addEventListener('click', () => { this.page++; this.renderBody(); });
    }

    bindBody() {
      const tb = this.el.querySelector('tbody');
      tb.querySelectorAll('tr.cg-grp').forEach((tr) => tr.addEventListener('click', () => { const p = tr.dataset.path; if (this.collapsed[p]) delete this.collapsed[p]; else this.collapsed[p] = true; this.renderBody(); }));
      tb.querySelectorAll('td.ed').forEach((td) => { if (td.dataset.k && !['bool', 'multi'].includes(this.col(td.dataset.k).type)) td.addEventListener('dblclick', () => this.startEdit(td)); });
      tb.querySelectorAll('td.ed').forEach((td) => { if (td.dataset.k && !['bool', 'multi'].includes(this.col(td.dataset.k).type)) td.addEventListener('click', (e) => { if (!e.target.closest('.cg-fill') && !td.querySelector('.cg-editor')) this.selectAnchor(td); }); });
      tb.querySelectorAll('input.cg-bool').forEach((cb) => cb.addEventListener('change', () => this.commit(cb.closest('tr').dataset.id, cb.dataset.k, cb.checked, cb)));
      tb.querySelectorAll('input.cg-selrow').forEach((cb) => cb.addEventListener('change', () => {
        const id = cb.closest('tr').dataset.id;
        if (cb.checked) this.sel.add(String(id)); else this.sel.delete(String(id));
        cb.closest('tr').classList.toggle('cg-picked', cb.checked);
        this.updateSelUi();
      }));
      tb.querySelectorAll('input.cg-selgrp').forEach((cb) => {
        cb.addEventListener('click', (e) => e.stopPropagation());
        cb.addEventListener('change', () => {
          (this.groupIds[cb.dataset.path] || []).forEach((id) => { if (cb.checked) this.sel.add(id); else this.sel.delete(id); });
          this.renderBody();
        });
      });
      tb.querySelectorAll('[data-act]').forEach((b) => b.addEventListener('click', () => this.runAction(b.closest('tr').dataset.id, b.dataset.act)));
      tb.querySelectorAll('[data-more]').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); this.openRowMenu(b); }));
      tb.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', () => this.openEdit(b.closest('tr').dataset.id)));
      this.el.querySelectorAll('select.cg-agg').forEach((s) => s.addEventListener('change', () => { this.cfg.aggs[s.dataset.agg] = s.value; this.renderBody(); }));
    }

    // ------------------------------------------------------------------ selezione righe (casella nella prima colonna)
    /** aggiorna casella «seleziona tutte», contatore e testo del pulsante Esporta */
    updateSelUi() {
      const ids = new Set(this.rows.map((r) => String(r.id)));
      this.sel.forEach((id) => { if (!ids.has(id)) this.sel.delete(id); });         // righe sparite (eliminate, ricaricate)
      const filtered = this.filtered().map((r) => String(r.id));
      const inSel = filtered.filter((id) => this.sel.has(id)).length;
      const all = this.el.querySelector('.cg-selall');
      if (all) {
        all.checked = filtered.length > 0 && inSel === filtered.length;
        all.indeterminate = inSel > 0 && inSel < filtered.length;
        all.onchange = () => {
          filtered.forEach((id) => { if (all.checked) this.sel.add(id); else this.sel.delete(id); });
          this.renderBody();
        };
      }
      const info = this.el.querySelector('.cg-selinfo');
      info.innerHTML = this.sel.size ? '<b>' + CPQ.num(this.sel.size, 0) + (this.sel.size === 1 ? ' selezionata' : ' selezionate') + '</b> <button type="button" class="btn text sm cg-selclear">Deseleziona</button>' +
        (this.meta.bulkDelete ? ' <button type="button" class="btn sm danger cg-seldel">' + icon('delete') + 'Elimina</button>' : '') : '';
      const sd = info.querySelector('.cg-seldel');
      if (sd) sd.addEventListener('click', () => this.deleteSelected());
      const clr = info.querySelector('.cg-selclear');
      if (clr) clr.addEventListener('click', () => { this.sel.clear(); this.renderBody(); });
      const eb = this.el.querySelector('.cg-expbtn');
      if (eb) eb.lastChild.textContent = this.sel.size ? 'Esporta (' + this.sel.size + ')' : 'Esporta';
      eb && (eb.title = this.sel.size ? 'Esporta solo le ' + this.sel.size + ' righe selezionate' : 'Esporta le righe visualizzate');
    }

    // ------------------------------------------------------------------ modifica in cella
    rowById(id) { return this.rows.find((r) => String(r.id) === String(id)); }

    startEdit(td) {
      if (td.querySelector('.cg-editor')) return;
      const c = this.col(td.dataset.k), id = td.closest('tr').dataset.id, row = this.rowById(id);
      if (!row) return;
      const input = makeInput(c, row[c.key]);
      input.classList.add('cg-editor');
      td.classList.add('editing'); td.innerHTML = ''; td.appendChild(input);
      input.focus(); if (input.select) input.select();
      let done = false;
      input.addEventListener('input', () => CPQ.markDirty('cell'));
      input.addEventListener('change', () => CPQ.markDirty('cell'));
      const cancel = () => { if (done) return; done = true; CPQ.clean('cell'); this.renderBody(); };
      const ok = async () => {
        if (done) return;
        const val = input.value;
        const cur = row[c.key] == null ? '' : String(row[c.key]);
        if (val.trim() === cur.trim() || (G.isNumeric(c) && G.parseNum(val) != null && Number(G.parseNum(val)) === Number(row[c.key]))) return cancel();
        const err = this.localCheck(c, val);
        if (err) { td.classList.add('bad'); input.title = err; window.toast(err, true); return; }
        done = true; CPQ.clean('cell');
        await this.commit(id, c.key, val, input);
      };
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); ok(); } else if (e.key === 'Escape') { e.preventDefault(); cancel(); } });
      input.addEventListener('blur', () => setTimeout(() => { if (!done) { if (td.classList.contains('bad')) cancel(); else ok(); } }, 120));
    }

    // ------------------------------------------------------------------ trascinamento del valore (come «maniglia di riempimento» di Excel)
    /** seleziona la cella e mostra la maniglia in basso a destra: trascinandola verso il basso (o l'alto) il valore viene copiato nelle righe attraversate */
    selectAnchor(td) {
      if (!this.bh.inlineEdit) return;                         // «Modifica in griglia» spenta: niente selezione né maniglia
      this.el.querySelectorAll('td.cg-anchor').forEach((x) => { x.classList.remove('cg-anchor'); const h = x.querySelector('.cg-fill'); if (h) h.remove(); });
      td.classList.add('cg-anchor');
      const h = document.createElement('span');
      h.className = 'cg-fill'; h.title = 'Trascina per copiare il valore nelle righe sotto';
      td.appendChild(h);
      h.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation(); this.startFill(td, e); });
    }

    startFill(td, ev) {
      const tb = this.el.querySelector('tbody'), key = td.dataset.k, srcId = td.closest('tr').dataset.id;
      const trs = Array.from(tb.querySelectorAll('tr[data-id]'));
      const from = trs.findIndex((t) => t.dataset.id === String(srcId));
      let to = from;
      const mark = () => {
        tb.querySelectorAll('td.cg-fillrange').forEach((x) => x.classList.remove('cg-fillrange'));
        const a = Math.min(from, to), b = Math.max(from, to);
        for (let i = a; i <= b; i++) if (i !== from) { const c = trs[i].querySelector('td[data-k="' + key + '"]'); if (c) c.classList.add('cg-fillrange'); }
      };
      const move = (e) => {
        const el = document.elementFromPoint(e.clientX, e.clientY), tr = el && el.closest ? el.closest('tr[data-id]') : null;
        if (!tr) return;
        const i = trs.indexOf(tr);
        if (i >= 0 && i !== to) { to = i; mark(); }
      };
      const up = async () => {
        document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up);
        document.body.classList.remove('cg-filling');
        tb.querySelectorAll('td.cg-fillrange').forEach((x) => x.classList.remove('cg-fillrange'));
        if (to === from) return;
        const a = Math.min(from, to), b = Math.max(from, to);
        const ids = []; for (let i = a; i <= b; i++) if (i !== from) ids.push(trs[i].dataset.id);
        await this.fillCells(srcId, key, ids);
      };
      document.body.classList.add('cg-filling');
      document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
    }

    /** copia il valore della cella sorgente nella stessa colonna delle righe indicate (una modifica alla volta, con gli stessi controlli della modifica in cella) */
    async fillCells(srcId, key, ids) {
      if (!this.bh.inlineEdit) return;
      const c = this.col(key), src = this.rowById(srcId);
      if (!src) return;
      const val = String(makeInput(c, src[key]).value == null ? '' : makeInput(c, src[key]).value);
      const err = this.localCheck(c, val);
      if (err) { window.toast(err, true); return; }
      let n = 0;
      try {
        for (const id of ids) {
          const row = this.rowById(id);
          if (!row) continue;
          const cur = row[key] == null ? '' : String(row[key]);
          if (cur.trim() === val.trim() || (G.isNumeric(c) && G.parseNum(val) != null && Number(G.parseNum(val)) === Number(row[key]))) continue;
          const upd = await CPQ.api('PATCH', this.base + '/' + encodeURIComponent(id), { field: key, value: val });
          const i = this.rows.findIndex((r) => String(r.id) === String(id));
          if (i >= 0) this.rows[i] = upd;
          n++;
        }
        window.toast(n ? 'Valore copiato in ' + n + (n === 1 ? ' riga' : ' righe') : 'Nessuna riga da aggiornare');
      } catch (e) {
        window.toast((n ? 'Copiate ' + n + ' righe, poi: ' : '') + e.message, true);
      }
      this.renderBody();
    }

    localCheck(c, v) {
      const t = String(v).trim();
      if (c.required && !t) return c.label + ' è obbligatorio';
      if (c.maxLen && t.length > c.maxLen) return c.label + ': massimo ' + c.maxLen + ' caratteri';
      if (t && G.isNumeric(c)) {
        const n = G.parseNum(t);
        if (n == null) return c.label + ': inserire un numero';
        if (c.min != null && n < c.min) return c.label + ': minimo ' + c.min;
        if (c.max != null && n > c.max) return c.label + ': massimo ' + c.max;
      }
      return null;
    }

    async commit(id, field, value, ctl) {
      try {
        ctl.disabled = true;
        const upd = await CPQ.api('PATCH', this.base + '/' + encodeURIComponent(id), { field: field, value: value });
        const i = this.rows.findIndex((r) => String(r.id) === String(id));
        if (i >= 0) this.rows[i] = upd;
        this.renderBody();
        const td = this.el.querySelector('tr[data-id="' + cssEsc(id) + '"] td[data-k="' + field + '"]');
        if (td) { td.classList.add('saved'); setTimeout(() => td.classList.remove('saved'), 1200); }
      } catch (e) {
        window.toast(e.message, true);
        this.renderBody();                     // ripristina il valore precedente
        const td = this.el.querySelector('tr[data-id="' + cssEsc(id) + '"] td[data-k="' + field + '"]');
        if (td) { td.classList.add('bad'); setTimeout(() => td.classList.remove('bad'), 2500); }
      }
    }

    async remove(id) {
      const row = this.rowById(id);
      if (!(await CPQ.confirm('Spostare nel Cestino «' + (row ? (row.number || row.name || row.code || row.label || row.companyName || id) : id) + '»?', { title: 'Sposta nel Cestino', yes: 'Sì, sposta' }))) return;
      try {
        const res = await CPQ.api('POST', this.base + '/' + encodeURIComponent(id) + '/delete', {});
        this.rows = this.rows.filter((r) => String(r.id) !== String(id)); this.sel.delete(String(id));
        this.renderBody(); window.toast(res.message || 'Eliminato');
      } catch (e) { window.toast(e.message, true); }
    }

    async emptyTrash() {
      if (!this.rows.length) { window.toast('Il Cestino è già vuoto'); return; }
      const ok = await CPQ.confirm('Eliminare definitivamente tutti i ' + this.rows.length + ' elementi del Cestino (di tutti gli utenti)? L’operazione non è reversibile.',
        { title: 'Svuota cestino', yes: 'Sì, svuota', no: 'No', danger: true });
      if (!ok) return;
      try {
        const res = await CPQ.api('POST', '/api/grid/trash/empty', {});
        const d = await CPQ.api('GET', this.base);
        this.meta = d.meta; this.rows = d.rows; this.renderBody();
        window.toast(res.message, res.skipped > 0);
      } catch (e) { window.toast(e.message, true); }
    }

    async runAction(id, name) {
      const a = this.meta.actions.find((x) => x.name === name);
      if (a && a.confirm && !(await CPQ.confirm(a.confirm, { title: a.label, yes: 'Sì, procedi', danger: a.style === 'danger' }))) return;
      try {
        const res = await CPQ.api('POST', this.base + '/' + encodeURIComponent(id) + '/action/' + encodeURIComponent(name), {});
        if (res.redirect) { window.location.href = CPQ.url('/' + res.redirect); return; }          // es. nuova revisione: si apre subito quella in gestione
        const d = await CPQ.api('GET', this.base);
        this.meta = d.meta; this.rows = d.rows; this.cols = this.meta.columns;
        this.renderBody(); window.toast(res.message || 'Fatto');
      } catch (e) { window.toast(e.message, true); }
    }

    // ------------------------------------------------------------------ file collegati alla riga (es. documento Word del modello Power)
    uploadCols() { return this.cols.filter((c) => c.upload); }
    uploadUrl(c, id) { return '/' + c.upload.replace('{id}', encodeURIComponent(id)); }

    /** campi per scegliere il file (e, se presente, scollegarlo) nella scheda; restituisce {col: {input, remove}} */
    addFileFields(box, row) {
      const out = {};
      this.uploadCols().forEach((c) => {
        const wrap = document.createElement('div'); wrap.className = 'cg-fld cg-file';
        const cur = row && row[c.key] ? row[c.key] : '';
        wrap.innerHTML = '<span>' + esc(c.label) + '</span>' +
          (cur ? '<div class="cg-curfile">' + icon('doc') + ' <b>' + esc(cur) + '</b> <a href="' + esc(CPQ.url(this.uploadUrl(c, row.id))) + '">Scarica</a></div>' : '<div class="muted">Nessun documento collegato</div>') +
          '<input type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document">' +
          '<small class="muted">Il file viene salvato nella sottocartella «Power» della cartella dei modelli Word' + (cur ? '; se ne scegli un altro sostituisce quello attuale' : '') + '.</small>';
        box.appendChild(wrap);
        out[c.key] = { col: c, input: wrap.querySelector('input[type=file]') };
      });
      return out;
    }

    /** carica i file scelti per la riga; restituisce la riga aggiornata */
    async applyFiles(files, id, row) {
      const meta = (n) => { const m = document.querySelector('meta[name="' + n + '"]'); return m ? m.content : ''; };
      const headers = {}; if (meta('_csrf')) headers[meta('_csrf_header') || 'X-CSRF-TOKEN'] = meta('_csrf');
      const out = Object.assign({}, row);
      for (const k of Object.keys(files)) {
        const f = files[k], file = f.input.files && f.input.files[0];
        if (file) {
          const fd = new FormData(); fd.append('file', file);
          const res = await fetch(CPQ.url(this.uploadUrl(f.col, id)), { method: 'POST', headers: headers, body: fd, credentials: 'same-origin' });
          const data = await res.json().catch(() => ({}));
          if (!res.ok) throw new Error(data.message || ('Caricamento non riuscito (' + res.status + ')'));
          out[k] = data.file;
        }
      }
      return out;
    }

    // ------------------------------------------------------------------ scheda di modifica del record
    /** la scheda c'e' se la griglia ha colonne modificabili e non ha gia' una propria azione «modifica» (altra pagina) */
    hasEditForm() { return this.cols.some((c) => c.editable) && !this.meta.actions.some((a) => a.name === 'edit'); }

    openEdit(id) {
      const row = this.rowById(id); if (!row) return;
      const fields = this.cols.filter((c) => c.editable && !c.service);
      const dlg = document.createElement('dialog'); dlg.className = 'cg-dialog';
      dlg.innerHTML = '<form method="dialog" autocomplete="off"><h3>Modifica — ' + esc(this.meta.title) + '</h3><div class="cg-form"></div><div class="cg-err alert err" hidden></div>' +
        '<div class="cg-btns"><button type="button" class="btn text cg-cancel">Annulla</button><button type="submit" class="btn">Salva</button></div></form>';
      const box = dlg.querySelector('.cg-form'), inputs = {};
      fields.forEach((c) => {
        const wrap = document.createElement(c.type === 'multi' ? 'div' : 'label'); wrap.className = 'cg-fld' + (c.type === 'multi' ? ' cg-fld-multi' : '');
        wrap.innerHTML = '<span>' + esc(c.label) + (c.required ? ' *' : '') + '</span>';
        const inp = makeInput(c, row[c.key]); inputs[c.key] = inp; wrap.appendChild(inp); box.appendChild(wrap);
      });
      const files = this.addFileFields(box, row);
      if (this.key === 'sedi') this.addGeoFields(box, row, id, inputs, dlg);
      this.bindAutoDescription(inputs, row);
      document.body.appendChild(dlg); dlg.showModal();
      // i valori mostrati sono sempre quelli del record: si riapplicano dopo l'apertura (nessuno stato ereditato dalla scheda precedente)
      fields.forEach((c) => { if (!(this.key === 'power-models' && c.key === 'description')) setInputValue(c, inputs[c.key], row[c.key]); });
      const first = box.querySelector('input,select'); if (first) first.focus();
      let closed = false;                      // dopo la chiusura eventuali 'change' tardivi (perdita del fuoco) non devono riaccendere lo stato di modifica
      const shut = () => { closed = true; CPQ.clean('dlg'); setTimeout(() => CPQ.clean('dlg'), 0); setTimeout(() => CPQ.clean('dlg'), 300); };
      const close = () => { shut(); dlg.close(); dlg.remove(); };
      const touch = () => { if (!closed) CPQ.markDirty('dlg', dlg.querySelector('form')); };
      dlg.addEventListener('input', touch); dlg.addEventListener('change', touch);
      dlg.querySelector('.cg-cancel').addEventListener('click', close);
      dlg.addEventListener('cancel', () => { shut(); dlg.remove(); });
      dlg.querySelector('form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const err = dlg.querySelector('.cg-err'), values = {};
        for (const c of fields) {
          const inp = inputs[c.key];
          const m = ['bool', 'multi'].includes(c.type) ? null : this.localCheck(c, inp.value);
          if (m) { err.hidden = false; err.textContent = m; return; }
          values[c.key] = inputValue(c, inp);
        }
        try {
          let upd = Object.keys(values).length ? await CPQ.api('POST', this.base + '/' + encodeURIComponent(id) + '/update', { values: values }) : row;
          upd = await this.applyFiles(files, id, upd);
          const i = this.rows.findIndex((r) => String(r.id) === String(id)); if (i >= 0) this.rows[i] = upd;
          close(); this.renderBody(); window.toast('Salvato');
        } catch (ex) { err.hidden = false; err.textContent = ex.message; }
      });
    }

    /**
     * sede Sanmarco: pulsante che geolocalizza l'indirizzo salvato e registra le coordinate nel record; in fondo alla scheda, sulla stessa riga,
     * latitudine e longitudine in sola lettura (anche in inserimento, dove restano vuote: le calcola l'agente Geo dopo il salvataggio)
     */
    addGeoFields(box, row, id, inputs, dlg) {
      const fmt = (v) => (v == null || v === '' ? '' : Number(v).toFixed(6));
      const btnWrap = document.createElement('div'); btnWrap.className = 'cg-fld cg-fld-multi cg-geo-row';
      if (row) {
        btnWrap.innerHTML = '<button type="button" class="btn tonal sm cg-geo">' + icon('search') + 'Geolocalizza indirizzo e salva coordinate</button><small class="muted">Usa l\'indirizzo salvato nel record: se lo modifichi, salva prima la scheda.</small>';
        box.appendChild(btnWrap);
      }
      const pair = document.createElement('div'); pair.className = 'cg-fld-multi cg-geo-pair';
      const mk = (label, key) => {
        const l = document.createElement('label'); l.className = 'cg-fld';
        l.innerHTML = '<span>' + label + '</span><input type="text" disabled value="' + esc(fmt(row ? row[key] : '')) + '"' + (row ? '' : ' placeholder="Calcolata dopo il salvataggio"') + '>';
        pair.appendChild(l); return l.querySelector('input');
      };
      const lat = mk('Latitudine (sola lettura)', 'latitude'), lon = mk('Longitudine (sola lettura)', 'longitude');
      box.appendChild(pair);
      if (!row) return;
      const btn = btnWrap.querySelector('.cg-geo');
      btn.addEventListener('click', async () => {
        const err = dlg.querySelector('.cg-err');
        const changed = ['street', 'streetNumber', 'postalCode', 'city', 'province'].some((k) => inputs[k] && String(inputs[k].value || '').trim() !== String(row[k] == null ? '' : row[k]).trim());
        if (changed) { err.hidden = false; err.textContent = 'L\'indirizzo è stato modificato: salva la scheda e poi geolocalizza.'; return; }
        err.hidden = true; btn.disabled = true;
        try {
          const r = await CPQ.api('POST', this.base + '/' + encodeURIComponent(id) + '/geolocate', {});
          row.latitude = r.latitude; row.longitude = r.longitude;
          const i = this.rows.findIndex((x) => String(x.id) === String(id)); if (i >= 0) { this.rows[i].latitude = r.latitude; this.rows[i].longitude = r.longitude; }
          lat.value = fmt(r.latitude); lon.value = fmt(r.longitude);
          window.toast(r.message || 'Coordinate salvate'); this.renderBody();
        } catch (ex) { err.hidden = false; err.textContent = ex.message; }
        btn.disabled = false;
      });
    }

    /**
     * Modelli Power System: la descrizione proposta e' «Model <modello> <core> core <montaggio>»; segue le modifiche di
     * modello / core / montaggio finche' non viene personalizzata a mano.
     */
    bindAutoDescription(inputs, row) {
      if (this.key !== 'power-models' || !inputs.description || !inputs.model || !inputs.cores || !inputs.mounting) return;
      const auto = () => 'Model ' + (inputs.model.value || '').trim() + ' ' + inputs.cores.value + ' core ' + inputs.mounting.value;
      let last = row ? 'Model ' + row.model + ' ' + row.cores + ' core ' + row.mounting : auto();
      if (!row) inputs.description.value = auto();
      const follow = () => { if (inputs.description.value === last || !inputs.description.value) { last = auto(); inputs.description.value = last; } else { last = auto(); } };
      ['model', 'cores', 'mounting'].forEach((k) => { inputs[k].addEventListener('input', follow); inputs[k].addEventListener('change', follow); });
    }

    // ------------------------------------------------------------------ nuovo record
    openCreate() {
      const fields = this.cols.filter((c) => c.creatable && !c.service);
      const dlg = document.createElement('dialog'); dlg.className = 'cg-dialog';
      dlg.innerHTML = '<form method="dialog" autocomplete="off"><h3>Aggiungi — ' + esc(this.meta.title) + '</h3><div class="cg-form"></div><div class="cg-err alert err" hidden></div>' +
        '<div class="cg-btns"><button type="button" class="btn text cg-cancel">Annulla</button><button type="submit" class="btn">Salva</button></div></form>';
      const box = dlg.querySelector('.cg-form');
      const inputs = {};
      fields.forEach((c) => {
        const wrap = document.createElement(c.type === 'multi' ? 'div' : 'label'); wrap.className = 'cg-fld' + (c.type === 'multi' ? ' cg-fld-multi' : '');
        wrap.innerHTML = '<span>' + esc(c.label) + (c.required ? ' *' : '') + '</span>';
        const inp = makeInput(c, c.type === 'bool' ? true : (c.type === 'int' && c.key === 'sortOrder' ? this.nextOrder() : ''));
        inputs[c.key] = inp; wrap.appendChild(inp); box.appendChild(wrap);
      });
      const files = this.addFileFields(box, null);
      if (this.key === 'sedi') this.addGeoFields(box, null, null, inputs, dlg);
      this.bindAutoDescription(inputs, null);
      document.body.appendChild(dlg); dlg.showModal();
      const first = box.querySelector('input,select'); if (first) first.focus();
      dlg.querySelector('.cg-cancel').addEventListener('click', () => { dlg.close(); dlg.remove(); });
      dlg.addEventListener('cancel', () => dlg.remove());
      dlg.querySelector('form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const values = {}, err = dlg.querySelector('.cg-err');
        this.cols.filter((c) => c.creatable && c.service && c.type === 'int').forEach((c) => { values[c.key] = this.nextOrder(); });   // ordine: assegnato in automatico
        for (const c of fields) {
          const inp = inputs[c.key]; values[c.key] = inputValue(c, inp);
          const m = ['bool', 'multi'].includes(c.type) ? null : this.localCheck(c, inp.value);
          if (m) { err.hidden = false; err.textContent = m; return; }
        }
        try {
          let row = await CPQ.api('POST', this.base, { values: values });
          this.rows.push(row);
          try { const withFile = await this.applyFiles(files, row.id, row); this.rows[this.rows.length - 1] = withFile; }
          catch (fe) { window.toast('Record creato, ma il documento non è stato caricato: ' + fe.message, true); }
          dlg.close(); dlg.remove(); this.renderBody(); window.toast('Aggiunto');
        } catch (ex) { err.hidden = false; err.textContent = ex.message; }
      });
    }
    /** «Elimina» sulle righe selezionate: conferma (predefinito No), poi eliminazione definitiva sul server */
    async deleteSelected() {
      const ids = Array.from(this.sel);
      if (!ids.length) return;
      const ok = await CPQ.confirm('Eliminare definitivamente ' + (ids.length === 1 ? 'la riga selezionata' : 'le ' + ids.length + ' righe selezionate') + '? L’operazione non è reversibile.',
        { title: 'Elimina', yes: 'Sì, elimina', no: 'No', danger: true });
      if (!ok) return;
      try {
        const r = await CPQ.api('POST', this.base + '/delete-many', { ids: ids });
        const gone = new Set(ids);
        this.rows = this.rows.filter((x) => !gone.has(String(x.id))); this.sel.clear();
        this.renderAll(); window.toast(r.message || 'Eliminato');
      } catch (e) { window.toast(e.message, true); }
    }

    nextOrder() { const m = this.rows.reduce((a, r) => Math.max(a, Number(r.sortOrder) || 0), 0); return m + (m % 10 === 0 ? 10 : 1); }

    // ------------------------------------------------------------------ colonne e viste salvate
    renderColsMenu() {
      const pop = this.el.querySelector('.cg-cols');
      pop.innerHTML = '<div class="cg-ph">Colonne visibili</div>' + this.cfg.order.map((k) => {
        const c = this.col(k);
        return '<label class="cg-it"><input type="checkbox" data-k="' + esc(k) + '"' + (this.cfg.hidden.includes(k) ? '' : ' checked') + '> ' + esc(c.label) + '</label>';
      }).join('');
      pop.querySelectorAll('input').forEach((i) => i.addEventListener('change', () => {
        const k = i.dataset.k;
        if (i.checked) this.cfg.hidden = this.cfg.hidden.filter((x) => x !== k);
        else if (this.visibleCols().length > 1) this.cfg.hidden.push(k); else i.checked = true;
        this.renderHead(); this.renderBody();
      }));
    }

    getConfig() {
      return { order: this.cfg.order, hidden: this.cfg.hidden, sorts: this.cfg.sorts, filters: this.cfg.filters, groups: this.cfg.groups, aggs: this.cfg.aggs, pageSize: this.cfg.pageSize };
    }
    applyConfig(c) { const ps = this.cfg.pageSize; this.cfg = G.normalizeConfig(c, this.cols); this.cfg.pageSize = ps; this.enforce(); this.collapsed = {}; this.page = 0; this.renderAll(); }

    renderViewsMenu() {
      const pop = this.el.querySelector('.cg-views');
      pop.innerHTML = '<div class="cg-ph">Viste salvate</div>' +
        (this.layouts.length ? this.layouts.map((l) => '<div class="cg-vw" data-id="' + l.id + '"><button type="button" class="cg-use" title="Applica la vista">' + esc(l.name) + '</button>' +
          '<button type="button" class="cg-star' + (l.isDefault ? ' on' : '') + '" title="' + (l.isDefault ? 'Vista predefinita (clic per togliere)' : 'Imposta come predefinita') + '">' + (l.isDefault ? '★' : '☆') + '</button>' +
          '<button type="button" class="cg-rm" title="Elimina la vista">×</button></div>').join('') : '<div class="cg-ghost" style="padding:6px 12px">Nessuna vista salvata</div>') +
        '<div class="cg-sep"></div><button type="button" class="cg-save">Salva la configurazione attuale…</button><button type="button" class="cg-reset">Ripristina impostazioni iniziali</button>';
      pop.querySelectorAll('.cg-vw').forEach((row) => {
        const l = this.layouts.find((x) => String(x.id) === row.dataset.id);
        row.querySelector('.cg-use').addEventListener('click', () => { this.applyConfig(safeParse(l.config)); pop.hidden = true; });
        row.querySelector('.cg-star').addEventListener('click', async () => {
          try { await CPQ.api('POST', this.base + '/layouts/' + l.id + '/default', { on: !l.isDefault }); await this.reloadLayouts(); this.renderViewsMenu(); } catch (e) { window.toast(e.message, true); }
        });
        row.querySelector('.cg-rm').addEventListener('click', async () => {
          if (!(await CPQ.confirm('Eliminare la vista «' + l.name + '»?', { title: 'Elimina vista', yes: 'Sì, elimina', danger: true }))) return;
          try { await CPQ.api('DELETE', this.base + '/layouts/' + l.id); await this.reloadLayouts(); this.renderViewsMenu(); } catch (e) { window.toast(e.message, true); }
        });
      });
      pop.querySelector('.cg-save').addEventListener('click', async () => {
        const ans = await CPQ.ask('Nome della vista (se esiste già viene sostituita):', { title: 'Salva vista', flag: 'Usa come vista predefinita all’apertura della pagina' });
        if (!ans) return;
        try { await CPQ.api('POST', this.base + '/layouts', { name: ans.value, config: JSON.stringify(this.getConfig()), makeDefault: ans.flag }); await this.reloadLayouts(); this.renderViewsMenu(); window.toast('Vista salvata'); }
        catch (e) { window.toast(e.message, true); }
      });
      pop.querySelector('.cg-reset').addEventListener('click', () => { this.quick = ''; this.el.querySelector('.cg-quick').value = ''; this.applyConfig(null); pop.hidden = true; });
    }
    async reloadLayouts() { this.layouts = await CPQ.api('GET', this.base + '/layouts'); }

    /** righe come visualizzate (filtri e ordinamento; con la pivot, nell'ordine dei gruppi) */
    exportRows() {
      // con righe selezionate si esportano solo quelle (nell'ordine visualizzato)
      const rows = this.filtered().filter((r) => !this.sel.size || this.sel.has(String(r.id)));
      if (!this.cfg.groups.length) return G.multiSort(rows, this.cols, this.cfg.sorts);
      const out = [];
      G.flatten(G.group(rows, this.cols, this.cfg.groups, this.cfg.aggs, this.cfg.sorts), {}).forEach((it) => { if (it.kind === 'row') out.push(it.row); });
      return out;
    }

    async exportXlsx() {
      try {
        const meta = (n) => { const m = document.querySelector('meta[name="' + n + '"]'); return m ? m.content : ''; };
        const headers = { 'Content-Type': 'application/json' };
        if (meta('_csrf')) headers[meta('_csrf_header') || 'X-CSRF-TOKEN'] = meta('_csrf');
        const res = await fetch(CPQ.url(this.base + '/export.xlsx'), { method: 'POST', headers: headers, credentials: 'same-origin',
          body: JSON.stringify({ columns: this.visibleCols().map((c) => c.key), rows: this.exportRows().map((r) => String(r.id)) }) });
        if (!res.ok) throw new Error('Esportazione non riuscita (' + res.status + ')');
        const blob = await res.blob();
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = this.key + '.xlsx';
        document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
      } catch (e) { window.toast(e.message, true); }
    }

    /**
     * Google Sheets: il server crea il foglio sul Drive dell'utente (titolo = nome griglia + data/ora; al primo uso Google chiede
     * l'autorizzazione). Se la creazione diretta non e' possibile (Google non configurato, indirizzo http di rete) si ripiega
     * sulla copia negli appunti con un foglio nuovo in cui incollare.
     */
    async exportGoogle() {
      const w = window.open('', '_blank');             // aperto subito, nel gesto dell'utente (evita il blocco dei popup)
      if (w) w.document.write('<p style="font-family:system-ui;padding:24px">Creazione del foglio Google in corso…</p>');
      try {
        const r = await CPQ.api('POST', this.base + '/export/gsheet', { columns: this.visibleCols().map((c) => c.key), rows: this.exportRows().map((x) => String(x.id)) });
        if (r.mode === 'open' && w) { w.location = r.url; window.toast('Foglio Google creato'); return; }
        if (r.mode === 'auth' && w) { w.location = r.authUrl; window.toast('Autorizza l’accesso a Google: il foglio verrà creato subito dopo'); return; }
        // ripiego: copia negli appunti
        const text = G.toTsv(this.exportRows(), this.visibleCols());
        const ok = await copyText(text);
        if (w) w.location = 'https://sheets.new'; else window.open('https://sheets.new', '_blank', 'noopener');
        if (ok) window.toast((r.message ? r.message + '. ' : '') + 'Dati copiati: nel foglio Google incolla con Cmd/Ctrl+V');
        else showCopyDialog(text);
      } catch (e) {
        if (w) w.close();
        window.toast(e.message, true);
      }
    }

    exportCsv() {
      const cols = this.visibleCols(), rows = this.exportRows();
      const blob = new Blob([G.toCsv(rows, cols)], { type: 'text/csv;charset=utf-8' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = this.key + '.csv';
      document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    }
  }

  // ------------------------------------------------------------------ utilita'
  const PAGE_SIZES = [10, 15, 25, 50, 100, 200, 500, 0];
  const FILTER_HELP = {
    t: 'Testo: contiene · =esatto · !non contiene · ^inizia con · a|b uno tra · vuoto',
    n: 'Numeri: 100 · >100 · >=100 · <100 · !=0 · 50..120 (intervallo) · vuoto',
    d: 'Date: 2026 · 10/2026 · 26/10/2026 · >=01/10/2026 · 01/01/2026..31/03/2026',
  };
  /**
   * Copia negli appunti. navigator.clipboard funziona solo in contesto sicuro (HTTPS o localhost): aprendo l'applicazione
   * da un indirizzo http di rete si usa il metodo classico (campo di testo + execCommand).
   */
  async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      try { await navigator.clipboard.writeText(text); return true; } catch (e) { /* si prova il metodo classico */ }
    }
    const ta = document.createElement('textarea');
    ta.value = text; ta.setAttribute('readonly', ''); ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0';
    document.body.appendChild(ta); ta.focus(); ta.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    ta.remove();
    return ok;
  }

  /** se proprio non si riesce a copiare: pannello con il testo gia' selezionato (Cmd/Ctrl+C) */
  function showCopyDialog(text) {
    const dlg = document.createElement('dialog');
    dlg.className = 'cpq-confirm';
    dlg.style.width = 'min(560px, 94vw)';
    dlg.innerHTML = '<h3>Copia i dati</h3><p>Il browser non permette la copia automatica: premi <b>Cmd/Ctrl+C</b> (il testo è già selezionato), poi incolla con <b>Cmd/Ctrl+V</b> nel foglio Google appena aperto.</p>' +
      '<textarea readonly style="width:100%;height:160px;font:12px ui-monospace,Menlo,monospace;border:1px solid var(--outline);border-radius:6px;padding:8px;background:var(--surface);color:var(--on-surface)"></textarea>' +
      '<div class="cpq-confirm-btns"><button type="button" class="btn">Chiudi</button></div>';
    dlg.querySelector('textarea').value = text;
    dlg.querySelector('button').addEventListener('click', () => dlg.close());
    dlg.addEventListener('close', () => dlg.remove());
    document.body.appendChild(dlg);
    dlg.showModal();
    const ta = dlg.querySelector('textarea'); ta.focus(); ta.select();
  }

  function safeParse(s) { try { return JSON.parse(s); } catch (e) { return null; } }
  function cssEsc(s) { return String(s).replace(/["\\]/g, '\\$&'); }
  function collectIds(node) {
    if (node.rows) return node.rows.map((r) => String(r.id));
    return (node.children || []).reduce((a, c) => a.concat(collectIds(c)), []);
  }
  function totalText(t, c) {
    if (t.value == null) return '';
    return t.fn === 'count' ? CPQ.num(t.value, 0) : G.fmtNumber(t.value, c);
  }
  /** campo di input adatto al tipo di colonna */
  /** riporta un campo al valore del record (usato all'apertura della scheda, per non ereditare stati del browser) */
  function setInputValue(c, inp, value) {
    if (c.type === 'multi') {
      const have = new Set(String(value == null ? '' : value).split(',').filter(Boolean));
      inp.querySelectorAll('input').forEach((cb) => { cb.checked = have.has(cb.value); });
    } else if (c.type === 'bool') inp.checked = !!value;
    else if (inp.tagName === 'SELECT') inp.value = value == null ? '' : String(value);
    else inp.value = value == null ? '' : (G.isNumeric(c) ? String(value).replace('.', ',') : String(value));
  }

  /** valore da inviare al server per il campo (casella, scelta multipla o testo) */
  function inputValue(c, inp) {
    if (c.type === 'bool') return inp.checked;
    if (c.type === 'multi') return Array.from(inp.querySelectorAll('input:checked')).map((x) => x.value);
    return inp.value;
  }

  function makeInput(c, value) {
    let el;
    if (c.type === 'multi') {                                                 // scelta multipla: elenco di caselle
      const have = new Set(String(value == null ? '' : value).split(',').filter(Boolean));
      el = document.createElement('div'); el.className = 'cg-multi';
      el.innerHTML = (c.options || []).map((o) => '<label class="check"><input type="checkbox" autocomplete="off" value="' + esc(o.value) + '"> ' + esc(o.label) + '</label>').join('');
      el.querySelectorAll('input').forEach((cb) => { cb.checked = have.has(cb.value); cb.defaultChecked = cb.checked; });
    } else if (c.type === 'enum' || (c.options && c.options.length)) {         // elenco chiuso (anche per i numeri interi)
      el = document.createElement('select');
      el.innerHTML = c.options.map((o) => '<option value="' + esc(o.value) + '"' + ((value == null ? '' : String(value)) === o.value ? ' selected' : '') + '>' + esc(o.label) + '</option>').join('');
    } else if (c.type === 'bool') {
      el = document.createElement('input'); el.type = 'checkbox'; el.checked = !!value;
    } else {
      el = document.createElement('input');
      if (c.type === 'date') { el.type = 'date'; el.value = value || ''; }
      else {
        el.type = 'text'; if (G.isNumeric(c)) el.inputMode = 'decimal';
        el.value = value == null ? '' : (G.isNumeric(c) ? String(value).replace('.', ',') : String(value));
        if (c.maxLen) el.maxLength = c.maxLen;
      }
    }
    return el;
  }

  document.querySelectorAll('.cpgrid[data-grid]').forEach((el) => new Grid(el, el.dataset.grid).init());
})();
