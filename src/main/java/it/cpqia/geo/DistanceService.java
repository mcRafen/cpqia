package it.cpqia.geo;

import it.cpqia.geo.GeoService.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Distanza tra l'account e la sede Sanmarco piu' vicina. Usa SOLO le coordinate gia' memorizzate (account e sedi): la geolocalizzazione
 * la esegue l'agente Geo all'inserimento / modifica dell'indirizzo, mai al momento del calcolo.
 */
@Service
public class DistanceService {

    /** method: ROAD = percorso stradale; ESTIMATE = linea d'aria x fattore (stima); NONE = non calcolabile */
    public record Result(boolean ok, BigDecimal km, String sede, Long sedeId, String method, String message) {
    }

    private record Loc(long id, String name, Double lat, Double lon) {
    }

    private final JdbcClient jdbc;
    private final GeoService geo;
    private final GeoAgentService geoAgent;
    private final double factor;

    public DistanceService(JdbcClient jdbc, GeoService geo, GeoAgentService geoAgent, @Value("${cpqia.geo.straight-line-factor:1.3}") double factor) {
        this.jdbc = jdbc;
        this.geo = geo;
        this.geoAgent = geoAgent;
        this.factor = factor;
    }

    private static Result none(String msg) {
        return new Result(false, null, null, null, "NONE", msg);
    }

    public Result nearestSede(long customerId) {
        if (!geo.enabled()) {
            return none("Calcolo distanze disattivato (cpqia.geo.enabled)");
        }
        record Acc(Double lat, Double lon, String status) {
        }
        Acc c = jdbc.sql("select latitude::float8 as lat, longitude::float8 as lon, geo_status as status from account where id = :id and deleted_at is null")
                .param("id", customerId).query((rs, n) -> new Acc((Double) rs.getObject("lat"), (Double) rs.getObject("lon"), rs.getString("status"))).optional().orElse(null);
        if (c == null) {
            return none("Account inesistente");
        }
        if ((c.lat() == null || c.lon() == null) && ("PENDING".equals(c.status()) || "TODO".equals(c.status()))) {
            // indirizzo non ancora elaborato dall'agente Geo (appena inserito/modificato o precedente all'agente): lo si geolocalizza ora, una volta sola
            try {
                geoAgent.locateNow("account", customerId);
                c = jdbc.sql("select latitude::float8 as lat, longitude::float8 as lon, geo_status as status from account where id = :id")
                        .param("id", customerId).query((rs, n) -> new Acc((Double) rs.getObject("lat"), (Double) rs.getObject("lon"), rs.getString("status"))).single();
            } catch (IllegalArgumentException e) {
                return none(e.getMessage());
            }
        }
        if (c.lat() == null || c.lon() == null) {
            return none(switch (c.status() == null ? "" : c.status()) {
                case "PENDING" -> "Geolocalizzazione dell'account in corso: riprova tra qualche istante";
                case "NOT_FOUND" -> "Indirizzo dell'account non trovato sulla mappa: controlla via, civico, CAP e città";
                default -> "Account non ancora geolocalizzato: avvia l'agente Geo (Impostazioni > Integrazioni > Geo)";
            });
        }
        Point cp = new Point(c.lat(), c.lon());
        List<Loc> sedi = jdbc.sql("select id, name, latitude::float8 as lat, longitude::float8 as lon from sede where deleted_at is null and latitude is not null and longitude is not null order by sort_order, id")
                .query((rs, n) -> new Loc(rs.getLong("id"), rs.getString("name"), (Double) rs.getObject("lat"), (Double) rs.getObject("lon"))).list();
        record Cand(Loc sede, Point p, double air) {
        }
        List<Cand> cands = new ArrayList<>();
        for (Loc s : sedi) {
            Point sp = new Point(s.lat(), s.lon());
            cands.add(new Cand(s, sp, GeoService.haversineKm(cp, sp)));
        }
        if (cands.isEmpty()) {
            return none("Nessuna sede Sanmarco geolocalizzata (Impostazioni > Integrazioni > Geo)");
        }
        cands.sort(Comparator.comparingDouble(Cand::air));
        // percorso stradale per le 3 sedi piu' vicine in linea d'aria; vince il percorso piu' breve
        Cand best = null;
        double bestKm = Double.MAX_VALUE;
        boolean road = false;
        for (Cand k : cands.subList(0, Math.min(3, cands.size()))) {
            Optional<Double> r = geo.roadKm(cp, k.p());
            if (r.isPresent() && r.get() < bestKm) {
                bestKm = r.get();
                best = k;
                road = true;
            }
        }
        if (!road) {
            best = cands.get(0);
            bestKm = best.air() * factor;
        }
        BigDecimal km = BigDecimal.valueOf(bestKm).setScale(1, RoundingMode.HALF_UP);
        return new Result(true, km, best.sede().name(), best.sede().id(), road ? "ROAD" : "ESTIMATE",
                road ? "Percorso stradale più breve" : "Stima: linea d'aria × " + factor + " (servizio di percorso non raggiungibile)");
    }
}
