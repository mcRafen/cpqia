package it.cpqia.web;

import it.cpqia.domain.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form anagrafica cliente/prospect. Il codice cliente e' assegnato dal sistema e non e' modificabile. */
public class CustomerForm {

    private Long id;
    @NotBlank(message = "Selezionare il tipo anagrafica")
    @Pattern(regexp = "CLIENTE|PROSPECT", message = "Tipo non valido")
    private String type = "PROSPECT";
    @NotBlank(message = "Ragione sociale obbligatoria")
    @Size(max = 200)
    private String companyName;
    @NotBlank(message = "Via obbligatoria")
    @Size(max = 30)
    private String streetType = "Via";
    @NotBlank(message = "Indirizzo obbligatorio")
    @Size(max = 200)
    private String streetName;
    @NotBlank(message = "Numero civico obbligatorio")
    @Size(max = 20)
    private String streetNumber;
    @NotBlank(message = "CAP obbligatorio")
    @Size(max = 10)
    private String postalCode;
    @NotBlank(message = "Citta' obbligatoria")
    @Size(max = 100)
    private String city;
    @Size(max = 5)
    private String province;
    @NotBlank(message = "Partita IVA obbligatoria")
    @Size(max = 20)
    private String vatNumber;
    @Size(max = 20)
    private String taxCode;
    @Size(max = 20)
    private String agentCode;          // prospect: scelto dall'elenco agenti; cliente: arriva da IBM Power System e non si modifica

    public static CustomerForm from(Customer c) {
        CustomerForm f = new CustomerForm();
        f.id = c.id();
        f.type = c.type();
        f.companyName = c.companyName();
        f.streetType = c.streetType();
        f.streetName = c.streetName();
        f.streetNumber = c.streetNumber();
        f.postalCode = c.postalCode();
        f.city = c.city();
        f.province = c.province();
        f.vatNumber = c.vatNumber();
        f.taxCode = c.taxCode();
        f.agentCode = c.agentCode();
        return f;
    }

    public Customer toCustomer() {
        return new Customer(id, null, type, companyName.trim(), streetType.trim(), streetName.trim(), streetNumber.trim(),
                postalCode.trim(), city.trim(), province, vatNumber.trim().replaceAll("\\s", ""), taxCode, null, null, agentCode == null || agentCode.isBlank() ? null : agentCode.trim());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getStreetType() { return streetType; }
    public void setStreetType(String streetType) { this.streetType = streetType; }
    public String getStreetName() { return streetName; }
    public void setStreetName(String streetName) { this.streetName = streetName; }
    public String getStreetNumber() { return streetNumber; }
    public void setStreetNumber(String streetNumber) { this.streetNumber = streetNumber; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
}
