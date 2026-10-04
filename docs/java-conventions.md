# Java Conventions Plugin

* **Plugin ID**: `com.minicdesign.java-conventions`
* **Implementation Class**: [`JavaConventionsPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/java/com/minicdesign/buildlogic/JavaConventionsPlugin.java)
* **Configuration Extension**: [`JavaConventionsExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/java/com/minicdesign/buildlogic/JavaConventionsExtension.java) under `javaConventions { ... }`

---

## What It Does

1. **Java Language & Compilation**:
   - Applies the Gradle `java` plugin.
   - Configures Java Toolchain language version (default: JDK 25).
   - Enforces strict compiler arguments: `-Xlint:all`, `-Xlint:-dangling-doc-comments`, `-Xlint:-processing`, `-Werror` (warnings treated as errors), and UTF-8 encoding.
2. **Testing & Integration Testing**:
   - Configures all test tasks to use JUnit 5 (`useJUnitPlatform()`).
   - Automatically provides JUnit Jupiter (`org.junit.jupiter:junit-jupiter`) and JUnit Platform launcher dependencies.
   - Registers a dedicated **`testIntegration`** source set and **`integrationTest`** task with separate classpaths and dependency inheritance from unit tests.
   - Automatically hooks `integrationTest` into `check` (`check.dependsOn(integrationTest)`).
3. **Code Formatting (Spotless)**:
   - Applies Spotless (`com.diffplug.spotless`).
   - Enforces Eclipse Java code formatter conventions, trims trailing whitespace, removes unused imports, and ensures trailing newlines.
   - Excludes generated files under `build/generated/**`.
4. **Code Coverage & Quality Gates (JaCoCo)**:
   - Automatically configures `jacocoTestReport` to produce both XML and HTML reports.
   - Automatically runs `jacocoTestReport` after `test` and `integrationTest`.
   - Aggregates execution data across unit tests and integration tests (`*.exec`).
   - Configures `jacocoTestCoverageVerification` to enforce a line coverage threshold (default: 90%).
   - Hooks `jacocoTestCoverageVerification` into `check` (`check.dependsOn(jacocoTestCoverageVerification)`).
5. **Developer Ergonomics**:
   - Configures Lombok (`org.projectlombok:lombok`) on `compileOnly`, `annotationProcessor`, `testCompileOnly`, and `testAnnotationProcessor`.
   - Configures IntelliJ IDEA module to download Javadoc and source attachments automatically.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.java-conventions")
}
```

---

## Configuration Reference

Configure the `javaConventions` block:

```kotlin
javaConventions {
    // Target Java language version for toolchain (Default: 25)
    javaVersion.set(21)

    // Java Platform Module System (JPMS): enables inferModulePath for module-info.java (Default: false)
    modular.set(true)

    // Code coverage verification threshold (0.0 to 1.0, Default: 0.90 i.e. 90%)
    coverageThreshold.set(0.85)

    // Patterns excluded from JaCoCo verification and reports
    jacocoExclusionPatterns.set(listOf(
        "**/model/*.*",
        "**/beans/*",
        "**/config/*",
        "**/api/**",
        "**/*Application*",
        "**/otel/**",
        "**/generated/**"
    ))
}
```

### Parameters Summary

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `javaVersion` | `Property<Integer>` | `25` | Java Toolchain language version to use for compilation and testing. |
| `modular` | `Property<Boolean>` | `false` | Enables JPMS module path inference (`inferModulePath`). Also automatically enabled if `src/main/java/module-info.java` exists. |
| `coverageThreshold` | `Property<Double>` | `0.90` | Minimum line coverage ratio required for `check` to pass. |
| `jacocoExclusionPatterns` | `ListProperty<String>` | `["**/model/*.*", "**/beans/*", "**/config/*", "**/api/**", "**/*Application*", "**/otel/**"]` | Ant-style pattern exclusions from JaCoCo reporting and verification. |

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `test` | `verification` | Executes standard unit tests via JUnit 5. Finalized by `jacocoTestReport`. |
| `integrationTest` | `verification` | Executes integration tests from `src/testIntegration/java`. Finalized by `jacocoTestReport`. |
| `jacocoTestReport` | `verification` | Generates unified HTML and XML coverage reports aggregating unit and integration test data. |
| `jacocoTestCoverageVerification` | `verification` | Verifies that class line coverage meets the specified threshold. |
| `spotlessCheck` / `spotlessApply` | `verification` | Checks or formats Java code using Eclipse formatter conventions. |
| `check` | `verification` | Runs verification including `test`, `integrationTest`, `jacocoTestCoverageVerification`, and `spotlessCheck`. |
