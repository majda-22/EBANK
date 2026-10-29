package com.ebank.banking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ebank-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
@TestPropertySource(locations = "file:../config-repo/ebank-service.properties")
class BankAccountIntegrationTests {
    private static final String FIND_CIRCUIT = "CustomerRestClientfindCustomerByIdLong";
    private static HttpServer customerServer;
    private static final AtomicBoolean available = new AtomicBoolean(true);
    private static final String CUSTOMER_ONE = """
            {"id":1,"firstName":"Amine","lastName":"El Mansouri","email":"amine@example.com"}
            """;
    private static final String CUSTOMER_TWO = """
            {"id":2,"firstName":"Salma","lastName":"Bennani","email":"salma@example.com"}
            """;

    private final TestRestTemplate rest;
    private final CircuitBreakerRegistry circuitBreakers;

    @Autowired
    BankAccountIntegrationTests(TestRestTemplate rest, CircuitBreakerRegistry circuitBreakers) {
        this.rest = rest;
        this.circuitBreakers = circuitBreakers;
    }

    @BeforeEach
    void resetCircuits() {
        available.set(true);
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
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
        assertThat(outage.getStatusCode()).isEqualTo(HttpStatus.OK);
        outage.getBody().forEach(account -> {
            assertThat(account.path("customer").path("firstName").asText()).isEqualTo("Not available");
            assertThat(account.path("customer").path("id").asLong()).isEqualTo(account.path("customerId").asLong());
        });
    }

    @Test
    void feignFallbackOpensCircuitThenRecoversThroughHalfOpenAndExposesEvents() {
        var accounts = rest.getForObject("/api/accounts", JsonNode.class);
        var id = accounts.get(0).path("id").asText();
        var customerId = accounts.get(0).path("customerId").asLong();
        var breaker = circuitBreakers.circuitBreaker(FIND_CIRCUIT);
        breaker.reset();
        var config = breaker.getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(5);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        var transitions = new CopyOnWriteArrayList<CircuitBreaker.StateTransition>();
        breaker.getEventPublisher().onStateTransition(event -> transitions.add(event.getStateTransition()));

        available.set(false);
        for (int call = 0; call < 5; call++) {
            var response = rest.getForEntity("/api/accounts/" + id, JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var customer = response.getBody().path("customer");
            assertThat(customer.path("id").asLong()).isEqualTo(customerId);
            for (var field : new String[]{"firstName", "lastName", "email"}) {
                assertThat(customer.path(field).asText()).isEqualTo("Not available");
            }
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(rest.getForObject("/api/accounts/" + id, JsonNode.class)
                .path("customer").path("firstName").asText()).isEqualTo("Not available");
        await().atMost(Duration.ofSeconds(13)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN));

        available.set(true);
        var firstProbe = rest.getForObject("/api/accounts/" + id, JsonNode.class);
        assertThat(firstProbe.path("customer").path("firstName").asText()).isNotEqualTo("Not available");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        rest.getForObject("/api/accounts/" + id, JsonNode.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(transitions).containsSubsequence(CircuitBreaker.StateTransition.CLOSED_TO_OPEN,
                CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN, CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED);

        var names = rest.getForEntity("/actuator/circuitbreakers", JsonNode.class);
        assertThat(names.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names.getBody().toString()).contains(FIND_CIRCUIT, "CustomerRestClientallCustomers");
        var events = rest.getForEntity("/actuator/circuitbreakerevents", JsonNode.class);
        assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events.getBody().toString()).contains(FIND_CIRCUIT, "STATE_TRANSITION");
        assertThat(circuitBreakers.getAllCircuitBreakers()).noneMatch(cb -> cb.getName().equals("customer-service"));
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
