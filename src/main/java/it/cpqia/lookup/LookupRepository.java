package it.cpqia.lookup;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Tabelle di riferimento dell'account: Tipo Azienda e Tipo Indirizzo. */
@Repository
public class LookupRepository {

    public record CompanyType(String code, String label) {
    }

    private final JdbcClient jdbc;

    public LookupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompanyType> companyTypes() {
        return jdbc.sql("select code, label from company_type order by sort_order, label")
                .query((rs, n) -> new CompanyType(rs.getString("code"), rs.getString("label"))).list();
    }

    public List<String> streetTypes() {
        return jdbc.sql("select name from address_type order by sort_order, name").query(String.class).list();
    }
}
