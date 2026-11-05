package it.cpqia.integration;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Configurazione e verifica del collegamento JDBC a IBM Power System (IBM i) con il driver JTOpen. */
@Service
public class IbmiService {

    public static final String DRIVER = "com.ibm.as400.access.AS400JDBCDriver";
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9._-]{1,255}$");
    private static final Pattern LIB = Pattern.compile("^[A-Za-z0-9_$#@]{1,10}$");

    /** configurazione salvata (senza password) */
    public record View(String host, String user, boolean passwordSet, String library, boolean secure,
                       LocalDateTime lastTestAt, Boolean lastTestOk, String lastTestMsg, boolean customKey) {
    }

    /** esito del test */
    public record TestResult(boolean ok, String message, long millis) {
    }

    public record Settings(String host, String user, String password, String library, boolean secure) {
    }

    private final JdbcClient jdbc;
    private final CryptoService crypto;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ibmi-test");
        t.setDaemon(true);
        return t;
    });

    public IbmiService(JdbcClient jdbc, CryptoService crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    private record Row(String host, String userName, String passwordEnc, String library, boolean secure,
                       LocalDateTime lastTestAt, Boolean lastTestOk, String lastTestMsg) {
    }

    private Row row() {
        return jdbc.sql("select host, user_name, password_enc, library, secure, last_test_at, last_test_ok, last_test_msg from ibmi_connection where id = 1")
                .query(Row.class).single();
    }

    public View view() {
        Row r = row();
        return new View(r.host(), r.userName(), r.passwordEnc() != null && !r.passwordEnc().isBlank(), r.library(), r.secure(),
                r.lastTestAt(), r.lastTestOk(), r.lastTestMsg(), crypto.customKey());
    }

    /** impostazioni salvate con la password in chiaro (solo per il collegamento) */
    public Settings stored() {
        Row r = row();
        String pwd = r.passwordEnc() == null || r.passwordEnc().isBlank() ? "" : crypto.decrypt(r.passwordEnc());
        return new Settings(r.host(), r.userName(), pwd, r.library(), r.secure());
    }

    private static String validate(Settings s) {
        if (s.host() == null || !HOST.matcher(s.host().trim()).matches()) {
            return "Indirizzo IP o nome del server non valido";
        }
        if (s.user() == null || s.user().trim().isEmpty() || s.user().length() > 50) {
            return "Utente obbligatorio (max 50 caratteri)";
        }
        if (s.library() != null && !s.library().isBlank() && !LIB.matcher(s.library().trim()).matches()) {
            return "Libreria non valida (fino a 10 caratteri: lettere, cifre, _ $ # @)";
        }
        return null;
    }

    /** salva; password vuota = mantieni quella esistente */
    public void save(Settings s, long userId) {
        String err = validate(s);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        String enc = s.password() == null || s.password().isEmpty() ? null : crypto.encrypt(s.password());
        jdbc.sql("""
                update ibmi_connection set host = :h, user_name = :u, library = :l, secure = :s,
                    password_enc = coalesce(:p, password_enc), updated_at = now(), updated_by = :by where id = 1""")
                .param("h", s.host().trim()).param("u", s.user().trim()).param("l", s.library() == null ? "" : s.library().trim().toUpperCase())
                .param("s", s.secure()).param("p", enc).param("by", userId).update();
    }

    /** Verifica il collegamento (password vuota = quella salvata) e registra l'esito. */
    public TestResult test(Settings in) {
        Settings s = in.password() == null || in.password().isEmpty()
                ? new Settings(in.host(), in.user(), stored().password(), in.library(), in.secure()) : in;
        String err = validate(s);
        TestResult res;
        if (err != null) {
            res = new TestResult(false, err, 0);
        } else {
            long t0 = System.currentTimeMillis();
            Future<String> f = pool.submit(() -> connectAndProbe(s));
            try {
                String info = f.get(20, TimeUnit.SECONDS);
                res = new TestResult(true, info, System.currentTimeMillis() - t0);
            } catch (TimeoutException e) {
                f.cancel(true);
                res = new TestResult(false, "Nessuna risposta entro 20 secondi: controlla indirizzo, rete e firewall (porta 8470/8471 per SSL e porte di accesso)", System.currentTimeMillis() - t0);
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                res = new TestResult(false, friendly(c), System.currentTimeMillis() - t0);
            }
        }
        jdbc.sql("update ibmi_connection set last_test_at = now(), last_test_ok = :ok, last_test_msg = :m where id = 1")
                .param("ok", res.ok()).param("m", res.message().length() > 990 ? res.message().substring(0, 990) : res.message()).update();
        return res;
    }

    private static String friendly(Throwable c) {
        if (c instanceof ClassNotFoundException || c instanceof NoClassDefFoundError) {
            return "Driver JDBC IBM i (JTOpen, " + DRIVER + ") non trovato: controlla la dipendenza net.sf.jt400 nel pom.xml";
        }
        String m = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
        return c.getClass().getSimpleName() + ": " + m;
    }

    /** apre la connessione JDBC a IBM i (JTOpen); chi la riceve deve chiuderla */
    public Connection connect(Settings s) throws Exception {
        Class<?> cls = Class.forName(DRIVER);
        Driver driver = (Driver) cls.getDeclaredConstructor().newInstance();
        String lib = s.library() == null ? "" : s.library().trim().toUpperCase();
        // host e libreria sono validati (niente caratteri speciali): l'URL non puo' essere alterato dall'input
        String url = "jdbc:as400://" + s.host().trim() + (lib.isEmpty() ? "" : "/" + lib)
                + ";naming=sql;errors=full;prompt=false" + (lib.isEmpty() ? "" : ";libraries=" + lib) + (s.secure() ? ";secure=true" : "");
        Properties p = new Properties();
        p.setProperty("user", s.user().trim());
        p.setProperty("password", s.password() == null ? "" : s.password());
        Connection c = driver.connect(url, p);
        if (c == null) {
            throw new IllegalStateException("URL non accettato dal driver");
        }
        return c;
    }

    private String connectAndProbe(Settings s) throws Exception {
        String lib = s.library() == null ? "" : s.library().trim().toUpperCase();
        try (Connection c = connect(s)) {
            DatabaseMetaData md = c.getMetaData();
            StringBuilder sb = new StringBuilder("Connessione riuscita: ").append(md.getDatabaseProductName()).append(' ')
                    .append(md.getDatabaseProductVersion()).append(" (driver ").append(md.getDriverVersion()).append(')');
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select 1 from sysibm.sysdummy1")) {
                rs.next();
            }
            if (!lib.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("select count(*) from qsys2.sysschemas where schema_name = ?")) {
                    ps.setString(1, lib);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        sb.append(rs.getInt(1) > 0 ? ". Libreria " + lib + " trovata" : ". ATTENZIONE: libreria " + lib + " non trovata sul server");
                    }
                } catch (Exception e) {
                    sb.append(". Libreria ").append(lib).append(": verifica non possibile (").append(e.getMessage()).append(')');
                }
            }
            return sb.toString();
        }
    }
}
