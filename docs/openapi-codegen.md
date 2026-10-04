# OpenAPI Codegen Plugin

* **Plugin ID**: `com.minicdesign.openapi-codegen` (legacy aliases: `com.minicdesign.openapi-generation`, `com.minicdesign.api-generation`)
* **Implementation Class**: [`OpenApiCodegenPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/generators/openapi/OpenApiCodegenPlugin.kt)
* **Configuration Extension**: [`OpenApiCodegenExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/generators/openapi/OpenApiCodegenExtension.kt) under `openapiCodegen { ... }` (aliases: `openApiCodegen { ... }`, `openApiGeneration { ... }`, `apiGeneration { ... }`)

---

## What It Does

Automatically scans convention directories and custom spec declarations for OpenAPI / Swagger REST API specifications, generating type-safe Java code and configuration during the build:

1. **REST APIs (OpenAPI / Swagger)**:
   - Distinguishes **Inbound** (server APIs implemented by this service) vs. **Outbound** (client APIs consumed by this service).
   - **Inbound contracts** generate Spring REST controller interfaces (`interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`) and models.
   - **Outbound contracts** generate models only (`--global-property models`, `generateApis=false`, `generateSupportingFiles=false`), encouraging clean `RestClient` adapters.
   - Executes `org.openapitools:openapi-generator-cli`.
2. **Java Source Post-Processing**:
   - Replaces generic `@Nullable` annotations with JSpecify (`org.jspecify.annotations.Nullable`).
   - Removes obsolete `org.hibernate.validator.constraints.*` annotations and imports in favor of `jakarta.validation.constraints.*`.
   - Adds `@jakarta.annotation.Nonnull` to getter methods returning collection types (`List`, `Set`, `Map`) to enforce non-null collections.
   - Adds `@JsonInclude(JsonInclude.Include.NON_NULL)` to generated model classes.
3. **Outbound OAuth2 Scope Extraction & Configuration**:
   - Parses outbound OpenAPI specs for security schemes and operation scopes.
   - Generates Spring Security OAuth2 Client registration configuration (one registration per distinct scope set, avoiding overly broad scopes) and catalog path mappings into `application-oauth2-catalogs.yml`.
4. **Build & IDE Wiring**:
   - Automatically registers output directories into the `main` source set (`sourceSets.main.java`).
   - Ensures `compileJava` and `compileKotlin` depend on generation tasks (`openApiGenerate`, `openapiCodeGen`, `generateOpenApi`, `generateApi`).
   - Ensures `processResources` depends on OAuth2 config generation tasks.
   - Registers generated source directories as generated source roots in IntelliJ IDEA (`ideaModel.module.generatedSourceDirs`).

> [!NOTE]
> `OpenApiCodegenPlugin` strictly handles OpenAPI REST specifications. For SOAP WSDL, gRPC Protobuf, or GraphQL, apply the dedicated self-contained generator plugins:
> - SOAP WSDL: [`com.minicdesign.cxf-codegen`](file:///Users/brankominic/dev/personal/build-logic/docs/cxf-codegen.md)
> - gRPC Protobuf: [`com.minicdesign.grpc-codegen`](file:///Users/brankominic/dev/personal/build-logic/docs/grpc-codegen.md)
> - GraphQL: [`com.minicdesign.graphql-codegen`](file:///Users/brankominic/dev/personal/build-logic/docs/graphql-codegen.md)

---

## Directory Conventions

By default, the plugin scans the `contract/openapi` (or `contracts/openapi`) directory with symmetric **inbound** (server) and **outbound** (client) conventions:

```
my-service/
└── contract/ (or contracts/)
    └── openapi/
        ├── in/                             # Inbound: Server controller interfaces + models
        │   └── petstore-service.yaml
        └── out/                            # Outbound: Client models only (for RestClient adapters)
            └── payment-gateway.yaml
```

- Any YAML/JSON spec placed in `contract/openapi/in` (or files ending in `.in.yaml` / `.in.json`) is treated as **inbound**.
- Any YAML/JSON spec placed in `contract/openapi/out` (or files ending in `.out.yaml` / `.out.json`) is treated as **outbound**.
- Directory trees are recursively traversed, and nested directories automatically map to subpackages (e.g. `contract/openapi/out/payments/v1/api.yaml` -> package `...out.payments.v1`).

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.openapi-codegen")
}
```

---

## Configuration Reference

### 1. Global Plugin Configuration

```kotlin
openapiCodegen {
    // Base Java package for generated OpenAPI models and APIs (Default: com.minicdesign.generated.openapi)
    openApiBasePackage.set("com.example.orders.generated.openapi")

    // OpenAPI Generator CLI version (Default: 7.11.0)
    openApiVersion.set("7.11.0")

    // Spring Boot major version (Default: 3)
    springBootVersion.set(3)

    // Enable/disable AST source post-processing (Default: true)
    enableSourcePostProcessing.set(true)

    // Global OAuth2 scope configuration derivation for outbound specs (Default: false)
    oauth2ScopeConfig.set(false)
    oauth2ProviderId.set("okta")
}
```

### 2. Custom Spec Directory Declarations (`openApiSpecDirectories`)

If you have additional folders outside the standard `contract/openapi` tree:

```kotlin
openApiSpecDirectories {
    create("partnerApis") {
        specPath.set("specs/partners")
        packagePrefix.set("out.partners")
        oauth2ScopeConfig.set(true)
        oauth2ProviderId.set("okta")
        oauth2ClientIdProperty.set("\${partners.client-id}")
        oauth2ClientSecretProperty.set("\${partners.client-secret}")
    }
}
```

### 3. Explicit Individual Specs (`openApiSpecs`)

For granular control over an individual specification:

```kotlin
openApiSpecs {
    create("paymentApi") {
        specPath.set("custom-specs")
        specFilename.set("payments-v2.yaml")
        apiPackage.set("out.payments")
        modelPackage.set("out.payments.model")
        isOutbound.set(true)
        oauth2ScopeConfig.set(true)
        oauth2ProviderId.set("okta")
        oauth2ClientIdProperty.set("\${payments.client-id}")
        oauth2ClientSecretProperty.set("\${payments.client-secret}")
    }
}
```

---

## Extension Properties Reference

### Global Properties (`openapiCodegen`)

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `openApiBasePackage` | `Property<String>` | `com.minicdesign.generated.openapi` | Prefix package name for generated OpenAPI controllers and models. |
| `openApiVersion` | `Property<String>` | `7.11.0` | CLI version of `org.openapitools:openapi-generator-cli`. |
| `springBootVersion` | `Property<Int>` | `3` | Spring Boot major version targeting generated annotations. |
| `enableSourcePostProcessing` | `Property<Boolean>` | `true` | Runs AST source cleaning (JSpecify, Nonnull getters, JsonInclude). |
| `oauth2ScopeConfig` | `Property<Boolean>` | `false` | Whether to automatically generate OAuth2 client configs for outbound specs. |
| `oauth2ProviderId` | `Property<String>` | `okta` | Default provider identifier in generated OAuth2 configs. |
| `oauth2ClientIdProperty` | `Property<String>` | `\${oauth2.client-id}` | Spring property placeholder for client ID. |
| `oauth2ClientSecretProperty` | `Property<String>` | `\${oauth2.client-secret}` | Spring property placeholder for client secret. |

### `openApiSpecs` Container Properties

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `specPath` | `Property<String>` | `contract/openapi/in` | Directory containing the spec, relative to project root. |
| `specFilename` | `Property<String>` | `${project.name}.yml` | Spec filename (e.g. `order-service.yaml`). |
| `apiPackage` | `Property<String>` | `api` | Target subpackage for generated API interfaces (inbound only). |
| `modelPackage` | `Property<String>` | `model` | Target subpackage for generated DTO models. |
| `isOutbound` | `Property<Boolean>` | `false` | When true, generates client models only without server interfaces. |
| `oauth2ScopeConfig` | `Property<Boolean>` | `false` | Whether to derive OAuth2 client configuration for this spec. |
| `oauth2ProviderId` | `Property<String>` | `okta` | Provider identifier in generated OAuth2 configuration. |
| `oauth2ClientIdProperty` | `Property<String>` | `\${oauth2.client-id}` | Spring property placeholder for client ID. |
| `oauth2ClientSecretProperty` | `Property<String>` | `\${oauth2.client-secret}` | Spring property placeholder for client secret. |

---

## Generated Tasks

* **`openApiGenerate`**: Master aggregation task that runs code generation across all discovered and declared OpenAPI specs.
* **`generateOpenApi<SpecName>`**: Generates Java models and/or interfaces for a specific spec.
* **`generateOAuth2Config<SpecName>`**: Extracts scopes and writes `application-oauth2-catalogs.yml` for outbound specs.
* **`openapiCodeGen`** / **`generateOpenApi`** / **`generateApi`**: Aliases pointing to `openApiGenerate`.
* All compilation tasks (`compileJava`, `compileKotlin`) depend on `openApiGenerate`, and `processResources` depends on OAuth2 configuration generation tasks.
