package it.cpqia.setup;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Procedura di setup di CPQIA, attiva subito dopo il deploy del WAR quando il contesto non e' ancora configurato
 * (manca ${catalina.base}/conf/cpqia/&lt;contesto&gt;.properties con il database). E' un servlet puro, senza Spring: parte anche se
 * PostgreSQL non e' raggiungibile. Raccoglie i parametri di accesso a PostgreSQL (con creazione facoltativa di utente e database),
 * le impostazioni dell'applicazione, scrive il file di configurazione e ricarica il contesto, che al riavvio esegue le migrazioni Flyway.
 * L'accesso e' protetto da un codice generato a ogni avvio (file conf/cpqia/&lt;contesto&gt;.setup-token e log di Tomcat).
 */
public final class SetupServlet extends HttpServlet {

    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9._\\-\\[\\]:]{1,255}$");
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]{0,62}$");
    private static final Pattern DBNAME = Pattern.compile("^[A-Za-z0-9_\\-$]{1,63}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> SSL = Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");
    /** chiavi scritte dalla procedura: se il file esiste gia' vengono sostituite, le altre righe restano */
    private static final Set<String> MANAGED = Set.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.datasource.hikari.maximum-pool-size", "spring.datasource.hikari.minimum-idle", "spring.datasource.hikari.connection-timeout",
            "cpqia.bootstrap-admin-email", "cpqia.auto-register", "cpqia.dev-login", "cpqia.oauth.google.client-id",
            "cpqia.oauth.google.client-secret", "cpqia.crypto-key", "cpqia.template-dir");

    private final String name;
    private final Path confDir;
    private final Path propsFile;
    private final String token;
    private final ServletContext ctx;

    private SetupServlet(ServletContext ctx, String name, Path confDir, String token) {
        this.ctx = ctx;
        this.name = name;
        this.confDir = confDir;
        this.propsFile = confDir.resolve(name + ".properties");
        this.token = token;
    }

    // ------------------------------------------------------------------ attivazione
    /** il contesto e' configurato se il file di configurazione definisce il database (url oppure JNDI) */
    public static boolean configured(String contextName) {
        String base = System.getProperty("catalina.base");
        if (base == null) {
            return true;                                              // fuori da Tomcat (avvio embedded) non c'e' nulla da configurare qui
        }
        if (Boolean.getBoolean("cpqia.setup.force")) {
            return false;                                             // -Dcpqia.setup.force=true: riesegue la procedura anche se gia' configurato
        }
        Path f = Paths.get(base, "conf", "cpqia", contextName + ".properties");
        if (!Files.isReadable(f)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("spring.datasource.url") || t.startsWith("spring.datasource.jndi-name")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** registra la procedura di setup al posto dell'applicazione */
    public static void install(ServletContext ctx, String contextName) {
        String base = System.getProperty("catalina.base", ".");
        Path dir = Paths.get(base, "conf", "cpqia");
        byte[] r = new byte[18];
        new SecureRandom().nextBytes(r);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(r);
        Path tokenFile = dir.resolve(contextName + ".setup-token");
        try {
            Files.createDirectories(dir);
            Files.writeString(tokenFile, token + System.lineSeparator(), StandardCharsets.UTF_8);
            restrict(tokenFile);
        } catch (Exception e) {
            ctx.log("CPQIA setup: impossibile scrivere " + tokenFile + " (" + e + "): usare il codice scritto nel log");
        }
        ctx.log("CPQIA setup: contesto '" + contextName + "' NON configurato. Aprire " + ctx.getContextPath() + "/ e inserire il codice di setup: "
                + token + " (anche nel file " + tokenFile + ")");
        SetupServlet s = new SetupServlet(ctx, contextName, dir, token);
        var reg = ctx.addServlet("cpqiaSetup", s);
        reg.addMapping("/*");
        reg.setLoadOnStartup(1);
    }

    private static void restrict(Path p) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // file system senza permessi POSIX (Windows)
        }
    }

    // ------------------------------------------------------------------ richieste
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String p = req.getPathInfo() == null ? "/" : req.getPathInfo();
        if (p.equals("/setup-status")) {
            json(res, 200, "{\"setup\":true}");
            return;
        }
        res.setStatus(200);
        res.setContentType("text/html;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        String html;
        try (InputStream in = SetupServlet.class.getResourceAsStream("/setup/setup.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String conf = propsFile.toString();
        html = html.replace("@@CTX@@", esc(ctx.getContextPath())).replace("@@NAME@@", esc(name)).replace("@@FILE@@", esc(conf))
                .replace("@@TOKENFILE@@", esc(confDir.resolve(name + ".setup-token").toString()))
                .replace("@@CRYPTO@@", esc(randomKey()));
        res.getWriter().write(html);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        req.setCharacterEncoding("UTF-8");
        String p = req.getPathInfo() == null ? "/" : req.getPathInfo();
        if (!tokenOk(req.getParameter("token"))) {
            sleep(700);
            json(res, 403, obj(false, "Codice di setup non valido. Lo trovi nel file " + confDir.resolve(name + ".setup-token") + " e nel log di Tomcat (catalina.out)."));
            return;
        }
        try {
            switch (p) {
                case "/create" -> json(res, 200, createDatabase(req));
                case "/test" -> json(res, 200, test(req));
                case "/save" -> json(res, 200, save(req));
                default -> json(res, 404, obj(false, "Richiesta sconosciuta"));
            }
        } catch (IllegalArgumentException e) {
            json(res, 200, obj(false, e.getMessage()));
        } catch (Exception e) {
            ctx.log("CPQIA setup: errore", e);
            json(res, 200, obj(false, "Errore: " + e));
        }
    }

    private boolean tokenOk(String given) {
        return given != null && MessageDigest.isEqual(given.trim().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ parametri
    private record Db(String host, int port, String name, String user, String password, String ssl, String schema, int timeout, int poolMax, int poolMin) {
        String url() {
            return urlFor(host, port, name, ssl, timeout, schema);
        }
    }

    private static String urlFor(String host, int port, String db, String ssl, int timeout, String schema) {
        String h = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;      // IPv6
        return "jdbc:postgresql://" + h + ":" + port + "/" + db + "?sslmode=" + ssl + "&connectTimeout=" + timeout
                + "&ApplicationName=cpqia" + (schema.isEmpty() ? "" : "&currentSchema=" + schema);
    }

    private static String param(HttpServletRequest r, String k) {
        String v = r.getParameter(k);
        return v == null ? "" : v.trim();
    }

    private static int intParam(HttpServletRequest r, String k, int def, int min, int max, String label) {
        String v = param(r, k);
        if (v.isEmpty()) {
            return def;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw new IllegalArgumentException(label + ": valore tra " + min + " e " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + ": numero non valido");
        }
    }

    private Db db(HttpServletRequest r) {
        String host = param(r, "dbHost");
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Server PostgreSQL: indirizzo non valido");
        }
        int port = intParam(r, "dbPort", 5432, 1, 65535, "Porta");
        String name = param(r, "dbName");
        if (!DBNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Database: nome non valido (lettere, cifre, _ - $, max 63)");
        }
        String user = param(r, "dbUser");
        if (user.isEmpty() || user.length() > 63 || user.chars().anyMatch(c -> c < 32)) {
            throw new IllegalArgumentException("Utente PostgreSQL non valido");
        }
        String pwd = r.getParameter("dbPassword") == null ? "" : r.getParameter("dbPassword");
        String ssl = param(r, "sslMode").isEmpty() ? "prefer" : param(r, "sslMode");
        if (!SSL.contains(ssl)) {
            throw new IllegalArgumentException("Modalità SSL non valida");
        }
        String schema = param(r, "dbSchema");
        if (!schema.isEmpty() && !IDENT.matcher(schema).matches()) {
            throw new IllegalArgumentException("Schema: nome non valido");
        }
        int timeout = intParam(r, "connectTimeout", 10, 1, 120, "Timeout di connessione");
        int max = intParam(r, "poolMax", 10, 1, 200, "Connessioni massime del pool");
        int min = intParam(r, "poolMin", 2, 0, 200, "Connessioni minime del pool");
        if (min > max) {
            throw new IllegalArgumentException("Connessioni minime maggiori delle massime");
        }
        return new Db(host, port, name, user, pwd, ssl, schema, timeout, max, min);
    }

    private static Connection connect(String url, String user, String pwd) throws Exception {
        Driver d = (Driver) Class.forName("org.postgresql.Driver", true, SetupServlet.class.getClassLoader()).getDeclaredConstructor().newInstance();
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pwd);
        Connection c = d.connect(url, p);
        if (c == null) {
            throw new IllegalStateException("URL non accettato dal driver");
        }
        return c;
    }

    private static String hint(SQLException e) {
        String s = e.getSQLState() == null ? "" : e.getSQLState();
        String m = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
        return switch (s) {
            case "28P01", "28000" -> m + " – utente o password errati (controlla anche pg_hba.conf)";
            case "3D000" -> m + " – il database non esiste: usa «Crea database e utente» oppure crealo a mano";
            case "08001", "08006" -> m + " – server non raggiungibile: controlla indirizzo, porta, firewall, listen_addresses e pg_hba.conf";
            default -> m;
        };
    }

    // ------------------------------------------------------------------ azioni
    private String test(HttpServletRequest r) {
        Db d = db(r);
        long t0 = System.currentTimeMillis();
        try (Connection c = connect(d.url(), d.user(), d.password())) {
            StringBuilder sb = new StringBuilder("Connessione riuscita (" + (System.currentTimeMillis() - t0) + " ms): PostgreSQL "
                    + c.getMetaData().getDatabaseProductVersion());
            int major = c.getMetaData().getDatabaseMajorVersion();
            if (major < 12) {
                return obj(false, sb + ". Versione troppo vecchia: serve PostgreSQL 12 o superiore.");
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "select (select count(*) from information_schema.tables where table_schema = current_schema() and table_name = 'flyway_schema_history'), "
                            + "(select count(*) from information_schema.tables where table_schema = current_schema()), current_schema()")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    sb.append(". Database già inizializzato da CPQIA: le migrazioni mancanti verranno applicate all'avvio.");
                } else if (rs.getInt(2) == 0) {
                    sb.append(". Database vuoto: lo schema e il catalogo verranno creati all'avvio.");
                } else {
                    return obj(false, sb + ". Lo schema «" + rs.getString(3) + "» contiene già altre tabelle ma non è un database CPQIA: usa un database o uno schema vuoto.");
                }
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select has_schema_privilege(current_schema(), 'CREATE')")) {
                rs.next();
                if (!rs.getBoolean(1)) {
                    return obj(false, sb + ". L'utente non può creare tabelle nello schema corrente (serve il permesso CREATE).");
                }
            }
            return obj(true, sb.toString());
        } catch (SQLException e) {
            return obj(false, hint(e));
        } catch (Exception e) {
            return obj(false, "Driver PostgreSQL non disponibile: " + e);
        }
    }

    private static String qi(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String ql(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static boolean exists(Connection c, String sql, String arg) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** crea (se mancano) l'utente e il database con le credenziali di un amministratore PostgreSQL */
    private String createDatabase(HttpServletRequest r) {
        Db d = db(r);
        String au = param(r, "adminUser"), ap = r.getParameter("adminPassword") == null ? "" : r.getParameter("adminPassword");
        String adb = param(r, "adminDb").isEmpty() ? "postgres" : param(r, "adminDb");
        if (au.isEmpty()) {
            throw new IllegalArgumentException("Indica l'utente amministratore PostgreSQL (es. postgres)");
        }
        if (!IDENT.matcher(d.user()).matches()) {
            throw new IllegalArgumentException("Per la creazione l'utente applicativo deve avere un nome semplice (lettere, cifre, _)");
        }
        if (d.password().isEmpty() || d.password().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Indica la password dell'utente applicativo");
        }
        if (!DBNAME.matcher(adb).matches()) {
            throw new IllegalArgumentException("Database di servizio non valido");
        }
        StringBuilder log = new StringBuilder();
        try (Connection c = connect(urlFor(d.host(), d.port(), adb, d.ssl(), d.timeout(), ""), au, ap)) {
            c.setAutoCommit(true);
            if (exists(c, "select 1 from pg_roles where rolname = ?", d.user())) {
                log.append("Utente «").append(d.user()).append("» già esistente (password non modificata). ");
            } else {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE ROLE " + qi(d.user()) + " LOGIN PASSWORD " + ql(d.password()));
                }
                log.append("Utente «").append(d.user()).append("» creato. ");
            }
            if (exists(c, "select 1 from pg_database where datname = ?", d.name())) {
                log.append("Database «").append(d.name()).append("» già esistente.");
            } else {
                String base = "CREATE DATABASE " + qi(d.name()) + " OWNER " + qi(d.user()) + " ENCODING 'UTF8' ";
                try (Statement st = c.createStatement()) {
                    try {
                        st.execute(base + "LC_COLLATE 'it_IT.UTF-8' LC_CTYPE 'it_IT.UTF-8' TEMPLATE template0");
                        log.append("Database «").append(d.name()).append("» creato (collazione it_IT.UTF-8).");
                    } catch (SQLException e) {                      // collazione italiana non installata sul server
                        st.execute(base + "TEMPLATE template0");
                        log.append("Database «").append(d.name()).append("» creato (collazione predefinita del server: it_IT.UTF-8 non disponibile).");
                    }
                }
            }
            return obj(true, log.toString().trim());
        } catch (SQLException e) {
            return obj(false, "Creazione non riuscita: " + hint(e));
        } catch (Exception e) {
            return obj(false, "Driver PostgreSQL non disponibile: " + e);
        }
    }

    private String save(HttpServletRequest r) throws Exception {
        Db d = db(r);
        String adminEmail = param(r, "adminEmail");
        if (!EMAIL.matcher(adminEmail).matches()) {
            throw new IllegalArgumentException("Primo amministratore: email non valida");
        }
        boolean dev = "on".equals(param(r, "devLogin"));
        String gid = param(r, "googleId"), gsec = param(r, "googleSecret");
        if (!dev && (gid.isEmpty() || gsec.isEmpty())) {
            throw new IllegalArgumentException("Indica client ID e client secret Google (oppure abilita l'accesso di sviluppo, solo per prove)");
        }
        String crypto = param(r, "cryptoKey");
        if (crypto.length() < 16) {
            throw new IllegalArgumentException("Chiave di cifratura: almeno 16 caratteri");
        }
        String tdir = param(r, "templateDir");
        if (!tdir.isEmpty() && !Files.isDirectory(Paths.get(tdir))) {
            throw new IllegalArgumentException("Cartella dei modelli Word inesistente: " + tdir);
        }
        // il database deve rispondere con questi parametri
        String t = test(r);
        if (!t.startsWith("{\"ok\":true")) {
            return t;
        }
        Map<String, String> kv = new LinkedHashMap<>();
        kv.put("spring.datasource.url", d.url());
        kv.put("spring.datasource.username", d.user());
        kv.put("spring.datasource.password", d.password());
        kv.put("spring.datasource.hikari.maximum-pool-size", String.valueOf(d.poolMax()));
        kv.put("spring.datasource.hikari.minimum-idle", String.valueOf(d.poolMin()));
        kv.put("spring.datasource.hikari.connection-timeout", String.valueOf(d.timeout() * 1000));
        kv.put("cpqia.bootstrap-admin-email", adminEmail);
        kv.put("cpqia.auto-register", "on".equals(param(r, "autoRegister")) ? "true" : "false");
        kv.put("cpqia.dev-login", dev ? "true" : "false");
        kv.put("cpqia.oauth.google.client-id", gid);
        kv.put("cpqia.oauth.google.client-secret", gsec);
        kv.put("cpqia.crypto-key", crypto);
        kv.put("cpqia.template-dir", tdir);
        String extra = r.getParameter("extra") == null ? "" : r.getParameter("extra").trim();
        try {
            writeProps(kv, extra);
        } catch (IOException e) {
            return obj(false, "Impossibile scrivere " + propsFile + " (" + e.getMessage() + "). L'utente di Tomcat deve poter scrivere in "
                    + confDir + ": es. «mkdir -p " + confDir + " && chown tomcat: " + confDir + "».");
        }
        String how = scheduleReload();
        return obj(true, "Configurazione salvata in " + propsFile + ". " + how);
    }

    // ------------------------------------------------------------------ file di configurazione
    private static String escProp(String v, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case ':', '=', '#', '!' -> sb.append('\\').append(ch);
                case ' ' -> sb.append(key || i == 0 ? "\\ " : " ");
                default -> {
                    if (ch > 126) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void writeProps(Map<String, String> kv, String extra) throws IOException {
        Files.createDirectories(confDir);
        List<String> keep = new ArrayList<>();
        if (Files.exists(propsFile)) {
            Files.copy(propsFile, confDir.resolve(name + ".properties.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            for (String line : Files.readAllLines(propsFile, StandardCharsets.UTF_8)) {
                String t = line.trim();
                int eq = t.indexOf('=');
                String key = eq > 0 ? t.substring(0, eq).trim() : t;
                if (!MANAGED.contains(key)) {
                    keep.add(line);
                }
            }
        }
        List<String> out = new ArrayList<>(keep);
        out.add("");
        out.add("# ---- scritto dalla procedura di setup (" + java.time.LocalDateTime.now().withNano(0) + ")");
        for (Map.Entry<String, String> e : kv.entrySet()) {
            out.add(e.getKey() + "=" + escProp(e.getValue(), false));
        }
        if (!extra.isEmpty()) {
            out.add("");
            out.add("# ---- impostazioni aggiuntive");
            for (String l : extra.split("\\R")) {
                if (!l.isBlank()) {
                    out.add(l.strip());
                }
            }
        }
        Path tmp = confDir.resolve(name + ".properties.tmp");
        Files.write(tmp, out, StandardCharsets.UTF_8);
        restrict(tmp);
        Files.move(tmp, propsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        restrict(propsFile);
        try {
            Files.deleteIfExists(confDir.resolve(name + ".setup-token"));
        } catch (IOException ignored) {
            // facoltativo
        }
    }

    // ------------------------------------------------------------------ ricarica del contesto
    private String scheduleReload() {
        String path = ctx.getContextPath();
        Thread t = new Thread(() -> {
            sleep(1800);                                                // il tempo di consegnare la risposta
            try {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                for (ObjectName on : mbs.queryNames(new ObjectName("Catalina:j2eeType=WebModule,*"), null)) {
                    Object p = mbs.getAttribute(on, "path");
                    if (path.equals(p)) {
                        mbs.invoke(on, "reload", new Object[0], new String[0]);
                        return;
                    }
                }
            } catch (Throwable e) {
                ctx.log("CPQIA setup: reload via JMX non riuscito (" + e + "), provo con la data del WAR");
            }
            try {                                                       // ripiego: Tomcat ridistribuisce un WAR modificato
                Path war = Paths.get(System.getProperty("catalina.base", "."), "webapps", name + ".war");
                if (Files.exists(war)) {
                    Files.setLastModifiedTime(war, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                } else {
                    ctx.log("CPQIA setup: ricaricare il contesto " + path + " dal Manager di Tomcat o riavviare Tomcat");
                }
            } catch (Exception e) {
                ctx.log("CPQIA setup: ricaricare il contesto " + path + " dal Manager di Tomcat o riavviare Tomcat (" + e + ")");
            }
        }, "cpqia-setup-reload");
        t.setDaemon(true);
        t.start();
        return "Il contesto si ricarica da solo (alla prima partenza vengono create le tabelle: può servire un minuto). "
                + "Se non riparte, ricaricalo dal Manager di Tomcat o riavvia Tomcat.";
    }

    // ------------------------------------------------------------------ utilita'
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String randomKey() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jstr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String obj(boolean ok, String message) {
        return "{\"ok\":" + ok + ",\"message\":" + jstr(message) + "}";
    }

    private static void json(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write(body);
    }
}
