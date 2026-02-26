package org.cardanofoundation.reeve_ipfs_publisher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reeve")
@Getter
@Setter
public class ReeveProperties {
    private Long metadataLabel;
    private Organisation organisation = new Organisation();

    // Nested Class: reeve.organisation
    public static class Organisation {
        private String id;
        private String name;
        private String currencyId;  // Maps from currency_id
        private String countryCode; // Maps from country_code
        private String taxIdNumber; // Maps from tax_id_number
        // Getters & Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCurrencyId() { return currencyId; }
        public void setCurrencyId(String currencyId) { this.currencyId = currencyId; }
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getTaxIdNumber() { return taxIdNumber; }
        public void setTaxIdNumber(String taxIdNumber) { this.taxIdNumber = taxIdNumber; }
    }
}