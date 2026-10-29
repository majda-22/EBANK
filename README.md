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
│   └── application.properties
├── discovery-service/
├── config-service/
├── customer-service/
├── ebank-service/
├── chatbot-service/
├── gateway-service/
└── docs/
```

Empty module and documentation directories contain `.gitkeep` placeholders so Git preserves the structure.

`config-repo` is a separate Git repository, excluded from the parent repository and not registered as a submodule. Its history must be cloned or provisioned separately when setting up another workspace. Its initial `application.properties` is intentionally empty.

## Versions

- Java: **21**.
- Spring Boot, Spring Cloud, and Spring AI: **to be selected together after checking their official compatibility documentation**, before adding Maven dependencies. All backend modules will share one compatible Spring Boot version.
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

Startup commands will be added as each application is implemented.

## Development and validation

Backend services will use entity, repository, DTO, mapper, service, and REST controller layers, Lombok, and constructor injection.

Once Maven projects and runnable applications exist, validate each task with `mvn -q clean verify`, start the affected application, and report the runtime checks. This scaffold contains no POM files or executable application yet.
