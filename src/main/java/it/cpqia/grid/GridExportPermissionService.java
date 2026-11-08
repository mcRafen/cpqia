package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Griglie su cui l'utente puo' esportare i dati (Excel, CSV, Google Sheets). «*» = tutte; l'administrator puo' sempre. */
@Service
public class GridExportPermissionService {

    public static final String ALL = "*";

    private final JdbcClient jdbc;

    public GridExportPermissionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean allowed(AppUser u, String gridKey) {
        if (u == null) {
            return false;
        }
        if (u.administrator()) {
            return true;
        }
        return jdbc.sql("select count(*) from user_grid_export where user_id = :u and grid_key in (:k, '*')")
                .param("u", u.id()).param("k", gridKey).query(Long.class).single() > 0;
    }

    public Set<String> keys(long userId) {
        return new LinkedHashSet<>(jdbc.sql("select grid_key from user_grid_export where user_id = :u order by grid_key")
                .param("u", userId).query(String.class).list());
    }

    @Transactional
    public void save(long userId, Collection<String> keys) {
        jdbc.sql("delete from user_grid_export where user_id = :u").param("u", userId).update();
        Set<String> ks = new LinkedHashSet<>(keys);
        if (ks.contains(ALL)) {
            ks = Set.of(ALL);
        }
        for (String k : ks) {
            jdbc.sql("insert into user_grid_export (user_id, grid_key) values (:u, :k)").param("u", userId).param("k", k).update();
        }
    }
}
