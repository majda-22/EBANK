# Feign circuit breaker and recovery

Run the automated test from `ebank-service/` with Java 21 and Maven:

```bash
mvn -q clean verify
```

`BankAccountIntegrationTests.feignFallbackOpensCircuitThenRecoversThroughHalfOpenAndExposesEvents` uses real Feign HTTP calls to a local customer stub and the actual settings from `config-repo/ebank-service.properties`. It returns five failures, checks HTTP 200 fallback customer data, observes OPEN and the automatic transition to HALF_OPEN after 10 seconds, restores the customer endpoint, and verifies that two successful probes close the circuit. It also checks both Actuator endpoints. A separate test proves startup succeeds when customer-service cannot be reached and account creation remains rejected.

## Configuration

Feign circuit breaking is enabled with `spring.cloud.openfeign.circuitbreaker.enabled=true` and alphanumeric IDs enabled. The generated method names, as described in the [Spring Cloud OpenFeign documentation](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html), are:

| Feign method | Resilience4j instance |
| --- | --- |
| `findCustomerById(Long)` | `CustomerRestClientfindCustomerByIdLong` |
| `allCustomers()` | `CustomerRestClientallCustomers` |

Both instances use the `customerFeign` base configuration: count-based window 5, minimum calls 5, failure threshold 50%, open wait 10 seconds, and 2 half-open probes. Automatic transition to HALF_OPEN is enabled. A successful fallback is still recorded as a failed upstream call; it does not conceal failures from the circuit breaker. Genuine customer 404s are excluded from the failure statistics.

`CustomerRestClientFallbackFactory` logs the cause. Individual lookups return the original customer ID with firstName, lastName, and email set to `Not available`. The customer-list fallback is an empty list, so startup never seeds invented customers. Account creation rejects an unverified fallback customer with 503; reading an existing account continues with HTTP 200 and fallback customer details. A genuine customer 404 still returns 404.

The common Actuator exposure and explicit banking configuration expose `/actuator/circuitbreakers` and `/actuator/circuitbreakerevents`. Event buffers retain 100 events per instance. The [Resilience4j endpoint documentation](https://resilience4j.readme.io/docs/getting-started-3) describes these event buffers and endpoints.

## Manual scenario with the actual services

1. Start discovery, config, customer, and ebank in that order. Keep each process in its own terminal. Build and restart ebank after changing these properties; the Config Server must serve the updated configuration. Keep ebank running throughout the outage scenario so its in-memory accounts remain available.

2. Record an existing account ID. These commands use `curl` and `jq`:

   ```bash
   ACCOUNT_ID=$(curl -fsS http://localhost:8082/api/accounts | jq -r '.[0].id')
   curl -fsS "http://localhost:8082/api/accounts/$ACCOUNT_ID" | jq
   curl -fsS http://localhost:8082/actuator/circuitbreakers | jq
   ```

   The customer object initially contains the real customer name and email. Both generated circuit names appear in Actuator.

3. Stop **customer-service only** using Ctrl+C in its terminal. Make five requests:

   ```bash
   for attempt in 1 2 3 4 5; do
     curl -fsS "http://localhost:8082/api/accounts/$ACCOUNT_ID" | jq '.customer'
   done
   ```

   Every response is HTTP 200 with this shape (the ID matches the account's customerId):

   ```json
   {"id":1,"firstName":"Not available","lastName":"Not available","email":"Not available"}
   ```

   The circuit opens when at least half of its five-call window has failed. The service logs show the fallback cause, then rejected calls while OPEN.

4. Inspect the transition events and circuit state metric:

   ```bash
   curl -fsS http://localhost:8082/actuator/circuitbreakerevents \
     | jq '.circuitBreakerEvents[] | select(.type == "STATE_TRANSITION")'
   curl -fsS 'http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:CustomerRestClientfindCustomerByIdLong&tag=state:open' | jq
   ```

   OPEN has metric value 1 initially. Allow 10 seconds without making more account requests. The automatic transition produces an OPEN → HALF_OPEN event. Check the `state:half_open` metric in the same way; it becomes 1. Calling while customer-service remains down would fail a recovery probe and reopen the circuit.

5. Restart customer-service from its module directory and wait until its API and Eureka registration are ready:

   ```bash
   java -jar target/customer-service-0.0.1-SNAPSHOT.jar
   ```

   In the request terminal, make two successful probes:

   ```bash
   curl -fsS "http://localhost:8082/api/accounts/$ACCOUNT_ID" | jq '.customer'
   curl -fsS "http://localhost:8082/api/accounts/$ACCOUNT_ID" | jq '.customer'
   curl -fsS http://localhost:8082/actuator/circuitbreakerevents \
     | jq '.circuitBreakerEvents[] | select(.type == "STATE_TRANSITION")'
   curl -fsS 'http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:CustomerRestClientfindCustomerByIdLong&tag=state:closed' | jq
   ```

   Real customer data returns, and the transitions include CLOSED → OPEN → HALF_OPEN → CLOSED. The closed metric is 1. If discovery has not yet refreshed or a probe fails during customer startup, allow another 10-second open interval and repeat the two probes once the customer API is ready.
