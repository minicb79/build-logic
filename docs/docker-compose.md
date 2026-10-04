# Docker Compose Plugin

* **Plugin ID**: `com.minicdesign.docker-compose`
* **Implementation Class**: [`DockerComposePlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/dockercompose/DockerComposePlugin.kt)
* **Configuration Extension**: [`DockerComposeExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/dockercompose/DockerComposePlugin.kt#L7-L10) under `dockerCompose { ... }`

---

## What It Does

1. **Automatic Discovery**:
   - Scans subdirectories under `docker/` for Docker Compose files (`docker-compose.yml`, `docker-compose.yaml`, `compose.yml`, `compose.yaml`).
2. **Per-Component Lifecycle Tasks**:
   - For each discovered directory (e.g. `docker/postgres/docker-compose.yml`, `docker/kafka/docker-compose.yml`), dynamically registers:
     - `start<ComponentName>`: Starts containers in detached mode (`docker compose up -d`).
     - `stop<ComponentName>`: Stops containers (`docker compose down`).
     - `check<ComponentName>`: Displays running status and health of the component's containers.
3. **Unified Project Status Dashboard**:
   - Registers a root project task: **`dockerComposeStatus`**.
   - Aggregates all compose definitions across all subprojects into an ASCII table displaying:
     - Submodule path
     - Component name
     - Service name
     - Running state (UP / DOWN)
     - Configured ports vs active port mappings

---

## Directory Structure Example

```
my-project/
├── docker/
│   ├── postgres/
│   │   └── docker-compose.yml  -> creates startPostgres, stopPostgres, checkPostgres
│   ├── redis/
│   │   └── docker-compose.yml  -> creates startRedis, stopRedis, checkRedis
│   └── kafka/
│       └── compose.yaml        -> creates startKafka, stopKafka, checkKafka
```

---

## How to Apply

In consumer `build.gradle.kts` (root or subproject):

```kotlin
plugins {
    id("com.minicdesign.docker-compose")
}
```

---

## Configuration Reference

Configure the `dockerCompose` block:

```kotlin
dockerCompose {
    // Custom binary path for docker (Default: docker)
    dockerPath.set("/usr/local/bin/docker")

    // Explicit path or command for docker-compose (Default: auto-detects 'docker-compose' or 'docker compose')
    dockerComposePath.set("docker compose")
}
```

### Parameters Summary

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `dockerPath` | `Property<String>` | `"docker"` | Path to Docker CLI binary. |
| `dockerComposePath` | `Property<String>` | (Auto-detected) | Full command to invoke compose (e.g., `docker-compose` or `docker compose`). |

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `start<Component>` | `docker` | Starts Docker Compose services for the specified component. |
| `stop<Component>` | `docker` | Stops Docker Compose services for the specified component. |
| `check<Component>` | `docker` | Checks and prints state of containers for the specified component. |
| `dockerComposeStatus` | `docker` | Prints a unified status table of all containers across all project modules. |
