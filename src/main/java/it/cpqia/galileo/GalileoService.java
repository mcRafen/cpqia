package it.cpqia.galileo;

import it.cpqia.galileo.GalileoModel.Input;
import it.cpqia.galileo.GalileoModel.Mod;
import it.cpqia.galileo.GalileoModel.Profile;
import it.cpqia.galileo.GalileoModel.Result;
import it.cpqia.galileo.GalileoRepository.Rule;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

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
    public record Selection(Long moduleId, String state) {
    }

    /** richiesta del wizard (calcolo e salvataggio) */
    public record Request(Long profileId, String release, Integer users, Integer addUsers, Integer source, Boolean aggiunta,
                          List<Selection> selections) {
    }

    public record Violation(String moduleCode, String severity, String message) {
    }

    public record Calc(Result result, List<Violation> violations, boolean listinoExpired, String listinoVersion, String listinoValidUntil) {
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
        Result r = GalileoPricing.calculate(p, in);
        Set<String> present = new HashSet<>(in.d());
        present.addAll(in.f());
        List<Violation> v = r.valid() ? violations(p, present) : List.of();
        Optional<GalileoRepository.Listino> l = repo.listino();
        return new Calc(r, v, l.map(GalileoRepository.Listino::expired).orElse(false), l.map(GalileoRepository.Listino::version).orElse(null),
                l.map(x -> x.validUntil() == null ? null : x.validUntil().toString()).orElse(null));
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
                out.add(new Violation(r.moduleCode(), r.severity(), r.moduleCode() + ": " + r.message()));
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
        for (String code : in.f()) {
            jdbc.sql("insert into offer_galileo_module (offer_id, module_id, state) values (:o, :m, 'F')").param("o", offerId).param("m", byCode.get(code).moduleId()).update();
        }
        for (String code : in.d()) {
            Mod m = byCode.get(code);
            if ("CB".equals(m.flag()) || "V11".equals(m.flag())) {
                continue;                           // impliciti (configurazione base / release)
            }
            jdbc.sql("insert into offer_galileo_module (offer_id, module_id, state) values (:o, :m, 'D')").param("o", offerId).param("m", m.moduleId()).update();
        }
    }

    public Request load(long offerId) {
        Optional<Request> head = jdbc.sql("select profile_id, release, users, add_users, source, aggiunta from offer_galileo where offer_id = :o").param("o", offerId)
                .query((rs, n) -> new Request(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(6), List.of())).optional();
        if (head.isEmpty()) {
            return null;
        }
        List<Selection> sel = jdbc.sql("select module_id, state from offer_galileo_module where offer_id = :o order by module_id").param("o", offerId)
                .query((rs, n) -> new Selection(rs.getLong(1), rs.getString(2))).list();
        Request h = head.get();
        return new Request(h.profileId(), h.release(), h.users(), h.addUsers(), h.source(), h.aggiunta(), sel);
    }
}
