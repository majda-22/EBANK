package com.ebank.banking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ebank-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "resilience4j.circuitbreaker.instances.customer-service.minimum-number-of-calls=100"
})
class BankAccountIntegrationTests {
    private static HttpServer customerServer;
    private static final AtomicBoolean available = new AtomicBoolean(true);
    private static final String CUSTOMER_ONE = """
            {"id":1,"firstName":"Amine","lastName":"El Mansouri","email":"amine@example.com"}
            """;
    private static final String CUSTOMER_TWO = """
            {"id":2,"firstName":"Salma","lastName":"Bennani","email":"salma@example.com"}
            """;

    private final TestRestTemplate rest;

    @Autowired
    BankAccountIntegrationTests(TestRestTemplate rest) {
        this.rest = rest;
    }

    @DynamicPropertySource
    static void startCustomerServer(DynamicPropertyRegistry registry) throws IOException {
        customerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        customerServer.createContext("/api/customers", exchange -> {
            var path = exchange.getRequestURI().getPath();
            int status = 200;
            String body;
            if (!available.get()) {
                status = 503;
                body = "{\"message\":\"Unavailable\"}";
            } else if (path.equals("/api/customers")) {
                body = "[" + CUSTOMER_ONE + "," + CUSTOMER_TWO + "]";
            } else if (path.equals("/api/customers/1")) {
                body = CUSTOMER_ONE;
            } else if (path.equals("/api/customers/2")) {
                body = CUSTOMER_TWO;
            } else {
                status = 404;
                body = "{\"message\":\"Customer not found\"}";
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        customerServer.start();
        registry.add("spring.cloud.openfeign.client.config.CUSTOMER-SERVICE.url",
                () -> "http://127.0.0.1:" + customerServer.getAddress().getPort());
    }

    @AfterEach
    void restoreCustomerAvailability() {
        available.set(true);
    }

    @AfterAll
    static void stopCustomerServer() {
        customerServer.stop(0);
    }

    @Test
    void seedsTwoTypesPerCustomerAndEnrichesAllReadEndpointsThroughFeign() {
        var accounts = rest.getForEntity("/api/accounts", JsonNode.class);
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.getBody().size()).isGreaterThanOrEqualTo(4);
        for (long customerId : new long[]{1, 2}) {
            var filtered = rest.getForObject("/api/accounts/customer/" + customerId, JsonNode.class);
            assertThat(filtered.size()).isGreaterThanOrEqualTo(2);
            var types = new java.util.HashSet<String>();
            filtered.forEach(account -> {
                assertThat(account.path("customerId").asLong()).isEqualTo(customerId);
                assertThat(account.path("customer").path("id").asLong()).isEqualTo(customerId);
                assertThat(account.path("customer").path("email").asText()).contains("@example.com");
                types.add(account.path("type").asText());
                var id = account.path("id").asText();
                assertThat(UUID.fromString(id).toString()).isEqualTo(id);
                assertThat(Instant.parse(account.path("createdAt").asText())).isBeforeOrEqualTo(Instant.now());
                var detail = rest.getForObject("/api/accounts/" + id, JsonNode.class);
                assertThat(detail.path("customer")).isEqualTo(account.path("customer"));
            });
            assertThat(types).contains("CURRENT_ACCOUNT", "SAVING_ACCOUNT");
        }
        assertThat(rest.getForObject("/api/accounts/customer/999", JsonNode.class)).isEmpty();
    }

    @Test
    void createsForExistingCustomerAndRejectsMissingCustomerWithoutSaving() {
        var created = rest.postForEntity("/api/accounts", Map.of(
                "balance", 125.50, "type", "CURRENT_ACCOUNT", "customerId", 1), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("customer").path("firstName").asText()).isEqualTo("Amine");
        assertThat(created.getBody().path("balance").asDouble()).isEqualTo(125.50);
        assertThat(created.getHeaders().getLocation().getPath())
                .isEqualTo("/api/accounts/" + created.getBody().path("id").asText());
        assertThat(rest.getForObject(created.getHeaders().getLocation(), JsonNode.class).path("customer").path("id").asLong())
                .isEqualTo(1L);

        var before = rest.getForObject("/api/accounts", JsonNode.class).size();
        var missing = rest.postForEntity("/api/accounts", Map.of(
                "balance", 100, "type", "SAVING_ACCOUNT", "customerId", 999), JsonNode.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().path("message").asText()).contains("Customer with id 999");
        assertThat(rest.getForObject("/api/accounts", JsonNode.class)).hasSize(before);
    }

    @Test
    void returnsCleanErrorsForMissingAccountInvalidRequestAndCustomerOutage() {
        var missing = rest.getForEntity("/api/accounts/" + UUID.randomUUID(), JsonNode.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().path("status").asInt()).isEqualTo(404);
        var invalid = rest.postForEntity("/api/accounts", Map.of(
                "balance", -1, "type", "CURRENT_ACCOUNT", "customerId", 1), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().path("fieldErrors").has("balance")).isTrue();
        assertThat(rest.postForEntity("/api/accounts", Map.of(
                "balance", 1, "type", "UNKNOWN", "customerId", 1), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        available.set(false);
        var outage = rest.getForEntity("/api/accounts", JsonNode.class);
        assertThat(outage.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(outage.getBody().path("message").asText()).contains("temporarily unavailable");
        assertThat(outage.getBody().has("trace")).isFalse();
    }

    @Test
    void servesSwaggerAndOpenApi() {
        var ui = rest.getForEntity("/swagger-ui.html", String.class);
        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).contains("Swagger UI");
        var paths = rest.getForObject("/v3/api-docs", JsonNode.class).path("paths");
        assertThat(paths.path("/api/accounts").has("post")).isTrue();
        assertThat(paths.path("/api/accounts/{id}").has("get")).isTrue();
        assertThat(paths.path("/api/accounts/customer/{customerId}").has("get")).isTrue();
    }
}
