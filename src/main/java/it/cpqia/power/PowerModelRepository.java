package it.cpqia.power;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Modelli IBM Power System attivi (anagrafica in Amministrazione) per la scelta nell'offerta. */
@Repository
public class PowerModelRepository {

    public record PowerModel(Long id, String model, String type, int cores, String mounting, String version, int cpw,
                             String description, String processorGroup, String ramOptions, String diskOptions, String ghz) {
    }

    private final JdbcClient jdbc;

    public PowerModelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PowerModel> active() {
        return jdbc.sql("select id, model, type, cores, mounting, version, cpw, coalesce(nullif(description, ''), 'Model ' || model || ' ' || cores || ' core ' || mounting) as description, "
                + "processor_group, ram_options, disk_options, ghz from power_model where deleted_at is null and active order by sort_order, model, cores, mounting")
                .query(PowerModel.class).list();
    }
}
