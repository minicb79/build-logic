# MinicDesign Build Logic

Centralized Gradle convention plugins library providing consistent build pipelines, quality gates, observability, architectural governance, and developer tooling across Java and Kotlin services.

---

## Documentation

Full documentation for humans and AI agents is available in the **[`/docs`](file:///Users/brankominic/dev/personal/build-logic/docs/README.md)** directory:

* **[Overview & Getting Started](file:///Users/brankominic/dev/personal/build-logic/docs/README.md)**
* **[Java Conventions (`com.minicdesign.java-conventions`)](file:///Users/brankominic/dev/personal/build-logic/docs/java-conventions.md)**
* **[Kotlin Conventions (`com.minicdesign.kotlin-conventions`)](file:///Users/brankominic/dev/personal/build-logic/docs/kotlin-conventions.md)**
* **[Spring Service (`com.minicdesign.spring-service` & `spring-service-kotlin`)](file:///Users/brankominic/dev/personal/build-logic/docs/spring-service.md)**
* **[ArchUnit Governance (`com.minicdesign.archunit`)](file:///Users/brankominic/dev/personal/build-logic/docs/archunit.md)**
* **[OpenTelemetry & Logging (`com.minicdesign.otel-base` & `spring-otel-logging`)](file:///Users/brankominic/dev/personal/build-logic/docs/otel-and-logging.md)**
* **[OpenAPI Codegen (`com.minicdesign.openapi-codegen`)](file:///Users/brankominic/dev/personal/build-logic/docs/openapi-codegen.md)**
* **[Apache CXF SOAP Codegen (`com.minicdesign.cxf-codegen`)](file:///Users/brankominic/dev/personal/build-logic/docs/cxf-codegen.md)**
* **[gRPC & Protobuf Codegen (`com.minicdesign.grpc-codegen`)](file:///Users/brankominic/dev/personal/build-logic/docs/grpc-codegen.md)**
* **[GraphQL Codegen (`com.minicdesign.graphql-codegen`)](file:///Users/brankominic/dev/personal/build-logic/docs/graphql-codegen.md)**
* **[WireMock (`com.minicdesign.wiremock`)](file:///Users/brankominic/dev/personal/build-logic/docs/wiremock.md)**
* **[Hexagonal Architecture (`com.minicdesign.hexagonal-architecture`)](file:///Users/brankominic/dev/personal/build-logic/docs/hexagonal-architecture.md)**
* **[Docker Compose (`com.minicdesign.docker-compose`)](file:///Users/brankominic/dev/personal/build-logic/docs/docker-compose.md)**
* **[Pact Contract Testing (`com.minicdesign.pact`)](file:///Users/brankominic/dev/personal/build-logic/docs/pact.md)**

> [!TIP]
> AI agents should review [`AGENTS.md`](file:///Users/brankominic/dev/personal/build-logic/AGENTS.md) for automated execution rules and guidelines.

---

## Quickstart

### Include in a Consumer Project (`settings.gradle.kts`)

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("../build-logic") // Adjust relative path as needed
```

### Apply in Service `build.gradle.kts`

```kotlin
plugins {
    id("com.minicdesign.spring-service")
    id("com.minicdesign.spring-otel-logging")
    id("com.minicdesign.openapi-codegen")
}
```

---

## Building & Testing `build-logic`

```bash
# Build and execute all tests and plugin validations
./gradlew check

# Build artifacts
./gradlew build
```
