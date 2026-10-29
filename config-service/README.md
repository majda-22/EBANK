# Config service

Git-backed Spring Cloud Config Server using Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, Eureka Discovery Client, Actuator, and Lombok.

Start discovery-service first. With Java 21 and Maven 3.6.3 or later installed, run from this directory:

```bash
mvn -q clean verify
java -jar target/config-service-0.0.1-SNAPSHOT.jar
```

The server listens on port 8888 and registers as `config-service` with `http://localhost:8761/eureka`. It bootstraps from local properties so that it can start before its configuration clients.

`config.repo.path` defaults to `${user.dir}/../config-repo`, an absolute path to the sibling repository when launched from this directory. The Git URI is `file://${config.repo.path}` and the default label is `main`, without a fallback to `master`. The [Spring Cloud Git backend documentation](https://docs.spring.io/spring-cloud-config/reference/4.3/server/environment-repository/git-backend.html) describes local repositories and labels.

To start from another directory or use another repository, provide its absolute path:

```bash
CONFIG_REPO_PATH=/absolute/path/to/ebank-project/config-repo \
  java -jar /absolute/path/to/ebank-project/config-service/target/config-service-0.0.1-SNAPSHOT.jar
```

Alternatively pass `--config.repo.path=/absolute/path/to/config-repo`. The repository must have a committed `main` branch. Commit configuration changes in that independent repository.

Check <http://localhost:8888/customer-service/default> for JSON containing the common properties from `application.properties`, including:

```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
management.endpoints.web.exposure.include=*
```

The empty service-specific files allow future overrides. Explicit branch requests such as `/customer-service/default/main` are supported. Health is available at <http://localhost:8888/actuator/health>.

The HTTP integration test creates an isolated Git repository on `main`, disables Eureka only for the test, and verifies both the default and explicit branch responses, common properties, and commit version.
