package it.cpqia.repo;

import it.cpqia.domain.AppUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("select * from app_user where lower(email) = lower(:e) and deleted_at is null").param("e", email)
                .query(AppUser.class).optional();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.sql("select * from app_user where id = :id").param("id", id).query(AppUser.class).optional();
    }

    public List<AppUser> findAll() {
        return jdbc.sql("select * from app_user where deleted_at is null order by lower(coalesce(full_name, email))").query(AppUser.class).list();
    }

    public long count() {
        return jdbc.sql("select count(*) from app_user where deleted_at is null").query(Long.class).single();
    }

    public long insert(String email, String firstName, String lastName, String fullName, String initials,
                       String pictureUrl, String provider, String subject, boolean active, boolean administrator) {
        return jdbc.sql("""
                insert into app_user (email, first_name, last_name, full_name, initials, picture_url, auth_provider,
                    provider_subject, active, administrator, can_manage_catalog, can_view_all_offers, can_delete_offers)
                values (:email, :fn, :ln, :full, :ini, :pic, :prov, :sub, :act, :adm, :adm, :adm, :adm)
                returning id""")
                .param("email", email).param("fn", firstName).param("ln", lastName).param("full", fullName)
                .param("ini", initials == null ? "" : initials).param("pic", pictureUrl).param("prov", provider)
                .param("sub", subject).param("act", active).param("adm", administrator)
                .query(Long.class).single();
    }

    public void touchLogin(long id, String provider, String subject, String pictureUrl) {
        jdbc.sql("""
                update app_user set last_login_at = now(), auth_provider = :prov, provider_subject = :sub,
                    picture_url = coalesce(:pic, picture_url) where id = :id""")
                .param("prov", provider).param("sub", subject).param("pic", pictureUrl).param("id", id).update();
    }

    public void updateProfile(long id, String firstName, String lastName, String initials) {
        jdbc.sql("""
                update app_user set first_name = :fn, last_name = :ln, initials = :ini,
                    full_name = trim(coalesce(:fn, '') || ' ' || coalesce(:ln, '')) where id = :id""")
                .param("fn", firstName).param("ln", lastName).param("ini", initials).param("id", id).update();
    }

    /** aggiornamento completo da parte di un administrator */
    public void updateAdmin(AppUser u) {
        jdbc.sql("""
                update app_user set first_name = :fn, last_name = :ln, full_name = :full, initials = :ini, active = :act,
                    administrator = :adm, can_create_offers = :c1, can_edit_offers = :c2, can_view_all_offers = :c3,
                    can_delete_offers = :c4, can_export_docx = :c5, can_manage_customers = :c6,
                    can_manage_catalog = :c7, can_view_prices = :c8, agent_code = :ag where id = :id""")
                .param("fn", u.firstName()).param("ln", u.lastName()).param("full", u.fullName())
                .param("ini", u.initials() == null ? "" : u.initials()).param("act", u.active())
                .param("adm", u.administrator()).param("c1", u.canCreateOffers()).param("c2", u.canEditOffers())
                .param("c3", u.canViewAllOffers()).param("c4", u.canDeleteOffers()).param("c5", u.canExportDocx())
                .param("c6", u.canManageCustomers()).param("c7", u.canManageCatalog()).param("c8", u.canViewPrices())
                .param("ag", u.agentCode() == null || u.agentCode().isBlank() ? null : u.agentCode().trim()).param("id", u.id()).update();
    }

    public boolean agentExists(String code) {
        return jdbc.sql("select count(*) from agent where code = :c").param("c", code).query(Long.class).single() > 0;
    }

    /** agenti selezionabili: {codice, descrizione} */
    public java.util.List<String[]> agents() {
        return jdbc.sql("select code, description from agent order by lower(description), code")
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list();
    }
}
