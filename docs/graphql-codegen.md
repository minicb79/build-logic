# GraphQL Codegen Plugin

* **Plugin ID**: `com.minicdesign.graphql-codegen`
* **Implementation Class**: [`GraphQLCodegenPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/graphql/GraphQLCodegenPlugin.kt)
* **Configuration Extension**: [`GraphQLCodegenExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/graphql/GraphQLCodegenExtension.kt) under `graphqlCodegen { ... }`

---

## What It Does

Generates type-safe Java classes, interfaces, and client query APIs from GraphQL schemas (`.graphql`, `.graphqls`) using Netflix DGS Codegen:

1. **DGS Code Generation**:
   - Generates Java data models and DTOs mapping GraphQL types, inputs, and enums.
   - Generates type-safe client query and mutation builders when `generateClient` is enabled.
   - Generates DGS / Spring for GraphQL data fetcher interfaces and constants (`DgsConstants`).
   - Automatically enriches generated classes with `@Generated` annotations.
2. **Directory & File Conventions**:
   - **Convention Scan**: Automatically detects schemas placed in:
     - `contract/graphql/`
     - `contracts/graphql/`
     - `src/main/resources/graphql/`
     - `src/main/resources/schema/`
3. **Build & IDE Wiring**:
   - Registers generated classes into `sourceSets.main.java`.
   - Enforces task ordering: `compileJava` and `compileKotlin` depend on `generateGraphQL`.
   - Registers generated source directories in IntelliJ IDEA (`ideaModel.module.generatedSourceDirs`).

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.graphql-codegen")
}
```

> [!TIP]
> This plugin is self-contained. If your project also uses REST, SOAP, or gRPC APIs, apply their respective plugins: `com.minicdesign.openapi-codegen`, `com.minicdesign.cxf-codegen`, `com.minicdesign.grpc-codegen`.

---

## Directory Conventions

Place your GraphQL schema files (`.graphql` or `.graphqls`) in standard directories, organized by **inbound** (server) and **outbound** (client):

```
my-service/
├── contract/ (or contracts/)
│   └── graphql/
│       ├── in/                             # Inbound: Server schema (data fetchers, types, generateClient=false)
│       │   └── schema.graphqls
│       └── out/                            # Outbound: Remote schema (client query builders, generateClient=true)
│           └── partner-api.graphqls
└── src/main/resources/
    └── graphql/                            # Standard Spring for GraphQL location (also supports in/ and out/)
        └── schema.graphqls
```

* **Inbound (`in/`)**: Schemas hosted by this service. Generates server types, data fetcher interfaces, and constants into package `<packageName>.inbound` with `generateClient = false`.
* **Outbound (`out/`)**: Schemas of remote services this application queries. Generates type-safe query/mutation request builders and projections into package `<packageName>.outbound` with `generateClient = true`.
* **Root / Flat (`graphql/`)**: Unsegregated schemas follow global `graphqlCodegen` extension settings.

---

## Configuration Reference

### 1. Server-Side Data Types & Interfaces (Default)

For backend GraphQL services implementing resolvers/data fetchers:

```kotlin
graphqlCodegen {
    // Target package name for generated types
    packageName.set("com.example.inventory.generated.graphql")

    // Generate Java DTOs for types, inputs, enums (Default: true)
    generateDataTypes.set(true)

    // Generate interfaces for GraphQL interface types (Default: true)
    generateInterfaces.set(true)

    // Custom scalar type mappings
    typeMapping.set(mapOf(
        "DateTime" to "java.time.OffsetDateTime",
        "UUID" to "java.util.UUID"
    ))
}
```

### 2. Client-Side Query API Generation

For services calling an external GraphQL endpoint:

```kotlin
graphqlCodegen {
    packageName.set("com.example.inventory.client")

    // Generate type-safe query/mutation request builders
    generateClient.set(true)

    // Output directory (Default: build/generated/sources/graphql/java)
    outputDir.set(layout.buildDirectory.dir("generated/sources/graphql/client"))
}
```

---

## Extension Properties Reference

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `packageName` | `Property<String>` | `com.minicdesign.generated.graphql` | Base package for generated classes. |
| `schemaPaths` | `ListProperty<String>` | `["contracts/graphql", "src/main/resources/graphql", ...]` | Directories scanned for `.graphql` and `.graphqls` files. |
| `generateClient` | `Property<Boolean>` | `false` | When true, generates client query and mutation builders. |
| `generateDataTypes` | `Property<Boolean>` | `true` | When true, generates Java classes for types, inputs, and enums. |
| `generateInterfaces` | `Property<Boolean>` | `true` | When true, generates interfaces for GraphQL interface types. |
| `outputDir` | `DirectoryProperty` | `build/generated/sources/graphql/java` | Directory for generated Java sources. |
| `typeMapping` | `MapProperty<String, String>` | `{}` | Custom scalar mapping from GraphQL schema types to Java types. |

---

## Generated Tasks

* **`generateGraphQL`**: Master lifecycle task running GraphQL code generation.
* **`graphqlCodeGen`**: Convenience alias task matching standard codegen naming.
* **`generateJava`**: Underlying Netflix DGS task executing the generator.
* `compileJava` and `compileKotlin` are automatically wired to depend on `generateGraphQL`.
