#!/usr/bin/env python3
"""catalog.json -> src/main/resources/db/migration/V2__seed_catalog.sql   (uso: python3 -I tools/gen_seed.py)"""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
cat = json.load(open(os.path.join(HERE, "catalog.json")))
INF = 10**9

def q(s):
    return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"
def n(v):
    return "NULL" if v is None else repr(round(float(v), 5))

out = ["-- GENERATO da tools/gen_seed.py a partire dal listino 'dipartimentali rev3' - non modificare a mano", ""]
ids = {"app": 0, "mode": 0, "drv": 0, "grp": 0, "tier": 0, "mod": 0, "rule": 0}
def nid(k):
    ids[k] += 1
    return ids[k]

for ai, a in enumerate(cat):
    app_id = nid("app")
    notes = " | ".join(a["notes"]) if a["notes"] else None
    out.append(f"INSERT INTO application (id, code, name, family, maintenance_pct, needs_review, notes, source_sheet, sort_order) VALUES "
               f"({app_id}, {q(a['code'])}, {q(a['name'])}, {q(a['family'])}, {a['maintenance_pct']}, {str(a['review']).upper()}, {q(notes)}, {q(a['sheet'])}, {ai * 10});")
    for si, m in enumerate(a["modes"]):
        out.append(f"INSERT INTO app_mode (id, application_id, code, label, is_default, sort_order) VALUES ({nid('mode')}, {app_id}, {q(m['code'])}, {q(m['label'])}, {str(m['default']).upper()}, {si * 10});")
    drv = {}
    for si, d in enumerate(a["drivers"]):
        did = nid("drv"); drv[d["code"]] = did
        out.append(f"INSERT INTO app_driver (id, application_id, code, label, license_code, unit_label, sort_order) VALUES ({did}, {app_id}, {q(d['code'])}, {q(d['label'])}, {q(d['license_code'])}, {q(d['unit'])}, {si * 10});")
    grp = {}
    for si, g in enumerate(a["groups"]):
        gid = nid("grp"); grp[g["code"]] = gid
        out.append(f"INSERT INTO price_group (id, application_id, driver_id, code, label, base_mode, sort_order) VALUES ({gid}, {app_id}, {drv[g['driver']]}, {q(g['code'])}, {q(g['label'])}, {q(g['base_mode'])}, {si * 10});")
        for mode, amt in (g.get("fixed_base") or {}).items():
            out.append(f"INSERT INTO price_group_base (group_id, mode_code, amount) VALUES ({gid}, {q(mode)}, {n(amt)});")
        for ti, t in enumerate(g["tiers"]):
            tid = nid("tier")
            to = "NULL" if t["to"] >= INF else t["to"]
            out.append(f"INSERT INTO pricing_tier (id, group_id, from_qty, to_qty, label, pct, sort_order) VALUES ({tid}, {gid}, {t['from']}, {to}, {q(t['label'])}, {n(t['pct'])}, {ti * 10});")
            for mode, up in (t.get("unit_by_mode") or {}).items():
                out.append(f"INSERT INTO pricing_tier_price (tier_id, mode_code, unit_price) VALUES ({tid}, {q(mode)}, {n(up)});")
    for m in a["modules"]:
        mid = nid("mod")
        gid = grp.get(m["group"]) if m["group"] else None
        out.append("INSERT INTO module (id, application_id, group_id, code, print_code, description, section, kind, qty_enabled, excl_group, default_selected, included, maintenance_pct, onetime_price, link_text, note, sort_order) VALUES "
                   f"({mid}, {app_id}, {gid if gid else 'NULL'}, {q(m['code'])}, {q(m.get('print_code'))}, {q(m['description'])}, {q(m['section'])}, {q(m['kind'])}, "
                   f"{str(m['qty_enabled']).upper()}, {q(m['excl_group'])}, {str(m['default_selected']).upper()}, {str(m['included']).upper()}, {n(m['maintenance_pct'])}, "
                   f"{n(m.get('onetime') or 0)}, {q(m['link'])}, {q(m['note'])}, {m['sort']});")
        if m["section"] != "LINK" and not m["included"]:
            for mode, price in m["prices"].items():
                out.append(f"INSERT INTO module_price (module_id, mode_code, price) VALUES ({mid}, {q(mode)}, {n(price if price is not None else 0)});")
        else:
            out.append(f"INSERT INTO module_price (module_id, mode_code, price) VALUES ({mid}, 'STD', 0);")
        for r in m["rules"]:
            out.append(f"INSERT INTO module_rule (id, module_id, rule_type, target_codes, severity, message) VALUES ({nid('rule')}, {mid}, {q(r['type'])}, {q(r['target'])}, {q(r['severity'])}, {q(r['message'])});")
    out.append("")

for seq, tbl, k in (("application_id_seq", "application", "app"), ("app_mode_id_seq", "app_mode", "mode"), ("app_driver_id_seq", "app_driver", "drv"),
                    ("price_group_id_seq", "price_group", "grp"), ("pricing_tier_id_seq", "pricing_tier", "tier"), ("module_id_seq", "module", "mod"),
                    ("module_rule_id_seq", "module_rule", "rule")):
    out.append(f"SELECT setval('{seq}', (SELECT COALESCE(MAX(id), 1) FROM {tbl}));")
dest = os.path.join(HERE, "..", "src", "main", "resources", "db", "migration", "V2__seed_catalog.sql")
open(dest, "w", encoding="utf8").write("\n".join(out) + "\n")
print("scritto", dest, len(out), "righe")
