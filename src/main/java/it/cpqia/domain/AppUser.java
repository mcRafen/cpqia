package it.cpqia.domain;

import java.time.LocalDateTime;

/** Utente applicativo con i flag di abilitazione alle funzioni. */
public record AppUser(
        Long id, String email, String firstName, String lastName, String fullName, String initials,
        String pictureUrl, String authProvider, String providerSubject, boolean active,
        boolean administrator, boolean canCreateOffers, boolean canEditOffers, boolean canViewAllOffers,
        boolean canDeleteOffers, boolean canExportDocx, boolean canManageCustomers, boolean canManageCatalog,
        boolean canViewPrices, LocalDateTime createdAt, LocalDateTime lastLoginAt, String agentCode) {

    /** utente associato a un agente: vede solo i propri prospect e i clienti con lo stesso codice agente */
    public boolean agentRestricted() {
        return agentCode != null && !agentCode.isBlank();
    }


    public String displayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return email;
    }
}
