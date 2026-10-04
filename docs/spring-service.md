# Spring Service Conventions Plugins

* **Java Plugin ID**: `com.minicdesign.spring-service`
* **Kotlin Plugin ID**: `com.minicdesign.spring-service-kotlin`
* **Java Implementation**: [`SpringServicePlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/SpringServicePlugin.kt)
* **Kotlin Implementation**: [`SpringServiceKotlinPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/SpringServiceKotlinPlugin.kt)

---

## What They Do

### 1. Java Spring Service (`com.minicdesign.spring-service`)
* Applies `com.minicdesign.java-conventions` (Java toolchain, Spotless, JUnit 5, JaCoCo, Lombok).
* Applies the official Spring Boot (`org.springframework.boot`) and Spring Dependency Management (`io.spring.dependency-management`) plugins.
* Applies architectural governance via **`com.minicdesign.archunit`** (checking rules like constructor injection and logging standards).
* Pre-configures core Spring microservice runtime and test dependencies:
  * `spring-boot-starter-web` (REST APIs)
  * `spring-boot-starter-restclient` (HTTP client)
  * `spring-boot-starter-test` (Spring Test, Mockito, AssertJ)

### 2. Kotlin Spring Service (`com.minicdesign.spring-service-kotlin`)
* Extends `com.minicdesign.spring-service` with Kotlin support.
* Applies `com.minicdesign.kotlin-conventions` (ktlint, JVM target).
* Applies the Spring Kotlin all-open plugin (`org.jetbrains.kotlin.plugin.spring`) to automatically open Spring bean classes and methods for proxying.
* Pre-configures Kotlin runtime dependencies:
  * `kotlin-reflect`
  * `jackson-module-kotlin` (for JSON serialization/deserialization)

---

## How to Apply

### For Java Spring Boot Services

```kotlin
plugins {
    id("com.minicdesign.spring-service")
}
```

### For Kotlin Spring Boot Services

```kotlin
plugins {
    id("com.minicdesign.spring-service-kotlin")
}
```

---

## Configuration Reference

Both plugins expose the underlying conventions blocks:

### 1. ArchUnit Rules Configuration (`archUnitConventions`)
Refer to [ArchUnit Plugin Documentation](file:///Users/brankominic/dev/personal/build-logic/docs/archunit.md) for complete options.

```kotlin
archUnitConventions {
    noAutowiredFields.set(true)   // Disallow field injection (enforce constructor injection)
    noStandardStreams.set(true)   // Disallow System.out/System.err (enforce logger)
    noJunitAsserts.set(true)      // Enforce AssertJ over JUnit assertions
}
```

### 2. Java Conventions Configuration (`javaConventions`)
```kotlin
javaConventions {
    javaVersion.set(21)
    coverageThreshold.set(0.90)
}
```

### 3. Kotlin Conventions Configuration (`kotlinConventions` - Kotlin services only)
```kotlin
kotlinConventions {
    jvmTarget.set(21)
}
```

---

## Built-In Spring Boot & ArchUnit Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `bootRun` | `application` | Runs the Spring Boot application locally. |
| `bootJar` | `build` | Packages an executable Spring Boot fat JAR. |
| `checkRules` | `verification` | Runs ArchUnit architecture tests on compiled classes. |
| `check` | `verification` | Runs all verification: unit tests, integration tests, JaCoCo threshold checks, Spotless, and ArchUnit rules. |
