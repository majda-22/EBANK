package com.ebank.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class ConfigServiceApplicationTests {

    private static Path repository;
    private static String commitId;

    private final TestRestTemplate restTemplate;

    @Autowired
    ConfigServiceApplicationTests(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @DynamicPropertySource
    static void configureRepository(DynamicPropertyRegistry registry) throws IOException, GitAPIException {
        repository = Files.createTempDirectory("ebank-config-test-");
        Files.writeString(repository.resolve("application.properties"), """
                eureka.client.service-url.defaultZone=http://localhost:8761/eureka
                management.endpoints.web.exposure.include=*
                """);
        Files.createFile(repository.resolve("customer-service.properties"));
        try (var git = Git.init().setDirectory(repository.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            commitId = git.commit().setMessage("Test configuration")
                    .setAuthor("Config Test", "config-test@example.invalid")
                    .setCommitter("Config Test", "config-test@example.invalid")
                    .call().getName();
        }
        registry.add("spring.cloud.config.server.git.uri", () -> repository.toUri().toString());
    }

    @AfterAll
    static void deleteRepository() throws IOException {
        if (repository != null) {
            FileSystemUtils.deleteRecursively(repository);
        }
    }

    @Test
    void servesCommittedCommonPropertiesFromMainWithEmptyServiceConfiguration() {
        for (var endpoint : new String[]{"/customer-service/default", "/customer-service/default/main"}) {
            var response = restTemplate.getForEntity(endpoint, JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.path("name").asText()).isEqualTo("customer-service");
            assertThat(body.path("profiles").get(0).asText()).isEqualTo("default");
            assertThat(body.path("version").asText()).isEqualTo(commitId);
            assertThat(body.findValue("eureka.client.service-url.defaultZone").asText())
                    .isEqualTo("http://localhost:8761/eureka");
            assertThat(body.findValue("management.endpoints.web.exposure.include").asText())
                    .isEqualTo("*");
        }
    }
}
