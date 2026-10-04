# AI Agent Instructions for `build-logic`

This file provides operating rules and context for AI coding agents (such as Antigravity, Gemini, Claude, Cursor, Copilot, etc.) inspecting, maintaining, or consuming this repository.

---

## 1. Documentation First Principle

> [!IMPORTANT]
> **MANDATORY**: Before proposing, writing, or modifying Gradle build scripts, plugin configurations, or dependencies in any service using or contributing to `build-logic`, **you MUST read the relevant documentation in [`/docs`](file:///Users/brankominic/dev/personal/build-logic/docs/README.md)**.

The [`/docs`](file:///Users/brankominic/dev/personal/build-logic/docs/README.md) directory contains complete, up-to-date specifications for all convention plugins, their extension properties, default values, tasks, and conventions.

### Quick Reference Map
* **Java Services & Quality Gates**: [`docs/java-conventions.md`](file:///Users/brankominic/dev/personal/build-logic/docs/java-conventions.md)
* **Kotlin Services & Formatting**: [`docs/kotlin-conventions.md`](file:///Users/brankominic/dev/personal/build-logic/docs/kotlin-conventions.md)
* **Spring Boot Applications**: [`docs/spring-service.md`](file:///Users/brankominic/dev/personal/build-logic/docs/spring-service.md)
* **Architectural Governance & Rules**: [`docs/archunit.md`](file:///Users/brankominic/dev/personal/build-logic/docs/archunit.md)
* **OpenTelemetry, Tracing & Log Masking**: [`docs/otel-and-logging.md`](file:///Users/brankominic/dev/personal/build-logic/docs/otel-and-logging.md)
* **OpenAPI REST Code Generation**: [`docs/openapi-codegen.md`](file:///Users/brankominic/dev/personal/build-logic/docs/openapi-codegen.md)
* **Apache CXF SOAP / WSDL Code Generation**: [`docs/cxf-codegen.md`](file:///Users/brankominic/dev/personal/build-logic/docs/cxf-codegen.md)
* **gRPC & Protobuf Code Generation**: [`docs/grpc-codegen.md`](file:///Users/brankominic/dev/personal/build-logic/docs/grpc-codegen.md)
* **GraphQL Code Generation**: [`docs/graphql-codegen.md`](file:///Users/brankominic/dev/personal/build-logic/docs/graphql-codegen.md)
* **WireMock Stubs & Mock Server**: [`docs/wiremock.md`](file:///Users/brankominic/dev/personal/build-logic/docs/wiremock.md)
* **Hexagonal Architecture & Service Layout**: [`docs/hexagonal-architecture.md`](file:///Users/brankominic/dev/personal/build-logic/docs/hexagonal-architecture.md)
* **Docker Compose Management**: [`docs/docker-compose.md`](file:///Users/brankominic/dev/personal/build-logic/docs/docker-compose.md)
* **Pact Contract Testing**: [`docs/pact.md`](file:///Users/brankominic/dev/personal/build-logic/docs/pact.md)

---

## 2. Guidelines for Modifying This Repository

When adding or altering plugins in `build-logic`:
1. **Self-Containment**: Group related tasks, extensions, and plugins into clean, dedicated packages under `com.minicdesign.buildlogic` (e.g., `archunit`, `wiremock`, `dockercompose`, `generators`). Code generators reside under `com.minicdesign.buildlogic.generators.*`.
2. **Register All Plugins**: Always register new plugins in `gradlePlugin.plugins` in [`build.gradle.kts`](file:///Users/brankominic/dev/personal/build-logic/build.gradle.kts) with standard plugin IDs, display names, and descriptions.
3. **Keep Tests Green**: Run `./gradlew check` to ensure plugin validation (`:validatePlugins`) and unit tests pass before finishing your work.
4. **Update Documentation**: Whenever an extension property, task, or convention is changed, immediately update the corresponding markdown file in [`/docs`](file:///Users/brankominic/dev/personal/build-logic/docs/README.md).

---

## 3. Guidelines for Consuming `build-logic` in Other Projects

When generating or editing consumer microservices:
1. **Do Not Re-invent Build Logic**:
   - Do NOT manually configure Spotless, JaCoCo, Lombok, Spring Boot starter dependencies, OpenTelemetry trace filters, or ArchUnit rules in consumer `build.gradle.kts`. Apply the appropriate convention plugin instead.
2. **Respect Defaults**:
   - Rely on plugin defaults unless specifically requested by the user.
   - For example, if a service requires 80% line coverage instead of the default 90%, configure `javaConventions { coverageThreshold.set(0.80) }`.
3. **Spring Services**:
   - For Java: apply `id("com.minicdesign.spring-service")`.
   - For Kotlin: apply `id("com.minicdesign.spring-service-kotlin")`.
