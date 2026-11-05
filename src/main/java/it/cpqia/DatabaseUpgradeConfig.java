package it.cpqia;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Aggiornamento del database all'avvio del WAR. Tutte le migrazioni (V1 ... ultima) sono nel WAR: Flyway applica in ordine
 * quelle che mancano, qualunque sia la versione che girava prima (una installazione nuova le applica tutte).
 * Controlli di sicurezza: il database non puo' essere piu' nuovo del WAR (WAR vecchio su dati aggiornati) e le migrazioni gia'
 * applicate non possono essere state alterate; in entrambi i casi l'applicazione NON parte e il log spiega il motivo.
 */
@Configuration
public class DatabaseUpgradeConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpgradeConfig.class);

    /** con ignoreMigrationPatterns vuoto anche le migrazioni "future" (presenti nel database ma non nel WAR) bloccano l'avvio */
    @Bean
    public FlywayConfigurationCustomizer cpqiaFlywayCustomizer() {
        return c -> c.ignoreMigrationPatterns(new String[0]).validateOnMigrate(true).outOfOrder(false).baselineOnMigrate(false);
    }

    @Bean
    public FlywayMigrationStrategy cpqiaMigrationStrategy() {
        return (Flyway flyway) -> {
            MigrationInfoService info = flyway.info();
            MigrationInfo current = info.current();
            MigrationInfo[] pending = info.pending();
            String from = current == null ? "database vuoto" : "V" + current.getVersion();
            if (pending.length == 0) {
                log.info("CPQIA database: schema {} gia' aggiornato, nessuna migrazione da applicare", from);
            } else {
                log.info("CPQIA database: aggiornamento da {} a V{} ({} migrazioni): {}", from, pending[pending.length - 1].getVersion(), pending.length,
                        String.join(", ", Arrays.stream(pending).map(m -> "V" + m.getVersion()).toList()));
            }
            flyway.migrate();
            MigrationInfo now = flyway.info().current();
            log.info("CPQIA database: schema alla versione V{}", now == null ? "?" : now.getVersion());
        };
    }
}
