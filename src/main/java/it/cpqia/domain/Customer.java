package it.cpqia.domain;

import java.time.LocalDateTime;

public record Customer(
        Long id, String code, String type, String companyName, String streetType, String streetName,
        String streetNumber, String postalCode, String city, String province, String vatNumber, String taxCode,
        LocalDateTime createdAt, Long createdBy, String agentCode) {

    /** "Via dell'Elettronica, 9" */
    public String addressLine1() {
        return streetType + " " + streetName + ", " + streetNumber;
    }

    /** "36061 Thiene (VI)" */
    public String addressLine2() {
        String p = province == null || province.isBlank() ? "" : " (" + province.toUpperCase() + ")";
        return postalCode + " " + city + p;
    }
}
