#!/usr/bin/env python3
"""Genera la migrazione SQL dell'applicazione «AGGIUNTA MODULI» (CRM_JGAL_MODULI, famiglia GALILEO) dal foglio «AGGIUNTA NUOVI MODULI»
del listino JGalileo: moduli con prezzo, area (intestazioni di sezione del foglio) e prerequisiti (colonna F + commenti), profilo di calcolo AGG
con la tabella utenti / moltiplicatori letta dalle celle H16:M21.

Come funziona la tabella H16:M21 (formule del foglio):
  L17 = C167 = somma dei moduli selezionati (colonna E). Le fasce (H = da, I = a) sono sugli utenti B7:
  - fino a 10 utenti (I17): nessun costo per utente, il totale e' la somma dei moduli (P17 = L17);
  - oltre: ogni utente della fascia costa  K = L17 * M / 100  (M = moltiplicatore %: 3,5 / 2,4 / 1,4 / 1,3);
    O = utenti che cadono nella fascia, P = K * O; totale configurazione P23 = SUM(P17:P22) = listino di vendita (C169 -> L28);
  - manutenzione annua = listino * 18% (L29).
Uso: python3 -I tools/galileo/gen_seed_agg.py [listino.xlsm] > src/main/resources/db/migration/V82__galileo_aggiunta_moduli.sql"""
import json, os, re, sys
import openpyxl

HERE = os.path.dirname(os.path.abspath(__file__))
XLSM = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "documenti", "20260930 listino_jgalileo_rev5.xlsm")
ws = openpyxl.load_workbook(XLSM, data_only=False)["AGGIUNTA NUOVI MODULI"]
d = json.load(open(os.path.join(HERE, "profiles.json"), encoding="utf-8"))
APP_CODE, APP_NAME = "CRM_JGAL_MODULI", "AGGIUNTA MODULI"


def parse_prereq(text):
    if not text:
        return None
    toks = text.replace(",", " ").replace("-", " ").split()
    any_ = any(x.lower() == "o" for x in toks)
    codes = [x.upper() for x in toks if x.lower() != "o" and re.fullmatch(r"[A-Za-z0-9_]+", x)]
    if not codes or text.strip().upper() == "VEDERE COMMENTO":
        return None
    return ("REQUIRES_ANY" if any_ else "REQUIRES", codes)


def code_desc(b):
    m = re.match(r"\s*([A-Za-z0-9_]+)\s*-\s*(.*)$", b.strip()) or re.match(r"\s*(SMI[A-Za-z0-9_]+)\s+(.*)$", b.strip())
    return (m.group(1).upper(), m.group(2).strip()) if m else None


# moduli e aree: le righe senza prezzo sono intestazioni di area (B10 «MODULI BASE», B20, B38 ...)
area, mods, order = None, [], 0
for r in range(10, 163):
    b, c = ws.cell(r, 2).value, ws.cell(r, 3).value
    if not isinstance(b, str) or not b.strip():
        continue
    if c is None:
        area = b.strip()
        continue
    cd = code_desc(b)
    if not cd:
        raise SystemExit("riga %d non riconosciuta: %r" % (r, b))
    order += 10
    pc = ws.cell(r, 6)
    texts = [str(pc.value).strip() if pc.value else None, pc.comment.text.split("\n", 1)[-1].strip() if pc.comment else None]
    pr = next((x for x in (parse_prereq(t) for t in texts) if x), None)
    mods.append({"c": cd[0], "d": cd[1], "a": area, "o": order, "p": c, "pr": pr, "row": r})
codes = {m["c"] for m in mods}
rules = []
for m in mods:
    if not m["pr"]:
        continue
    targets = [t for t in m["pr"][1] if t != m["c"]]
    if not targets:
        continue
    unknown = [t for t in targets if t not in codes]
    txt = ("Richiede " if m["pr"][0] == "REQUIRES" else "Richiede almeno uno tra ") + ", ".join(targets)
    if unknown:
        txt += " (non presente nel listino: " + ", ".join(unknown) + ")"
    rules.append({"c": m["c"], "t": m["pr"][0], "g": ",".join(targets), "s": "WARNING" if unknown else "ERROR", "m": txt})

# fasce utenti H17:I21 e moltiplicatori M18:M21 (la prima fascia e' inclusa nella somma dei moduli)
tiers = []
for q, r in enumerate(range(17, 22), start=1):
    frm = ws.cell(r, 8).value or 1
    to = ws.cell(r, 9).value
    to = None if to is None or to >= 10 ** 9 else int(to)
    pct = ws.cell(r, 13).value if r > 17 else None
    tiers.append({"q": q, "f": int(frm), "t": to, "p": pct})
payload = {"mods": [{k: m[k] for k in ("c", "d", "a", "o", "p")} for m in mods], "rules": rules, "tiers": tiers}
js = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
assert "$agg$" not in js and "${" not in js

print("-- GENERATO da tools/galileo/gen_seed_agg.py dal foglio «AGGIUNTA NUOVI MODULI» del listino JGalileo.")
print("-- Applicazione «%s» (%s, famiglia GALILEO): %d moduli con area e prerequisiti; profilo di calcolo AGG («MODULI AGGIUNTIVI»)." % (APP_NAME, APP_CODE, len(mods)))
print("-- Prezzo = somma dei moduli selezionati; fino a 10 utenti incluso, poi per ogni utente di fascia: somma moduli x moltiplicatore % (tabella H16:M21).")
print("ALTER TABLE galileo_profile DROP CONSTRAINT IF EXISTS galileo_profile_kind_check;")
print("ALTER TABLE galileo_profile ADD CONSTRAINT galileo_profile_kind_check CHECK (kind IN ('NEW', 'CONS', 'MIG', 'MIGCONS', 'IMMOB', 'AGG'));")
print("CREATE TEMP TABLE _agg ON COMMIT DROP AS SELECT $agg$\n" + js + "\n$agg$::jsonb AS j;")
print("""
INSERT INTO application (code, name, family, description, maintenance_pct, additional_reduction_pct, active, sort_order)
    SELECT '%(code)s', '%(name)s', 'GALILEO', '%(name)s', 18, 0, TRUE, 2020
    WHERE NOT EXISTS (SELECT 1 FROM application a WHERE a.code = '%(code)s' AND a.deleted_at IS NULL);

INSERT INTO module (application_id, code, description, section, kind, default_selected, included, sort_order, area)
    SELECT a.id, m->>'c', m->>'d', 'FIXED', 'LICENSE', FALSE, FALSE, (m->>'o')::int, m->>'a'
    FROM _agg g CROSS JOIN LATERAL jsonb_array_elements(g.j->'mods') m
         JOIN application a ON a.code = '%(code)s' AND a.deleted_at IS NULL
    WHERE NOT EXISTS (SELECT 1 FROM module x WHERE x.application_id = a.id AND x.code = m->>'c' AND x.deleted_at IS NULL);

INSERT INTO module_price (module_id, mode_code, price)
    SELECT mo.id, 'STD', (m->>'p')::numeric
    FROM _agg g CROSS JOIN LATERAL jsonb_array_elements(g.j->'mods') m
         JOIN application a ON a.code = '%(code)s' AND a.deleted_at IS NULL
         JOIN module mo ON mo.application_id = a.id AND mo.code = m->>'c' AND mo.deleted_at IS NULL
    ON CONFLICT (module_id, mode_code) DO NOTHING;

INSERT INTO module_rule (module_id, rule_type, target_codes, severity, message)
    SELECT mo.id, r->>'t', r->>'g', r->>'s', r->>'m'
    FROM _agg g CROSS JOIN LATERAL jsonb_array_elements(g.j->'rules') r
         JOIN application a ON a.code = '%(code)s' AND a.deleted_at IS NULL
         JOIN module mo ON mo.application_id = a.id AND mo.code = r->>'c' AND mo.deleted_at IS NULL
    WHERE NOT EXISTS (SELECT 1 FROM module_rule x WHERE x.module_id = mo.id AND x.deleted_at IS NULL);

-- profilo di calcolo: nessun prezzo di configurazione, nessuna V11
INSERT INTO galileo_profile (application_id, kind, label, source_sheet, module_discount_pct, new_discount_pct, v11_discount, cons_divisor, aggiunta_supported, pro_rata_below_first, sort_order)
    SELECT a.id, 'AGG', 'Moduli aggiuntivi', 'AGGIUNTA NUOVI MODULI', 0, 0, FALSE, 1, FALSE, FALSE, 6
    FROM application a WHERE a.code = '%(code)s' AND a.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM galileo_profile p WHERE p.application_id = a.id AND p.kind = 'AGG');

INSERT INTO galileo_tier (profile_id, tier_set, seq, from_users, to_users, pct)
    SELECT gp.id, 'MAIN', (t->>'q')::int, (t->>'f')::int, (t->>'t')::int, (t->>'p')::numeric
    FROM _agg g CROSS JOIN LATERAL jsonb_array_elements(g.j->'tiers') t
         JOIN application a ON a.code = '%(code)s' AND a.deleted_at IS NULL
         JOIN galileo_profile gp ON gp.application_id = a.id AND gp.kind = 'AGG'
    ON CONFLICT DO NOTHING;

-- ogni modulo e' una funzione aggiuntiva (FA) addebitata se selezionato (regola D)
INSERT INTO galileo_module (profile_id, module_id, flag, e_rule, g_counted, sheet_order)
    SELECT gp.id, mo.id, 'FA', 'D', FALSE, (m->>'o')::int
    FROM _agg g CROSS JOIN LATERAL jsonb_array_elements(g.j->'mods') m
         JOIN application a ON a.code = '%(code)s' AND a.deleted_at IS NULL
         JOIN galileo_profile gp ON gp.application_id = a.id AND gp.kind = 'AGG'
         JOIN module mo ON mo.application_id = a.id AND mo.code = m->>'c' AND mo.deleted_at IS NULL
    ON CONFLICT DO NOTHING;""" % {"code": APP_CODE, "name": APP_NAME})
