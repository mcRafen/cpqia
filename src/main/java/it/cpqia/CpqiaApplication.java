package it.cpqia;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point. Su Tomcat ogni WAR e' un contesto separato: la configurazione di ciascun contesto viene letta da
 * ${catalina.base}/conf/cpqia/&lt;nome-contesto&gt;.properties (es. conf/cpqia/cpqia.properties per /cpqia),
 * quindi piu' istanze (test, produzione, clienti diversi) possono convivere con database e credenziali OAuth distinti.
 */
@SpringBootApplication
public class CpqiaApplication extends SpringBootServletInitializer {

    private static final ThreadLocal<String> CONTEXT_NAME = new ThreadLocal<>();

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        String path = servletContext.getContextPath();
        String name = (path == null || path.isEmpty() || "/".equals(path)) ? "ROOT" : path.substring(1).replace('/', '#');
        if (!it.cpqia.setup.SetupServlet.configured(name)) {      // contesto non ancora configurato: parte la procedura di setup
            it.cpqia.setup.SetupServlet.install(servletContext, name);
            return;
        }
        servletContext.setSessionTimeout(480);
        servletContext.getSessionCookieConfig().setHttpOnly(true);
        CONTEXT_NAME.set(name);
        try {
            super.onStartup(servletContext);
        } finally {
            CONTEXT_NAME.remove();
        }
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        String name = CONTEXT_NAME.get() == null ? "cpqia" : CONTEXT_NAME.get();
        return builder.sources(CpqiaApplication.class)
                .properties("cpqia.context-name=" + name,
                        "spring.config.additional-location=optional:file:${catalina.base:.}/conf/cpqia/" + name + ".properties");
    }

    public static void main(String[] args) {
        SpringApplication.run(CpqiaApplication.class, args);
    }
}
