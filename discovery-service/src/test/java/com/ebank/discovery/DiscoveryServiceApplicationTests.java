package com.ebank.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceApplicationTests {

    private final TestRestTemplate restTemplate;

    @Autowired
    DiscoveryServiceApplicationTests(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Test
    void servesEurekaDashboardAndHealthyActuator() {
        var dashboard = restTemplate.getForEntity("/", String.class);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getBody()).contains("Eureka", "Instances currently registered with Eureka");

        var health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }
}
