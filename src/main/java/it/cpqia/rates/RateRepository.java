package it.cpqia.rates;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** Lettura e gestione delle tabelle di tariffe (profili professionali, trasferte Italia). */
@Repository
public class RateRepository {

    public record RateRow(Long id, String name, BigDecimal amount, int sortOrder, boolean active) {
    }

    private final JdbcClient jdbc;

    public RateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RateRow> list(RateTable t, boolean onlyActive) {
        return jdbc.sql("select id, name, amount, sort_order, active from " + t.sqlTable()
                + " where deleted_at is null" + (onlyActive ? " and active" : "") + " order by sort_order, lower(name)")
                .query(RateRow.class).list();
    }

    /** inserisce (id null) o aggiorna una voce */
    public void save(RateTable t, Long id, String name, BigDecimal amount, Integer sortOrder, boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("La descrizione è obbligatoria");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("L'importo deve essere un numero maggiore o uguale a zero");
        }
        try {
            if (id == null) {
                jdbc.sql("insert into " + t.sqlTable() + " (name, amount, sort_order, active) values (:n, :a, "
                        + "coalesce(:s, (select coalesce(max(sort_order), 0) + 10 from " + t.sqlTable() + ")), :act)")
                        .param("n", name.trim()).param("a", amount).param("s", sortOrder).param("act", active).update();
            } else {
                jdbc.sql("update " + t.sqlTable() + " set name = :n, amount = :a, sort_order = coalesce(:s, sort_order), active = :act where id = :id and deleted_at is null")
                        .param("n", name.trim()).param("a", amount).param("s", sortOrder).param("act", active).param("id", id).update();
            }
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Esiste già una voce con la descrizione \"" + name.trim() + "\"");
        }
    }
}
