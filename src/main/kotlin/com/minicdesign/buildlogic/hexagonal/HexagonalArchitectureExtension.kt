package com.minicdesign.buildlogic.hexagonal

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

interface HexagonalArchitectureExtension {
    /**
     * Architectural configuration mode: SINGLE_MODULE (default) or MULTI_MODULE.
     */
    val mode: Property<HexagonalMode>

    /**
     * Root directory of the service (or monorepo root).
     * Defaults to the project directory.
     */
    val serviceDir: DirectoryProperty

    /**
     * Project base package (e.g. "com.example" or "com.minicdesign").
     * Defaults to the project's group or "com.example".
     */
    val basePackage: Property<String>

    /**
     * When true, configures the repository as a monorepo containing multiple
     * microservices under `services/` and shared library modules under `shared/`.
     * Default: false
     */
    val monorepo: Property<Boolean>

    /**
     * Directory containing services in a monorepo.
     * Defaults to `services/` under `serviceDir`.
     */
    val servicesDir: DirectoryProperty

    /**
     * Directory containing shared library modules in a monorepo.
     * Defaults to `shared/` under `serviceDir`.
     */
    val sharedDir: DirectoryProperty

    /**
     * In monorepo mode, enforce existence of services/ directory.
     * Default: true
     */
    val enforceServices: Property<Boolean>

    /**
     * In monorepo mode, enforce existence of shared/ directory.
     * Default: true
     */
    val enforceShared: Property<Boolean>

    /**
     * In monorepo mode, optionally override the HexagonalMode for specific services by name.
     * Services not in this map default to the mode defined in `mode`.
     */
    val serviceModes: MapProperty<String, HexagonalMode>

    /**
     * Enforce existence of contracts directory (contracts/ or contract/).
     * Default: true
     */
    val enforceContracts: Property<Boolean>

    /**
     * In MULTI_MODULE mode, enforce that contracts/ is its own Gradle module
     * with build.gradle or build.gradle.kts.
     * Default: true
     */
    val contractsAsModule: Property<Boolean>

    /**
     * Enforce existence of wiremock directory (wiremock/).
     * Default: true
     */
    val enforceWiremock: Property<Boolean>

    /**
     * In MULTI_MODULE mode, enforce existence of app/boot as a Gradle module.
     * Default: true
     */
    val enforceAppBoot: Property<Boolean>

    /**
     * In MULTI_MODULE mode, enforce existence of lib/core as a Gradle module.
     * Default: true
     */
    val enforceCore: Property<Boolean>

    /**
     * In MULTI_MODULE mode, enforce existence of lib/adapters/in and lib/adapters/out directories.
     * Default: true
     */
    val enforceAdapters: Property<Boolean>

    /**
     * When true, requires at least one adapter module inside lib/adapters/in/.
     * Only applies in MULTI_MODULE mode.
     * Default: false
     */
    val requireInboundAdapters: Property<Boolean>

    /**
     * When true, requires at least one adapter module inside lib/adapters/out/.
     * Only applies in MULTI_MODULE mode.
     * Default: false
     */
    val requireOutboundAdapters: Property<Boolean>

    /**
     * When true, violations cause the build to fail; when false, warnings are logged.
     * Default: true
     */
    val failOnViolation: Property<Boolean>
}
