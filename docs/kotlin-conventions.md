# Kotlin Conventions Plugin

* **Plugin ID**: `com.minicdesign.kotlin-conventions`
* **Implementation Class**: [`KotlinConventionsPlugin`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/KotlinConventionsPlugin.kt)
* **Configuration Extension**: [`KotlinConventionsExtension`](file:///Users/brankominic/dev/personal/build-logic/src/main/kotlin/com/minicdesign/buildlogic/KotlinConventionsExtension.kt) under `kotlinConventions { ... }`

---

## What It Does

1. **Kotlin JVM Setup**:
   - Applies `org.jetbrains.kotlin.jvm`.
   - Configures the Kotlin compiler options (`jvmTarget` and `freeCompilerArgs`).
2. **Code Formatting (ktlint via Spotless)**:
   - Applies Spotless (`com.diffplug.spotless`).
   - Enforces **ktlint** (`1.5.0`) code style, trims trailing whitespace, and ensures trailing newlines.
3. **Integration Testing**:
   - Sets up a separate `testIntegration` source set (`src/testIntegration/kotlin`).
   - Registers the `integrationTest` task with JUnit Platform support.
   - Automatically wires `check.dependsOn(integrationTest)`.
4. **Dependencies**:
   - Automatically adds `kotlin-test-junit5` to `testImplementation` and `junit-platform-launcher` to `testRuntimeOnly`.
   - Hooks into JaCoCo report generation if the `jacoco` plugin is present.

---

## How to Apply

In consumer `build.gradle.kts`:

```kotlin
plugins {
    id("com.minicdesign.kotlin-conventions")
}
```

---

## Configuration Reference

Configure the `kotlinConventions` block:

```kotlin
kotlinConventions {
    // Kotlin JVM target version (Default: 25)
    jvmTarget.set(21)

    // Additional compiler arguments
    freeCompilerArgs.set(listOf(
        "-Xjsr305=strict",
        "-Xannotation-default-target=param-property",
        "-opt-in=kotlin.RequiresOptIn"
    ))
}
```

### Parameters Summary

| Property | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `jvmTarget` | `Property<Int>` | `25` | Target JVM bytecode version for Kotlin compiler. |
| `freeCompilerArgs` | `ListProperty<String>` | `["-Xjsr305=strict", "-Xannotation-default-target=param-property"]` | Additional compiler arguments passed to the Kotlin compiler. |

---

## Available Tasks

| Task | Group | Description |
| :--- | :--- | :--- |
| `integrationTest` | `verification` | Runs Kotlin integration tests from `src/testIntegration/kotlin`. |
| `spotlessCheck` / `spotlessApply` | `verification` | Checks or reformats Kotlin code using ktlint. |
| `check` | `verification` | Lifecycle task running all verifications, including unit and integration tests. |
