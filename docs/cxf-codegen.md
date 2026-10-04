# Apache CXF Codegen Plugin (SOAP / WSDL)

* **Plugin ID**: `com.minicdesign.cxf-codegen`
* **Implementation Class**: [`CxfCodegenPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/cxf/CxfCodegenPlugin.kt)
* **Configuration Extension**: [`CxfCodegenExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/cxf/CxfCodegenExtension.kt) under `cxfCodegen { ... }`

---

## What It Does

Generates type-safe Java client interfaces and JAXB DTO classes from SOAP WSDL contracts using Apache CXF `wsdl2java`:

1. **JAX-WS & JAXB Generation**:
   - Executes `org.apache.cxf.tools.wsdlto.WSDLToJava` in a standalone worker process with Jakarta EE 10 compatibility.
   - Generates `@Generated` annotations (CXF `-mark-generated`) to indicate code provenance.
   - Enables `-autoNameResolution` by default to handle complex XSD schema naming clashes automatically.
2. **Directory & File Conventions**:
   - **Convention Scan**: Automatically detects `.wsdl` files in `contract/wsdl`, `contracts/wsdl`, and `src/main/resources/wsdl`.
   - **Single WSDL Property**: Supports `wsdlLocation` for straightforward single-WSDL services (e.g. `wsdl/billing.wsdl` or `src/main/resources/...`).
   - **Multi-WSDL Container**: Supports `wsdls { create("billing") { ... } }` for explicit, multi-WSDL configurations.
3. **Build & IDE Wiring**:
   - Registers output directories into `sourceSets.main.java`.
   - Enforces task ordering: `compileJava` and `compileKotlin` depend on `generateWsdl` / `cxfCodeGen` / `wsdl2java`.
   - Registers generated source roots in IntelliJ IDEA (`ideaModel.module.generatedSourceDirs`).
   - Adds Jakarta XML Web Services and JAXB API dependencies (`jakarta.xml.ws-api`, `jakarta.annotation-api`, `jakarta.xml.bind-api`) to `implementation`.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.cxf-codegen")
}
```

> [!TIP]
> This plugin is self-contained. If your service also uses REST, gRPC, or GraphQL APIs, apply their respective plugins: `com.minicdesign.openapi-codegen`, `com.minicdesign.grpc-codegen`, `com.minicdesign.graphql-codegen`.

---

## Directory Conventions

Place your WSDL contract files in standard directories, optionally organized by **inbound** (server) and **outbound** (client):

```
my-service/
├── contract/ (or contracts/)
│   └── wsdl/
│       ├── in/                             # Inbound: Server skeletons & models (package: <base>.inbound.<service>)
│       │   └── billing-service.wsdl
│       └── out/                            # Outbound: Client proxies & models (package: <base>.outbound.<service>)
│           └── payment-gateway.wsdl
└── src/main/resources/
    └── wsdl/                               # Standard resources directory (also supports in/ and out/)
        └── account-service.wsdl
```

* **Inbound (`in/`)**: Contracts this service serves/implements. Automatically maps to package `<wsdlBasePackage>.inbound.<wsdlName>`.
* **Outbound (`out/`)**: Contracts of external services this microservice calls. Automatically maps to package `<wsdlBasePackage>.outbound.<wsdlName>`.
* **Root / Flat (`wsdl/`)**: Unsegregated contracts map directly to `<wsdlBasePackage>.<wsdlName>`.

---

## Configuration Reference

### 1. Single WSDL Convenience Configuration

For services calling a single external SOAP endpoint:

```kotlin
cxfCodegen {
    // Relative path or file under src/main/resources, contracts/wsdl, or project root
    wsdlLocation.set("wsdl/billing-service.wsdl")

    // Target packages for the generated client
    packageNames.set(listOf("com.example.billing.generated.client"))

    // Generate @Generated annotations on Java sources (Default: true)
    markGenerated.set(true)

    // Output directory (Default: build/generated/sources/wsdl/java)
    outputDir.set(layout.projectDirectory.dir("build/generated/client"))
}
```

### 2. Multi-WSDL Container Configuration

For services interacting with multiple SOAP providers:

```kotlin
cxfCodegen {
    wsdlBasePackage.set("com.example.integrations.generated")

    wsdls {
        create("billing") {
            wsdlLocation.set("contracts/wsdl/billing-service.wsdl")
            packageNames.set(listOf("com.example.billing.client"))
            markGenerated.set(true)
        }
        create("shipping") {
            wsdlFile.set(file("custom/shipping-v2.wsdl"))
            packageNames.set(listOf("com.example.shipping.client"))
            extraArgs.set(listOf("-validate"))
        }
    }
}
```

---

## Extension Properties Reference

### Global Properties (`cxfCodegen { ... }`)

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `cxfVersion` | `Property<String>` | `4.0.5` | Version of `org.apache.cxf` tools used for code generation. |
| `wsdlBasePackage` | `Property<String>` | `com.minicdesign.generated.wsdl` | Fallback base package for generated client and model classes. |
| `outputDir` | `DirectoryProperty` | `build/generated/sources/wsdl/java` | Base directory for generated Java sources. |
| `markGenerated` | `Property<Boolean>` | `true` | When true, passes `-mark-generated` to CXF `WSDLToJava`. |
| `autoNameResolution` | `Property<Boolean>` | `true` | When true, passes `-autoNameResolution` to CXF `WSDLToJava`. |
| `wsdlLocation` | `Property<String>` | `null` | Single WSDL location shorthand (path or filename). |
| `packageNames` | `ListProperty<String>` | `[]` | Package name mappings for single `wsdlLocation`. |

### `wsdls` Container Properties (`wsdls.create("name") { ... }`)

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `wsdlFile` | `RegularFileProperty` | `null` | Explicit WSDL file reference. |
| `wsdlLocation` | `Property<String>` | `null` | Relative path or filename of WSDL file. |
| `outputDir` | `DirectoryProperty` | `<global.outputDir>/<specName>` | Custom output directory for this spec. |
| `packageNames` | `ListProperty<String>` | Derived from `wsdlBasePackage` | Package name arguments (`-p`) passed to CXF. |
| `markGenerated` | `Property<Boolean>` | Inherits global | Overrides `-mark-generated` for this spec. |
| `autoNameResolution` | `Property<Boolean>` | Inherits global | Overrides `-autoNameResolution` for this spec. |
| `extraArgs` | `ListProperty<String>` | `[]` | Extra command-line arguments passed to CXF `WSDLToJava`. |

---

## Generated Tasks

* **`generateWsdl<SpecName>`**: Generates Java client classes for a specific WSDL (e.g., `generateWsdlBillingService`).
* **`generateWsdl`**: Master lifecycle task running all registered WSDL generation tasks.
* **`cxfCodeGen`**: Master alias task (matching `cxfcodegen-common.gradle.kts`).
* **`wsdl2java`**: Master alias task (matching CXF Gradle plugin conventions).
* `compileJava` and `compileKotlin` are automatically wired to depend on `generateWsdl`.
