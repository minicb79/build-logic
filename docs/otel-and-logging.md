# OpenTelemetry & Observability Plugins

This repository provides two complementary observability plugins:
1. **`com.minicdesign.otel-base`**: OpenTelemetry base dependencies (BOM, API, SDK, Logback appender).
2. **`com.minicdesign.spring-otel-logging`**: Automated code and resource generation for Spring Boot services (tracing filters, log masking, RFC 7807 problem details, and metrics).

* **Base Plugin**: [`OtelBasePlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/OtelBasePlugin.kt)
* **Spring Plugin**: [`SpringOtelLoggingPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/SpringOtelLoggingPlugin.kt)

---

## 1. OpenTelemetry Base (`com.minicdesign.otel-base`)

### What It Does
Adds standard OpenTelemetry libraries to the project's dependencies:
- OpenTelemetry BOM (`io.opentelemetry:opentelemetry-bom`)
- `io.opentelemetry:opentelemetry-api`
- `io.opentelemetry:opentelemetry-sdk`
- `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`

### How to Apply
```kotlin
plugins {
    id("com.minicdesign.otel-base")
}
```

---

## 2. Spring OpenTelemetry & Logging (`com.minicdesign.spring-otel-logging`)

### What It Does
Applies `com.minicdesign.otel-base` and configures Spring Boot observability without requiring manual boilerplate in your application code:

1. **Dependencies**:
   - `spring-boot-starter-actuator`
   - `spring-boot-starter-opentelemetry`
   - `io.micrometer:micrometer-registry-otlp` (metrics export)
   - `io.micrometer:micrometer-tracing-bridge-otel` (bridge between Micrometer Tracing and OTel)

2. **Dynamically Generated Java Classes** (`build/generated/sources/otel/java`):
   - **`TraceIdFilter.java`**: A `OncePerRequestFilter` that inspects the current trace context and appends the trace ID to HTTP response headers (defaults to `x-trace-id`).
   - **`CustomHeaderPropagator.java`**: A custom `TextMapPropagator` that extracts and injects trace headers across distributed service boundaries.
   - **`GlobalExceptionHandler.java`**: A `@RestControllerAdvice` that catches unhandled exceptions and returns an RFC 7807 `ProblemDetail` response enriched with `traceId`.
   - **`MaskingMessageConverter.java`**: A Logback message converter that automatically masks sensitive fields (passwords, credit cards, SSNs, emails) in console and file logs.
   - **`OpenTelemetryConfiguration.java`**: Automatically binds ClassLoader, JVM memory, GC, thread, and processor metrics to the Micrometer registry.
   - **`InstallOpenTelemetryAppender.java`**: Installs the OpenTelemetry Logback appender once the Spring context is initialized.
   - **`ContextPropagationConfiguration.java`**: Registers `ContextPropagatingTaskDecorator` to maintain trace context across asynchronous thread pools.
   - **`OtelAutoConfiguration.java`**: Master Spring auto-configuration importing all components.

3. **Dynamically Generated Resources** (`build/generated/sources/otel/resources`):
   - **`logback-spring.xml`**: Pre-configures console logging, log masking rules, and the `OpenTelemetryAppender`.
   - **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**: Auto-registers `OtelAutoConfiguration`.

4. **IDE & Build Integration**:
   - Automatically registers generated directories with `compileJava`, `compileKotlin`, and IntelliJ IDEA source roots.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.spring-otel-logging")
}
```

---

## Runtime Application Configuration (`application.yml` or `application.properties`)

The generated classes look for standard Spring properties:

```yaml
logging:
  trace:
    # Header name to extract and inject the trace ID (Default: x-trace-id)
    header-name: x-trace-id
  mask:
    # Comma-separated list of field names to mask with '***' in logs (Default: password,creditCard,ssn,email)
    fields: password,creditCard,ssn,email,token,secret

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `generateOtelConfig` | `other` | Generates the Java configuration classes into `build/generated/sources/otel/java`. |
| `generateOtelResources` | `other` | Generates `logback-spring.xml` and auto-configuration imports into `build/generated/sources/otel/resources`. |
