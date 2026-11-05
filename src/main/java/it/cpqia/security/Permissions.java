package it.cpqia.security;

import it.cpqia.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/** Traduce i flag dell'utente applicativo in authority Spring Security. */
public final class Permissions {

    public static final String CREATE_OFFERS = "P_CREATE_OFFERS";
    public static final String EDIT_OFFERS = "P_EDIT_OFFERS";
    public static final String VIEW_ALL_OFFERS = "P_VIEW_ALL_OFFERS";
    public static final String DELETE_OFFERS = "P_DELETE_OFFERS";
    public static final String EXPORT_DOCX = "P_EXPORT_DOCX";
    public static final String MANAGE_CUSTOMERS = "P_MANAGE_CUSTOMERS";
    public static final String MANAGE_CATALOG = "P_MANAGE_CATALOG";
    public static final String VIEW_PRICES = "P_VIEW_PRICES";

    private Permissions() {
    }

    public static List<GrantedAuthority> of(AppUser u) {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_USER"));
        boolean adm = u.administrator();
        if (adm) {
            list.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        add(list, adm || u.canCreateOffers(), CREATE_OFFERS);
        add(list, adm || u.canEditOffers(), EDIT_OFFERS);
        add(list, adm || u.canViewAllOffers(), VIEW_ALL_OFFERS);
        add(list, adm || u.canDeleteOffers(), DELETE_OFFERS);
        add(list, adm || u.canExportDocx(), EXPORT_DOCX);
        add(list, adm || u.canManageCustomers(), MANAGE_CUSTOMERS);
        add(list, adm || u.canManageCatalog(), MANAGE_CATALOG);
        add(list, adm || u.canViewPrices(), VIEW_PRICES);
        return list;
    }

    private static void add(List<GrantedAuthority> list, boolean cond, String name) {
        if (cond) {
            list.add(new SimpleGrantedAuthority(name));
        }
    }
}
