# ArchUnit Architecture Governance Plugin

* **Plugin ID**: `com.minicdesign.archunit`
* **Implementation Class**: [`ArchUnitPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/archunit/ArchUnitPlugin.kt)
* **Configuration Extension**: [`ArchUnitConventionsExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/archunit/ArchUnitConventionsExtension.kt) under `archUnitConventions { ... }`
* **Rule Definitions**: [`SocieteGeneraleRule`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/archunit/SocieteGeneraleRule.kt)

---

## What It Does

The ArchUnit plugin enforces code and architecture rules at build time via Societe Generale's ArchUnit rule base (`com.societegenerale.commons:arch-unit-gradle-plugin`).
* Validates compiled classes against architectural constraints during `gradle check` or `gradle checkRules`.
* Provides 18 pre-configured rules with convenient boolean switches.
* Allows path exclusions (e.g. generated OpenAPI/WSDL models or legacy classes).
* Allows adding custom or third-party ArchUnit rules.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.archunit")
}
```

*(Note: If you already apply `com.minicdesign.spring-service` or `com.minicdesign.spring-service-kotlin`, this plugin is applied automatically).*

---

## Configuration Reference

### Available Rules & Defaults

```kotlin
import com.minicdesign.buildlogic.archunit.SocieteGeneraleRule

archUnitConventions {
    // Master switch to enable or disable ArchUnit checking entirely (Default: true)
    enabled.set(true)

    // -------------------------------------------------------------------------
    // Pre-configured Rules (Default: true)
    // -------------------------------------------------------------------------
    noAutowiredFields.set(true)       // Disallow @Autowired / @Inject on fields (enforce constructor injection)
    noStandardStreams.set(true)       // Disallow System.out and System.err (enforce SLF4J / logback)
    noJavaUtilDate.set(true)          // Disallow legacy java.util.Date in favor of java.time.*
    noJodaTime.set(true)              // Disallow Joda-Time in favor of java.time.*
    noPowerMock.set(true)             // Disallow PowerMock in favor of modular design

    // -------------------------------------------------------------------------
    // Optional Rules (Default: false)
    // -------------------------------------------------------------------------
    noJunitAsserts.set(false)                          // Disallow org.junit.Assert; enforce AssertJ
    noPublicFields.set(false)                          // Disallow public non-static fields (encapsulation)
    noPrefixForInterfaces.set(false)                   // Disallow interface names starting with 'I' (e.g. IService)
    dontReturnNullCollection.set(false)                // Enforce methods returning collections do not return null
    finalNonStaticFieldsHaveToBeStaticFinal.set(false) // Enforce constant fields (UPPER_CASE) that are final be static
    constantsAndStaticNonFinalFieldsNames.set(false)   // Enforce naming conventions for constants and static fields
    testMethodsNaming.set(false)                       // Enforce standardized test method naming
    testClassesNaming.set(false)                       // Enforce test class naming (*Test, *Tests, *IT)
    noTestIgnore.set(false)                            // Disallow @Ignore or @Disabled tests entirely
    noTestIgnoreWithoutComment.set(false)              // Require reasons on @Disabled / @Ignore tests
    stringFieldsThatAreActuallyDates.set(false)        // Disallow String fields that represent dates
    hexagonalArchitecture.set(false)                   // Enforce Hexagonal architecture package boundaries
    restrictInternalPackages.set(false)                // Enforce that ..internal.. packages cannot be accessed from outside

    // -------------------------------------------------------------------------
    // Excluded Paths
    // -------------------------------------------------------------------------
    excludePaths(
        "build/generated",
        "com/example/legacy/dto"
    )

    // -------------------------------------------------------------------------
    // Adding Rules by Enum Reference
    // -------------------------------------------------------------------------
    enableRules(
        SocieteGeneraleRule.DONT_RETURN_NULL_COLLECTION,
        SocieteGeneraleRule.NO_PREFIX_FOR_INTERFACES
    )

    // -------------------------------------------------------------------------
    // Adding Custom / 3rd-Party Rules
    // -------------------------------------------------------------------------
    addRule("com.example.rules.CustomArchRuleTest")
}
```

### Complete Parameter Matrix

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | `Property<Boolean>` | `true` | Master switch to enable or disable ArchUnit execution. |
| `noAutowiredFields` | `Property<Boolean>` | `true` | Disallows field injection with `@Autowired` or `@Inject`. |
| `noStandardStreams` | `Property<Boolean>` | `true` | Disallows `System.out`, `System.err`, and `printStackTrace`. |
| `noJavaUtilDate` | `Property<Boolean>` | `true` | Disallows `java.util.Date` in favor of `java.time.*`. |
| `noJodaTime` | `Property<Boolean>` | `true` | Disallows Joda-Time in favor of `java.time.*`. |
| `noPowerMock` | `Property<Boolean>` | `true` | Disallows PowerMock. |
| `noJunitAsserts` | `Property<Boolean>` | `false` | Enforces AssertJ assertions instead of JUnit assertions. |
| `noPublicFields` | `Property<Boolean>` | `false` | Disallows public non-static fields on classes. |
| `noPrefixForInterfaces` | `Property<Boolean>` | `false` | Disallows interface names prefixed with `I` (e.g. `IService`). |
| `dontReturnNullCollection` | `Property<Boolean>` | `false` | Ensures methods returning collections return empty instead of null. |
| `finalNonStaticFieldsHaveToBeStaticFinal` | `Property<Boolean>` | `false` | Checks for constant-cased final fields that should be static. |
| `constantsAndStaticNonFinalFieldsNames` | `Property<Boolean>` | `false` | Checks constant naming and static non-final field names. |
| `testMethodsNaming` | `Property<Boolean>` | `false` | Enforces naming patterns on test methods. |
| `testClassesNaming` | `Property<Boolean>` | `false` | Enforces test class naming conventions. |
| `noTestIgnore` | `Property<Boolean>` | `false` | Banned `@Ignore` / `@Disabled` annotations on test cases. |
| `noTestIgnoreWithoutComment` | `Property<Boolean>` | `false` | Requires a non-empty comment/reason when ignoring tests. |
| `stringFieldsThatAreActuallyDates` | `Property<Boolean>` | `false` | Flags String fields holding date values. |
| `hexagonalArchitecture` | `Property<Boolean>` | `false` | Checks hexagonal architecture layers (domain/port/adapter). |
| `restrictInternalPackages` | `Property<Boolean>` | `false` | Enforces package closure: classes in `..internal..` packages cannot be accessed from outside. |
| `excludedPaths` | `ListProperty<String>` | `[]` | Path patterns skipped during analysis (e.g. `build/generated`). |
| `additionalRules` | `ListProperty<String>` | `[]` | Fully qualified class names of additional ArchUnit rules to execute. |
| `enabledRules` | `SetProperty<SocieteGeneraleRule>` | `[]` | Set of explicit `SocieteGeneraleRule` enum values to activate. |

---

## Adding Extra Rules (Project-Local & Global)

The ArchUnit plugin allows you to enforce custom rules beyond the built-in Societe Generale rules. Rules can be authored in two ways:
1. **Standard ArchUnit Rules**: Classes containing `public static final ArchRule` fields or `public static void <name>(JavaClasses classes)` methods.
2. **Societe Generale `ArchRuleTest`**: Classes implementing `com.societegenerale.commons.plugin.rules.ArchRuleTest`.

---

### 1. Project-Local Rules (Service-Specific)

Project-local rules are defined directly inside your service repository (typically under `src/test/java` or `src/test/kotlin`).

#### Step 1: Write the Rule Class

In `src/test/java/com/example/rules/ServiceArchitectureRules.java`:

```java
package com.example.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public class ServiceArchitectureRules {

    // ArchUnit detects public static final ArchRule fields
    public static final ArchRule servicesMustBeInServicePackage =
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .should().resideInAPackage("..service..")
            .as("Classes ending with 'Service' must reside in a '..service..' package");

    public static final ArchRule controllersMustNotDependOnRepositories =
        classes()
            .that().resideInAPackage("..controller..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackages("..repository..")
            .as("Controllers must not directly depend on repository classes");
}
```

#### Step 2: Register in `build.gradle.kts`

```kotlin
archUnitConventions {
    addRule("com.example.rules.ServiceArchitectureRules")
}
```

> [!NOTE]
> `com.minicdesign.archunit` automatically adds your project's compiled test classes (`sourceSets.test.output`) to the `checkRules` classpath and ensures `checkRules` depends on `testClasses`. Your local rules are compiled and evaluated automatically with no extra configuration.

---

### 2. Global Rules (Enterprise & Shared Libraries)

Global rules are authored once in a shared repository or Gradle subproject and published to an internal artifact registry (Nexus/Artifactory) so all microservices can reuse them.

#### Step 1: Author the Shared Rules Library

In a shared repository (e.g. `enterprise-arch-rules`):

```java
package com.myorg.archrules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class EnterpriseNamingRules {

    public static final ArchRule noDeprecatedLibraries =
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
            "org.apache.commons.httpclient..",
            "org.json.."
        ).as("Legacy HTTP and JSON libraries are banned in production code");
}
```

Publish this library to your Maven repository as `com.myorg:enterprise-arch-rules:1.0.0`.

#### Step 2: Add to `archUnitExtraLib` in Consumer Services

The underlying Societe Generale plugin provides a dedicated Gradle configuration named **`archUnitExtraLib`** to feed external classes to the isolated ArchUnit execution worker:

```kotlin
dependencies {
    // For published binary artifacts:
    archUnitExtraLib("com.myorg:enterprise-arch-rules:1.0.0")

    // Or in a multi-project monorepo:
    // archUnitExtraLib(project(":shared-arch-rules"))
}

archUnitConventions {
    addRules(
        "com.myorg.archrules.EnterpriseNamingRules",
        "com.myorg.archrules.SecurityBoundariesRules"
    )
}
```

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `checkRules` | `verification` | Runs all configured ArchUnit rules against compiled classes. |
| `check` | `verification` | Lifecycle task; depends on `checkRules`. |
