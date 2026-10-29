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

`config-repo` is a separate Git repository, excluded from the parent repository and not registered as a submodule. Its history must be cloned or provisioned separately when setting up another workspace. Its `application.properties` contains the common Eureka URL and Actuator endpoint exposure. Customer and banking properties supply their ports, separate in-memory H2 databases, H2 consoles, and JPA settings. Banking properties also configure Feign and Resilience4j. Gateway properties supply port 8080, explicit API routes, and Angular CORS. The chatbot property file remains an empty placeholder.

## Versions

- Java: **21**.
- Spring Boot: **3.5.16**; shared baseline for all backend modules.
- Spring Cloud: **2025.0.3**. The [official compatibility table](https://spring.io/projects/spring-cloud/) maps 2025.0.x to Boot 3.5.x.
- Gateway: **4.3.5**, managed by the Cloud BOM. The reactive starter uses `spring.cloud.gateway.server.webflux.*` properties, as documented in the [Cloud release notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes).
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

## Run the stack

Prerequisites: JDK 21, Maven 3.6.3 or later, and the separate local `config-repo` Git repository on branch `main`. Run commands from `ebank-project/` unless another directory is specified. There is no root aggregator POM.

Build all implemented modules first:

```bash
for module in discovery-service config-service customer-service ebank-service gateway-service; do
  (cd "$module" && mvn -q clean verify) || exit 1
done
```

Start each application in a separate terminal, in this order. Change into its module directory before executing the command:

| Order | Directory | Command | Ready check |
| --- | --- | --- | --- |
| 1 | `discovery-service/` | `java -jar target/discovery-service-0.0.1-SNAPSHOT.jar` | <http://localhost:8761/actuator/health> |
| 2 | `config-service/` | `java -jar target/config-service-0.0.1-SNAPSHOT.jar` | <http://localhost:8888/customer-service/default> |
| 3 | `customer-service/` | `java -jar target/customer-service-0.0.1-SNAPSHOT.jar` | <http://localhost:8081/api/customers> |
| 4 | `ebank-service/` | `java -jar target/ebank-service-0.0.1-SNAPSHOT.jar` | <http://localhost:8082/api/accounts> |
| 5 (planned) | `chatbot-service/` | Add its startup command when implemented | Not implemented yet |
| 6 | `gateway-service/` | `java -jar target/gateway-service-0.0.1-SNAPSHOT.jar` | <http://localhost:8080/api/customers> and <http://localhost:8080/api/accounts> |
| 7 (planned) | `ebank-frontend/` | Add its startup command when implemented | Not scaffolded yet |

The current stack consists of the five executable Java modules. Skip the planned chatbot and frontend; the gateway customer/account routes work without them. The chat route will return 503 until chatbot-service exists.

Wait for each ready check before starting the next application. Customer-service seeds eight customers; ebank-service retrieves them through Eureka/Feign and seeds two accounts per customer. Check <http://localhost:8761> for `CONFIG-SERVICE`, `CUSTOMER-SERVICE`, `EBANK-SERVICE`, and `GATEWAY-SERVICE`. Discovery updates may take about 30 seconds; wait for registration before running acceptance tests. If banking started while customers were unavailable, restart banking after customers are ready to seed its H2 database.

Config Server defaults to the sibling `config-repo` directory when launched from `config-service/`. Set `CONFIG_REPO_PATH` to the absolute repository path when launching elsewhere. The customer, banking, and gateway ports come from Config Server. Configuration remains in its own local Git repository; it is intentionally excluded from this monorepo. The configuration revision used for `v1-microservices` is `9b5cdbbf639544e4c1d63700b8b2d7e71c565b09`.

H2 data resets when its service restarts. Stop applications with Ctrl+C in their terminals, in reverse startup order. See the module README files for more details.

## Swagger UI

- Customer API: <http://localhost:8081/swagger-ui.html>; OpenAPI JSON: <http://localhost:8081/v3/api-docs>.
- Banking API: <http://localhost:8082/swagger-ui.html>; OpenAPI JSON: <http://localhost:8082/v3/api-docs>.

Open the appropriate UI, expand an operation, select **Try it out**, provide the ID or JSON body, and select **Execute**. Use a customer ID returned by the customer API when creating an account. Account responses include customer data.

Swagger runs directly on each service. The gateway exposes the explicit `/api/customers/**`, `/api/accounts/**`, and `/api/chat/**` route patterns; Swagger and MCP endpoints are not routed through it. Use gateway URLs for the Postman acceptance collection.

## Postman acceptance collection

Import both files into Postman:

- [E-Bank collection](docs/postman/ebank.postman_collection.json)
- [Local environment](docs/postman/ebank-local.postman_environment.json)

Select **E-Bank Local Gateway**, leave `baseUrl=http://localhost:8080` and `resilienceMode=healthy`, and run the entire collection in folder order: **Customers CRUD**, **Accounts**, then **resilience**. IDs and a unique customer email are captured automatically; no IDs need to be entered. Use the Collection Runner to see status-code and response-field assertions. The stack, including customer-service, must be healthy for this default run.

For a command-line run, install Node.js and run the pinned Newman runner from the monorepo root:

```bash
npx --yes newman@6.2.1 run docs/postman/ebank.postman_collection.json \
  -e docs/postman/ebank-local.postman_environment.json \
  --timeout-request 15000
```

Add `--iteration-count 2` to check repeatability. Newman returns a failing exit code if any assertion fails; see its [official command reference](https://learning.postman.com/docs/collections/using-newman-cli/newman-options).

Release validation on 2026-10-29: Newman 6.2.1 completed two iterations against the live gateway with **42 requests, 138 assertions, and zero failures**. Gateway `mvn -q clean verify` passed all four integration tests.

All 21 requests go through the gateway. Customer tests create, read, update, validate, and delete their own temporary customer. Account tests list/read/filter/create accounts, verify nested customer data, and check rejected inputs. Each complete iteration leaves one newly created account attached to an existing customer, since the banking API has no DELETE operation. Restart banking to reset its in-memory database. Existing customers are preserved, and the temporary customer is deleted before account tests begin.

The **resilience** folder is green by default with real customer data. For a deliberate outage, first complete a healthy run, then follow its folder instructions: stop customer-service, set `resilienceMode=outage`, run **Open circuit (five probes)**, allow 10 seconds, restart customer-service and wait for discovery, set `resilienceMode=recovery`, and run **Recovery (two probes)**. Reset the mode to `healthy` before another complete run. State/event observation uses the direct banking Actuator endpoints described in [docs/resilience-test.md](docs/resilience-test.md); every request saved in this collection still uses the gateway. Newman cannot stop and restart local Java processes automatically.

## Development and validation

Backend services will use entity, repository, DTO, mapper, service, and REST controller layers, Lombok, and constructor injection.

Validate each task with `mvn -q clean verify` in the affected Maven module, start the application, and report the runtime checks. The discovery-service test exercises the Eureka dashboard and Actuator health over HTTP. The config-service test verifies configuration delivery from an isolated Git repository. Customer-service tests cover seeded data, CRUD, validation, JSON errors, and Swagger. Banking tests cover real Feign calls against an HTTP stub, account enrichment and creation, Swagger, and unavailable-customer startup. Gateway tests cover load-balanced routing, CORS, and MCP exclusion. Chatbot remains a placeholder.
