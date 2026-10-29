package com.ebank.banking;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.ebank.banking.repository.BankAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ebank-outage-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.cloud.openfeign.client.config.CUSTOMER-SERVICE.url=http://127.0.0.1:1",
        "spring.cloud.openfeign.client.config.CUSTOMER-SERVICE.connectTimeout=100",
        "spring.cloud.openfeign.client.config.CUSTOMER-SERVICE.readTimeout=100"
})
class CustomerUnavailableStartupTests {
    private final TestRestTemplate rest;
    private final BankAccountRepository repository;

    @Autowired
    CustomerUnavailableStartupTests(TestRestTemplate rest, BankAccountRepository repository) {
        this.rest = rest;
        this.repository = repository;
    }

    @Test
    void startsWithoutCustomersAndRejectsUnverifiableAccountCreation() {
        var accounts = rest.getForEntity("/api/accounts", JsonNode.class);
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.getBody()).isEmpty();
        var response = rest.postForEntity("/api/accounts", Map.of(
                "balance", 100, "type", "CURRENT_ACCOUNT", "customerId", 1), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(repository.count()).isZero();
    }
}
