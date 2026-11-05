package it.cpqia.repo;

import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Driver;
import it.cpqia.pricing.PricingModel.Group;
import it.cpqia.pricing.PricingModel.Mode;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.pricing.PricingModel.Rule;
import it.cpqia.pricing.PricingModel.Tier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lettura del catalogo (applicazioni, moduli, prezzi, fasce, vincoli) nel modello di calcolo. Con cache. */
@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;
    private volatile List<App> cache;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** invalida la cache; se c'e' una transazione in corso lo fa dopo il commit (evita di ri-cachare dati vecchi) */
    public void evict() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CatalogRepository.this.cache = null;
                }
            });
        } else {
            this.cache = null;
        }
    }

    public List<App> loadAll(boolean onlyActive) {
        List<App> all = cache;
        if (all == null) {
            all = load();
            cache = all;
        }
        if (!onlyActive) {
            return all;
        }
        List<App> res = new ArrayList<>();
        for (App a : all) {
            if (a.active()) {
                res.add(a);
            }
        }
        return res;
    }

    public Optional<App> find(long id) {
        for (App a : loadAll(false)) {
            if (a.id() == id) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ caricamento
    private record AppRow(long id, String code, String name, String family, BigDecimal maintenancePct,
                          boolean needsReview, String notes, boolean active) {
    }

    private record ModeRow(long applicationId, String code, String label, boolean isDefault) {
    }

    private record DriverRow(long id, long applicationId, String code, String label, String licenseCode, String unitLabel) {
    }

    private record GroupRow(long id, long applicationId, long driverId, String code, String label, String baseMode) {
    }

    private record BaseRow(long groupId, String modeCode, BigDecimal amount) {
    }

    private record TierRow(long id, long groupId, long fromQty, Long toQty, String label, BigDecimal pct) {
    }

    private record TierPriceRow(long tierId, String modeCode, BigDecimal unitPrice) {
    }

    private record ModuleRow(long id, long applicationId, Long groupId, String code, String printCode, String description,
                             String section, String kind, boolean qtyEnabled, String exclGroup, boolean defaultSelected,
                             boolean included, BigDecimal maintenancePct, BigDecimal onetimePrice, String linkText,
                             String note, boolean active, String area) {
    }

    private record PriceRow(long moduleId, String modeCode, BigDecimal price) {
    }

    private record RuleRow(long id, long moduleId, String ruleType, String targetCodes, String severity, String message) {
    }

    private synchronized List<App> load() {
        List<AppRow> apps = jdbc.sql("select id, code, name, family, maintenance_pct, needs_review, notes, active from application where deleted_at is null order by sort_order, name")
                .query(AppRow.class).list();
        List<ModeRow> modes = jdbc.sql("select application_id, code, label, is_default from app_mode where deleted_at is null order by sort_order, id").query(ModeRow.class).list();
        List<DriverRow> drivers = jdbc.sql("select id, application_id, code, label, license_code, unit_label from app_driver where deleted_at is null order by sort_order, id").query(DriverRow.class).list();
        List<GroupRow> groups = jdbc.sql("select id, application_id, driver_id, code, label, base_mode from price_group where deleted_at is null order by sort_order, id").query(GroupRow.class).list();
        List<BaseRow> bases = jdbc.sql("select group_id, mode_code, amount from price_group_base").query(BaseRow.class).list();
        List<TierRow> tiers = jdbc.sql("select id, group_id, from_qty, to_qty, label, pct from pricing_tier where deleted_at is null order by group_id, sort_order, from_qty").query(TierRow.class).list();
        List<TierPriceRow> tierPrices = jdbc.sql("select tier_id, mode_code, unit_price from pricing_tier_price").query(TierPriceRow.class).list();
        List<ModuleRow> modules = jdbc.sql("""
                select id, application_id, group_id, code, print_code, description, section, kind, qty_enabled, excl_group,
                       default_selected, included, maintenance_pct, onetime_price, link_text, note, active, area
                from module where active and deleted_at is null order by application_id, sort_order, id""").query(ModuleRow.class).list();
        List<PriceRow> prices = jdbc.sql("select module_id, mode_code, price from module_price").query(PriceRow.class).list();
        List<RuleRow> rules = jdbc.sql("select id, module_id, rule_type, target_codes, severity, message from module_rule where deleted_at is null order by id").query(RuleRow.class).list();

        Map<Long, Map<String, BigDecimal>> tierPriceMap = new LinkedHashMap<>();
        for (TierPriceRow t : tierPrices) {
            tierPriceMap.computeIfAbsent(t.tierId(), k -> new LinkedHashMap<>()).put(t.modeCode(), t.unitPrice());
        }
        Map<Long, List<Tier>> tiersByGroup = new LinkedHashMap<>();
        for (TierRow t : tiers) {
            tiersByGroup.computeIfAbsent(t.groupId(), k -> new ArrayList<>()).add(
                    new Tier(t.id(), t.fromQty(), t.toQty(), t.label(), t.pct(), tierPriceMap.getOrDefault(t.id(), Map.of())));
        }
        Map<Long, Map<String, BigDecimal>> baseMap = new LinkedHashMap<>();
        for (BaseRow b : bases) {
            baseMap.computeIfAbsent(b.groupId(), k -> new LinkedHashMap<>()).put(b.modeCode(), b.amount());
        }
        Map<Long, Map<String, BigDecimal>> priceMap = new LinkedHashMap<>();
        for (PriceRow p : prices) {
            priceMap.computeIfAbsent(p.moduleId(), k -> new LinkedHashMap<>()).put(p.modeCode(), p.price());
        }
        Map<Long, List<Rule>> ruleMap = new LinkedHashMap<>();
        for (RuleRow r : rules) {
            ruleMap.computeIfAbsent(r.moduleId(), k -> new ArrayList<>()).add(new Rule(r.id(), r.ruleType(), r.targetCodes(), r.severity(), r.message()));
        }

        List<App> result = new ArrayList<>();
        for (AppRow a : apps) {
            List<Mode> ms = new ArrayList<>();
            for (ModeRow m : modes) {
                if (m.applicationId() == a.id()) {
                    ms.add(new Mode(m.code(), m.label(), m.isDefault()));
                }
            }
            List<Driver> ds = new ArrayList<>();
            for (DriverRow d : drivers) {
                if (d.applicationId() == a.id()) {
                    ds.add(new Driver(d.id(), d.code(), d.label(), d.licenseCode(), d.unitLabel()));
                }
            }
            List<Group> gs = new ArrayList<>();
            for (GroupRow g : groups) {
                if (g.applicationId() == a.id()) {
                    gs.add(new Group(g.id(), g.code(), g.label(), g.driverId(), g.baseMode(),
                            baseMap.getOrDefault(g.id(), Map.of()), tiersByGroup.getOrDefault(g.id(), List.of())));
                }
            }
            List<Module> mods = new ArrayList<>();
            for (ModuleRow m : modules) {
                if (m.applicationId() == a.id()) {
                    mods.add(new Module(m.id(), m.code(), m.printCode(), m.description(), m.groupId(), m.section(), m.kind(),
                            m.qtyEnabled(), m.exclGroup(), m.defaultSelected(), m.included(), m.maintenancePct(),
                            m.onetimePrice(), m.linkText(), m.note(), priceMap.getOrDefault(m.id(), Map.of()),
                            ruleMap.getOrDefault(m.id(), List.of()), m.area()));
                }
            }
            result.add(new App(a.id(), a.code(), a.name(), a.family(), a.maintenancePct(), a.needsReview(), a.active(), a.notes(), ms, ds, gs, mods));
        }
        return result;
    }
}
