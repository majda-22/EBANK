# Customer service

Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, JPA, H2, Lombok, Bean Validation, Actuator, and springdoc 2.10.0. The [springdoc compatibility matrix](https://springdoc.org/v2/) supports this version with Boot 3.5.x.

Start discovery-service and config-service first. From this directory with Java 21 and Maven installed:

```bash
mvn -q clean verify
java -jar target/customer-service-0.0.1-SNAPSHOT.jar
```

Local bootstrap configuration sets only the service name and `spring.config.import=optional:configserver:http://localhost:8888`. Port **8081**, H2 settings, and SQL logging come from the committed `config-repo/customer-service.properties`. The Eureka URL and common Actuator exposure come from `config-repo/application.properties`. The optional import allows startup without Config Server, but the configured port and database settings require it.

The service registers as `CUSTOMER-SERVICE`. H2 is in memory and resets on restart, with eight fictional customer records seeded when empty. No database credentials are stored in source control; Spring Boot's local H2 defaults are used.

- API: <http://localhost:8081/api/customers>
- Swagger UI: <http://localhost:8081/swagger-ui.html>
- OpenAPI: <http://localhost:8081/v3/api-docs>
- Health: <http://localhost:8081/actuator/health>
- H2 console: <http://localhost:8081/h2-console> (JDBC URL `jdbc:h2:mem:customers`, user `sa`, empty password)

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/customers` | 200, customer array |
| GET | `/api/customers/{id}` | 200, customer |
| POST | `/api/customers` | 201, customer and Location header |
| PUT | `/api/customers/{id}` | 200, updated customer |
| DELETE | `/api/customers/{id}` | 204 |

POST and PUT accept `firstName`, `lastName`, and `email`. IDs are generated and read-only; PUT uses the path ID. Names must be nonblank and at most 100 characters; email must be nonblank, valid, and at most 254 characters.

```bash
curl -i -X POST http://localhost:8081/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Sara","lastName":"Fassi","email":"sara.fassi@example.com"}'
```

Missing customers return HTTP 404 with JSON such as:

```json
{
  "timestamp": "2026-10-29T15:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Customer with id 999 was not found",
  "path": "/api/customers/999",
  "fieldErrors": {}
}
```

Validation failures return HTTP 400 with the same structure and per-field messages in `fieldErrors`. Malformed JSON and invalid ID types return clean HTTP 400 responses.

The integration suite uses an isolated H2 database without Config Server or Eureka and covers seed data, CRUD, server-generated IDs, missing records, validation, malformed input, Swagger UI, and OpenAPI operations. Runtime checks verify remote configuration and Eureka registration separately.

## MCP tools

Spring AI 1.1.8 exposes `getAllCustomers`, `getCustomerById`, and `createCustomer` at **http://localhost:8081/mcp** using Streamable HTTP. `CustomerTools` delegates to `CustomerService`, and `CustomerMcpConfiguration` registers a `MethodToolCallbackProvider` and logs the tool names. Protocol, server identity, and endpoint are supplied by Config Server. The integration suite also exercises real MCP initialization, discovery, calls, DTO validation, and missing-ID errors. See [docs/mcp.md](../docs/mcp.md) for compatibility evidence and HTTP examples.
