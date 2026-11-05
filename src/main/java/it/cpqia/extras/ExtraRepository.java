package it.cpqia.extras;

import it.cpqia.extras.ExtraModel.Group;
import it.cpqia.extras.ExtraModel.Option;
import it.cpqia.extras.ExtraModel.OptionQty;
import it.cpqia.extras.ExtraModel.Selection;
import it.cpqia.extras.ExtraModel.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Catalogo dei servizi accessori (con cache) e selezioni salvate per offerta. */
@Repository
public class ExtraRepository {

    private final JdbcClient jdbc;
    private volatile List<Service> cache;

    public ExtraRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void evict() {
        this.cache = null;
    }

    private record ServiceRow(long id, String code, String title, String summaryName, String includedText) {
    }

    private record GroupRow(long id, long serviceId, String code, String title, String selection, boolean required) {
    }

    private record OptionRow(long id, long groupId, String rowKey, String articleCode, String label, BigDecimal onetime,
                             BigDecimal annual, BigDecimal extraUnit, boolean adHoc, String adHocText, boolean qtyEnabled) {
    }

    private record SelOption(long optionId, int qty) {
    }

    public synchronized List<Service> loadAll() {
        List<Service> c = cache;
        if (c != null) {
            return c;
        }
        List<ServiceRow> services = jdbc.sql("select id, code, title, summary_name, included_text from extra_service where active order by sort_order, id")
                .query(ServiceRow.class).list();
        List<GroupRow> groups = jdbc.sql("select id, service_id, code, title, selection, required from extra_group order by sort_order, id")
                .query(GroupRow.class).list();
        List<OptionRow> options = jdbc.sql("""
                select id, group_id, row_key, article_code, label, onetime, annual, extra_unit, ad_hoc, ad_hoc_text, qty_enabled
                from extra_option order by sort_order, id""").query(OptionRow.class).list();
        List<Service> result = new ArrayList<>();
        for (ServiceRow s : services) {
            List<Group> gs = new ArrayList<>();
            for (GroupRow g : groups) {
                if (g.serviceId() != s.id()) {
                    continue;
                }
                List<Option> os = new ArrayList<>();
                for (OptionRow o : options) {
                    if (o.groupId() == g.id()) {
                        os.add(new Option(o.id(), o.groupId(), o.rowKey(), o.articleCode(), o.label(), o.onetime(), o.annual(),
                                o.extraUnit(), o.adHoc(), o.adHocText(), o.qtyEnabled()));
                    }
                }
                gs.add(new Group(g.id(), g.serviceId(), g.code(), g.title(), g.selection(), g.required(), os));
            }
            result.add(new Service(s.id(), s.code(), s.title(), s.summaryName(), s.includedText(), gs));
        }
        this.cache = result;
        return result;
    }

    // ------------------------------------------------------------------ selezioni per offerta
    public void saveSelection(long offerId, Selection sel) {
        jdbc.sql("delete from offer_extra_option where offer_id = :o").param("o", offerId).update();
        jdbc.sql("delete from offer_extra_service where offer_id = :o").param("o", offerId).update();
        if (sel == null) {
            return;
        }
        for (Long sid : sel.services()) {
            jdbc.sql("insert into offer_extra_service (offer_id, service_id) values (:o, :s)").param("o", offerId).param("s", sid).update();
        }
        for (OptionQty oq : sel.options()) {
            jdbc.sql("insert into offer_extra_option (offer_id, option_id, qty) values (:o, :p, :q)")
                    .param("o", offerId).param("p", oq.optionId()).param("q", oq.qty() == null ? 1 : oq.qty()).update();
        }
    }

    public Selection loadSelection(long offerId) {
        List<Long> services = jdbc.sql("select service_id from offer_extra_service where offer_id = :o order by service_id")
                .param("o", offerId).query(Long.class).list();
        List<OptionQty> options = new ArrayList<>();
        for (SelOption so : jdbc.sql("select option_id, qty from offer_extra_option where offer_id = :o order by option_id")
                .param("o", offerId).query(SelOption.class).list()) {
            options.add(new OptionQty(so.optionId(), so.qty()));
        }
        return new Selection(services, options);
    }
}
