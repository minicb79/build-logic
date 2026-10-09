# Floci & Infrastructure as Code (IaC) Plugin

The **`com.minicdesign.infra`** plugin coordinates local "pseudo cloud" deployments for Java and Kotlin microservices. It combines **Floci** (an open-source, MIT-licensed Quarkus-native LocalStack drop-in replacement on port `4566`), **Traefik** (ingress proxy with multi-environment host routing on port `8080`), **Terraform / OpenTofu** (declarative AWS infrastructure), and automated **Cognito M2M OAuth2 / OpenAPI scope synchronization**.

---

## 1. Architectural Overview

```mermaid
flowchart TD
    Client["Developer / Test Runner / Browser"]
    
    subgraph Ingress ["Traefik Ingress Proxy (:8080)"]
        RouterDev["api-dev.order-service.minicdesign.com<br/>api-dev.order-service.minicdesign.localhost:8080"]
        RouterTest["api-test.order-service.minicdesign.com<br/>api-test.order-service.minicdesign.localhost:8080"]
        RouterPath["api-dev.minicdesign.com:8080/order"]
    end

    subgraph ServiceMesh ["Local Service Containers / Host JVMs"]
        DevInstance["order-service (DEV) [:8080 JVM]"]
        TestInstance["order-service (TEST) [:8081 JVM]"]
    end

    subgraph FlociCloud ["Floci Mock AWS Cloud (:4566)"]
        SecretsMgr["AWS Secrets Manager<br/>(Two-Phase Lifecycle)"]
        CognitoIDP["AWS Cognito User Pool<br/>& Resource Server (M2M Scopes)"]
        S3Bucket["AWS S3 / SQS / SNS / SSM"]
    end

    Client -->|HTTP / OAuth2| Ingress
    RouterDev --> DevInstance
    RouterTest --> TestInstance
    RouterPath --> DevInstance

    DevInstance -->|AWS SDK| FlociCloud
    TestInstance -->|AWS SDK| FlociCloud
```

---

## 2. Directory Layout Convention

When applied to a service, all infrastructure files are co-located in the `<service>/infra` directory:

```text
<service>/
  ├── contracts/
  │   └── openapi/
  │       └── api-v1.yaml
  ├── infra/
  │   ├── build.gradle.kts                   <-- Applies com.minicdesign.infra
  │   ├── docker/
  │   │   ├── docker-compose.yml             <-- Floci + Traefik definitions
  │   │   └── ingress/
  │   │       └── traefik.yml                <-- Dynamic host/path routing rules
  │   ├── terraform/
  │   │   ├── provider.tf                    <-- AWS provider pointing to http://localhost:4566
  │   │   ├── variables.tf                   <-- Environment, service_name, oauth_scopes
  │   │   ├── main.tf                        <-- Root module wiring
  │   │   ├── environments/
  │   │   │   ├── dev.tfvars
  │   │   │   └── test.tfvars
  │   │   └── modules/
  │   │       ├── secrets/
  │   │       │   └── main.tf                <-- Placeholder secrets with ignore_changes
  │   │       └── security/
  │   │           └── cognito.tf             <-- Cognito User Pool & Resource Server
  │   └── scripts/
  │       ├── manage-secrets.sh              <-- Secure out-of-band secret setter
  │       └── setup-hosts.sh                 <-- Automated /etc/hosts helper
  └── src/
```

---

## 3. Configuration Extension (`infra`)

Configure the plugin in your `infra/build.gradle.kts` (or root `build.gradle.kts`):

```kotlin
plugins {
    id("com.minicdesign.infra")
}

infra {
    environment.set("dev")                                  // Target environment: dev, test, staging
    serviceName.set("order-service")                        // Service identifier used for routing & naming
    domainName.set("minicdesign.com")                       // Top-level domain for host routing
    flociPort.set(4566)                                     // Floci AWS mock port (default: 4566)
    ingressPort.set(8080)                                   // Traefik ingress port (default: 8080)
    terraformDir.set(file("terraform"))                     // Directory containing Terraform code
    dockerComposeDir.set(file("docker"))                    // Directory containing docker-compose.yml
    syncOpenApiScopes.set(true)                             // Auto-sync OpenAPI scopes to Cognito
    persistentState.set(true)                               // Persist Floci state across restarts
    secretsModule.set("module.secrets")                     // Module target for Phase 1 secret init
    dockerPath.set("docker")                                // Path to docker binary
    terraformPath.set("terraform")                          // Path to terraform / tofu binary
    awsPath.set("aws")                                      // Path to aws cli binary
}
```

### Property Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `environment` | `Property<String>` | `"dev"` | Active environment tier (matches `.tfvars` file). |
| `serviceName` | `Property<String>` | `project.name` | Unique service name for DNS routers, Cognito resource servers, and secret paths. |
| `domainName` | `Property<String>` | `"minicdesign.com"` | Base domain for multi-environment routing rules. |
| `flociPort` | `Property<Int>` | `4566` | Port where Floci listens for AWS service calls. |
| `ingressPort` | `Property<Int>` | `8080` | Port where Traefik routes inbound HTTP requests. |
| `terraformDir` | `DirectoryProperty` | `infra/terraform` or `terraform` | Location of Terraform scripts and modules. |
| `dockerComposeDir` | `DirectoryProperty` | `infra/docker` or `docker` | Location of `docker-compose.yml` and Traefik config. |
| `syncOpenApiScopes` | `Property<Boolean>` | `true` | Extract OAuth2 scopes from OpenAPI contracts before Terraform apply. |
| `persistentState` | `Property<Boolean>` | `true` | Retain Floci mock cloud storage across restarts. |
| `secretsModule` | `Property<String>` | `"module.secrets"` | Terraform module target for Phase 1 partial provisioning. |
| `dockerPath` | `Property<String>` | `"docker"` | Executable path for Docker CLI. |
| `terraformPath` | `Property<String>` | `"terraform"` | Executable path for Terraform or OpenTofu. |
| `awsPath` | `Property<String>` | `"aws"` | Executable path for AWS CLI. |

---

## 4. Gradle Tasks & Lifecycle

| Task | Group | Description | Dependencies |
| :--- | :--- | :--- | :--- |
| **`flociStart`** | `infra` | Starts Floci and Traefik containers and polls readiness on `http://localhost:<flociPort>/_floci/health`. | None |
| **`flociStatus`** | `infra` | Prints health and active cloud services running in Floci. | None |
| **`flociStop`** | `infra` | Shuts down Floci and Traefik containers. | None |
| **`infraConfigureHosts`** | `infra` | Checks `/etc/hosts` and prints DNS alias helpers and zero-config RFC 6761 URLs. | None |
| **`syncOpenApiScopes`** | `infra` | Extracts `securitySchemes` scopes from OpenAPI specs and generates `scopes.auto.tfvars.json`. | None |
| **`infraInitSecrets`** | `infra` | Phase 1 partial deployment: applies only `-target=module.secrets` and checks secret status. | `flociStart` |
| **`infraApply`** | `infra` | Phase 2 full Terraform apply using the active environment's `.tfvars`. | `flociStart`, `syncOpenApiScopes`, `infraInitSecrets` |
| **`infraDeploy`** | `infra` | Master orchestrator: runs `flociStart` -> `syncOpenApiScopes` -> `infraInitSecrets` -> `infraApply`. | All above |
| **`infraDestroy`** | `infra` | Destroys all cloud infrastructure in Floci via `terraform destroy`. | None |

---

## 5. Host Routing & Multi-Environment Aliases

Traefik dynamically routes incoming traffic to the appropriate service instance based on the **Host Header** or **URL Path**:

### A. Zero-Configuration RFC 6761 `*.localhost` (Recommended)
All modern operating systems and browsers resolve any domain ending with `.localhost` to `127.0.0.1` without requiring `/etc/hosts` edits or root privileges:
- **DEV**: `http://api-dev.order-service.minicdesign.localhost:8080/orders`
- **TEST**: `http://api-test.order-service.minicdesign.localhost:8080/orders`

### B. Custom Corporate Domain Aliases
To simulate production cloud DNS domains (e.g. `api-dev.order-service.minicdesign.com`):
Run `./infra/scripts/setup-hosts.sh` or add the entry to `/etc/hosts`:
```text
127.0.0.1 api-dev.order-service.minicdesign.com api-test.order-service.minicdesign.com
```

### C. Path-Based Routing
Accessing the central domain with a service prefix routes directly to that service's dev instance:
- `http://api-dev.minicdesign.com:8080/order-service/...`

---

## 6. Two-Phase Secret Management Lifecycle

To satisfy strict security guidelines where passwords, API keys, and credentials must **never** be checked into version control or leaked into Terraform state:

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / CI Script
    participant Gradle as Gradle (infra)
    participant TF as Terraform
    participant Floci as Floci Secrets Manager
    participant App as Spring Boot Service

    Dev->>Gradle: ./gradlew infraInitSecrets (Phase 1)
    Gradle->>TF: terraform apply -target=module.secrets
    TF->>Floci: Create secret with status: UNCONFIGURED & ignore_changes
    Gradle->>Dev: Prompt: Populate credentials before running full deploy

    Dev->>Floci: ./scripts/manage-secrets.sh set /dev/order/db password123
    Note over Dev,Floci: Credentials securely written directly via AWS API

    Dev->>Gradle: ./gradlew infraApply (Phase 2)
    Gradle->>TF: terraform apply -var-file=environments/dev.tfvars
    TF->>Floci: Validate secrets & provision Cognito, S3, etc.
    Dev->>App: ./gradlew bootRun
    App->>Floci: Fetch decrypted credentials at startup
```

1. **Phase 1 (`infraInitSecrets`)**:
   - Deploys only the secrets module (`-target=module.secrets`).
   - Creates the AWS Secrets Manager resource with an `UNCONFIGURED` placeholder payload.
   - Terraform lifecycle contains `ignore_changes = [secret_string]`, guaranteeing subsequent Terraform applies will **never overwrite** or expose values.
2. **Out-of-band Population**:
   - Insert secrets manually or via pipeline script using the AWS CLI or `scripts/manage-secrets.sh`:
     ```bash
     ./infra/scripts/manage-secrets.sh set /dev/order-service/database '{"username":"app_user","password":"MySecretPassword!"}'
     ```
3. **Phase 2 (`infraApply` / `infraDeploy`)**:
   - Verifies the secret status is no longer `UNCONFIGURED` and deploys the rest of the infrastructure.

---

## 7. Machine-to-Machine (M2M) Security & Cognito Scopes

Server-to-server calls authenticate using OAuth2 Client Credentials grant with AWS Cognito User Pools and Resource Servers.

1. **Scope Declaration in OpenAPI**:
   Define scopes directly in your OpenAPI specification (`contracts/openapi/*.yaml`):
   ```yaml
   components:
     securitySchemes:
       oauth2:
         type: oauth2
         flows:
           clientCredentials:
             tokenUrl: https://auth.minicdesign.com/oauth2/token
             scopes:
               orders:read: Read order details
               orders:write: Create and cancel orders
   ```
2. **Automated Synchronization (`syncOpenApiScopes`)**:
   - The plugin extracts all scopes from OpenAPI files and writes `infra/terraform/scopes.auto.tfvars.json`.
3. **Terraform Cognito Provisioning**:
   - The security module registers the custom scopes on the Cognito Resource Server:
   ```hcl
   resource "aws_cognito_resource_server" "service_api" {
     user_pool_id = aws_cognito_user_pool.m2m_pool.id
     identifier   = var.service_name
     name         = "${var.service_name} API"

     dynamic "scope" {
       for_each = var.oauth_scopes
       content {
         scope_name        = scope.value.name
         scope_description = scope.value.description
       }
     }
   }
   ```
4. **M2M Token Retrieval**:
   Downstream client services request access tokens via Floci Cognito on port 4566 using client credentials:
   ```bash
   curl -X POST http://localhost:4566/oauth2/token \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=client_credentials&client_id=<ID>&client_secret=<SECRET>&scope=order-service/orders:read"
   ```
