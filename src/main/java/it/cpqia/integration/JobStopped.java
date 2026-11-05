package it.cpqia.integration;

/** Esecuzione di un job (sync clienti, sync agenti, agente Geo) interrotta dall'utente con «Ferma». */
public class JobStopped extends RuntimeException {
    public JobStopped() {
        super("interrotta dall'utente", null, false, false);
    }
}
