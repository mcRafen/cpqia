#!/usr/bin/env python3
"""Genera la migrazione SQL del catalogo JGalileo da tools/galileo/profiles.json (prodotto da extract.py).
Uso: python3 -I tools/galileo/gen_seed.py > src/main/resources/db/migration/V60__seed_galileo.sql"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
d = json.load(open(os.path.join(HERE, "profiles.json"), encoding="utf-8"))
APPS = {  # mercato -> (codice applicazione, ordine)
    "ALIMENTARE E GDO": ("CRM_JGAL_ALIM_GDO", 2001), "AMMINISTRAZIONE": ("CRM_JGAL_AMM", 2002), "COMMESSA E MACHINERY": ("CRM_JGAL_COMMESSA", 2004),
    "MODA": ("CRM_JGAL_MODA", 2008), "PRODUZIONE IN SERIE": ("CRM_JGAL_PROD_SER", 2010), "VINICOLO": ("CRM_JGAL_VINI", 2011), "COMMERCIALE": ("CRM_JGAL_COMM", 2012)}
master = d["master"]
prereq = d["prereq"]
KIND_ORDER = {"NEW": 1, "CONS": 2, "MIG": 3, "MIGCONS": 4, "IMMOB": 5}

def r2(x):
    return None if x is None else (int(x) if float(x) == int(x) else round(float(x), 4))

apps = []
profiles = []
for market, (code, order) in APPS.items():
    ps = [p for p in d["profiles"] if market in p["markets"]]
    mods = {}
    for p in sorted(ps, key=lambda p: KIND_ORDER[p["kind"]]):
        for m in p["modules"]:
            cur = mods.get(m["code"])
            if cur is None:
                mm = master.get(m["code"])
                mods[m["code"]] = {"c": m["code"], "d": (mm["desc"] if mm else m["desc"]), "a": (mm["area"] if mm else m["area"]),
                                   "o": (mm["order"] if mm else 5000 + m["row"]), "p": m["price"], "f": m["flag"], "inc": m["flag"] in ("CB", "V11")}
    codes = set(mods)
    rules = []
    for c in sorted(mods):
        pr = prereq.get(c)
        if not pr:
            continue
        targets = [t for t in pr["targets"] if t != c]
        if not targets:
            continue
        unknown = [t for t in targets if t not in codes]
        txt = ("Richiede " if pr["type"] == "REQUIRES" else "Richiede almeno uno tra ") + ", ".join(targets)
        if unknown:
            txt += " (non presente nel listino di questo mercato: " + ", ".join(unknown) + ")"
        rules.append({"c": c, "t": pr["type"], "g": ",".join(targets), "s": "WARNING" if unknown else "ERROR", "m": txt})
    apps.append({"market": market, "code": code, "order": order, "modules": sorted(mods.values(), key=lambda x: (x["o"], x["c"])), "rules": rules})
    for p in ps:
        std = {c: mods[c]["p"] for c in mods}
        pm = []
        for m in p["modules"]:
            e = {"c": m["code"], "f": m["flag"], "e": m["e"], "g": 1 if m["g"] else 0, "r": m["row"]}
            if m["price"] is not None and m["price"] != std[m["code"]]:
                e["p"] = m["price"]
            if "ePrice" in m:
                e["ep"] = m["ePrice"]
            if "gPrice" in m:
                e["gp"] = m["gPrice"]
            pm.append(e)
        tiers = []
        for s, lst in p.get("tiers", {}).items():
            for q, t in enumerate(lst, start=1):
                tiers.append({"s": s, "q": q, "f": t["from"], "t": t["to"], "p": t["pct"]})
        cfg = p["cfg"]
        profiles.append({"app": code, "kind": p["kind"], "label": p["label"], "sheet": p["sheet"], "c9": r2(cfg.get("V9")), "c11": r2(cfg.get("V11")),
                         "disc": r2((p.get("discount") or 0) * 100), "discNew": r2((p.get("discountNew", 0.3)) * 100), "v11d": p.get("v11Discount", True),
                         "cd": p.get("consDivisor", 1), "mc": p.get("migCaratterePct"), "mv": p.get("migVisualePct"), "m9": p.get("migV9Flat"),
                         "mu": p.get("maxUsers"), "agg": bool(p.get("aggiuntaSupported")), "pr": bool(p.get("proRataBelowFirst")), "so": KIND_ORDER[p["kind"]],
                         "tiers": tiers, "mods": pm})
payload = {"listino": d["listino"], "apps": apps, "profiles": profiles}
js = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
assert "$galileo$" not in js and "${" not in js
print("-- GENERATO da tools/galileo/gen_seed.py (profili da tools/galileo/extract.py) a partire dal listino JGalileo %s (scadenza %s)." % (d["listino"]["version"], d["listino"]["validUntil"]))
print("-- Applicazioni (mercati) della famiglia GALILEO, moduli con prezzi, aree e vincoli, profili di calcolo, fasce utenti.")
print("-- in alcuni fogli (MIG PRODUZIONE) lo stesso modulo compare in due righe con regole di addebito diverse: la riga fa parte della chiave")
print("ALTER TABLE galileo_module DROP CONSTRAINT galileo_module_pkey;")
print("ALTER TABLE galileo_module ADD PRIMARY KEY (profile_id, module_id, sheet_order);")
print("CREATE TEMP TABLE _gal ON COMMIT DROP AS SELECT $galileo$\n" + js + "\n$galileo$::jsonb AS j;")      # a capo dopo il tag: evita che Flyway legga ${...} come segnaposto
print("""
INSERT INTO galileo_listino (id, name, version, valid_until)
    SELECT 1, j->'listino'->>'name', j->'listino'->>'version', (j->'listino'->>'validUntil')::date FROM _gal;

-- mercati mancanti (COMMERCIALE e' nuovo; gli altri sono gia' in V17)
INSERT INTO application (code, name, family, description, maintenance_pct, active, sort_order)
    SELECT app->>'code', app->>'market', 'GALILEO', app->>'market', 18, TRUE, (app->>'order')::int
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'apps') app
    WHERE NOT EXISTS (SELECT 1 FROM application a WHERE a.code = app->>'code' AND a.deleted_at IS NULL);

-- moduli
INSERT INTO module (application_id, code, description, section, kind, default_selected, included, sort_order, area)
    SELECT a.id, m->>'c', m->>'d', 'FIXED', 'LICENSE', (m->>'inc')::boolean, (m->>'inc')::boolean, (m->>'o')::int, m->>'a'
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'apps') app
         CROSS JOIN LATERAL jsonb_array_elements(app->'modules') m
         JOIN application a ON a.code = app->>'code' AND a.deleted_at IS NULL;

INSERT INTO module_price (module_id, mode_code, price)
    SELECT mo.id, 'STD', (m->>'p')::numeric
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'apps') app
         CROSS JOIN LATERAL jsonb_array_elements(app->'modules') m
         JOIN application a ON a.code = app->>'code' AND a.deleted_at IS NULL
         JOIN module mo ON mo.application_id = a.id AND mo.code = m->>'c' AND mo.deleted_at IS NULL
    WHERE m->>'p' IS NOT NULL;

-- vincoli (prerequisiti)
INSERT INTO module_rule (module_id, rule_type, target_codes, severity, message)
    SELECT mo.id, r->>'t', r->>'g', r->>'s', r->>'m'
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'apps') app
         CROSS JOIN LATERAL jsonb_array_elements(app->'rules') r
         JOIN application a ON a.code = app->>'code' AND a.deleted_at IS NULL
         JOIN module mo ON mo.application_id = a.id AND mo.code = r->>'c' AND mo.deleted_at IS NULL;

-- profili di calcolo
INSERT INTO galileo_profile (application_id, kind, label, source_sheet, cfg_v9, cfg_v11, module_discount_pct, new_discount_pct, v11_discount,
                             cons_divisor, mig_carattere_pct, mig_visuale_pct, mig_v9_flat, max_users, aggiunta_supported, pro_rata_below_first, sort_order)
    SELECT a.id, pr->>'kind', pr->>'label', pr->>'sheet', (pr->>'c9')::numeric, (pr->>'c11')::numeric, (pr->>'disc')::numeric, (pr->>'discNew')::numeric,
           (pr->>'v11d')::boolean, (pr->>'cd')::numeric, (pr->>'mc')::numeric, (pr->>'mv')::numeric, (pr->>'m9')::numeric, (pr->>'mu')::int,
           (pr->>'agg')::boolean, (pr->>'pr')::boolean, (pr->>'so')::int
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'profiles') pr
         JOIN application a ON a.code = pr->>'app' AND a.deleted_at IS NULL;

INSERT INTO galileo_tier (profile_id, tier_set, seq, from_users, to_users, pct)
    SELECT gp.id, t->>'s', (t->>'q')::int, (t->>'f')::int, (t->>'t')::int, (t->>'p')::numeric
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'profiles') pr
         CROSS JOIN LATERAL jsonb_array_elements(pr->'tiers') t
         JOIN application a ON a.code = pr->>'app' AND a.deleted_at IS NULL
         JOIN galileo_profile gp ON gp.application_id = a.id AND gp.kind = pr->>'kind';

INSERT INTO galileo_module (profile_id, module_id, flag, e_rule, g_counted, price, e_price, g_price, sheet_order)
    SELECT gp.id, mo.id, m->>'f', m->>'e', (m->>'g')::int = 1, (m->>'p')::numeric, (m->>'ep')::numeric, (m->>'gp')::numeric, (m->>'r')::int
    FROM _gal g CROSS JOIN LATERAL jsonb_array_elements(g.j->'profiles') pr
         CROSS JOIN LATERAL jsonb_array_elements(pr->'mods') m
         JOIN application a ON a.code = pr->>'app' AND a.deleted_at IS NULL
         JOIN galileo_profile gp ON gp.application_id = a.id AND gp.kind = pr->>'kind'
         JOIN module mo ON mo.application_id = a.id AND mo.code = m->>'c' AND mo.deleted_at IS NULL;""")
