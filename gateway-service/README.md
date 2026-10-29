# Gateway service

Reactive API gateway using Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, Eureka Discovery Client, Config Client, LoadBalancer, and Actuator. The Cloud BOM resolves Gateway 4.3.5.

This module uses `spring-cloud-starter-gateway-server-webflux` with Reactor Netty. Routes and CORS use **`spring.cloud.gateway.server.webflux.*`**, the replacement for the deprecated `spring.cloud.gateway.*` prefix in the [Spring Cloud 2025.0 release notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes). The resolved Gateway configuration metadata confirms this namespace.

Start discovery, config, customer, ebank, and (when implemented) chatbot before the gateway. From this directory with Java 21 and Maven installed:

```bash
mvn -q clean verify
java -jar target/gateway-service-0.0.1-SNAPSHOT.jar
```

The local bootstrap properties set `spring.application.name=gateway-service` and import `optional:configserver:http://localhost:8888`. Port **8080**, explicit routes, and global CORS are supplied by the committed `config-repo/gateway-service.properties`. The Eureka URL and Actuator exposure are inherited from common configuration.

| Incoming path | Target |
| --- | --- |
| `/api/customers/**` | `lb://CUSTOMER-SERVICE` |
| `/api/accounts/**` | `lb://EBANK-SERVICE` |
| `/api/chat/**` | `lb://CHATBOT-SERVICE` |

The root collection paths, such as `/api/customers`, also match. Paths are forwarded unchanged. Eureka and Spring Cloud LoadBalancer select the target instance. Without a running chatbot, chat requests return 503; customer and account routes remain usable.

Global CORS allows exactly `http://localhost:4200`, methods GET/POST/PUT/DELETE/OPTIONS, and all request headers. Preflight support is enabled on the simple URL handler mapping too. Other origins and CORS methods are rejected.

There is no `/mcp` route. The discovery locator is explicitly disabled, so it cannot create service-name routes that expose backend MCP endpoints. Requests to `/mcp`, `/mcp/**`, and service-name MCP paths return 404.

Runtime checks:

```bash
curl -fsS http://localhost:8080/api/customers
curl -fsS http://localhost:8080/api/accounts
curl -i -X OPTIONS http://localhost:8080/api/customers \
  -H 'Origin: http://localhost:4200' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: Content-Type,Authorization'
curl -i http://localhost:8080/mcp
```

Health is available at <http://localhost:8080/actuator/health>. Eureka lists the service as `GATEWAY-SERVICE`.

The integration suite loads the real gateway properties and uses a simple discovery client plus HTTP stubs to exercise all three `lb://` routes, unchanged paths, CORS preflights for each allowed method and arbitrary headers, rejected origins/methods, and MCP exclusion. Live acceptance checks separately verify Config Server settings and Eureka-backed routing to customer-service and ebank-service.
