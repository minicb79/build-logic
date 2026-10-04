# WireMock Plugin

* **Plugin ID**: `com.minicdesign.wiremock`
* **Implementation Class**: [`WiremockPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/wiremock/WiremockPlugin.kt)
* **Configuration Extension**: [`WiremockExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/wiremock/WiremockPlugin.kt#L8-L13) under `wiremock { ... }`

---

## What It Does

1. **Automatic Discovery & Merging**:
   - Recursively scans the project for any directory named `wiremock/` (containing `mappings/` and `__files/`), as well as test integration resources in `src/testIntegration/resources/` (including `wiremock/` subfolders and standalone `mappings/`).
   - Copies and prefixes mappings to avoid filename conflicts.
   - Detects **Mapping Collisions**:
     - *ID collision*: Same mapping ID or request signature with conflicting request definitions.
     - *Response collision*: Same request criteria mapping to different response bodies.
     - Aborts the build with a detailed diagnostic report if collisions are found.
   - Merges identical duplicate stubs cleanly.
2. **Integration Test Wiring (`testIntegration` / `integrationTest`)**:
   - Automatically registers root service `wiremock/` into the `testIntegration` source set resources.
   - Sets `wiremock.root-dir`, `wiremock.dir`, and `wiremock.search-dirs` system properties on `integrationTest` and `testIntegration` tasks so integration tests locate stubs transparently.
3. **Local Mock Server**:
   - Spawns a standalone embedded WireMock server directly from Gradle using merged stubs.
   - Supports HTTP and HTTPS with mTLS / keystore / truststore certificates.
   - Supports both interactive blocking mode (waits for Enter / Ctrl+C) and background daemon mode.
4. **Publishing to Remote Mock Servers**:
   - Publishes merged JSON stubs via HTTP to an external standalone WireMock server's admin API (`POST /__admin/mappings`).
   - Supports diff mode (`checkMissingOnly = true`) to only push new/missing stubs.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.wiremock")
}
```

---

## Configuration Reference

Configure the `wiremock` block:

```kotlin
wiremock {
    // Port for the local WireMock server (Default: 8080)
    port.set(8085)

    // Directory where stubs from all submodules are merged (Default: build/wiremock/merged)
    rootDir.set(layout.buildDirectory.dir("wiremock/merged"))

    // URL of remote WireMock server to publish to (Default: http://localhost:<port>)
    serverUrl.set("https://wiremock.dev.internal:8443")

    // In publish mode, only post mappings not already present on server (Default: true for remote, false for localhost)
    checkMissingOnly.set(true)
}
```

### Parameters Summary

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `port` | `Property<Int>` | `8080` | Port for the local standalone WireMock HTTP server. |
| `rootDir` | `DirectoryProperty` | `build/wiremock/merged` | Output directory containing merged `mappings/` and `__files/`. |
| `serverUrl` | `Property<String>` | `"http://localhost:$port"` | Remote WireMock admin endpoint for publishing. |
| `checkMissingOnly` | `Property<Boolean>` | `true` if remote, `false` if localhost | Skips uploading stubs whose UUIDs already exist on the server. |

---

## Command-Line Flags

Override configuration at execution time via Gradle project properties:

```bash
# Start WireMock on custom port
./gradlew startWiremockLocal -Pwiremock.port=9090

# Start WireMock in background
./gradlew startWiremockLocal -Pwiremock.background=true

# Stop background WireMock
./gradlew stopWiremockLocal

# Start WireMock with HTTPS / mutual TLS (port 8443)
./gradlew startWiremockLocal -Pwiremock.https=true

# Publish stubs to remote environment
./gradlew publishWiremock -Pwiremock.serverUrl=https://mock.staging.internal -Pwiremock.checkMissingOnly=true
```

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `mergeWiremockSources` | `other` | Scans all `wiremock` folders, validates for conflicts, and merges mappings into `build/wiremock/merged`. |
| `startWiremockLocal` | `other` | Merges stubs and starts the local WireMock server. |
| `stopWiremockLocal` | `other` | Stops a background WireMock server previously started with `-Pwiremock.background=true`. |
| `publishWiremock` | `other` | Merges stubs and uploads them to `wiremock.serverUrl`. |
