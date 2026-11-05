package it.cpqia.audit;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * DataSource che, a ogni prelievo di connessione, comunica al database l'utente corrente (variabile di sessione cpqia.user_email)
 * letta dal trigger di journaling. Il valore e' ricordato per connessione fisica, quindi la query extra si fa solo quando l'utente cambia.
 */
public class AuditDataSource extends DelegatingDataSource {

    private final Map<Object, String> applied = Collections.synchronizedMap(new WeakHashMap<>());

    public AuditDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection c = super.getConnection();
        apply(c);
        return c;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection c = super.getConnection(username, password);
        apply(c);
        return c;
    }

    private void apply(Connection c) {
        String email = AuditContext.email();
        String want = email == null ? "" : email;
        Object key = physical(c);
        if (key != null && want.equals(applied.get(key))) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("select set_config('cpqia.user_email', ?, false)")) {
            ps.setString(1, want);
            ps.execute();
            if (key != null) {
                applied.put(key, want);
            }
        } catch (SQLException e) {
            // il journaling resta attivo ma l'utente risultera' «Sistema»
        }
    }

    private static Object physical(Connection c) {
        try {
            return c.unwrap(Connection.class);
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }
}
