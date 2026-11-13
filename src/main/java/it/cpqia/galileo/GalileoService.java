package it.cpqia.galileo;

import it.cpqia.galileo.GalileoModel.Input;
import it.cpqia.galileo.GalileoModel.Mod;
import it.cpqia.galileo.GalileoModel.Profile;
import it.cpqia.galileo.GalileoModel.Result;
import it.cpqia.galileo.GalileoRepository.Rule;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Calcolo, controlli sui vincoli e salvataggio della configurazione Galileo dell'offerta. */
@Service
public class GalileoService {

    /** state: D = selezionato/in comune/migrato, F = nuovo */
    public record Selection(Long moduleId, String state, BigDecimal listPrice, BigDecimal customerPrice) {
        public Selection(Long moduleId, String state) {
            this(moduleId, state, null, null);
        }
    }

    /** richiesta del wizard (calcolo e salvataggio) */
    public record Request(Long profileId, String release, Integer users, Integer addUsers, Integer source, Boolean aggiunta,
                          List<Selection> selections) {
    }

    /** missing = moduli da aggiungere per rispettare la dipendenza (con REQUIRES_ANY: le alternative, ne basta una) */
    public record Violation(String moduleCode, String severity, String message, List<String> missing, boolean anyOf) {
    }

    /** result = calcolo sui prezzi a cliente (e' quello che entra nei totali); listResult = calcolo sui prezzi di listino (solo se ci sono prezzi modificati) */
    public record Calc(Result result, List<Violation> violations, boolean listinoExpired, String listinoVersion, String listinoValidUntil, Result listResult) {
    }

    private final GalileoRepository repo;
    private final JdbcClient jdbc;

    public GalileoService(GalileoRepository repo, JdbcClient jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    public GalileoRepository repository() {
        return repo;
    }

    // ------------------------------------------------------------------ calcolo
    /** selezioni -> insiemi di codici D/F; i moduli base (CB) sono sempre «in comune» salvo quelli marcati come nuovi; V11 segue la release */
    static Input toInput(Profile p, Request req) {
        Map<Long, Mod> byId = new HashMap<>();
        for (Mod m : p.modules()) {
            byId.put(m.moduleId(), m);
        }
        Set<String> d = new HashSet<>();
        Set<String> f = new HashSet<>();
        if (req.selections() != null) {
            for (Selection s : req.selections()) {
                Mod m = s.moduleId() == null ? null : byId.get(s.moduleId());
                if (m == null) {
                    throw new IllegalArgumentException("Modulo non previsto in questo listino");
                }
                if ("F".equals(s.state())) {
                    f.add(m.code());
                } else if ("D".equals(s.state())) {
                    d.add(m.code());
                } else {
                    throw new IllegalArgumentException("Stato del modulo " + m.code() + " non valido");
                }
            }
        }
        String rel0 = req.release() == null ? "" : req.release().trim();
        boolean v11 = "V11".equals(rel0) && p.hasV11();
        // release: V9 o V11 (obbligatoria se il listino prevede la V11; senza V11 vale V9); vuota = non selezionata
        String release = v11 ? "V11" : ("V9".equals(rel0) || !p.hasV11() ? "V9" : "");
        for (Mod m : p.modules()) {
            if ("CB".equals(m.flag()) && !f.contains(m.code())) {
                d.add(m.code());
            }
            if ("V11".equals(m.flag())) {
                if (v11) {
                    d.add(m.code());
                } else {
                    d.remove(m.code());
                }
            }
        }
        d.removeAll(f);
        return new Input(nz(req.users()), nz(req.addUsers()), release, nz(req.source()), Boolean.TRUE.equals(req.aggiunta()), d, f);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    public Calc calculate(Request req) {
        Profile p = repo.profile(req.profileId() == null ? -1 : req.profileId()).orElseThrow(() -> new IllegalArgumentException("Configurazione Galileo non valida"));
        Input in = toInput(p, req);
        Map<Long, BigDecimal> listOv = new HashMap<>(), custOv = new HashMap<>();
        collectPrices(p, req, listOv, custOv);
        Result r = GalileoPricing.calculate(withPrices(p, mergeOv(listOv, custOv)), in);              // totali sui prezzi a cliente
        Result rl = listOv.isEmpty() && custOv.isEmpty() ? null : GalileoPricing.calculate(withPrices(p, listOv), in);
        Set<String> present = new HashSet<>(in.d());
        present.addAll(in.f());
        List<Violation> v = r.valid() ? violations(p, present) : List.of();
        Optional<GalileoRepository.Listino> l = repo.listino();
        return new Calc(r, v, l.map(GalileoRepository.Listino::expired).orElse(false), l.map(GalileoRepository.Listino::version).orElse(null),
                l.map(x -> x.validUntil() == null ? null : x.validUntil().toString()).orElse(null), rl);
    }

    /**
     * prezzi unitari modificati a mano dei moduli scelti: listOv = listino, custOv = a cliente (solo se indicato). Controlla che il listino
     * (effettivo) non sia inferiore al prezzo a cliente (effettivo, uguale al listino se non indicato).
     */
    static void collectPrices(Profile p, Request req, Map<Long, BigDecimal> listOv, Map<Long, BigDecimal> custOv) {
        if (req.selections() == null) {
            return;
        }
        Map<Long, Mod> byId = new HashMap<>();
        for (Mod m : p.modules()) {
            byId.put(m.moduleId(), m);
        }
        for (Selection s : req.selections()) {
            Mod m = s.moduleId() == null ? null : byId.get(s.moduleId());
            if (m == null || (s.listPrice() == null && s.customerPrice() == null)) {
                continue;
            }
            if ((s.listPrice() != null && s.listPrice().signum() < 0) || (s.customerPrice() != null && s.customerPrice().signum() < 0)) {
                throw new IllegalArgumentException(m.code() + ": i prezzi non possono essere negativi");
            }
            BigDecimal list = s.listPrice() != null ? s.listPrice() : (m.price() == null ? BigDecimal.ZERO : m.price());
            BigDecimal cust = s.customerPrice() != null ? s.customerPrice() : list;
            if (list.compareTo(cust) < 0) {
                throw new IllegalArgumentException(m.code() + ": il prezzo di listino non può essere inferiore a quello a cliente");
            }
            if (s.listPrice() != null) {
                listOv.put(m.moduleId(), s.listPrice());
            }
            custOv.put(m.moduleId(), cust);
        }
    }

    /** prezzi a cliente effettivi: quello indicato, altrimenti il listino modificato */
    static Map<Long, BigDecimal> mergeOv(Map<Long, BigDecimal> listOv, Map<Long, BigDecimal> custOv) {
        Map<Long, BigDecimal> m = new HashMap<>(listOv);
        m.putAll(custOv);
        return m;
    }

    /** copia del profilo con il prezzo dei moduli indicati sostituito (anche dove il foglio addebita il prezzo di un'altra riga) */
    static Profile withPrices(Profile p, Map<Long, BigDecimal> ov) {
        if (ov.isEmpty()) {
            return p;
        }
        List<Mod> mods = new ArrayList<>();
        for (Mod m : p.modules()) {
            BigDecimal v = ov.get(m.moduleId());
            mods.add(v == null ? m : new Mod(m.moduleId(), m.code(), m.description(), m.area(), m.flag(), m.eRule(), m.gCounted(), v, v, v, m.order()));
        }
        return new Profile(p.id(), p.applicationId(), p.kind(), p.label(), p.sheet(), p.cfgV9(), p.cfgV11(), p.discountPct(), p.newDiscountPct(),
                p.v11Discount(), p.consDivisor(), p.migCarattere(), p.migVisuale(), p.migV9Flat(), p.maxUsers(), p.aggiuntaSupported(),
                p.proRataBelowFirst(), p.maintenancePct(), p.tiers(), mods, p.reductionPct());
    }

    /** vincoli sui moduli presenti nella configurazione (i prerequisiti esterni al listino del mercato vengono ignorati) */
    List<Violation> violations(Profile p, Set<String> present) {
        Set<String> inProfile = new HashSet<>();
        for (Mod m : p.modules()) {
            inProfile.add(m.code());
        }
        Set<String> known = new HashSet<>(inProfile);
        for (Rule r : repo.rules(p.applicationId())) {
            known.add(r.moduleCode());
        }
        List<Violation> out = new ArrayList<>();
        for (Rule r : repo.rules(p.applicationId())) {
            if (!present.contains(r.moduleCode())) {
                continue;
            }
            List<String> t = r.targets().stream().map(String::trim).filter(x -> !x.isEmpty()).toList();
            List<String> inList = t.stream().filter(inProfile::contains).toList();
            if (inList.isEmpty()) {
                continue;
            }
            boolean ok;
            if ("REQUIRES_ANY".equals(r.type())) {
                ok = inList.stream().anyMatch(present::contains);
            } else {
                ok = inList.stream().allMatch(present::contains);
            }
            if (!ok) {
                if ("AGG".equals(p.kind())) {
                    // MODULI AGGIUNTIVI: le dipendenze non sono vincolanti, l'operatore deve solo verificare che il cliente abbia gia' i moduli richiesti
                    List<String> missing = inList.stream().filter(x -> !present.contains(x)).toList();
                    String which = "REQUIRES_ANY".equals(r.type()) ? "almeno uno tra " + String.join(", ", inList) : String.join(", ", missing);
                    out.add(new Violation(r.moduleCode(), "WARNING", r.moduleCode() + ": verifica con il cliente se ha già " + (missing.size() > 1 || "REQUIRES_ANY".equals(r.type()) ? "i moduli " : "il modulo ") + which
                            + " (dipendenza non vincolante)", "REQUIRES_ANY".equals(r.type()) ? inList : missing, "REQUIRES_ANY".equals(r.type())));
                } else {
                    List<String> miss = "REQUIRES_ANY".equals(r.type()) ? inList : inList.stream().filter(x -> !present.contains(x)).toList();
                    out.add(new Violation(r.moduleCode(), r.severity(), r.moduleCode() + ": " + r.message(), miss, "REQUIRES_ANY".equals(r.type())));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ offerta
    public void delete(long offerId) {
        jdbc.sql("delete from offer_galileo where offer_id = :o").param("o", offerId).update();
    }

    /** salva (sostituendola) la configurazione Galileo dell'offerta; senza richiesta la rimuove */
    public void save(long offerId, Long galileoApplicationId, Request req) {
        delete(offerId);
        if (galileoApplicationId == null) {
            return;
        }
        if (req == null || req.profileId() == null) {
            long n = jdbc.sql("select count(*) from galileo_profile where application_id = :a and active").param("a", galileoApplicationId).query(Long.class).single();
            if (n > 0) {
                throw new IllegalArgumentException("Galileo: completa la configurazione nella pagina Galileo");
            }
            return;                                 // mercato senza listino configurato
        }
        Profile p = repo.profile(req.profileId()).orElseThrow(() -> new IllegalArgumentException("Configurazione Galileo non valida"));
        if (p.applicationId() != galileoApplicationId) {
            throw new IllegalArgumentException("La configurazione Galileo non corrisponde alla tipologia scelta");
        }
        Calc c = calculate(req);
        if (!c.result().valid()) {
            throw new IllegalArgumentException("Galileo: " + c.result().message());
        }
        for (Violation v : c.violations()) {
            if ("ERROR".equals(v.severity())) {
                throw new IllegalArgumentException("Galileo – " + v.message());
            }
        }
        Input in = toInput(p, req);
        jdbc.sql("""
                insert into offer_galileo (offer_id, profile_id, release, users, add_users, source, aggiunta, list_total, maintenance)
                values (:o, :p, :r, :u, :au, :s, :a, :l, :m)""")
                .param("o", offerId).param("p", p.id()).param("r", in.release()).param("u", in.users()).param("au", in.addUsers())
                .param("s", in.source()).param("a", in.aggiunta() && p.aggiuntaSupported()).param("l", c.result().list()).param("m", c.result().maintenance()).update();
        Map<String, Mod> byCode = new LinkedHashMap<>();
        for (Mod m : p.modules()) {
            byCode.put(m.code(), m);
        }
        Map<Long, Selection> chosen = new HashMap<>();
        if (req.selections() != null) {
            for (Selection x : req.selections()) {
                if (x.moduleId() != null) {
                    chosen.put(x.moduleId(), x);
                }
            }
        }
        for (String code : in.f()) {
            Mod m = byCode.get(code);
            insertModule(offerId, m, "F", chosen.get(m.moduleId()));
        }
        for (String code : in.d()) {
            Mod m = byCode.get(code);
            if ("CB".equals(m.flag()) || "V11".equals(m.flag())) {
                continue;                           // impliciti (configurazione base / release)
            }
            insertModule(offerId, m, "D", chosen.get(m.moduleId()));
        }
    }

    /** modulo della configurazione salvata, con i prezzi modificati a mano solo se diversi dal listino / dal prezzo di listino */
    private void insertModule(long offerId, Mod m, String state, Selection sel) {
        BigDecimal cat = m.price() == null ? BigDecimal.ZERO : m.price();
        BigDecimal lp = sel != null && sel.listPrice() != null && sel.listPrice().compareTo(cat) != 0 ? sel.listPrice() : null;
        BigDecimal effList = lp != null ? lp : cat;
        BigDecimal cp = sel != null && sel.customerPrice() != null && sel.customerPrice().compareTo(effList) != 0 ? sel.customerPrice() : null;
        jdbc.sql("insert into offer_galileo_module (offer_id, module_id, state, list_price, customer_price) values (:o, :m, :s, :lp, :cp)")
                .param("o", offerId).param("m", m.moduleId()).param("s", state).param("lp", lp).param("cp", cp).update();
    }

    public Request load(long offerId) {
        Optional<Request> head = jdbc.sql("select profile_id, release, users, add_users, source, aggiunta from offer_galileo where offer_id = :o").param("o", offerId)
                .query((rs, n) -> new Request(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(6), List.of())).optional();
        if (head.isEmpty()) {
            return null;
        }
        List<Selection> sel = jdbc.sql("select module_id, state, list_price, customer_price from offer_galileo_module where offer_id = :o order by module_id").param("o", offerId)
                .query((rs, n) -> new Selection(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4))).list();
        Request h = head.get();
        return new Request(h.profileId(), h.release(), h.users(), h.addUsers(), h.source(), h.aggiunta(), sel);
    }
}
