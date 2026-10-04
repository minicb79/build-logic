package com.minicdesign.buildlogic.hexagonal

/**
 * Architectural configuration mode for Hexagonal Architecture enforcement.
 */
enum class HexagonalMode {
    /**
     * Single-module service (default):
     * Organizes domain core and adapters by package within a single Gradle project.
     * Enforces the presence of API contracts (contracts/), WireMock stubs (wiremock/),
     * and standard source structure (src/main).
     */
    SINGLE_MODULE,

    /**
     * Multi-module service:
     * Enforces strict physical Gradle subproject boundaries:
     * - app/boot (Spring Boot application module)
     * - contracts/ (Dedicated Gradle module for generated API models & interfaces)
     * - lib/core (Domain logic and port interfaces module)
     * - lib/adapters/in/<adapter> (Inbound adapter Gradle modules)
     * - lib/adapters/out/<adapter> (Outbound adapter Gradle modules)
     * - wiremock/ (WireMock stubs)
     */
    MULTI_MODULE
}
