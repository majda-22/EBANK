# E-Bank microservices with AI agent

Monorepo scaffold for a Java 21, Maven, Spring Boot banking platform with an Angular frontend and a Spring AI agent.

## Architecture

- **discovery-service**: Eureka Server for service discovery.
- **config-service**: Spring Cloud Config Server using the local `./config-repo` Git repository as its backend.
- **customer-service**: Customer REST API and MCP server, using JPA and H2.
- **ebank-service**: Banking REST API and MCP server, using JPA and H2; calls customer-service through OpenFeign and Resilience4j.
- **chatbot-service**: Spring AI agent and MCP client, with Telegram and optional Discord integration.
- **gateway-service**: Spring Cloud Gateway, with CORS for `http://localhost:4200`.
- **ebank-frontend**: Planned Angular client; not scaffolded in this task.

MCP uses **Streamable HTTP**. Application services register with Eureka and read configuration from the Config Server.

## Ports

| Component | Port |
| --- | --- |
| discovery-service | 8761 |
| config-service | 8888 |
| customer-service | 8081 |
| ebank-service | 8082 |
| chatbot-service | 8090 |
| gateway-service | 8080 |
| ebank-frontend (planned) | 4200 |

## Repository layout

```text
ebank-project/
├── .env.example
├── .gitignore
├── README.md
├── config-repo/             # Independent Git repository, branch main
│   ├── application.properties
│   ├── customer-service.properties
│   ├── ebank-service.properties
│   ├── chatbot-service.properties
│   └── gateway-service.properties
├── discovery-service/
├── config-service/
├── customer-service/
├── ebank-service/
├── chatbot-service/
├── gateway-service/
└── docs/
```

Empty module and documentation directories contain `.gitkeep` placeholders so Git preserves the structure.

`config-repo` is a separate Git repository, excluded from the parent repository and not registered as a submodule. Its history must be cloned or provisioned separately when setting up another workspace. Its `application.properties` contains the common Eureka URL and Actuator endpoint exposure. Customer and banking properties supply their ports, separate in-memory H2 databases, H2 consoles, and JPA settings. Banking properties also configure Feign and Resilience4j. Chatbot and gateway property files remain empty placeholders.

## Versions

- Java: **21**.
- Spring Boot: **3.5.16**; shared baseline for all backend modules.
- Spring Cloud: **2025.0.3**. The [official compatibility table](https://spring.io/projects/spring-cloud/) maps 2025.0.x to Boot 3.5.x.
- Spring AI: **1.1.8**, reserved for the chatbot module. The [official Spring AI compatibility guide](https://github.com/spring-projects/spring-ai#readme) maps AI 1.1.x to Boot 3.5.x.
- springdoc: **2.10.0** for customer-service and ebank-service. The [official compatibility matrix](https://springdoc.org/v2/) supports it with Boot 3.5.x.
- Angular: to be selected when the frontend is implemented.

## Configuration and secrets

Copy `.env.example` to `.env` for local reference and provide these environment variables to application processes:

```text
OPENAI_API_KEY=
TELEGRAM_BOT_TOKEN=
DISCORD_BOT_TOKEN=
```

Spring Boot does not automatically load `.env` files; export the variables in your shell or configure your IDE to supply them. Never commit real tokens or API keys, including in the independent configuration repository.

## Startup order

1. discovery-service
2. config-service
3. customer-service
4. ebank-service
5. chatbot-service
6. gateway-service
7. ebank-frontend

Start discovery first with JDK 21 and Maven installed:

```bash
cd discovery-service
mvn -q clean verify
java -jar target/discovery-service-0.0.1-SNAPSHOT.jar
```

Open <http://localhost:8761> for the Eureka dashboard or <http://localhost:8761/actuator/health> for health. Discovery starts independently of the Config Server.

Next, in a separate terminal, run from `config-service/`:

```bash
mvn -q clean verify
java -jar target/config-service-0.0.1-SNAPSHOT.jar
```

The default Git backend uses the sibling `config-repo` on branch `main`. Set `CONFIG_REPO_PATH` to its absolute path when launching from another directory. Check <http://localhost:8888/customer-service/default> for common configuration and the Eureka dashboard for `CONFIG-SERVICE` registration. See [config-service/README.md](config-service/README.md) for details. Startup commands for other services will be added as they are implemented.

Then, in another terminal, run from `customer-service/`:

```bash
mvn -q clean verify
java -jar target/customer-service-0.0.1-SNAPSHOT.jar
```

Customer-service gets port 8081 from the Config Server and registers as `CUSTOMER-SERVICE` in Eureka. Open <http://localhost:8081/swagger-ui.html> for Swagger or <http://localhost:8081/api/customers> for the eight seeded customers. See [customer-service/README.md](customer-service/README.md) for API and database details.

Next, in another terminal, run from `ebank-service/`:

```bash
mvn -q clean verify
java -jar target/ebank-service-0.0.1-SNAPSHOT.jar
```

Ebank-service gets port 8082 from the Config Server and registers as `EBANK-SERVICE`. It discovers customers through Feign and seeds two accounts per existing customer. Open <http://localhost:8082/swagger-ui.html> or <http://localhost:8082/api/accounts>; account responses include customer details. Startup continues without seed data if customer-service is unavailable. See [ebank-service/README.md](ebank-service/README.md).

## Development and validation

Backend services will use entity, repository, DTO, mapper, service, and REST controller layers, Lombok, and constructor injection.

Validate each task with `mvn -q clean verify` in the affected Maven module, start the application, and report the runtime checks. The discovery-service test exercises the Eureka dashboard and Actuator health over HTTP. The config-service test verifies configuration delivery from an isolated Git repository. Customer-service tests cover seeded data, CRUD, validation, JSON errors, and Swagger. Banking tests cover real Feign calls against an HTTP stub, account enrichment and creation, Swagger, and unavailable-customer startup. Chatbot and gateway remain placeholders.
