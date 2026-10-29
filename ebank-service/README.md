# E-Bank service

Bank accounts with Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, JPA/H2, Lombok, Validation, Config Client, Eureka, OpenFeign, Resilience4j, Actuator, and springdoc 2.10.0. These versions match customer-service and the root compatibility documentation.

Start discovery-service, config-service, and customer-service first. From this directory with Java 21 and Maven installed:

```bash
mvn -q clean verify
java -jar target/ebank-service-0.0.1-SNAPSHOT.jar
```

The bootstrap file contains the application name and optional Config Server import. Port **8082**, in-memory H2, SQL logging, Feign timeouts, and Resilience4j settings come from committed `config-repo/ebank-service.properties`. Shared Eureka and Actuator settings come from `config-repo/application.properties`.

The Feign client resolves `CUSTOMER-SERVICE` through Eureka and Spring Cloud LoadBalancer; it has no hardcoded customer-service URL. Feign circuit breaker support uses Resilience4j and `CustomerRestClientFallbackFactory`, which logs the cause and returns placeholder customer details during outages. The circuits are `CustomerRestClientfindCustomerByIdLong` and `CustomerRestClientallCustomers`, with a five-call window, 50% failure threshold, ten-second open wait, and two half-open probes. See [docs/resilience-test.md](../docs/resilience-test.md) for the configuration and recovery scenario. The `Customer` model is a POJO, and the account's customer field is `@Transient`; customer details are retrieved rather than stored in the banking database.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/accounts` | Accounts enriched with customers |
| GET | `/api/accounts/{id}` | One account enriched with its customer |
| GET | `/api/accounts/customer/{customerId}` | Accounts for a customer, enriched with customer data |
| POST | `/api/accounts` | 201, generated account and Location header |

```bash
curl -i -X POST http://localhost:8082/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"balance":1500.0,"type":"CURRENT_ACCOUNT","customerId":1}'
```

`type` is `CURRENT_ACCOUNT` or `SAVING_ACCOUNT`. The initial balance is required and nonnegative, and customerId must be positive and refer to an existing customer. IDs are UUID strings and createdAt is a server-generated Instant. Balance uses `double` as requested.

All nonempty account responses include `customer` with id, firstName, lastName, and email. A list request looks up each distinct customer once. A missing account or customer returns a clean JSON 404. During customer-service outages, reads return HTTP 200 with the original customer ID and `Not available` for the three text fields. Account creation still returns 503 without saving when the customer cannot be verified. Validation and malformed JSON return 400. The customer filter returns an empty array when no matching accounts exist.

At startup, the CommandLineRunner retrieves the customer list through Feign and creates a current account with balance 1000 and a savings account with balance 2500 for each customer, skipping types already present. With eight customers this produces sixteen accounts. If customer-service is unavailable, startup continues without seeding; restart ebank-service after customer-service recovers to seed accounts. H2 resets on restart.

- Accounts: <http://localhost:8082/api/accounts>
- Swagger: <http://localhost:8082/swagger-ui.html>
- OpenAPI: <http://localhost:8082/v3/api-docs>
- Health: <http://localhost:8082/actuator/health>
- Circuits: <http://localhost:8082/actuator/circuitbreakers>
- Circuit events: <http://localhost:8082/actuator/circuitbreakerevents>
- H2: <http://localhost:8082/h2-console> (JDBC URL `jdbc:h2:mem:ebank`, user `sa`, empty password)

The integration tests use an isolated H2 database and an HTTP customer stub with real Feign serialization. They cover seeding, enrichment, UUIDs, timestamps, account creation, missing customers/accounts, validation, fallback reads, OPEN → HALF_OPEN → CLOSED recovery, Actuator events, Swagger, and startup without customer-service. Runtime validation separately checks the live Eureka and Config Server integrations.
