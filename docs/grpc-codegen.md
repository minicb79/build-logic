# gRPC & Protobuf Codegen Plugin

* **Plugin ID**: `com.minicdesign.grpc-codegen`
* **Implementation Class**: [`GrpcCodegenPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/grpc/GrpcCodegenPlugin.kt)
* **Configuration Extension**: [`GrpcCodegenExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/grpc/GrpcCodegenExtension.kt) under `grpcCodegen { ... }`

---

## What It Does

Generates Java message classes and gRPC service client/server stubs from Protocol Buffer (`.proto`) contracts using the official Google Protobuf Gradle Plugin:

1. **Protobuf & gRPC Code Generation**:
   - Executes `com.google.protobuf:protoc` to generate immutable Java message classes, builders, and parser utilities.
   - Executes `io.grpc:protoc-gen-grpc-java` compiler plugin (when `generateGrpc` is enabled) to generate type-safe client stubs and server base service implementations (`*Grpc.java`).
2. **Directory & File Conventions**:
   - **Convention Scan**: Automatically scans and includes `.proto` files placed under:
     - `contract/proto/`
     - `contracts/proto/`
     - `src/main/proto/`
3. **Build & IDE Wiring**:
   - Registers generated Java and gRPC sources into `sourceSets.main.java`.
   - Wires task dependencies: `compileJava` and `compileKotlin` depend on `generateProto`.
   - Registers generated source directories in IntelliJ IDEA (`ideaModel.module.generatedSourceDirs`).
   - Automatically adds required runtime dependencies (`com.google.protobuf:protobuf-java`, `io.grpc:grpc-stub`, `io.grpc:grpc-protobuf`, `jakarta.annotation:jakarta.annotation-api`) to the `implementation` configuration.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.grpc-codegen")
}
```

> [!TIP]
> This plugin is self-contained. If your service also uses REST, SOAP, or GraphQL APIs, apply their respective plugins: `com.minicdesign.openapi-codegen`, `com.minicdesign.cxf-codegen`, `com.minicdesign.graphql-codegen`.

---

## Directory Conventions

Place your `.proto` contract files in standard directories, organized by **inbound** (services this service provides) and **outbound** (external services this service calls):

```
my-service/
├── contract/ (or contracts/)
│   └── proto/
│       ├── in/                             # Inbound: Services implemented by this application
│       │   └── order_service.proto
│       └── out/                            # Outbound: Remote gRPC service stubs consumed
│           └── payment_service.proto
└── src/main/proto/
        └── common_types.proto              # Standard protobuf source set
```

* **Inbound (`in/`)**: Protobuf messages and gRPC service definitions for server implementations (`*ImplBase`).
* **Outbound (`out/`)**: Remote protobuf messages and gRPC client stubs (Blocking, Async, Future stubs) for outbound integration.
* **Root / Flat (`proto/`)**: Shared common types or unsegregated proto schemas.

---

## Configuration Reference

```kotlin
grpcCodegen {
    // Protobuf compiler artifact version (Default: 3.25.5)
    protobufVersion.set("3.25.5")

    // gRPC plugin artifact version (Default: 1.68.1)
    grpcVersion.set("1.68.1")

    // Generate gRPC client stubs and server service base classes (Default: true)
    generateGrpc.set(true)

    // Output directory for generated Java protobuf messages (Default: build/generated/sources/proto/main/java)
    outputDir.set(layout.buildDirectory.dir("generated/sources/proto/main/java"))

    // Output directory for generated gRPC stubs (Default: build/generated/sources/proto/main/grpc)
    grpcOutputDir.set(layout.buildDirectory.dir("generated/sources/proto/main/grpc"))

    // Additional proto directories to scan (Defaults: contract/proto, contracts/proto, src/main/proto)
    protoDirectories.set(listOf("contract/proto", "contracts/proto", "custom/protos"))
}
```

---

## Extension Properties Reference

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `protobufVersion` | `Property<String>` | `3.25.5` | Version of `com.google.protobuf:protoc` compiler artifact. |
| `grpcVersion` | `Property<String>` | `1.68.1` | Version of `io.grpc:protoc-gen-grpc-java` compiler artifact. |
| `generateGrpc` | `Property<Boolean>` | `true` | When true, generates gRPC client and server stubs in addition to protobuf DTOs. |
| `outputDir` | `DirectoryProperty` | `build/generated/sources/proto/main/java` | Directory for generated Java protobuf message classes. |
| `grpcOutputDir` | `DirectoryProperty` | `build/generated/sources/proto/main/grpc` | Directory for generated gRPC service stubs. |
| `protoDirectories` | `ListProperty<String>` | `["contract/proto/in", "contract/proto/out", "contract/proto", "contracts/proto/in", "contracts/proto/out", "contracts/proto", "src/main/proto"]` | Directory paths searched for `.proto` contract files. |

---

## Generated Tasks

* **`generateProto`**: Master Protobuf Gradle Plugin task generating Java messages and gRPC stubs.
* **`grpcCodeGen`**: Convenience alias task matching standard codegen lifecycle naming.
* `compileJava` and `compileKotlin` are automatically wired to depend on `generateProto`.
