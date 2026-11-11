package it.cpqia.terms;

import it.cpqia.terms.TermsModel.PaymentTerms;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TermsRepository {

    private final JdbcClient jdbc;

    public TermsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(long offerId, PaymentTerms t) {
        jdbc.sql("""
                update offer set bill_lic = :bl, bill_lic_date = :bld, bill_fee = :bf, bill_fee_date = :bfd,
                    fee_semiannual = :sem, bill_act = :ba, pay_lic = :pl, pay_lic_other = :plo, pay_fee = :pf,
                    pay_fee_other = :pfo, pay_act = :pa, pay_act_other = :pao,
                    bill_dri = coalesce(:bdr, 'INSTALL'), bill_dri_date = :bdrd, pay_dri = coalesce(:pdr, 'USUAL'), pay_dri_other = :pdro where id = :id""")
                .param("bl", t.billLic()).param("bld", t.billLicDate()).param("bf", t.billFee()).param("bfd", t.billFeeDate())
                .param("sem", t.feeSemiannual()).param("ba", t.billAct()).param("pl", t.payLic()).param("plo", blank(t.payLicOther()))
                .param("pf", t.payFee()).param("pfo", blank(t.payFeeOther())).param("pa", t.payAct()).param("pao", blank(t.payActOther()))
                .param("bdr", t.billDri()).param("bdrd", t.billDriDate()).param("pdr", t.payDri()).param("pdro", blank(t.payDriOther()))
                .param("id", offerId).update();
    }

    public PaymentTerms load(long offerId) {
        return jdbc.sql("""
                select bill_lic, bill_lic_date, bill_fee, bill_fee_date, fee_semiannual, bill_act, pay_lic, pay_lic_other,
                       pay_fee, pay_fee_other, pay_act, pay_act_other, bill_dri, bill_dri_date, pay_dri, pay_dri_other from offer where id = :id""")
                .param("id", offerId).query(PaymentTerms.class).single();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
