# MinicDesign Build Logic Documentation

Welcome to the documentation for **`build-logic`** (`com.minicdesign.buildlogic`). This suite of Gradle convention plugins standardizes enterprise builds, quality gates, observability, contract testing, and local developer environments across Java and Kotlin services.

---

## Instructions for AI Agents & Developers

> [!IMPORTANT]
> **For AI Agents & Developers**:
> When generating, refactoring, or configuring Gradle builds in projects that use `build-logic`:
> 1. Consult the plugin reference guides listed below before writing or modifying build scripts.
> 2. Always check the **configuration extension** and **property defaults** in the corresponding documentation before introducing manual plugins or custom tasks.
> 3. Avoid duplicating build logic (such as manual Jacoco verification, Spotless setup, Docker compose scripts, or OpenTelemetry filters) when a convention plugin already provides it.
> 4. Keep build scripts concise by relying on plugin defaults and customizing only what varies per service.

---

## Plugin Directory

| Plugin ID | Description | Docs |
| :--- | :--- | :--- |
| **`com.minicdesign.java-conventions`** | Toolchain, strict linting, Spotless, JUnit 5, Jacoco 90% coverage threshold, `testIntegration` source set. | [Java Conventions](file:///Users/brankominic/dev/personal/build-logic/docs/java-conventions.md) |
| **`com.minicdesign.kotlin-conventions`** | Kotlin JVM target, compiler arguments, ktlint, `testIntegration` source set, testing lifecycle. | [Kotlin Conventions](file:///Users/brankominic/dev/personal/build-logic/docs/kotlin-conventions.md) |
| **`com.minicdesign.spring-service`** | Spring Boot web service conventions for Java, starter dependencies, automated ArchUnit governance. | [Spring Service](file:///Users/brankominic/dev/personal/build-logic/docs/spring-service.md) |
| **`com.minicdesign.spring-service-kotlin`** | Spring Boot web service conventions for Kotlin, jackson-module-kotlin, kotlin-reflect. | [Spring Service (Kotlin)](file:///Users/brankominic/dev/personal/build-logic/docs/spring-service.md#kotlin-support-comminicdesignspring-service-kotlin) |
| **`com.minicdesign.archunit`** | Architectural governance based on Societe Generale's rule base (18 built-in rules, switches, path exclusions). | [ArchUnit Plugin](file:///Users/brankominic/dev/personal/build-logic/docs/archunit.md) |
| **`com.minicdesign.otel-base`** | OpenTelemetry BOM platform, API, SDK, and Logback appender dependencies. | [OpenTelemetry Base](file:///Users/brankominic/dev/personal/build-logic/docs/otel-and-logging.md#opentelemetry-base-comminicdesignotel-base) |
| **`com.minicdesign.spring-otel-logging`** | Automated OTel code generation: trace ID filter, RFC 7807 exception handler, log masking, micrometer metrics. | [Spring OTel & Logging](file:///Users/brankominic/dev/personal/build-logic/docs/otel-and-logging.md) |
| **`com.minicdesign.openapi-codegen`** | OpenAPI REST code generation (server interfaces, client DTOs, OAuth2 scope configs). | [OpenAPI Codegen](file:///Users/brankominic/dev/personal/build-logic/docs/openapi-codegen.md) |
| **`com.minicdesign.cxf-codegen`** | SOAP client generation from WSDL files (`contracts/wsdl/`, `src/main/resources/wsdl/`) using Apache CXF `wsdl2java`. | [CXF Codegen](file:///Users/brankominic/dev/personal/build-logic/docs/cxf-codegen.md) |
| **`com.minicdesign.grpc-codegen`** | Protobuf messages and gRPC stub generation from `.proto` contracts (`contracts/proto/`, `src/main/proto/`). | [gRPC Codegen](file:///Users/brankominic/dev/personal/build-logic/docs/grpc-codegen.md) |
| **`com.minicdesign.graphql-codegen`** | GraphQL data types and query API generation from schemas (`contracts/graphql/`, `src/main/resources/graphql/`) via DGS. | [GraphQL Codegen](file:///Users/brankominic/dev/personal/build-logic/docs/graphql-codegen.md) |
| **`com.minicdesign.wiremock`** | Multi-module WireMock stub merge, collision detection, local HTTP/HTTPS server, and broker publish. | [WireMock Plugin](file:///Users/brankominic/dev/personal/build-logic/docs/wiremock.md) |
| **`com.minicdesign.docker-compose`** | Per-component Docker compose tasks (`start<Component>`, `stop<Component>`) and global status dashboard. | [Docker Compose](file:///Users/brankominic/dev/personal/build-logic/docs/docker-compose.md) |
| **`com.minicdesign.pact`** | Consumer contract testing, parallel test runners, Pact Broker publishing, and `canIDeploy` gating. | [Pact Plugin](file:///Users/brankominic/dev/personal/build-logic/docs/pact.md) |
| **`com.minicdesign.infra`** | Local pseudo cloud with Floci AWS mock, Traefik ingress, Terraform/OpenTofu, two-phase secret lifecycle, and Cognito M2M OAuth2 scopes. | [Floci & Infra Plugin](file:///Users/brankominic/dev/personal/build-logic/docs/floci-and-infra.md) |

---

## How to Include `build-logic` in a Project

### Option A: Gradle Composite Build (`includedBuild`) - Recommended for Multi-Repo or Monorepo

In the consuming project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Include build-logic as a composite build
includeBuild("../build-logic") // Adjust relative path or use submodule
```

### Option B: Binary Dependency from Maven Repository

Publish `build-logic` using `./gradlew publish` to an internal Artifactory/Nexus repository or Maven Local, then in consumer `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://your-artifactory.domain.com/artifactory/libs-release")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```
