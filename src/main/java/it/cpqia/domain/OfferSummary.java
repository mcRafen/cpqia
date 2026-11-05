package it.cpqia.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OfferSummary(
        Long id, String number, LocalDate offerDate, String companyName, String customerCode, String projectTitle,
        String status, BigDecimal totalLicense, BigDecimal totalMaintenance, BigDecimal discountPct,
        String ownerName, Long createdBy, LocalDateTime updatedAt) {

    /** margine commerciale = netto licenze + netto manutenzione (dopo lo sconto) */
    public BigDecimal commercialMargin() {
        BigDecimal factor = BigDecimal.ONE.subtract((discountPct == null ? BigDecimal.ZERO : discountPct).divide(new BigDecimal("100")));
        BigDecimal lic = totalLicense == null ? BigDecimal.ZERO : totalLicense;
        BigDecimal man = totalMaintenance == null ? BigDecimal.ZERO : totalMaintenance;
        return lic.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP).add(man.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
