package it.cpqia.audit;

/** Utente della richiesta in corso: serve al trigger di journaling del database per sapere chi sta modificando i dati. */
public final class AuditContext {

    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void set(String email) {
        if (email == null || email.isBlank()) {
            EMAIL.remove();
        } else {
            EMAIL.set(email.trim().toLowerCase());
        }
    }

    /** null = nessun utente (operazioni di sistema: migrazioni, attivita' pianificate) */
    public static String email() {
        return EMAIL.get();
    }

    public static void clear() {
        EMAIL.remove();
    }
}
