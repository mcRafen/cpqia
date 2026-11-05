package it.cpqia.galileo;

import it.cpqia.galileo.GalileoModel.Mod;
import it.cpqia.galileo.GalileoModel.Profile;
import it.cpqia.galileo.GalileoModel.Tier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lettura dei profili di calcolo JGalileo, dei mercati e dei vincoli dei moduli. */
@Repository
public class GalileoRepository {

    public record Listino(String name, String version, LocalDate validUntil) {
        public boolean expired() {
            return validUntil != null && validUntil.isBefore(LocalDate.now());
        }
    }

    public record ProfileInfo(long id, String kind, String label, boolean hasV11, boolean aggiuntaSupported, Integer maxUsers) {
    }

    public record Market(long applicationId, String code, String name, List<ProfileInfo> profiles) {
    }

    public record Rule(String moduleCode, String type, List<String> targets, String severity, String message) {
    }

    private final JdbcClient jdbc;

    public GalileoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Listino> listino() {
        return jdbc.sql("select name, version, valid_until from galileo_listino where id = 1")
                .query((rs, n) -> new Listino(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class))).optional();
    }

    /** mercati Galileo con almeno un profilo di calcolo attivo */
    public List<Market> markets() {
        record Row(long appId, String code, String name, ProfileInfo info) {
        }
        List<Row> rows = jdbc.sql("""
                select a.id, a.code, a.name, p.id, p.kind, p.label, p.cfg_v11 is not null, p.aggiunta_supported, p.max_users
                from galileo_profile p join application a on a.id = p.application_id
                where a.deleted_at is null and a.active and p.active
                order by a.sort_order, a.name, p.sort_order""")
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3),
                        new ProfileInfo(rs.getLong(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBoolean(8), (Integer) rs.getObject(9))))
                .list();
        Map<Long, Market> out = new LinkedHashMap<>();
        for (Row r : rows) {
            out.computeIfAbsent(r.appId(), k -> new Market(r.appId(), r.code(), r.name(), new ArrayList<>())).profiles().add(r.info());
        }
        return new ArrayList<>(out.values());
    }

    public Optional<Profile> profile(long id) {
        record Row(long id, long applicationId, String kind, String label, String sheet, BigDecimal cfgV9, BigDecimal cfgV11,
                   BigDecimal disc, BigDecimal discNew, boolean v11d, BigDecimal consDiv, BigDecimal mc, BigDecimal mv, BigDecimal m9,
                   Integer maxUsers, boolean agg, boolean pro, BigDecimal maint) {
        }
        Optional<Row> r = jdbc.sql("""
                select p.id, p.application_id, p.kind, p.label, p.source_sheet, p.cfg_v9, p.cfg_v11, p.module_discount_pct, p.new_discount_pct,
                       p.v11_discount, p.cons_divisor, p.mig_carattere_pct, p.mig_visuale_pct, p.mig_v9_flat, p.max_users,
                       p.aggiunta_supported, p.pro_rata_below_first, a.maintenance_pct
                from galileo_profile p join application a on a.id = p.application_id
                where p.id = :id and a.deleted_at is null""").param("id", id)
                .query((rs, n) -> new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBoolean(10), rs.getBigDecimal(11), rs.getBigDecimal(12),
                        rs.getBigDecimal(13), rs.getBigDecimal(14), (Integer) rs.getObject(15), rs.getBoolean(16), rs.getBoolean(17), rs.getBigDecimal(18)))
                .optional();
        if (r.isEmpty()) {
            return Optional.empty();
        }
        Row p = r.get();
        Map<String, List<Tier>> tiers = new LinkedHashMap<>();
        jdbc.sql("select tier_set, from_users, to_users, pct from galileo_tier where profile_id = :id order by tier_set, seq").param("id", id)
                .query((rs, n) -> {
                    tiers.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(new Tier(rs.getInt(2), (Integer) rs.getObject(3), rs.getBigDecimal(4)));
                    return null;
                }).list();
        List<Mod> mods = jdbc.sql("""
                select m.id, m.code, m.description, m.area, gm.flag, gm.e_rule, gm.g_counted,
                       coalesce(gm.price, (select mp.price from module_price mp where mp.module_id = m.id and mp.mode_code = 'STD')),
                       gm.e_price, gm.g_price, gm.sheet_order
                from galileo_module gm join module m on m.id = gm.module_id
                where gm.profile_id = :id and m.deleted_at is null and m.active
                order by gm.sheet_order, m.sort_order, m.code""").param("id", id)
                .query((rs, n) -> new Mod(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getBoolean(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getInt(11))).list();
        return Optional.of(new Profile(p.id(), p.applicationId(), p.kind(), p.label(), p.sheet(), p.cfgV9(), p.cfgV11(), p.disc(), p.discNew(),
                p.v11d(), p.consDiv(), p.mc(), p.mv(), p.m9(), p.maxUsers(), p.agg(), p.pro(), p.maint(), tiers, mods));
    }

    /** vincoli (prerequisiti) dei moduli dell'applicazione */
    public List<Rule> rules(long applicationId) {
        return jdbc.sql("""
                select m.code, r.rule_type, r.target_codes, r.severity, r.message
                from module_rule r join module m on m.id = r.module_id
                where m.application_id = :a and m.deleted_at is null and r.deleted_at is null order by m.code, r.id""")
                .param("a", applicationId)
                .query((rs, n) -> new Rule(rs.getString(1), rs.getString(2), List.of(rs.getString(3) == null ? new String[0] : rs.getString(3).split(",")),
                        rs.getString(4), rs.getString(5))).list();
    }
}
