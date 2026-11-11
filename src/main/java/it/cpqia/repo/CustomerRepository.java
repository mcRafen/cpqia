package it.cpqia.repo;

import it.cpqia.domain.AppUser;
import it.cpqia.domain.Customer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Customer> findById(long id) {
        return jdbc.sql("select * from account where id = :id").param("id", id).query(Customer.class).optional();
    }

    /** account visibile all'utente: se ha un codice agente, solo i propri prospect e i clienti con lo stesso codice agente */
    public Optional<Customer> findById(long id, AppUser u) {
        return findById(id).filter(c -> visibleTo(c, u));
    }

    public static boolean visibleTo(Customer c, AppUser u) {
        if (u == null || !u.agentRestricted()) {
            return true;
        }
        return ("PROSPECT".equals(c.type()) && c.createdBy() != null && c.createdBy().equals(u.id()))
                || ("CLIENTE".equals(c.type()) && u.agentCode().trim().equals(c.agentCode()));
    }

    public Optional<Customer> findByVat(String vat) {
        return jdbc.sql("select * from account where vat_number = :v and deleted_at is null").param("v", vat).query(Customer.class).optional();
    }

    public List<Customer> search(String q, int limit) {
        return search(q, limit, null);
    }

    public List<Customer> search(String q, int limit, AppUser u) {
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        boolean restricted = u != null && u.agentRestricted();
        return jdbc.sql("""
                select * from account
                where deleted_at is null and (lower(company_name) like :q or lower(code) like :q or vat_number like :q or lower(city) like :q)
                  and (:all or (type = 'PROSPECT' and created_by = :uid) or (type = 'CLIENTE' and agent_code = :ag))
                order by lower(company_name) limit :lim""")
                .param("all", !restricted).param("uid", restricted ? u.id() : -1L).param("ag", restricted ? u.agentCode().trim() : "")
                .param("q", like).param("lim", limit).query(Customer.class).list();
    }

    public boolean vatExists(String vat, Long exceptId) {
        long n = jdbc.sql("select count(*) from account where vat_number = :v and id <> :id and deleted_at is null")
                .param("v", vat).param("id", exceptId == null ? -1L : exceptId).query(Long.class).single();
        return n > 0;
    }

    /** il codice cliente e' calcolato dal database (sequenza) e non e' modificabile */
    public long insert(Customer c, long userId) {
        return jdbc.sql("""
                insert into account (code, type, company_name, street_type, street_name, street_number, postal_code,
                    city, province, vat_number, tax_code, agent_code, created_by)
                values ('C' || lpad(nextval('account_code_seq')::text, 6, '0'), :type, :name, :st, :sn, :num, :cap,
                    :city, :prov, :vat, :cf, :ag, :by)
                returning id""")
                .param("type", c.type()).param("name", c.companyName()).param("st", c.streetType())
                .param("sn", c.streetName()).param("num", c.streetNumber()).param("cap", c.postalCode())
                .param("city", c.city()).param("prov", blankToNull(c.province())).param("vat", c.vatNumber())
                .param("cf", blankToNull(c.taxCode())).param("ag", blankToNull(c.agentCode())).param("by", userId)
                .query(Long.class).single();
    }

    public void update(Customer c) {
        jdbc.sql("""
                update account set type = :type, company_name = :name, street_type = :st, street_name = :sn,
                    street_number = :num, postal_code = :cap, city = :city, province = :prov, vat_number = :vat,
                    tax_code = :cf, agent_code = :ag where id = :id""")
                .param("type", c.type()).param("name", c.companyName()).param("st", c.streetType())
                .param("sn", c.streetName()).param("num", c.streetNumber()).param("cap", c.postalCode())
                .param("city", c.city()).param("prov", blankToNull(c.province())).param("vat", c.vatNumber())
                .param("cf", blankToNull(c.taxCode())).param("ag", blankToNull(c.agentCode())).param("id", c.id()).update();
    }

    /** {codice agente, descrizione decodificata dalla tabella agent} dell'account (stringhe vuote se assenti) */
    public String[] agentInfo(long accountId) {
        return jdbc.sql("select coalesce(c.agent_code, ''), coalesce(a.description, '') from account c left join agent a on a.code = c.agent_code where c.id = :id")
                .param("id", accountId).query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).optional().orElse(new String[]{"", ""});
    }

    /** agenti (codice, descrizione) per la scelta del codice agente di un prospect */
    public java.util.List<String[]> agents() {
        return jdbc.sql("select code, description from agent order by lower(description), code")
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list();
    }

    public boolean agentExists(String code) {
        return jdbc.sql("select count(*) from agent where code = :c").param("c", code).query(Long.class).single() > 0;
    }

    public boolean hasOffers(long id) {
        return jdbc.sql("select count(*) from offer where customer_id = :id and deleted_at is null").param("id", id).query(Long.class).single() > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** dati di geolocalizzazione (sola lettura): latitudine, longitudine, testo di stato */
    public java.util.Map<String, String> geo(long id) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        jdbc.sql("select latitude::text as lat, longitude::text as lon, geo_status as st, to_char(geo_at, 'DD/MM/YYYY HH24:MI:SS') as at from account where id = :id")
                .param("id", id).query((rs, n) -> {
                    String st = rs.getString("st");
                    out.put("lat", rs.getString("lat") == null ? "" : rs.getString("lat"));
                    out.put("lon", rs.getString("lon") == null ? "" : rs.getString("lon"));
                    out.put("status", st == null ? "" : switch (st) {
                        case "OK" -> rs.getString("at") != null ? "Localizzato il " + rs.getString("at") : "Localizzato";
                        case "PENDING" -> "In corso (indirizzo inserito o modificato)";
                        case "NOT_FOUND" -> "Indirizzo non trovato sulla mappa";
                        default -> "Da geolocalizzare (agente Geo)";
                    });
                    return null;
                }).list();
        return out;
    }
}
