package it.cpqia.trash;

import it.cpqia.domain.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cancellazione logica, Cestino (elenco, ripristino, eliminazione definitiva). */
@Service
public class TrashService {

    private final JdbcClient jdbc;

    private final it.cpqia.repo.CatalogRepository catalog;

    public TrashService(JdbcClient jdbc, it.cpqia.repo.CatalogRepository catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    /** il catalogo del configuratore e' in cache: va invalidato quando cambiano applicazioni, moduli e relativi dettagli */
    private void evictCatalog(TrashKind k) {
        switch (k) {
            case APPLICATION, MODULE, MODE, DRIVER, GROUP, TIER, RULE -> catalog.evict();
            default -> {
            }
        }
    }

    private long count(String sql, long id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    /** sposta il record nel Cestino, dopo i controlli di integrita' (record usati altrove non si eliminano) */
    public void delete(TrashKind k, long id, AppUser user) {
        switch (k) {
            case CUSTOMER -> {
                if (count("select count(*) from offer where customer_id = :id and deleted_at is null", id) > 0) {
                    throw new IllegalArgumentException("Impossibile eliminare: esistono offerte collegate all'account");
                }
            }
            case APPLICATION -> {
                if (count("select count(*) from offer_line l join offer o on o.id = l.offer_id where l.application_id = :id and o.deleted_at is null", id) > 0) {
                    throw new IllegalArgumentException("Impossibile eliminare: l'applicazione è usata in offerte. Disattivala invece di eliminarla");
                }
            }
            case MODULE -> {
                if (count("select count(*) from offer_line_module lm join offer_line l on l.id = lm.line_id join offer o on o.id = l.offer_id "
                        + "where lm.module_id = :id and o.deleted_at is null", id) > 0) {
                    throw new IllegalArgumentException("Impossibile eliminare: il modulo è usato in offerte");
                }
            }
            case USER -> {
                if (user != null && user.id() == id) {
                    throw new IllegalArgumentException("Non puoi eliminare il tuo utente");
                }
                boolean target = jdbc.sql("select administrator and active from app_user where id = :id").param("id", id).query(Boolean.class).optional().orElse(false);
                if (target && count("select count(*) from app_user where administrator and active and deleted_at is null and id <> :id", id) == 0) {
                    throw new IllegalArgumentException("Deve rimanere almeno un amministratore attivo");
                }
            }
            default -> {
            }
        }
        int n = jdbc.sql("update " + k.table() + " set deleted_at = now(), deleted_by = :u where id = :id and deleted_at is null")
                .param("u", user == null ? null : user.id()).param("id", id).update();
        if (n == 0) {
            throw new IllegalArgumentException("Record inesistente o già eliminato");
        }
        evictCatalog(k);
    }

    /** elenco del Cestino: l'amministratore vede tutto, gli altri solo quanto hanno eliminato loro */
    public List<Map<String, Object>> list(AppUser user) {
        boolean all = user.administrator();
        List<String> parts = new ArrayList<>();
        for (TrashKind k : TrashKind.values()) {
            parts.add("select '" + k.name() + "' as kind, '" + k.title().replace("'", "''") + "' as kind_title, id, "
                    + "(" + k.labelSql() + ") as label, deleted_at, deleted_by from " + k.table()
                    + " where deleted_at is not null" + (all ? "" : " and deleted_by = :me"));
        }
        String sql = "select t.kind || ':' || t.id as id, t.kind, t.kind_title, t.label, t.deleted_at, "
                + "coalesce(u.full_name, u.email) as deleted_by_name from (" + String.join(" union all ", parts) + ") t "
                + "left join app_user u on u.id = t.deleted_by order by t.deleted_at desc";
        JdbcClient.StatementSpec s = jdbc.sql(sql);
        if (!all) {
            s = s.param("me", user.id());
        }
        return s.query().listOfRows();
    }

    /** ripristino: l'amministratore qualsiasi record, gli altri solo quelli eliminati da loro */
    public void restore(TrashKind k, long id, AppUser user) {
        Long by = jdbc.sql("select deleted_by from " + k.table() + " where id = :id and deleted_at is not null").param("id", id)
                .query(Long.class).optional().orElse(null);
        boolean exists = jdbc.sql("select count(*) from " + k.table() + " where id = :id and deleted_at is not null").param("id", id)
                .query(Long.class).single() > 0;
        if (!exists) {
            throw new IllegalArgumentException("Record non presente nel Cestino");
        }
        if (!user.administrator() && (by == null || !by.equals(user.id()))) {
            throw new IllegalArgumentException("Puoi ripristinare solo i record eliminati da te");
        }
        if (k.deletedParentSql() != null && count(k.deletedParentSql(), id) > 0) {
            throw new IllegalArgumentException("Prima ripristina l'elemento da cui dipende (es. l'applicazione)");
        }
        if (k == TrashKind.OFFER) {
            if (count("select count(*) from account c join offer o on o.customer_id = c.id where o.id = :id and c.deleted_at is not null", id) > 0) {
                throw new IllegalArgumentException("Prima ripristina l'account dell'offerta");
            }
            if (count("select count(*) from offer_line l join application a on a.id = l.application_id where l.offer_id = :id and a.deleted_at is not null", id) > 0) {
                throw new IllegalArgumentException("L'offerta usa applicazioni eliminate: ripristinale prima");
            }
        }
        try {
            jdbc.sql("update " + k.table() + " set deleted_at = null, deleted_by = null where id = :id").param("id", id).update();
            evictCatalog(k);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Esiste già un record attivo con lo stesso codice/numero: rinominalo o eliminalo prima di ripristinare");
        }
    }

    /** eliminazione definitiva (solo amministratore) */
    public void purge(TrashKind k, long id, AppUser user) {
        if (!user.administrator()) {
            throw new IllegalArgumentException("Solo un amministratore può eliminare definitivamente");
        }
        try {
            int n = jdbc.sql("delete from " + k.table() + " where id = :id and deleted_at is not null").param("id", id).update();
            if (n == 0) {
                throw new IllegalArgumentException("Record non presente nel Cestino");
            }
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Il record è referenziato da altri dati: non può essere eliminato definitivamente");
        }
    }

    public record EmptyResult(int deleted, int skipped) {
    }

    /**
     * Svuota il Cestino (solo administrator): elimina definitivamente i record eliminati logicamente, figli prima dei genitori.
     * I record ancora referenziati da altri dati vengono saltati e contati.
     */
    public EmptyResult purgeAll(AppUser user) {
        if (!user.administrator()) {
            throw new IllegalArgumentException("Solo un amministratore può svuotare il Cestino");
        }
        TrashKind[] order = {TrashKind.RULE, TrashKind.TIER, TrashKind.GROUP, TrashKind.DRIVER, TrashKind.MODE, TrashKind.MODULE,
                TrashKind.OFFER, TrashKind.APPLICATION, TrashKind.CUSTOMER, TrashKind.USER, TrashKind.PROFILE, TrashKind.TRAVEL, TrashKind.SEDE};
        int deleted = 0;
        for (int round = 0; round < 3; round++) {          // piu' passate: un genitore si libera dopo i figli
            int before = deleted;
            for (TrashKind k : order) {
                List<Long> ids = jdbc.sql("select id from " + k.table() + " where deleted_at is not null").query(Long.class).list();
                for (Long id : ids) {
                    try {
                        deleted += jdbc.sql("delete from " + k.table() + " where id = :id and deleted_at is not null").param("id", id).update();
                    } catch (DataIntegrityViolationException e) {
                        // ancora referenziato: resta nel Cestino
                    }
                }
            }
            if (deleted == before) {
                break;
            }
        }
        int skipped = 0;
        for (TrashKind k : order) {
            skipped += jdbc.sql("select count(*) from " + k.table() + " where deleted_at is not null").query(Integer.class).single();
        }
        return new EmptyResult(deleted, skipped);
    }
}
