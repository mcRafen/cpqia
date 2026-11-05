/* Utilita' comuni: CSRF, fetch JSON, snackbar, menu utente, formato euro */
(function () {
  'use strict';

  const meta = (n) => (document.querySelector('meta[name="' + n + '"]') || {}).content || '';
  const ctx = meta('ctx') || '/';

  window.CPQ = {
    ctx: ctx,
    url: (p) => (ctx.endsWith('/') ? ctx.slice(0, -1) : ctx) + p,
    eur: (v) => '€ ' + new Intl.NumberFormat('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: 'always' }).format(v || 0),
    /** numero con separatore delle migliaia sempre (anche a 4 cifre) e fino a maxDec decimali, formato italiano */
    num: (v, maxDec) => new Intl.NumberFormat('it-IT', { maximumFractionDigits: maxDec == null ? 2 : maxDec, useGrouping: 'always' }).format(Number(v) || 0),
    esc: (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])),

    /** chiamata JSON con token CSRF; solleva Error(message) con eventuale .errors per i campi */
    api: async function (method, path, body) {
      const headers = { 'Accept': 'application/json' };
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const token = meta('_csrf');
      if (token) headers[meta('_csrf_header') || 'X-CSRF-TOKEN'] = token;
      const res = await fetch(CPQ.url(path), { method: method, headers: headers, credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body) });
      if (res.redirected && res.url.includes('/login')) { window.location = CPQ.url('/login'); throw new Error('Sessione scaduta'); }
      const ct = res.headers.get('content-type') || '';
      const data = ct.includes('json') ? await res.json() : null;
      if (!res.ok) {
        if (res.status === 401 || (res.redirected && res.url.includes('/login'))) { window.location = CPQ.url('/login'); }
        const e = new Error((data && data.message) || ('Errore ' + res.status));
        e.errors = data && data.errors; e.status = res.status;
        throw e;
      }
      return data;
    },
  };

  /** Recupero dati anagrafici dalla partita IVA: compila i campi del form (name = vatNumber, companyName, streetType, ...). */
  CPQ.bindVatLookup = function (form, btn, msgEl, onExisting) {
    if (!form || !btn) return;
    const say = (cls, html) => { msgEl.hidden = false; msgEl.className = 'span6 alert ' + cls; msgEl.innerHTML = '<span>' + html + '</span>'; };
    const set = (name, value) => {
      const el = form.elements[name]; if (!el || !value) return;
      if (el.tagName === 'SELECT' && ![...el.options].some((o) => o.value === value || o.text === value)) return;
      el.value = value; el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    const run = async () => {
      const vat = form.elements.vatNumber.value.trim();
      if (!vat) { say('warn', 'Inserisci prima la partita IVA.'); return; }
      btn.disabled = true; say('info', 'Ricerca in corso…');
      try {
        const res = await CPQ.api('GET', '/customers/api/vat-lookup?vat=' + encodeURIComponent(vat));
        const r = res.result;
        if (!r.valid) { say('err', CPQ.esc(r.message)); return; }
        set('vatNumber', r.vat);
        if (r.dataAvailable) {
          set('companyName', r.name); set('streetType', r.streetType); set('streetName', r.streetName);
          set('streetNumber', r.streetNumber); set('postalCode', r.postalCode); set('city', r.city); set('province', r.province);
        }
        let html = CPQ.esc(r.message);
        if (res.existing) {
          const c = res.existing;
          html += '<br><b>Attenzione:</b> esiste già l\'account ' + CPQ.esc(c.code) + ' – ' + CPQ.esc(c.companyName) + '.' +
            (onExisting ? ' <button type="button" class="btn sm text" id="vat-use-existing">Usa quella</button>' : '');
        }
        say(res.existing ? 'warn' : (r.dataAvailable ? 'ok' : 'warn'), html);
        const useBtn = msgEl.querySelector('#vat-use-existing');
        if (useBtn) useBtn.addEventListener('click', () => onExisting(res.existing));
      } catch (e) { say('err', CPQ.esc(e.message)); } finally { btn.disabled = false; }
    };
    btn.addEventListener('click', run);
    form.elements.vatNumber.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); run(); } });
  };

  let toastTimer;
  window.toast = function (msg, isErr) {
    const el = document.getElementById('snackbar');
    if (!el) return;
    el.textContent = msg;
    el.classList.toggle('err', !!isErr);
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), isErr ? 6000 : 3500);
  };

  document.addEventListener('click', function (e) {
    const btn = document.getElementById('usermenu-btn');
    const menu = document.getElementById('usermenu');
    if (!btn || !menu) return;
    if (btn.contains(e.target)) { menu.hidden = !menu.hidden; } else if (!menu.contains(e.target)) { menu.hidden = true; }
  });

  // selettore tema: Giorno / Notte / Sistema
  function markTheme() {
    const cur = window.CPQTheme ? window.CPQTheme.get() : 'system';
    document.querySelectorAll('[data-theme-set]').forEach(function (b) { b.classList.toggle('on', b.dataset.themeSet === cur); });
  }
  markTheme();
  document.addEventListener('click', function (e) {
    const btn = document.getElementById('theme-btn'), menu = document.getElementById('theme-menu');
    if (!btn || !menu) return;
    const opt = e.target.closest ? e.target.closest('[data-theme-set]') : null;
    if (opt) { window.CPQTheme.set(opt.dataset.themeSet); markTheme(); menu.hidden = true; return; }
    if (btn.contains(e.target)) { menu.hidden = !menu.hidden; markTheme(); } else if (!menu.contains(e.target)) { menu.hidden = true; }
  });

  /**
   * Pannello web di conferma (al posto di window.confirm). Restituisce una Promise<boolean>.
   * Il valore predefinito è sempre «No»: il fuoco è sul pulsante No e Invio/Esc/clic fuori lo scelgono.
   * opts: { title, yes, no, danger }
   */
  CPQ.confirm = function (message, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      const dlg = document.createElement('dialog');
      dlg.className = 'cpq-confirm';
      dlg.innerHTML = '<h3></h3><p></p><div class="cpq-confirm-btns"><button type="button" class="btn text" data-r="no"></button>' +
        '<button type="button" class="btn' + (opts.danger ? ' danger' : '') + '" data-r="yes"></button></div>';
      dlg.querySelector('h3').textContent = opts.title || 'Conferma';
      dlg.querySelector('p').textContent = message;
      dlg.querySelector('[data-r=no]').textContent = opts.no || 'No';
      dlg.querySelector('[data-r=yes]').textContent = opts.yes || 'Sì';
      let result = false;
      const done = function (v) { result = v; dlg.close(); };
      dlg.addEventListener('click', function (e) {
        const b = e.target.closest ? e.target.closest('[data-r]') : null;
        if (b) done(b.dataset.r === 'yes');
        else if (e.target === dlg) done(false);                // clic sullo sfondo = No
      });
      dlg.addEventListener('close', function () { dlg.remove(); resolve(result); });   // Esc = No
      document.body.appendChild(dlg);
      dlg.showModal();
      dlg.querySelector('[data-r=no]').focus();                // predefinito: No
    });
  };

  /** Pannello web per chiedere un testo (al posto di window.prompt): Promise<string|null> */
  CPQ.ask = function (message, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      const dlg = document.createElement('dialog');
      dlg.className = 'cpq-confirm';
      dlg.innerHTML = '<form method="dialog"><h3></h3><p></p><input type="text" class="cpq-ask" maxlength="80" style="width:100%;height:40px;border:1px solid var(--outline);border-radius:6px;padding:0 10px;font:inherit">' +
        '<label class="check" style="margin-top:12px;display:none"><input type="checkbox" class="cpq-ask-flag"><span class="cpq-ask-flag-l"></span></label>' +
        '<div class="cpq-confirm-btns"><button type="button" class="btn text" data-r="no">Annulla</button><button type="submit" class="btn" data-r="yes">OK</button></div></form>';
      dlg.querySelector('h3').textContent = opts.title || 'Inserisci';
      dlg.querySelector('p').textContent = message;
      if (opts.flag) { const l = dlg.querySelector('.check'); l.style.display = ''; dlg.querySelector('.cpq-ask-flag-l').textContent = opts.flag; }
      let result = null;
      dlg.querySelector('form').addEventListener('submit', function (e) {
        e.preventDefault();
        const v = dlg.querySelector('.cpq-ask').value.trim();
        if (!v) return;
        result = { value: v, flag: dlg.querySelector('.cpq-ask-flag').checked };
        dlg.close();
      });
      dlg.querySelector('[data-r=no]').addEventListener('click', function () { dlg.close(); });
      dlg.addEventListener('close', function () { dlg.remove(); resolve(result); });
      document.body.appendChild(dlg);
      dlg.showModal();
      dlg.querySelector('.cpq-ask').focus();
    });
  };



  // ------------------------------------------------------------------ ricerca inline di comune e provincia (tabelle comuni / province)
  /** Elenco a comparsa sotto un campo di testo. fetchItems(q) -> Promise<[{label, ...}]>; onPick(item) alla scelta. */
  function attachSuggest(input, fetchItems, onPick, minChars) {
    const wrap = input.closest('.field') || input.parentElement;
    wrap.style.position = wrap.style.position || 'relative';
    const list = document.createElement('div');
    list.className = 'ac-list'; list.hidden = true; list.setAttribute('role', 'listbox');
    wrap.appendChild(list);
    input.setAttribute('autocomplete', 'off');
    let items = [], active = -1, timer, seq = 0;
    const close = function () { list.hidden = true; active = -1; };
    const paint = function () {
      list.innerHTML = items.map(function (it, i) { return '<div class="ac-it' + (i === active ? ' on' : '') + '" data-i="' + i + '" role="option">' + it.html + '</div>'; }).join('');
      list.hidden = items.length === 0;
    };
    const pick = function (i) { const it = items[i]; if (!it) return; close(); onPick(it); };
    const run = function () {
      const q = input.value.trim();
      if (q.length < (minChars || 1) && minChars !== 0) { items = []; paint(); return; }
      const my = ++seq;
      fetchItems(q).then(function (res) { if (my !== seq) return; items = res; active = res.length ? 0 : -1; paint(); }).catch(function () { items = []; paint(); });
    };
    input.addEventListener('input', function () { clearTimeout(timer); timer = setTimeout(run, 150); });
    input.addEventListener('focus', function () { if (minChars === 0) run(); });
    input.addEventListener('keydown', function (e) {
      if (list.hidden && e.key === 'ArrowDown') { run(); return; }
      if (list.hidden) return;
      if (e.key === 'ArrowDown') { e.preventDefault(); active = Math.min(active + 1, items.length - 1); paint(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); active = Math.max(active - 1, 0); paint(); }
      else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); pick(active); }
      else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
    });
    list.addEventListener('mousedown', function (e) {                // mousedown: scatta prima del blur del campo
      const el = e.target.closest ? e.target.closest('.ac-it') : null;
      if (el) { e.preventDefault(); pick(Number(el.dataset.i)); }
    });
    input.addEventListener('blur', function () { setTimeout(close, 120); });
  }

  /**
   * Nei moduli con i campi city / province / postalCode: ricerca inline nelle tabelle dei comuni e delle province.
   * Scegliendo un comune si compilano provincia e (se unico) CAP; i CAP multipli sono proposti nel campo CAP.
   */
  CPQ.bindGeoSearch = function (form) {
    const city = form.querySelector('input[name=city]'), prov = form.querySelector('input[name=province]'), cap = form.querySelector('input[name=postalCode]');
    if (!city || !prov || form.dataset.geoBound) return;
    form.dataset.geoBound = '1';
    const fire = function (el) { el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); };
    const set = function (el, v) { if (el && el.value !== v) { el.value = v; fire(el); } };
    attachSuggest(city, function (q) {
      const p = prov.value.trim().length === 2 ? '&prov=' + encodeURIComponent(prov.value.trim()) : '';
      return CPQ.api('GET', '/geo/api/comuni?q=' + encodeURIComponent(q) + p).then(function (r) {
        return r.map(function (c) { return Object.assign({ html: '<b>' + CPQ.esc(c.nome) + '</b> <span class="muted">(' + CPQ.esc(c.sigla) + ') ' + CPQ.esc(c.provincia) + (c.cap ? ' · ' + CPQ.esc(c.cap) : '') + '</span>' }, c); });
      });
    }, function (c) {
      set(city, c.nome); set(prov, c.sigla);
      if (cap) {
        const caps = (c.capList || c.cap || '').split(',').filter(Boolean);
        if (caps.length === 1) set(cap, caps[0]);
        else if (caps.length > 1) { cap.value = ''; fire(cap); cap.dataset.caps = caps.join(','); cap.placeholder = ' '; cap.focus(); cap.dispatchEvent(new Event('focus')); }
      }
    }, 1);
    attachSuggest(prov, function (q) {
      return CPQ.api('GET', '/geo/api/province?q=' + encodeURIComponent(q)).then(function (r) {
        return r.map(function (p) { return Object.assign({ html: '<b>' + CPQ.esc(p.sigla) + '</b> ' + CPQ.esc(p.nome) + ' <span class="muted">' + CPQ.esc(p.regione || '') + '</span>' }, p); });
      });
    }, function (p) { set(prov, p.sigla); }, 0);
    if (cap) {                                     // CAP multipli del comune scelto: elenco a scelta
      attachSuggest(cap, function () {
        return Promise.resolve((cap.dataset.caps || '').split(',').filter(Boolean).map(function (x) { return { html: '<b>' + CPQ.esc(x) + '</b>', cap: x }; }));
      }, function (it) { set(cap, it.cap); }, 0);
    }
    prov.addEventListener('input', function () { prov.value = prov.value.toUpperCase(); });
  };
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('form').forEach(function (f) { CPQ.bindGeoSearch(f); });
  });


  // ------------------------------------------------------------------ menu laterale: voci con sottolivelli espandibili
  // Chiuse in partenza; si aprono da sole se la pagina corrente e' una voce figlia; la scelta dell'utente e' ricordata.
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.nav-group').forEach(function (g) {
      const key = 'cpqia.nav.' + g.dataset.group, btn = g.querySelector('.nav-toggle');
      try { if (localStorage.getItem(key) === '1') g.classList.add('open'); } catch (e) { /* storage non disponibile */ }
      const sync = function () { btn.setAttribute('aria-expanded', g.classList.contains('open') ? 'true' : 'false'); };
      sync();
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        g.classList.toggle('open'); sync();
        try { localStorage.setItem(key, g.classList.contains('open') ? '1' : '0'); } catch (e2) { /* ignora */ }
      });
    });
  });

  // ------------------------------------------------------------------ elenchi a discesa con pannello personalizzato
  // I <select> dei moduli, delle schede e delle tabelle di configurazione vengono sostituiti da un campo con pannello a comparsa
  // (stesso stile dell'applicazione, tastiera completa). Il <select> originale resta nel documento, nascosto: valore ed eventi
  // 'change' continuano a funzionare per il resto del codice.
  const CS_SEL = '.field select, .cg-fld select, .tcell select';
  const chevron = '<svg class="cs-chev" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M7 10l5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const tick = '<svg class="cs-tick" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  let csOpen = null;                                   // elenco attualmente aperto: { close }

  function enhanceSelect(sel) {
    if (sel.multiple || sel.dataset.native !== undefined || sel.classList.contains('cs-native')) return;
    const wrap = document.createElement('div');
    wrap.className = 'cs';
    const btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'cs-btn'; btn.setAttribute('aria-haspopup', 'listbox'); btn.setAttribute('aria-expanded', 'false');
    btn.innerHTML = '<span class="cs-val"></span>' + chevron;
    wrap.appendChild(btn);
    sel.parentNode.insertBefore(wrap, sel);            // prima del select: il suo <label> successivo resta adiacente al select
    sel.classList.add('cs-native'); sel.tabIndex = -1; sel.setAttribute('aria-hidden', 'true');
    const cls = sel.className.split(' ').filter((c) => c && c !== 'cs-native'); cls.forEach((c) => wrap.classList.add('cs-' + c));
    let panel = null, active = -1, typed = '', typedAt = 0;

    const opts = () => Array.from(sel.options);
    const refresh = () => {
      const o = sel.options[sel.selectedIndex];
      const label = o ? o.textContent : '';
      const v = btn.querySelector('.cs-val');
      v.textContent = label;
      v.classList.toggle('cs-ph', !o || o.value === '');
      btn.disabled = sel.disabled;
      wrap.classList.toggle('cs-disabled', sel.disabled);
    };
    const place = () => {
      const r = btn.getBoundingClientRect();
      panel.style.minWidth = Math.max(r.width, 160) + 'px';
      panel.style.left = Math.max(8, Math.min(window.innerWidth - panel.offsetWidth - 8, r.left)) + 'px';
      const below = window.innerHeight - r.bottom, h = panel.offsetHeight;
      panel.style.top = (below < h + 12 && r.top > below ? Math.max(8, r.top - h - 4) : r.bottom + 4) + 'px';
    };
    const paint = () => {
      panel.innerHTML = opts().map((o, i) => '<div class="cs-opt' + (o.selected ? ' sel' : '') + (o.disabled ? ' dis' : '') + (o.value === '' ? ' ph' : '') +
        (i === active ? ' act' : '') + '" role="option" data-i="' + i + '" aria-selected="' + o.selected + '"><span>' + CPQ.esc(o.textContent) + '</span>' + tick + '</div>').join('');
      const a = panel.querySelector('.cs-opt.act'); if (a) a.scrollIntoView({ block: 'nearest' });
    };
    const close = () => {
      if (!panel) return;
      panel.remove(); panel = null; wrap.classList.remove('open'); btn.setAttribute('aria-expanded', 'false');
      document.removeEventListener('mousedown', outside, true); window.removeEventListener('scroll', onScroll, true); window.removeEventListener('resize', close);
      if (csOpen && csOpen.close === close) csOpen = null;
    };
    const outside = (e) => { if (panel && !panel.contains(e.target) && !btn.contains(e.target)) close(); };
    const onScroll = (e) => { if (panel && !panel.contains(e.target)) close(); };
    const choose = (i) => {
      const o = sel.options[i]; if (!o || o.disabled) return;
      close();
      if (sel.selectedIndex !== i) {
        sel.selectedIndex = i; refresh();
        sel.dispatchEvent(new Event('input', { bubbles: true })); sel.dispatchEvent(new Event('change', { bubbles: true }));
      }
      btn.focus();
    };
    const open = () => {
      if (panel || sel.disabled) return;
      if (csOpen) csOpen.close();
      panel = document.createElement('div'); panel.className = 'cs-panel'; panel.setAttribute('role', 'listbox');
      active = Math.max(0, sel.selectedIndex);
      paint();
      (btn.closest('dialog') || document.body).appendChild(panel);     // nelle finestre modali il pannello deve stare dentro la finestra
      place();
      wrap.classList.add('open'); btn.setAttribute('aria-expanded', 'true');
      panel.addEventListener('mousedown', (e) => { e.preventDefault(); const it = e.target.closest('.cs-opt'); if (it) choose(Number(it.dataset.i)); });
      panel.addEventListener('mousemove', (e) => { const it = e.target.closest('.cs-opt'); if (it && Number(it.dataset.i) !== active) { active = Number(it.dataset.i); panel.querySelectorAll('.cs-opt.act').forEach((x) => x.classList.remove('act')); it.classList.add('act'); } });
      document.addEventListener('mousedown', outside, true); window.addEventListener('scroll', onScroll, true); window.addEventListener('resize', close);
      csOpen = { close: close };
    };
    const move = (d) => {
      const n = sel.options.length; if (!n) return;
      let i = active;
      for (let k = 0; k < n; k++) { i = (i + d + n) % n; if (!sel.options[i].disabled) break; }
      active = i; if (panel) paint();
    };
    btn.addEventListener('click', () => (panel ? close() : open()));
    btn.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!panel) open(); else move(e.key === 'ArrowDown' ? 1 : -1);
      } else if ((e.key === 'Enter' || e.key === ' ') ) {
        e.preventDefault();
        if (!panel) open(); else choose(active);
      } else if (e.key === 'Escape' && panel) {
        e.preventDefault(); e.stopPropagation(); close();
      } else if (e.key === 'Tab' && panel) {
        close();
      } else if (e.key === 'Home' && panel) { e.preventDefault(); active = 0; paint(); }
      else if (e.key === 'End' && panel) { e.preventDefault(); active = sel.options.length - 1; paint(); }
      else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) {          // ricerca digitando le prime lettere
        const now = Date.now(); typed = (now - typedAt > 700 ? '' : typed) + e.key.toLowerCase(); typedAt = now;
        const i = opts().findIndex((o) => !o.disabled && o.textContent.trim().toLowerCase().startsWith(typed));
        if (i >= 0) { if (panel) { active = i; paint(); } else choose(i); }
      }
    });
    // valore assegnato dal codice (select.value = ...) e modifiche alle opzioni / allo stato disabilitato
    const proto = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value');
    Object.defineProperty(sel, 'value', { configurable: true, get() { return proto.get.call(sel); }, set(v) { proto.set.call(sel, v); refresh(); } });
    new MutationObserver(() => { refresh(); wrap.classList.toggle('cs-bad', sel.classList.contains('bad')); if (panel) paint(); }).observe(sel, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled', 'selected', 'class'] });
    sel.addEventListener('change', refresh);
    refresh();
  }

  function enhanceAll(root) {
    (root.querySelectorAll ? root.querySelectorAll(CS_SEL) : []).forEach(enhanceSelect);
    if (root.matches && root.matches(CS_SEL)) enhanceSelect(root);
  }
  document.addEventListener('DOMContentLoaded', function () {
    enhanceAll(document);
    new MutationObserver(function (muts) {
      muts.forEach(function (m) { m.addedNodes.forEach(function (n) { if (n.nodeType === 1) enhanceAll(n); }); });
    }).observe(document.body, { childList: true, subtree: true });
  });

  // ------------------------------------------------------------------ modifiche non salvate: conferma prima di abbandonare
  // Si considera «in modifica» un campo toccato dentro un modulo di <main> o nel configuratore offerte; i campi con data-noguard
  // (ricerche, filtri) e le finestre di dialogo non contano. Pannello web con «No» predefinito; il browser chiede conferma
  // (finestra propria, non personalizzabile) solo per chiusura/ricarica/indirizzo digitato.
  const dirty = new Map();                       // chiave -> elemento sorgente (modulo o #wizard)
  CPQ.markDirty = function (key, source) { dirty.set(key || 'page', source || null); };
  CPQ.clean = function (key) { if (key) dirty.delete(key); else dirty.clear(); };
  CPQ.isDirty = function () { return dirty.size > 0; };
  CPQ.confirmLeave = function () {
    return CPQ.confirm('Hai modifiche non salvate. Se continui verranno perse. Vuoi abbandonare la modifica?',
      { title: 'Modifiche non salvate', yes: 'Sì, abbandona', no: 'No, resta qui', danger: true });
  };
  function guardSource(el) {
    if (!el || !el.closest || el.closest('[data-noguard], dialog, .cg')) return null;
    return el.closest('#wizard') || el.closest('main form:not([data-noguard])');
  }
  function touched(e) { const src = guardSource(e.target); if (src && e.target.matches('input, select, textarea')) CPQ.markDirty('page', src); }
  document.addEventListener('input', touched);
  document.addEventListener('change', touched);
  // clic su un collegamento (menu o altra funzione)
  document.addEventListener('click', function (e) {
    if (!dirty.size || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    const a = e.target.closest ? e.target.closest('a[href]') : null;
    if (!a || a.target === '_blank' || a.hasAttribute('download')) return;
    const href = a.getAttribute('href');
    if (!href || href.charAt(0) === '#' || /^(javascript|mailto|tel):/i.test(href)) return;
    e.preventDefault();
    CPQ.confirmLeave().then(function (ok) { if (ok) { CPQ.clean(); window.location.href = a.href; } });
  }, true);
  // invio di un altro modulo (es. Esci, elimina) mentre se ne sta modificando uno
  document.addEventListener('submit', function (e) {
    if (!dirty.size || e.target.dataset.leaveOk === '1') return;
    if (e.target.closest && e.target.closest('dialog')) return;       // i moduli nelle finestre di dialogo non abbandonano la pagina
    const same = Array.from(dirty.values()).includes(e.target);
    if (same) { setTimeout(function () { if (!e.defaultPrevented) CPQ.clean(); }, 0); return; }   // salvataggio del modulo stesso
    e.preventDefault(); e.stopImmediatePropagation();
    const submitter = e.submitter;
    CPQ.confirmLeave().then(function (ok) {
      if (!ok) return;
      CPQ.clean(); e.target.dataset.leaveOk = '1';
      if (e.target.requestSubmit) e.target.requestSubmit(submitter || undefined); else e.target.submit();
    });
  }, true);
  window.addEventListener('beforeunload', function (e) { if (dirty.size) { e.preventDefault(); e.returnValue = ''; } });

  // conferma sulle azioni distruttive: <form data-confirm="..."> (pannello web, predefinito No)
  document.addEventListener('submit', function (e) {
    const f = e.target;
    if (!f.dataset || !f.dataset.confirm || f.dataset.confirmed === '1') return;
    e.preventDefault();
    const submitter = e.submitter;
    CPQ.confirm(f.dataset.confirm, { title: 'Conferma eliminazione', yes: 'Sì, elimina', no: 'No', danger: true }).then(function (ok) {
      if (!ok) return;
      f.dataset.confirmed = '1';
      if (f.requestSubmit) f.requestSubmit(submitter || undefined); else f.submit();
    });
  });
})();


/* schede di Impostazioni: frecce laterali quando le voci non stanno tutte nella larghezza */
(function () {
  document.querySelectorAll('.st-wrap').forEach(function (w) {
    var tabs = w.querySelector('.settings-tabs'), prev = w.querySelector('.st-prev'), next = w.querySelector('.st-next');
    if (!tabs || !prev || !next) return;
    function update() {
      var max = tabs.scrollWidth - tabs.clientWidth;
      prev.hidden = tabs.scrollLeft <= 2;
      next.hidden = max <= 2 || tabs.scrollLeft >= max - 2;
    }
    function step(dir) { tabs.scrollBy({ left: dir * Math.max(160, tabs.clientWidth * 0.6), behavior: 'smooth' }); }
    prev.addEventListener('click', function () { step(-1); });
    next.addEventListener('click', function () { step(1); });
    tabs.addEventListener('scroll', update);
    window.addEventListener('resize', update);
    var act = tabs.querySelector('a.active');
    if (act) tabs.scrollLeft = Math.max(0, act.offsetLeft - (tabs.clientWidth - act.offsetWidth) / 2);   // la scheda corrente resta in vista
    update();
  });
})();

// menu laterale su smartphone/tablet: pannello a scomparsa
(function () {
  function set(open) {
    document.body.classList.toggle('nav-open', open);
    var b = document.getElementById('nav-toggle'); if (b) b.setAttribute('aria-expanded', open ? 'true' : 'false');
  }
  document.addEventListener('click', function (e) {
    if (e.target.closest && e.target.closest('#nav-toggle')) { set(!document.body.classList.contains('nav-open')); return; }
    if (!document.body.classList.contains('nav-open')) return;
    if (e.target.id === 'nav-scrim' || (e.target.closest && e.target.closest('.drawer a'))) set(false);
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') set(false); });
  window.addEventListener('resize', function () { if (window.innerWidth > 1000) set(false); });
})();

// popup animato vicino al campo con l'errore: porta il campo in vista, lo evidenzia e mostra il messaggio
(function () {
  let pop = null, target = null, timer = null, cleanup = null;
  function dismiss() {
    clearTimeout(timer);
    if (cleanup) { cleanup(); cleanup = null; }
    if (target) { target.classList.remove('fld-err', 'fld-shake'); target = null; }
    if (pop) { const p = pop; pop = null; p.classList.add('out'); setTimeout(() => p.remove(), 180); }
  }
  function visible(el) { return !!(el && (el.offsetWidth || el.offsetHeight || el.getClientRects().length)); }

  window.CPQ.fieldError = function (el, msg) {
    dismiss();
    if (!el) { window.toast(msg, true); return; }
    let anchor = el;
    while (anchor && !visible(anchor)) anchor = anchor.parentElement;       // campo nascosto: si ancora al contenitore visibile
    if (!anchor) { window.toast(msg, true); return; }
    const box = anchor.closest('.field') || anchor;
    target = box;
    anchor.scrollIntoView({ block: 'center', behavior: 'auto' });
    try { if (anchor.focus) anchor.focus({ preventScroll: true }); } catch (e) { /* non focalizzabile */ }
    box.classList.add('fld-err'); box.classList.remove('fld-shake'); void box.offsetWidth; box.classList.add('fld-shake');

    pop = document.createElement('div');
    pop.className = 'field-pop'; pop.setAttribute('role', 'alert');
    pop.innerHTML = '<svg class="ic"><use href="#i-warning"/></svg><span></span><button type="button" aria-label="Chiudi">×</button>';
    pop.querySelector('span').textContent = msg;
    pop.querySelector('button').addEventListener('click', dismiss);
    (anchor.closest('dialog') || document.body).appendChild(pop);          // nelle finestre modali il popup deve stare dentro la finestra

    const place = () => {
      if (!pop || !target) return;
      const r = box.getBoundingClientRect(), pw = pop.offsetWidth, ph = pop.offsetHeight;
      const below = r.bottom + ph + 18 <= window.innerHeight || r.top - ph - 18 < 0;
      const top = below ? r.bottom + 10 : r.top - ph - 10;
      const left = Math.max(8, Math.min(r.left + 14, window.innerWidth - pw - 8));
      pop.style.top = top + 'px'; pop.style.left = left + 'px';
      pop.classList.toggle('up', !below);
      pop.style.setProperty('--arrow-x', Math.max(14, Math.min(r.left + Math.min(r.width / 2, 40) - left, pw - 22)) + 'px');
    };
    place();
    requestAnimationFrame(() => { place(); pop && pop.classList.add('in'); });
    const onMove = () => place();
    const onDone = (e) => { if (e.type !== 'click' || !pop || !pop.contains(e.target)) dismiss(); };
    const onKey = (e) => { if (e.key === 'Escape') dismiss(); };
    window.addEventListener('resize', onMove); window.addEventListener('scroll', onMove, true);
    box.addEventListener('input', onDone, true); box.addEventListener('change', onDone, true); box.addEventListener('click', onDone, true);
    document.addEventListener('keydown', onKey);
    cleanup = () => {
      window.removeEventListener('resize', onMove); window.removeEventListener('scroll', onMove, true);
      box.removeEventListener('input', onDone, true); box.removeEventListener('change', onDone, true); box.removeEventListener('click', onDone, true);
      document.removeEventListener('keydown', onKey);
    };
    timer = setTimeout(dismiss, 9000);
  };
  window.CPQ.clearFieldError = dismiss;
})();
