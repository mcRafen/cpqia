"""
Motore di calcolo di RIFERIMENTO (Python). Il motore Java (PricingService) implementa lo stesso algoritmo
ed e' verificato con gli stessi scenari (src/test/resources/golden.json, generato da validate_engine.py
a partire dal ricalcolo LibreOffice del listino Excel).

Algoritmo (replica le formule del listino):
  per ogni gruppo di prezzo (moduli "a utente"):
     base      = somma dei prezzi dei moduli selezionati del gruppo   (oppure base fissa del gruppo)
     TIER1     : se qta>0 -> importo = base          (la prima fascia e' inclusa nel prezzo moduli)
                 per ogni fascia successiva: n = utenti ricadenti nella fascia, importo += n * unitario
                 unitario = prezzo esplicito (per modalita') oppure base * pct / 100
     LINEAR    : importo = qta * unitario (utenti visualizzatori APS)
  moduli a prezzo fisso: LICENSE (manutenzione = pct), ANNUAL (solo canone annuo), ONETIME (una tantum)
"""
INF = 10**9

def mprice(m, mode):
    p = m["prices"]
    v = p.get(mode) if mode in p else p.get("STD")
    return float(v) if v is not None else 0.0

def tier_amount(group, base, qty, mode):
    if qty <= 0:
        return 0.0
    tiers = group["tiers"]
    if group.get("base_mode") != "LINEAR" and base == 0:
        return 0.0          # JPA3 'funzionalita' aggiuntiva' (engine a 0): nessun costo utenti
    if group.get("base_mode") == "LINEAR":
        t = tiers[0]
        u = t["unit_by_mode"].get(mode, t["unit_by_mode"].get("STD", 0.0))
        return qty * u
    amount = base
    for k in range(1, len(tiers)):
        t, prev = tiers[k], tiers[k - 1]
        if qty >= t["to"]:
            n = t["to"] - prev["to"]
        elif qty > t["from"] - 1:
            n = qty - t["from"] + 1
        else:
            n = 0
        ub = t.get("unit_by_mode") or {}
        if mode in ub or "STD" in ub:
            unit = ub.get(mode, ub.get("STD"))
        else:
            unit = base * (t["pct"] or 0.0) / 100.0
        amount += n * unit
    return amount

def calc(app, mode, selected, driver_qty):
    """selected: {indice_modulo: qta}  driver_qty: {codice_driver: qta}"""
    groups = {g["code"]: g for g in app["groups"]}
    lic = 0.0; maint = 0.0; annual = 0.0; onetime = 0.0
    sums = {g: 0.0 for g in groups}
    for i, q in selected.items():
        m = app["modules"][i]
        if q <= 0 or m["section"] == "LINK":
            continue
        if m["group"]:
            sums[m["group"]] += mprice(m, mode) * q
            continue
        t = mprice(m, mode) * q
        if m["kind"] == "LICENSE":
            lic += t
            pct = m["maintenance_pct"] if m.get("maintenance_pct") is not None else app["maintenance_pct"]
            maint += t * pct / 100.0
        elif m["kind"] == "ANNUAL":
            annual += t
        else:
            onetime += t
        onetime += (m.get("onetime") or 0.0) * q
    for g in groups.values():
        qty = driver_qty.get(g["driver"], 0)
        fb = g.get("fixed_base")
        base = (fb.get(mode, fb.get("STD", 0.0)) if fb else sums[g["code"]])
        amt = tier_amount(g, base, qty, mode)
        lic += amt
        maint += amt * app["maintenance_pct"] / 100.0
    return {"license": lic, "onetime": onetime, "maintenance": maint + annual,
            "rent_month": (lic + 4 * (maint + annual)) / 48.0}
