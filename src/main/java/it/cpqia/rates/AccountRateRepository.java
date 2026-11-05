package it.cpqia.rates;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Tariffe dedicate all'account (profili professionali e trasferte) sopra il listino; vuote = listino. */
@Repository
public class AccountRateRepository {

    /** amount = valore da applicare (dedicato se presente, altrimenti listino) */
    public record EffRate(Long id, String name, BigDecimal listAmount, BigDecimal customAmount, BigDecimal amount, int sortOrder, boolean active, boolean custom) {
    }

    private final JdbcClient jdbc;

    public AccountRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }


    /** voci attive della tabella; per i profili con la tariffa dedicata dell'account (accountId null: solo listino). Le trasferte sono sempre di listino */
    public List<EffRate> effective(RateTable t, Long accountId) {
        String joinSql = t == RateTable.PROFILES
                ? "left join account_profile_rate x on x.profile_id = r.id and x.account_id = :acc" : "";
        String sql = "select r.id, r.name, r.amount as list_amount, " + (t == RateTable.PROFILES ? "x.rate" : "cast(null as numeric)") + " as custom_amount, r.sort_order, r.active "
                + "from " + t.sqlTable() + " r " + joinSql + " where r.deleted_at is null and r.active order by r.sort_order, r.name";
        JdbcClient.StatementSpec st = jdbc.sql(sql);
        if (t == RateTable.PROFILES) {
            st = st.param("acc", accountId);
        }
        return st
                .query((rs, n) -> {
                    BigDecimal list = rs.getBigDecimal("list_amount");
                    BigDecimal custom = rs.getBigDecimal("custom_amount");
                    return new EffRate(rs.getLong("id"), rs.getString("name"), list, custom, custom != null ? custom : list,
                            rs.getInt("sort_order"), rs.getBoolean("active"), custom != null);
                }).list();
    }

    /** sostituisce le tariffe dedicate dei profili dell'account: solo le voci presenti nella mappa (id profilo -> €/h) */
    public void saveProfileRates(long accountId, Map<Long, BigDecimal> custom) {
        jdbc.sql("delete from account_profile_rate where account_id = :a").param("a", accountId).update();
        for (Map.Entry<Long, BigDecimal> e : custom.entrySet()) {
            jdbc.sql("insert into account_profile_rate (account_id, profile_id, rate) values (:a, :k, :v)")
                    .param("a", accountId).param("k", e.getKey()).param("v", e.getValue()).update();
        }
    }

    /** voce di trasferta assegnata all'account (null = nessuna) */
    public Long travelRateId(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return jdbc.sql("select travel_rate_id from account where id = :a").param("a", accountId).query(Long.class).optional().orElse(null);
    }

    public void setTravelRate(long accountId, Long travelRateId) {
        if (travelRateId != null && jdbc.sql("select count(*) from travel_rate where id = :t and deleted_at is null").param("t", travelRateId)
                .query(Long.class).single() == 0) {
            throw new IllegalArgumentException("Spese di trasferta: voce non valida");
        }
        jdbc.sql("update account set travel_rate_id = :t where id = :a").param("t", travelRateId).param("a", accountId).update();
    }
}
