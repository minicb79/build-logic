package com.minicdesign.buildlogic.hexagonal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Verifies directory layout on disk")
abstract class CheckHexagonalArchitectureTask : DefaultTask() {

    @get:Input
    abstract val mode: Property<HexagonalMode>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serviceDir: DirectoryProperty

    @get:Input
    abstract val monorepo: Property<Boolean>

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val servicesDir: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedDir: DirectoryProperty

    @get:Input
    abstract val enforceServices: Property<Boolean>

    @get:Input
    abstract val enforceShared: Property<Boolean>

    @get:Input
    abstract val serviceModes: MapProperty<String, HexagonalMode>

    @get:Input
    abstract val enforceContracts: Property<Boolean>

    @get:Input
    abstract val contractsAsModule: Property<Boolean>

    @get:Input
    abstract val enforceWiremock: Property<Boolean>

    @get:Input
    abstract val enforceAppBoot: Property<Boolean>

    @get:Input
    abstract val enforceCore: Property<Boolean>

    @get:Input
    abstract val enforceAdapters: Property<Boolean>

    @get:Input
    abstract val requireInboundAdapters: Property<Boolean>

    @get:Input
    abstract val requireOutboundAdapters: Property<Boolean>

    @get:Input
    abstract val failOnViolation: Property<Boolean>

    @TaskAction
    fun checkStructure() {
        val root = serviceDir.get().asFile
        val isMonorepo = monorepo.get()
        val violations = mutableListOf<String>()

        fun isGradleModule(dir: File): Boolean {
            return dir.isDirectory && (dir.resolve("build.gradle").exists() || dir.resolve("build.gradle.kts").exists())
        }

        fun checkSingleService(serviceRoot: File, serviceMode: HexagonalMode, labelPrefix: String = "") {
            val prefix = if (labelPrefix.isNotEmpty()) "$labelPrefix: " else ""

            // 1. contracts
            val contractsDir = if (serviceRoot.resolve("contracts").exists()) serviceRoot.resolve("contracts") else serviceRoot.resolve("contract")
            if (enforceContracts.get()) {
                if (!contractsDir.exists()) {
                    violations.add("${prefix}Missing API contracts directory 'contracts/' (or 'contract/'). API specifications (openapi, wsdl, proto, graphql) must reside here.")
                } else if (!contractsDir.isDirectory) {
                    violations.add("${prefix}'contracts' is not a directory.")
                }
            }

            // 2. wiremock
            if (enforceWiremock.get()) {
                val wiremockDir = serviceRoot.resolve("wiremock")
                if (!wiremockDir.exists()) {
                    violations.add("${prefix}Missing WireMock stubs directory 'wiremock/'. Expected service-level WireMock stubs ('mappings/' and '__files/').")
                } else if (!wiremockDir.isDirectory) {
                    violations.add("${prefix}'wiremock' is not a directory.")
                }
            }

            if (serviceMode == HexagonalMode.SINGLE_MODULE) {
                // Single-module checks
                val srcMain = serviceRoot.resolve("src/main")
                if (!srcMain.exists()) {
                    violations.add("${prefix}Missing 'src/main/' directory for single-module hexagonal service.")
                } else if (!srcMain.isDirectory) {
                    violations.add("${prefix}'src/main' is not a directory.")
                }
            } else {
                // Multi-module checks
                if (enforceContracts.get() && contractsAsModule.get() && contractsDir.exists() && contractsDir.isDirectory) {
                    if (!isGradleModule(contractsDir)) {
                        violations.add("${prefix}Directory '${contractsDir.name}/' is not configured as a Gradle module (missing build.gradle or build.gradle.kts). In multi-module mode, contracts must be its own Gradle module.")
                    }
                }

                if (enforceAppBoot.get()) {
                    val bootDir = serviceRoot.resolve("app/boot")
                    if (!bootDir.exists()) {
                        violations.add("${prefix}Missing required directory 'app/boot/'. Expected a Gradle module for Spring Boot application configuration.")
                    } else if (!bootDir.isDirectory) {
                        violations.add("${prefix}'app/boot' is not a directory.")
                    } else if (!isGradleModule(bootDir)) {
                        violations.add("${prefix}Directory 'app/boot/' is not configured as a Gradle module (missing build.gradle or build.gradle.kts).")
                    }
                }

                if (enforceCore.get()) {
                    val coreDir = serviceRoot.resolve("lib/core")
                    if (!coreDir.exists()) {
                        violations.add("${prefix}Missing required directory 'lib/core/'. Expected a Gradle module containing domain entities, use-cases, and port interfaces.")
                    } else if (!coreDir.isDirectory) {
                        violations.add("${prefix}'lib/core' is not a directory.")
                    } else if (!isGradleModule(coreDir)) {
                        violations.add("${prefix}Directory 'lib/core/' is not configured as a Gradle module (missing build.gradle or build.gradle.kts).")
                    }
                }

                if (enforceAdapters.get()) {
                    val adaptersInDir = serviceRoot.resolve("lib/adapters/in")
                    val adaptersOutDir = serviceRoot.resolve("lib/adapters/out")

                    if (serviceRoot.resolve("lib/adapters-in").exists()) {
                        violations.add("${prefix}Found 'lib/adapters-in/'. Inbound adapters must reside under 'lib/adapters/in/<adapter>/'.")
                    }
                    if (serviceRoot.resolve("lib/adapters-out").exists()) {
                        violations.add("${prefix}Found 'lib/adapters-out/'. Outbound adapters must reside under 'lib/adapters/out/<adapter>/'.")
                    }

                    if (!adaptersInDir.exists()) {
                        violations.add("${prefix}Missing inbound adapters directory 'lib/adapters/in/'. Expected inbound adapter Gradle modules (e.g. web, kafka-consumer).")
                    } else if (!adaptersInDir.isDirectory) {
                        violations.add("${prefix}'lib/adapters/in' is not a directory.")
                    } else {
                        val inModules = adaptersInDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
                        for (moduleDir in inModules) {
                            if (!isGradleModule(moduleDir)) {
                                violations.add("${prefix}Inbound adapter '${moduleDir.relativeTo(serviceRoot).invariantSeparatorsPath}' is not a valid Gradle module (missing build.gradle or build.gradle.kts).")
                            }
                        }
                        if (requireInboundAdapters.get() && inModules.isEmpty()) {
                            violations.add("${prefix}Directory 'lib/adapters/in/' must contain at least one inbound adapter Gradle module.")
                        }
                    }

                    if (!adaptersOutDir.exists()) {
                        violations.add("${prefix}Missing outbound adapters directory 'lib/adapters/out/'. Expected outbound adapter Gradle modules (e.g. db, client-rest).")
                    } else if (!adaptersOutDir.isDirectory) {
                        violations.add("${prefix}'lib/adapters/out' is not a directory.")
                    } else {
                        val outModules = adaptersOutDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
                        for (moduleDir in outModules) {
                            if (!isGradleModule(moduleDir)) {
                                violations.add("${prefix}Outbound adapter '${moduleDir.relativeTo(serviceRoot).invariantSeparatorsPath}' is not a valid Gradle module (missing build.gradle or build.gradle.kts).")
                            }
                        }
                        if (requireOutboundAdapters.get() && outModules.isEmpty()) {
                            violations.add("${prefix}Directory 'lib/adapters/out/' must contain at least one outbound adapter Gradle module.")
                        }
                    }

                    val libDir = serviceRoot.resolve("lib")
                    if (libDir.exists() && libDir.isDirectory) {
                        val extraneous = libDir.listFiles { f ->
                            f.isDirectory && f.name != "core" && f.name != "adapters" && !f.name.startsWith(".")
                        } ?: emptyArray()
                        for (extra in extraneous) {
                            violations.add("${prefix}Extraneous directory in 'lib/': '${extra.name}'. Only 'lib/core/' and 'lib/adapters/' are allowed.")
                        }
                    }
                }
            }
        }

        if (!isMonorepo) {
            checkSingleService(root, mode.get())
        } else {
            val servicesBase = if (servicesDir.isPresent) servicesDir.get().asFile else root.resolve("services")
            val sharedBase = if (sharedDir.isPresent) sharedDir.get().asFile else root.resolve("shared")

            // 1. Verify shared/
            if (enforceShared.get()) {
                if (!sharedBase.exists()) {
                    violations.add("Missing shared libraries directory '${sharedBase.relativeTo(root).invariantSeparatorsPath}/'. In a monorepo, shared library Gradle modules must reside under '${sharedBase.name}/<library>/'.")
                } else if (!sharedBase.isDirectory) {
                    violations.add("'${sharedBase.relativeTo(root).invariantSeparatorsPath}' is not a directory.")
                } else {
                    val sharedModules = sharedBase.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
                    for (libDir in sharedModules) {
                        if (!isGradleModule(libDir)) {
                            violations.add("Shared library '${libDir.relativeTo(root).invariantSeparatorsPath}' is not a valid Gradle module (missing build.gradle or build.gradle.kts).")
                        }
                    }
                }
            }

            // 2. Verify services/
            if (enforceServices.get()) {
                if (!servicesBase.exists()) {
                    violations.add("Missing services directory '${servicesBase.relativeTo(root).invariantSeparatorsPath}/'. In a monorepo, services must reside under '${servicesBase.name}/<service>/'.")
                } else if (!servicesBase.isDirectory) {
                    violations.add("'${servicesBase.relativeTo(root).invariantSeparatorsPath}' is not a directory.")
                } else {
                    val serviceDirs = servicesBase.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
                    if (serviceDirs.isEmpty()) {
                        violations.add("Services directory '${servicesBase.relativeTo(root).invariantSeparatorsPath}/' contains no services. Expected at least one service directory.")
                    } else {
                        for (sDir in serviceDirs) {
                            val sMode = serviceModes.getting(sDir.name).orNull ?: mode.get()
                            checkSingleService(sDir, sMode, "Service '${sDir.relativeTo(root).invariantSeparatorsPath}' ($sMode)")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val sb = StringBuilder()
            sb.appendLine("\n======================================================================")
            if (isMonorepo) {
                sb.appendLine("   HEXAGONAL MONOREPO STRUCTURE VIOLATION(S)")
                sb.appendLine("======================================================================")
                sb.appendLine("Expected structure for monorepo '${root.name}':")
                sb.appendLine("  ${root.name}/")
                sb.appendLine("  ├── services/")
                sb.appendLine("  │   ├── <service-1>/       (Hexagonal service: single- or multi-module)")
                sb.appendLine("  │   └── <service-2>/       (Hexagonal service: single- or multi-module)")
                sb.appendLine("  ├── shared/")
                sb.appendLine("  │   ├── <shared-lib-1>/    (Gradle module: contains build.gradle[.kts])")
                sb.appendLine("  │   └── <shared-lib-2>/    (Gradle module: contains build.gradle[.kts])")
                sb.appendLine("  └── wiremock/              (Optional root-level WireMock stubs)")
            } else if (mode.get() == HexagonalMode.SINGLE_MODULE) {
                sb.appendLine("   HEXAGONAL ARCHITECTURE STRUCTURE VIOLATION(S)")
                sb.appendLine("======================================================================")
                sb.appendLine("Expected structure for service '${root.name}' (Single-Module mode):")
                sb.appendLine("  ${root.name}/")
                sb.appendLine("  ├── src/main/                 (Domain logic, ports, and adapters organized by package)")
                sb.appendLine("  ├── contracts/                (API contracts: openapi, wsdl, proto, graphql)")
                sb.appendLine("  └── wiremock/                 (WireMock stubs: mappings/ and __files/)")
            } else {
                sb.appendLine("   HEXAGONAL ARCHITECTURE STRUCTURE VIOLATION(S)")
                sb.appendLine("======================================================================")
                sb.appendLine("Expected structure for service '${root.name}' (Multi-Module mode):")
                sb.appendLine("  ${root.name}/")
                sb.appendLine("  ├── app/")
                sb.appendLine("  │   └── boot/                 (Gradle module: main Spring Boot application)")
                sb.appendLine("  ├── contracts/                (Gradle module: API contracts & code generation)")
                sb.appendLine("  ├── lib/")
                sb.appendLine("  │   ├── core/                 (Gradle module: domain, ports, entities)")
                sb.appendLine("  │   └── adapters/")
                sb.appendLine("  │       ├── in/")
                sb.appendLine("  │       │   └── <adapter>     (Gradle module(s): inbound controllers/consumers)")
                sb.appendLine("  │       └── out/")
                sb.appendLine("  │           └── <adapter>     (Gradle module(s): outbound clients/persistence)")
                sb.appendLine("  └── wiremock/                 (WireMock stubs: mappings/ and __files/)")
            }
            sb.appendLine("----------------------------------------------------------------------")
            sb.appendLine("Violations found:")
            violations.forEach { v ->
                sb.appendLine("  ✖ $v")
            }
            sb.appendLine("======================================================================\n")

            val message = sb.toString()
            if (failOnViolation.get()) {
                System.err.println(message)
                val violationsSummary = violations.joinToString("\n") { " - $it" }
                throw GradleException("Hexagonal architecture structure verification failed with ${violations.size} violation(s):\n$violationsSummary\nRun './gradlew scaffoldHexagonalArchitecture' to generate missing scaffolding.")
            } else {
                logger.warn(message)
            }
        } else {
            val desc = if (isMonorepo) "monorepo mode" else "${mode.get()} mode"
            logger.lifecycle("Hexagonal architecture layout verified cleanly for '${root.name}' ($desc).")
        }
    }
}
