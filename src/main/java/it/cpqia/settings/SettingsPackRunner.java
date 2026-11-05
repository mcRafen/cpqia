package it.cpqia.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** All'avvio (dopo le migrazioni del database) applica il pacchetto delle impostazioni contenuto nel WAR, se e' nuovo. */
@Component
@Order(10)
public class SettingsPackRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SettingsPackRunner.class);
    private final SettingsPackService service;

    public SettingsPackRunner(SettingsPackService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            service.applyIfNew().ifPresentOrElse(s -> log.info("CPQIA pacchetto impostazioni applicato"), () -> log.info("CPQIA pacchetto impostazioni: nulla da applicare"));
        } catch (Exception e) {
            // l'installazione continua a funzionare con le impostazioni attuali; il pacchetto sara' riprovato al prossimo avvio
            log.error("CPQIA pacchetto impostazioni NON applicato (transazione annullata): {}", e.toString(), e);
        }
    }
}
