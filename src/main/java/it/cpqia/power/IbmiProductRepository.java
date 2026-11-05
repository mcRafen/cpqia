package it.cpqia.power;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Prodotti IBM i attivi (anagrafica in Amministrazione), da valorizzare nella configurazione Power System dell'offerta. */
@Repository
public class IbmiProductRepository {

    public record Product(Long id, String code, String description) {
    }

    private final JdbcClient jdbc;

    public IbmiProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> active() {
        return jdbc.sql("select id, code, description from ibmi_product where deleted_at is null and active order by sort_order, code")
                .query(Product.class).list();
    }
}
