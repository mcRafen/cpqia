"""Motore di riferimento del listino JGalileo (Python), sui profili estratti in profiles.json.
Replica le formule dei fogli Excel (validato contro il ricalcolo LibreOffice da validate.py); il motore Java e' verificato con gli stessi scenari."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))

def load_profiles(path=None):
    return json.load(open(path or os.path.join(HERE, "profiles.json"), encoding="utf-8"))

def tier_total(base, users, tiers, pro_rata=False):
    """prima fascia inclusa; ogni utente delle fasce successive costa base*pct/100.
    pro_rata (solo MODA): la 2a fascia non ha il minimo a zero, quindi sotto i 6 utenti il prezzo scende"""
    if users <= 0 or base <= 0 or not tiers:
        return 0.0
    amt = base
    prev_to = tiers[0]["to"]
    for k, t in enumerate(tiers[1:], start=1):
        to = t["to"] if t["to"] is not None else 10 ** 12
        if users >= to:
            n = to - prev_to
        elif users > t["from"] - 1 or (pro_rata and k == 1):
            n = users - t["from"] + 1
        else:
            n = 0
        if n > 0 or (pro_rata and k == 1):
            amt += base * (t["pct"] or 0) / 100 * n
        prev_to = to
    return amt

def _e_sum(p, D, F, aggiunta):
    s = 0.0
    n5_is_1 = p.get("aggiuntaSupported", False) and not aggiunta          # N5 = 1: nuova configurazione (se il foglio non ha N5 la cella e' vuota)
    for m in p["modules"]:
        pr = m.get("ePrice", m["price"]) or 0
        e = m["e"]
        if not e:
            continue
        in_d, in_f = m["code"] in D, m["code"] in F
        if e == "D" and in_d:
            s += pr
        elif e == "F" and in_f:
            s += pr
        elif e == "G1" and in_d and (m["flag"] == "FA" or not n5_is_1):
            s += pr
        elif e == "DFA" and m["flag"] == "FA" and in_d:
            s += pr
    return s

def _g_sum(p, F):
    return sum((m.get("gPrice", m["price"]) or 0) for m in p["modules"] if m["g"] and m["code"] in F)

def supports_aggiunta(p):
    return bool(p.get("aggiuntaSupported"))

def calc(p, inp):
    """-> importo lordo (listino di vendita) oppure None se la combinazione non e' valida"""
    kind = p["kind"]
    U = inp.get("users", 0)
    rel = inp.get("release", "V9")
    D, F = set(inp.get("D", [])), set(inp.get("F", []))
    aggiunta = bool(inp.get("aggiunta")) and supports_aggiunta(p)
    cfg = p["cfg"].get(rel) if p["cfg"].get(rel) is not None else p["cfg"]["V9"]
    if kind == "IMMOB":
        return cfg if 1 <= U <= p["maxUsers"] else None
    if kind == "NEW":
        if U <= 0:
            return 0.0
        base = _e_sum(p, D, F, False) + cfg
        return tier_total(base, U, p["tiers"]["MAIN"], p.get("proRataBelowFirst", False))
    if kind == "CONS":
        if U <= 0:
            return 0.0
        base = _e_sum(p, D, F, aggiunta) + (0.0 if aggiunta else cfg)
        c124 = tier_total(base, U, p["tiers"]["MAIN"])
        r44 = tier_total(_g_sum(p, F), U, p["tiers"]["NEWMOD"])
        return c124 / p["consDivisor"] + r44
    # MIG / MIGCONS
    K5 = inp.get("source", 0)
    I5 = 1 if rel == "V9" else 4
    if I5 + K5 == 4:
        return None
    o14 = 0.0 if (K5 == 0 or U == 0) else _e_sum(p, D, F, aggiunta) + (0.0 if aggiunta else cfg)
    c124 = tier_total(o14, U, p["tiers"]["MAIN"])
    n24 = {1: c124 * p["migCaratterePct"] / 100, 2: c124 * p["migVisualePct"] / 100, 3: p["migV9Flat"]}.get(K5, 0.0)
    o37 = _g_sum(p, F) * 0.7 if U != 0 else 0.0
    n25 = tier_total(o37, U, p["tiers"]["NEWMOD"])
    n26 = 0.0
    U2 = inp.get("addUsers", 0)
    if U2 > 0:
        t = p["tiers"]["ADDUSR"]
        cnt = [0] * len(t)
        prev = t[0]["to"]
        for k in range(1, len(t)):
            to = t[k]["to"] if t[k]["to"] is not None else 10 ** 12
            if U2 >= to:
                cnt[k] = to - prev
            elif U2 > t[k]["from"] - 1:
                cnt[k] = U2 - t[k]["from"] + 1
            prev = to
        cnt[0] = U2 - sum(cnt[1:])
        n26 = (o14 / t[0]["to"]) * cnt[0] * 0.7 + sum(o14 * (t[k]["pct"] or 0) / 100 * cnt[k] for k in range(1, len(t)))
    return (n24 + n25 + n26) / p["consDivisor"]
