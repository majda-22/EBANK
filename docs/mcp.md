# Customer and banking MCP servers

Both services use the Spring AI **1.1.8** BOM with Spring Boot **3.5.16**. The [Spring AI compatibility guide](https://github.com/spring-projects/spring-ai#readme) maps AI 1.1.x to Boot 3.5.x; the [1.1.8 release notes](https://github.com/spring-projects/spring-ai/releases/tag/v1.1.8) confirm the Boot 3.5 baseline. The [versioned Streamable HTTP documentation](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/mcp/mcp-streamable-http-server-boot-starter-docs.adoc) specifies the starter, protocol, and endpoint properties used here.

Start discovery and config first, then customer-service and ebank-service. No OpenAI API key is needed to host these tools. Configuration is committed in the separate local `config-repo` repository:

```properties
spring.ai.mcp.server.name=customer-service
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp
```

Banking uses the same properties with `name=ebank-service`. Connect directly to `http://localhost:8081/mcp` or `http://localhost:8082/mcp` using a **Streamable HTTP** MCP client. The gateway has no MCP route.

| Server | Tool | Arguments | Result |
| --- | --- | --- | --- |
| customer-service | `getAllCustomers` | none | List of CustomerDTO |
| customer-service | `getCustomerById` | `customerId` (positive integer) | CustomerDTO |
| customer-service | `createCustomer` | `firstName`, `lastName`, `email` | Created CustomerDTO |
| ebank-service | `getAllAccounts` | none | List of BankAccountDTO with customers |
| ebank-service | `getAccountById` | `accountId` (UUID string) | BankAccountDTO with customer |
| ebank-service | `getAccountsByCustomer` | `customerId` (positive integer) | List of BankAccountDTO for an existing customer |
| ebank-service | `createAccount` | `balance` (nonnegative finite number), `type` (`CURRENT_ACCOUNT` or `SAVING_ACCOUNT`), `customerId` | Created BankAccountDTO with customer |

The tool classes delegate persistence and lookups to the existing services. Creation reuses Bean Validation constraints on the REST DTOs. Unknown customer/account lookups produce an MCP tool error containing the existing service's clear not-found message. Filtering by customer verifies that the customer exists; a known customer with no accounts returns an empty list. Account creation still requires real customer verification, and normal read enrichment retains the Feign fallback behavior.

Each service registers a `MethodToolCallbackProvider` bean and logs its tool names at startup. Tool and parameter descriptions are published in `tools/list` and its input schemas. Tool results contain serialized DTOs, never JPA entities.

## HTTP smoke check

Initialize a session (use port 8082 for banking):

```bash
curl -i -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"manual-check","version":"1.0.0"}}}'
```

The response is HTTP 200 and includes a session ID in the `Mcp-Session-Id` header. Copy its value into `MCP_SESSION_ID`, then send the initialized notification and list tools:

```bash
MCP_SESSION_ID='paste-session-id-here'
curl -i -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-03-26' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl -i -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-03-26' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

Call a tool in the same session:

```bash
curl -i -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-03-26' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getAllCustomers","arguments":{}}}'
```

Streamable HTTP can return SSE-framed messages on these POST requests; this is part of the Streamable HTTP protocol, not the legacy separate SSE transport. A bare POST without an MCP JSON-RPC message and negotiation headers is not a valid smoke test.

## Automated verification

Run `mvn -q clean verify` from each affected module. Their integration tests use the MCP SDK's `HttpClientStreamableHttpTransport` to initialize sessions, enumerate exact tool names, invoke all tools, deserialize DTO results, and verify validation and unknown-ID errors. Banking tests use a customer HTTP stub for deterministic Feign enrichment; customer tests delete their temporary customer through the existing REST endpoint.
