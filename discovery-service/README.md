# Discovery service

Standalone Eureka Server with Spring Boot 3.5.16, Spring Cloud 2025.0.3, Java 21, and Actuator. Start this service before the Config Server; it does not require remote configuration or register itself as a client.

With JDK 21 and Maven 3.6.3 or later on your path, run from this directory:

```bash
mvn -q clean verify
java -jar target/discovery-service-0.0.1-SNAPSHOT.jar
```

Open the Eureka dashboard at <http://localhost:8761>. Health is available at <http://localhost:8761/actuator/health>.

The automated test starts the application on a random port and checks both the dashboard and Actuator health over HTTP.
