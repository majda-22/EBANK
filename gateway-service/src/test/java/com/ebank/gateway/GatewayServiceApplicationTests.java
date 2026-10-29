package com.ebank.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"
})
@TestPropertySource(locations = "file:../config-repo/gateway-service.properties")
class GatewayServiceApplicationTests {
    private static HttpServer upstream;
    private static final AtomicInteger upstreamCalls = new AtomicInteger();

    private final WebTestClient client;

    @Autowired
    GatewayServiceApplicationTests(@LocalServerPort int port) {
        this.client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void configureDiscoveredServices(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            upstreamCalls.incrementAndGet();
            var body = ("{\"path\":\"" + exchange.getRequestURI().getPath()
                    + "\",\"method\":\"" + exchange.getRequestMethod() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        upstream.start();
        for (var service : new String[]{"CUSTOMER-SERVICE", "EBANK-SERVICE", "CHATBOT-SERVICE"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        }
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void routesAllThreeApisThroughLoadBalancerAndPreservesPaths() {
        for (var path : new String[]{"/api/customers", "/api/customers/1", "/api/accounts",
                "/api/accounts/customer/1", "/api/chat", "/api/chat/messages"}) {
            client.get().uri(path).exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo(path)
                    .jsonPath("$.method").isEqualTo("GET");
        }
        client.post().uri("/api/chat/messages").bodyValue("hello").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.method").isEqualTo("POST");
    }

    @Test
    void allowsAngularOriginAllRequestedMethodsAndArbitraryHeaders() {
        for (var method : new String[]{"GET", "POST", "PUT", "DELETE", "OPTIONS"}) {
            client.options().uri("/api/customers")
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-Request-Id,Authorization")
                    .exchange().expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
                    .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            value -> assertThat(value).contains(method))
                    .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            value -> assertThat(value.toLowerCase()).contains("content-type", "x-request-id", "authorization"));
        }
        client.get().uri("/api/customers").header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");
    }

    @Test
    void rejectsOtherOriginsAndMethods() {
        client.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
        client.options().uri("/api/accounts")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void neverRoutesMcpOrDiscoveryGeneratedServicePaths() {
        var before = upstreamCalls.get();
        for (var path : new String[]{"/mcp", "/mcp/tools", "/CUSTOMER-SERVICE/mcp", "/customer-service/mcp"}) {
            for (var method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST}) {
                client.method(method).uri(path).exchange().expectStatus().isNotFound();
            }
        }
        assertThat(upstreamCalls.get()).isEqualTo(before);
    }
}
