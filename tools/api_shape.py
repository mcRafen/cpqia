#!/usr/bin/env python3
"""
catalog.json -> src/test/resources/catalog-api.json
Stessa forma JSON (e stessi id sequenziali) di GET /offers/api/catalog: serve ai test Java del motore di calcolo
(PricingGoldenTest), ai test JS del wizard e all'anteprima grafica.   Uso: python3 -I tools/api_shape.py
"""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
cat = json.load(open(os.path.join(HERE, "catalog.json")))
INF = 10**9
ids = {"app": 0, "drv": 0, "grp": 0, "tier": 0, "mod": 0, "rule": 0}
def nid(k):
    ids[k] += 1
    return ids[k]

def strip(d):
    return {k: v for k, v in d.items() if v is not None}

out = []
for a in cat:
    app_id = nid("app")
    drv = {}; drivers = []
    for d in a["drivers"]:
        did = nid("drv"); drv[d["code"]] = did
        drivers.append(strip({"id": did, "code": d["code"], "label": d["label"], "licenseCode": d["license_code"], "unitLabel": d["unit"]}))
    grp = {}; groups = []
    for g in a["groups"]:
        gid = nid("grp"); grp[g["code"]] = gid
        tiers = []
        for t in g["tiers"]:
            tiers.append(strip({"id": nid("tier"), "fromQty": t["from"], "toQty": None if t["to"] >= INF else t["to"], "label": t["label"],
                                "pct": t["pct"], "unitByMode": t.get("unit_by_mode") or {}}))
        groups.append(strip({"id": gid, "code": g["code"], "label": g["label"], "driverId": drv[g["driver"]], "baseMode": g["base_mode"],
                             "fixedBase": g.get("fixed_base") or {}, "tiers": tiers}))
    modules = []
    for m in a["modules"]:
        mid = nid("mod")
        rules = [strip({"id": nid("rule"), "type": r["type"], "targetCodes": r["target"], "severity": r["severity"], "message": r["message"]}) for r in m["rules"]]
        if m["section"] == "LINK" or m["included"]:
            prices = {"STD": 0.0}
        else:
            prices = {k: (v if v is not None else 0.0) for k, v in m["prices"].items()}
        modules.append(strip({"id": mid, "code": m["code"], "printCode": m.get("print_code"), "description": m["description"],
                              "groupId": grp.get(m["group"]) if m["group"] else None, "section": m["section"], "kind": m["kind"],
                              "qtyEnabled": m["qty_enabled"], "exclGroup": m["excl_group"], "defaultSelected": m["default_selected"],
                              "included": m["included"], "maintenancePct": m["maintenance_pct"], "onetimePrice": m.get("onetime") or 0.0,
                              "linkText": m["link"], "note": m["note"], "prices": prices, "rules": rules}))
    out.append(strip({"id": app_id, "code": a["code"], "name": a["name"], "family": a["family"], "maintenancePct": a["maintenance_pct"],
                      "needsReview": a["review"], "active": True, "notes": " | ".join(a["notes"]) if a["notes"] else None,
                      "modes": [{"code": m["code"], "label": m["label"], "preferred": m["default"]} for m in a["modes"]],
                      "drivers": drivers, "groups": groups, "modules": modules}))
dest = os.path.join(HERE, "..", "src", "test", "resources", "catalog-api.json")
os.makedirs(os.path.dirname(dest), exist_ok=True)
json.dump(out, open(dest, "w"), ensure_ascii=False, indent=1)
print("scritto", os.path.normpath(dest), len(out), "applicazioni")
