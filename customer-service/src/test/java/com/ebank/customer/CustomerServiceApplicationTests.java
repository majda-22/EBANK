package com.ebank.customer;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:customer-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
@TestPropertySource(locations = "file:../config-repo/customer-service.properties")
class CustomerServiceApplicationTests {

    private final TestRestTemplate rest;

    @Autowired
    CustomerServiceApplicationTests(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    void exposesCustomerToolsOverStreamableHttpWithValidationAndClearErrors() throws Exception {
        var transport = HttpClientStreamableHttpTransport.builder(rest.getRootUri()).endpoint("/mcp").build();
        try (var client = McpClient.sync(transport).build()) {
            assertThat(client.initialize().serverInfo().name()).isEqualTo("customer-service");
            var tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("getAllCustomers", "getCustomerById", "createCustomer");
            tools.forEach(tool -> assertThat(tool.description()).isNotBlank());
            assertThat(readTool(client, "getAllCustomers", Map.of()).isArray()).isTrue();
            var created = readTool(client, "createCustomer", Map.of("firstName", "Mcp",
                    "lastName", "Test", "email", "mcp.customer@example.com"));
            var id = created.path("id").asLong();
            assertThat(id).isPositive();
            try {
                var found = readTool(client, "getCustomerById", Map.of("customerId", id));
                assertThat(found.path("email").asText()).isEqualTo("mcp.customer@example.com");
            } finally {
                rest.delete("/api/customers/" + id);
            }
            var missing = client.callTool(new McpSchema.CallToolRequest("getCustomerById", Map.of("customerId", id)));
            assertThat(missing.isError()).isTrue();
            assertThat(missing.content().toString()).contains("Customer with id " + id + " was not found");
            var invalid = client.callTool(new McpSchema.CallToolRequest("createCustomer", Map.of(
                    "firstName", "Mcp", "lastName", "Test", "email", "invalid")));
            assertThat(invalid.isError()).isTrue();
            assertThat(invalid.content().toString()).contains("Invalid customer", "email");
        }
    }

    private JsonNode readTool(McpSyncClient client, String name, Map<String, Object> arguments) throws Exception {
        var result = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertThat(result.isError()).isNotEqualTo(true);
        var text = (McpSchema.TextContent) result.content().getFirst();
        return new ObjectMapper().readTree(text.text());
    }

    @Test
    void seedsEightCustomersAndSupportsFullCrud() {
        var initial = rest.getForEntity("/api/customers", JsonNode.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody()).hasSize(8);

        var created = rest.postForEntity("/api/customers", Map.of("id", 99999,
                "firstName", "Sara", "lastName", "Fassi", "email", "sara.fassi@example.com"), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = created.getBody().path("id").asLong();
        assertThat(id).isPositive().isNotEqualTo(99999L);
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("/api/customers/" + id);
        var path = "/api/customers/" + id;
        var found = rest.getForEntity(path, JsonNode.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().path("email").asText()).isEqualTo("sara.fassi@example.com");

        var updated = rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(Map.of(
                "id", 55555, "firstName", "Sarah", "lastName", "Fassi", "email", "sarah.fassi@example.com")), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().path("id").asLong()).isEqualTo(id);
        assertThat(updated.getBody().path("firstName").asText()).isEqualTo("Sarah");
        assertThat(rest.getForObject(path, JsonNode.class).path("email").asText())
                .isEqualTo("sarah.fassi@example.com");

        assertThat(rest.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        var missing = rest.getForEntity(path, JsonNode.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody().path("message").asText()).isEqualTo("Customer with id " + id + " was not found");
        assertThat(missing.getBody().path("path").asText()).isEqualTo(path);
        assertThat(rest.getForObject("/api/customers", JsonNode.class)).hasSize(8);
    }

    @Test
    void returnsCleanNotFoundForMissingReadUpdateAndDelete() {
        for (var method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
            var response = rest.exchange("/api/customers/999999", method,
                    new HttpEntity<>(Map.of("firstName", "Sara", "lastName", "Fassi", "email", "sara@example.com")), JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().path("status").asInt()).isEqualTo(404);
            assertThat(response.getBody().path("error").asText()).isEqualTo("Not Found");
            assertThat(response.getBody().has("trace")).isFalse();
        }
    }

    @Test
    void rejectsInvalidFieldsMalformedJsonAndInvalidIds() {
        var response = rest.postForEntity("/api/customers", Map.of(
                "firstName", " ", "lastName", "Fassi", "email", "invalid-email"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("fieldErrors").has("email")).isTrue();
        assertThat(response.getBody().path("fieldErrors").has("firstName")).isTrue();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var malformed = rest.postForEntity("/api/customers", new HttpEntity<>("{broken", headers), JsonNode.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody().path("message").asText()).contains("valid customer JSON");
        assertThat(rest.getForEntity("/api/customers/not-a-number", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void servesSwaggerUiAndOpenApiForAllCrudOperations() {
        var ui = rest.getForEntity("/swagger-ui.html", String.class);
        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).contains("Swagger UI");
        var api = rest.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.OK);
        var paths = api.getBody().path("paths");
        assertThat(paths.path("/api/customers").has("get")).isTrue();
        assertThat(paths.path("/api/customers").has("post")).isTrue();
        for (var method : new String[]{"get", "put", "delete"}) {
            assertThat(paths.path("/api/customers/{id}").has(method)).isTrue();
        }
    }
}
