package it.cpqia.grid;

import it.cpqia.grid.GridModels.Behavior;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Funzioni delle griglie abilitate per ciascuna griglia (tabella grid_behavior). Senza riga: tutto abilitato. */
@Service
public class GridBehaviorService {

    private final JdbcClient jdbc;

    public GridBehaviorService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Behavior> all() {
        Map<String, Behavior> out = new HashMap<>();
        jdbc.sql("select grid_key, configurable, move_columns, sort_columns, pivot, totals, subtotals, inline_edit from grid_behavior")
                .query((rs, n) -> {
                    out.put(rs.getString(1), Behavior.of(rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8)));
                    return null;
                }).list();
        return out;
    }

    public Behavior get(String key) {
        return all().getOrDefault(key, Behavior.ALL);
    }

    public void save(String key, Behavior b) {
        Behavior n = Behavior.of(b.configurable(), b.moveColumns(), b.sortColumns(), b.pivot(), b.totals(), b.subtotals(), b.inlineEdit());
        jdbc.sql("insert into grid_behavior (grid_key, configurable, move_columns, sort_columns, pivot, totals, subtotals, inline_edit) "
                        + "values (:k, :c, :m, :s, :p, :t, :u, :i) on conflict (grid_key) do update set configurable = :c, move_columns = :m, "
                        + "sort_columns = :s, pivot = :p, totals = :t, subtotals = :u, inline_edit = :i")
                .param("k", key).param("c", n.configurable()).param("m", n.moveColumns()).param("s", n.sortColumns())
                .param("p", n.pivot()).param("t", n.totals()).param("u", n.subtotals()).param("i", n.inlineEdit()).update();
    }
}
