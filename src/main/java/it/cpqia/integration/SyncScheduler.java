package it.cpqia.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Controlla ogni minuto se la sincronizzazione clienti pianificata (Amministrazione > Integrazioni) e' da eseguire. */
@Component
@EnableScheduling
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private final AccountSyncService sync;
    private final AgentSyncService agents;
    private final HwCardSyncService hwCards;
    private final it.cpqia.geo.GeoAgentService geoAgent;

    public SyncScheduler(AccountSyncService sync, AgentSyncService agents, HwCardSyncService hwCards, it.cpqia.geo.GeoAgentService geoAgent) {
        this.sync = sync;
        this.agents = agents;
        this.hwCards = hwCards;
        this.geoAgent = geoAgent;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void tick() {
        try {
            if (sync.due(LocalDateTime.now())) {
                log.info("Avvio sincronizzazione clienti da IBM Power System");
                log.info("{}", sync.run("SCHEDULED").message());
            }
        } catch (Exception e) {
            log.warn("Sincronizzazione pianificata non eseguita: {}", e.toString());
        }
        try {
            if (agents.due(LocalDateTime.now())) {
                log.info("Avvio sincronizzazione agenti da IBM Power System");
                log.info("{}", agents.run("SCHEDULED").message());
            }
        } catch (Exception e) {
            log.warn("Sincronizzazione agenti pianificata non eseguita: {}", e.toString());
        }
        try {
            if (hwCards.due(LocalDateTime.now())) {
                log.info("Avvio sincronizzazione schede HW da IBM Power System");
                log.info("{}", hwCards.run("SCHEDULED").message());
            }
        } catch (Exception e) {
            log.warn("Sincronizzazione schede HW pianificata non eseguita: {}", e.toString());
        }
        try {
            if (geoAgent.due(LocalDateTime.now())) {
                log.info("Avvio agente Geo (pianificato)");
                log.info("{}", geoAgent.run("SCHEDULED").message());
            }
        } catch (Exception e) {
            log.warn("Agente Geo pianificato non eseguito: {}", e.toString());
        }
    }
}
