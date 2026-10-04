# Pact Contract Testing Plugin

* **Plugin ID**: `com.minicdesign.pact`
* **Implementation Class**: [`PactPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/PactPlugin.kt)
* **Configuration Extension**: [`PactExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/PactPlugin.kt#L9-L13) under `pact { ... }`

---

## What It Does

1. **System Property Propagation**:
   - Automatically injects Pact Broker connection details (`pactbroker.url`, `pact.provider.version`, `pact.provider.branch`, `pact.verifier.publishResults`) into all Gradle `Test` tasks.
2. **Dedicated Pact Test Source Sets**:
   - Detects source sets named `testPactConsumer*` or `testPactProvider`.
   - Creates corresponding test execution tasks configured for JUnit 5 parallel execution (`junit.jupiter.execution.parallel.enabled=true`).
   - Finalizes pact tests with JaCoCo reports.
   - Registers an aggregated task: **`testPact`**.
   - Wires pact tests into the Gradle `check` lifecycle.
3. **Pact Broker Integration (via Official Docker CLI)**:
   - **`publishConsumerContracts`**: Publishes generated consumer JSON contracts from `build/pacts` to the Pact Broker tagged with the application version and git branch.
   - **`canIDeploy`**: Queries the Pact Broker CLI (`can-i-deploy`) to verify whether the specified version is safe to deploy to the target environment (e.g. `production`).

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.pact")
}
```

---

## Configuration Reference

Configure the `pact` block:

```kotlin
pact {
    // Pact Broker base URL (Default: env PACT_BROKER_BASE_URL or http://localhost:9292)
    brokerUrl.set("https://pact-broker.internal.net")

    // Consumer/Provider application version (Default: Gradle property 'appVersion' or 1.0.0)
    appVersion.set("2.4.1")

    // Git branch name for tagging contracts (Default: Gradle property 'branch' or main)
    branch.set("feature/checkout-flow")
}
```

### Parameters Summary

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `brokerUrl` | `Property<String>` | `env(PACT_BROKER_BASE_URL)` or `"http://localhost:9292"` | Base URL of the Pact Broker instance. |
| `appVersion` | `Property<String>` | `project.property("appVersion")` or `"1.0.0"` | Semver/Git commit version of the participant. |
| `branch` | `Property<String>` | `project.property("branch")` or `"main"` | Branch name associated with contract publication. |

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `testPact` | `verification` | Runs all detected Pact consumer and provider verification test tasks. |
| `publishConsumerContracts` | `pact` | Publishes generated pact JSON files from `build/pacts` to the Pact Broker using Docker CLI. |
| `canIDeploy` | `pact` | Runs `pact-broker can-i-deploy` against the broker to check deployment safety. |
