package com.minicdesign.buildlogic.hexagonal

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Scaffolds directory structure on demand")
abstract class ScaffoldHexagonalArchitectureTask : DefaultTask() {

    @get:Input
    abstract val mode: Property<HexagonalMode>

    @get:Input
    abstract val monorepo: Property<Boolean>

    @get:Input
    abstract val basePackage: Property<String>

    @get:Internal
    abstract val serviceDir: DirectoryProperty

    @get:Internal
    abstract val servicesDir: DirectoryProperty

    @get:Internal
    abstract val sharedDir: DirectoryProperty

    @get:Internal
    abstract val serviceModes: MapProperty<String, HexagonalMode>

    /**
     * Target type to scaffold: 'service', 'shared', 'adapter', or 'all'.
     * Can be set directly on the task or passed via -Ptarget=<type>.
     */
    @get:Optional
    @get:Input
    abstract val scaffoldType: Property<String>

    /**
     * Target module name. Can be passed via -Pname=<name> or -PmoduleName=<name>.
     */
    @get:Optional
    @get:Input
    abstract val targetModuleName: Property<String>

    /**
     * Base package for the target module. Can be passed via -Ppackage=<package> or -PbasePackage=<package>.
     */
    @get:Optional
    @get:Input
    abstract val targetBasePackage: Property<String>

    /**
     * Comma-separated list of contract types (e.g. "openapi, grpc").
     * Can be passed via -PcontractTypes=<types> or -Pcontracts=<types>.
     */
    @get:Optional
    @get:Input
    abstract val targetContractTypes: Property<String>

    /**
     * Output package for OpenAPI generator. Can be passed via -PopenapiPackage=<package>.
     */
    @get:Optional
    @get:Input
    abstract val targetOpenApiPackage: Property<String>

    /**
     * Output package for WSDL / CXF generator. Can be passed via -PwsdlPackage=<package>.
     */
    @get:Optional
    @get:Input
    abstract val targetWsdlPackage: Property<String>

    /**
     * Output package for GraphQL generator. Can be passed via -PgraphqlPackage=<package>.
     */
    @get:Optional
    @get:Input
    abstract val targetGraphqlPackage: Property<String>

    /**
     * Adapter direction ('in' or 'out') when scaffolding an adapter.
     * Can be passed via -PadapterType=<in|out>.
     */
    @get:Optional
    @get:Input
    abstract val targetAdapterType: Property<String>

    companion object {
        val VALID_CONTRACT_TYPES = listOf("openapi", "wsdl", "grpc", "graphql")

        fun sanitize(name: String): String {
            return name.lowercase().replace(Regex("[^a-z0-9]"), "")
        }

        fun parseContractTypes(input: String?): List<String> {
            if (input.isNullOrBlank()) {
                return listOf("openapi")
            }
            val valid = input.split(",")
                .map { it.trim().lowercase() }
                .filter { it in VALID_CONTRACT_TYPES }
                .distinct()
            return if (valid.isEmpty()) listOf("openapi") else valid
        }
    }

    @TaskAction
    fun scaffold() {
        val root = serviceDir.get().asFile
        val isMonorepo = monorepo.get()
        val defaultProjectBasePackage = basePackage.get().takeIf { it.isNotBlank() } ?: "com.example"

        val console = System.console()

        // Helper to prompt interactively or read from Gradle properties / defaults
        fun prompt(propKey: String, directProp: Property<String>?, promptLabel: String, defaultVal: String): String {
            if (directProp != null && directProp.isPresent && directProp.get().isNotBlank()) {
                return directProp.get().trim()
            }
            val propKeys = when (propKey) {
                "name" -> listOf("moduleName", "serviceName", "sharedName", "adapterName")
                "package" -> listOf("basePackage", "packageName")
                "contracts" -> listOf("contractTypes", "contracts")
                "openapiPackage" -> listOf("openapiPackage", "openApiPackage")
                "wsdlPackage" -> listOf("wsdlPackage")
                "graphqlPackage" -> listOf("graphqlPackage", "graphQLPackage")
                else -> listOf(propKey)
            }
            for (key in propKeys) {
                val gradleProp = (project.findProperty(key) as? String)?.trim()
                if (!gradleProp.isNullOrEmpty()) {
                    return gradleProp
                }
            }
            if (propKey == "name" && project.extensions.extraProperties.has("name")) {
                val p = project.extensions.extraProperties.get("name")?.toString()?.trim()
                if (!p.isNullOrEmpty()) return p
            }
            if (console != null) {
                val input = console.readLine("$promptLabel [$defaultVal]: ")?.trim()
                if (!input.isNullOrEmpty()) {
                    return input
                }
            }
            return defaultVal
        }

        fun promptGeneratorPackages(
            contractTypes: List<String>,
            modBasePackage: String
        ): Triple<String?, String?, String?> {
            val openApiPkg = if ("openapi" in contractTypes) {
                prompt("openapiPackage", targetOpenApiPackage, "Enter output package for OpenAPI generator", "$modBasePackage.api")
            } else null

            val wsdlPkg = if ("wsdl" in contractTypes) {
                prompt("wsdlPackage", targetWsdlPackage, "Enter output package for WSDL generator", "$modBasePackage.soap")
            } else null

            val graphqlPkg = if ("graphql" in contractTypes) {
                prompt("graphqlPackage", targetGraphqlPackage, "Enter output package for GraphQL generator", "$modBasePackage.graphql")
            } else null

            return Triple(openApiPkg, wsdlPkg, graphqlPkg)
        }

        fun ensureDir(dir: File): File {
            if (!dir.exists()) {
                dir.mkdirs()
                logger.lifecycle("Created directory: ${dir.relativeTo(root).invariantSeparatorsPath}")
            }
            return dir
        }

        fun ensureFile(file: File, content: String) {
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.writeText(content)
                logger.lifecycle("Created file: ${file.relativeTo(root).invariantSeparatorsPath}")
            }
        }

        fun scaffoldContracts(contractsDir: File, types: List<String>, modBasePackage: String) {
            for (type in types) {
                ensureDir(contractsDir.resolve("$type/in"))
                ensureDir(contractsDir.resolve("$type/out"))
                ensureFile(contractsDir.resolve("$type/in/.gitkeep"), "")
                ensureFile(contractsDir.resolve("$type/out/.gitkeep"), "")

                if (type == "grpc") {
                    ensureFile(
                        contractsDir.resolve("grpc/in/sample.proto"),
                        """
                        syntax = "proto3";

                        // NOTE for gRPC: The output package is configured directly in this .proto file.
                        // Alter the option below to customize your generated Java package:
                        option java_multiple_files = true;
                        option java_package = "$modBasePackage.grpc";

                        package sample;

                        service SampleService {
                          rpc Ping (PingRequest) returns (PingResponse);
                        }

                        message PingRequest {
                          string message = 1;
                        }

                        message PingResponse {
                          string message = 1;
                        }
                        """.trimIndent() + "\n"
                    )
                }
            }
        }

        fun generateContractsBuildScript(
            contractTypes: List<String>,
            openApiPackage: String?,
            wsdlPackage: String?,
            graphqlPackage: String?
        ): String {
            val plugins = buildString {
                appendLine("plugins {")
                appendLine("    id(\"com.minicdesign.java-conventions\")")
                if ("openapi" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.openapi-codegen\")")
                }
                if ("wsdl" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.cxf-codegen\")")
                }
                if ("grpc" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.grpc-codegen\")")
                }
                if ("graphql" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.graphql-codegen\")")
                }
                appendLine("}")
            }

            val configs = buildString {
                if ("openapi" in contractTypes && !openApiPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("openapiCodegen {")
                    appendLine("    openApiBasePackage.set(\"$openApiPackage\")")
                    appendLine("}")
                }
                if ("wsdl" in contractTypes && !wsdlPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("cxfCodegen {")
                    appendLine("    wsdlBasePackage.set(\"$wsdlPackage\")")
                    appendLine("}")
                }
                if ("graphql" in contractTypes && !graphqlPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("graphqlCodegen {")
                    appendLine("    packageName.set(\"$graphqlPackage\")")
                    appendLine("}")
                }
                if ("grpc" in contractTypes) {
                    appendLine()
                    appendLine("// NOTE for gRPC: The output package is configured directly in your .proto file(s)")
                    appendLine("// using: option java_package = \"com.yourcompany.package\";")
                    appendLine("// Check and alter your .proto file(s) under contracts/proto/ to configure the generated output package.")
                }
            }

            return """
            $plugins
            $configs
            dependencies {
                // API contract generator dependencies
            }
            """.trimIndent() + "\n"
        }

        fun generateSingleModuleBuildScript(
            contractTypes: List<String>,
            openApiPackage: String?,
            wsdlPackage: String?,
            graphqlPackage: String?
        ): String {
            val plugins = buildString {
                appendLine("plugins {")
                appendLine("    id(\"com.minicdesign.spring-service\")")
                if ("openapi" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.openapi-codegen\")")
                }
                if ("wsdl" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.cxf-codegen\")")
                }
                if ("grpc" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.grpc-codegen\")")
                }
                if ("graphql" in contractTypes) {
                    appendLine("    id(\"com.minicdesign.graphql-codegen\")")
                }
                appendLine("}")
            }

            val configs = buildString {
                if ("openapi" in contractTypes && !openApiPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("openapiCodegen {")
                    appendLine("    openApiBasePackage.set(\"$openApiPackage\")")
                    appendLine("}")
                }
                if ("wsdl" in contractTypes && !wsdlPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("cxfCodegen {")
                    appendLine("    wsdlBasePackage.set(\"$wsdlPackage\")")
                    appendLine("}")
                }
                if ("graphql" in contractTypes && !graphqlPackage.isNullOrBlank()) {
                    appendLine()
                    appendLine("graphqlCodegen {")
                    appendLine("    packageName.set(\"$graphqlPackage\")")
                    appendLine("}")
                }
                if ("grpc" in contractTypes) {
                    appendLine()
                    appendLine("// NOTE for gRPC: The output package is configured directly in your .proto file(s)")
                    appendLine("// using: option java_package = \"com.yourcompany.package\";")
                    appendLine("// Check and alter your .proto file(s) under contracts/proto/ to configure the generated output package.")
                }
            }

            return """
            $plugins
            $configs
            dependencies {
                // Service dependencies
            }
            """.trimIndent() + "\n"
        }

        fun scaffoldServiceDirectory(
            serviceRoot: File,
            serviceMode: HexagonalMode,
            modBasePackage: String,
            contractTypes: List<String>,
            openApiPackage: String?,
            wsdlPackage: String?,
            graphqlPackage: String?
        ) {
            val pkgPath = modBasePackage.replace('.', '/')

            // 1. Contracts
            val contractsDir = serviceRoot.resolve("contracts")
            scaffoldContracts(contractsDir, contractTypes, modBasePackage)

            // 2. Wiremock
            ensureDir(serviceRoot.resolve("wiremock/mappings"))
            ensureDir(serviceRoot.resolve("wiremock/__files"))
            ensureFile(serviceRoot.resolve("wiremock/mappings/.gitkeep"), "")
            ensureFile(serviceRoot.resolve("wiremock/__files/.gitkeep"), "")

            if (serviceMode == HexagonalMode.SINGLE_MODULE) {
                ensureDir(serviceRoot.resolve("src/main/java/$pkgPath"))
                ensureDir(serviceRoot.resolve("src/main/resources"))
                ensureDir(serviceRoot.resolve("src/test/java/$pkgPath"))
                ensureDir(serviceRoot.resolve("src/testIntegration/java/$pkgPath"))
                ensureDir(serviceRoot.resolve("src/testIntegration/resources"))
                ensureFile(serviceRoot.resolve("src/main/java/$pkgPath/.gitkeep"), "")
                ensureFile(serviceRoot.resolve("src/main/resources/.gitkeep"), "")
                ensureFile(serviceRoot.resolve("src/testIntegration/resources/.gitkeep"), "")

                // Starter build.gradle.kts for single-module service
                ensureFile(
                    serviceRoot.resolve("build.gradle.kts"),
                    generateSingleModuleBuildScript(contractTypes, openApiPackage, wsdlPackage, graphqlPackage)
                )
            } else {
                // Multi-module:
                // contracts module
                ensureFile(
                    contractsDir.resolve("build.gradle.kts"),
                    generateContractsBuildScript(contractTypes, openApiPackage, wsdlPackage, graphqlPackage)
                )

                // app/boot module
                ensureDir(serviceRoot.resolve("app/boot/src/main/java/$pkgPath/boot"))
                ensureDir(serviceRoot.resolve("app/boot/src/main/resources"))
                ensureDir(serviceRoot.resolve("app/boot/src/testIntegration/java/$pkgPath/boot"))
                ensureDir(serviceRoot.resolve("app/boot/src/testIntegration/resources"))
                ensureFile(serviceRoot.resolve("app/boot/src/main/java/$pkgPath/boot/.gitkeep"), "")
                ensureFile(
                    serviceRoot.resolve("app/boot/build.gradle.kts"),
                    """
                    plugins {
                        id("com.minicdesign.spring-service")
                    }

                    dependencies {
                        implementation(project(":lib:core"))
                        implementation(project(":contracts"))
                        // Add adapter module dependencies as needed
                    }
                    """.trimIndent() + "\n"
                )

                // lib/core module
                ensureDir(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/ports/in"))
                ensureDir(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/ports/out"))
                ensureDir(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/internal"))
                ensureDir(serviceRoot.resolve("lib/core/src/test/java/$pkgPath/core"))
                ensureFile(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/ports/in/.gitkeep"), "")
                ensureFile(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/ports/out/.gitkeep"), "")
                ensureFile(serviceRoot.resolve("lib/core/src/main/java/$pkgPath/core/internal/.gitkeep"), "")
                ensureFile(
                    serviceRoot.resolve("lib/core/build.gradle.kts"),
                    """
                    plugins {
                        id("com.minicdesign.java-conventions")
                        id("com.minicdesign.archunit")
                    }

                    archUnitConventions {
                        // Enforces that classes in ..internal.. packages cannot be accessed from outside
                        restrictInternalPackages.set(true)
                    }

                    dependencies {
                        // Pure domain logic: no framework dependencies
                    }
                    """.trimIndent() + "\n"
                )

                // lib/adapters/in and out
                ensureDir(serviceRoot.resolve("lib/adapters/in"))
                ensureDir(serviceRoot.resolve("lib/adapters/out"))
                ensureFile(serviceRoot.resolve("lib/adapters/in/.gitkeep"), "")
                ensureFile(serviceRoot.resolve("lib/adapters/out/.gitkeep"), "")
            }
        }

        fun scaffoldSharedModule(libRoot: File, modBasePackage: String, contractTypes: List<String>) {
            val pkgPath = modBasePackage.replace('.', '/')
            ensureDir(libRoot.resolve("src/main/java/$pkgPath"))
            ensureDir(libRoot.resolve("src/main/resources"))
            ensureDir(libRoot.resolve("src/test/java/$pkgPath"))
            ensureFile(libRoot.resolve("src/main/java/$pkgPath/.gitkeep"), "")
            ensureFile(libRoot.resolve("src/main/resources/.gitkeep"), "")
            ensureFile(
                libRoot.resolve("build.gradle.kts"),
                """
                plugins {
                    id("com.minicdesign.java-conventions")
                }

                // For standalone Java libraries distributing JPMS module-info.java:
                // javaConventions {
                //     modular.set(true)
                // }

                dependencies {
                    // Shared library dependencies
                }
                """.trimIndent() + "\n"
            )

            if (contractTypes.isNotEmpty() && contractTypes != listOf("openapi")) {
                scaffoldContracts(libRoot.resolve("contracts"), contractTypes, modBasePackage)
            }
        }

        fun scaffoldAdapterModule(
            adapterRoot: File,
            direction: String,
            modBasePackage: String,
            contractTypes: List<String>,
            openApiPackage: String?,
            wsdlPackage: String?,
            graphqlPackage: String?
        ) {
            val pkgPath = modBasePackage.replace('.', '/')
            ensureDir(adapterRoot.resolve("src/main/java/$pkgPath"))
            ensureDir(adapterRoot.resolve("src/main/resources"))
            ensureDir(adapterRoot.resolve("src/test/java/$pkgPath"))
            ensureDir(adapterRoot.resolve("src/testIntegration/java/$pkgPath"))
            ensureDir(adapterRoot.resolve("src/testIntegration/resources"))
            ensureFile(adapterRoot.resolve("src/main/java/$pkgPath/.gitkeep"), "")
            ensureFile(
                adapterRoot.resolve("build.gradle.kts"),
                """
                plugins {
                    id("com.minicdesign.java-conventions")
                }

                dependencies {
                    implementation(project(":lib:core"))
                    // implementation(project(":contracts"))
                }
                """.trimIndent() + "\n"
            )
        }

        // Determine what to scaffold
        val targetArg = when {
            scaffoldType.isPresent -> scaffoldType.get()
            project.hasProperty("target") -> project.findProperty("target") as String
            project.hasProperty("type") -> project.findProperty("type") as String
            project.hasProperty("service") || project.hasProperty("serviceName") -> "service"
            project.hasProperty("shared") || project.hasProperty("sharedLib") -> "shared"
            project.hasProperty("adapter") || project.hasProperty("adapterName") -> "adapter"
            else -> null
        }

        val servicesBase = if (servicesDir.isPresent) servicesDir.get().asFile else root.resolve("services")
        val sharedBase = if (sharedDir.isPresent) sharedDir.get().asFile else root.resolve("shared")

        if (isMonorepo) {
            ensureDir(servicesBase)
            ensureDir(sharedBase)

            val effectiveTarget = targetArg ?: if (console != null) {
                prompt("target", null, "What would you like to scaffold? (service / shared / adapter / all)", "all")
            } else {
                "all"
            }

            when (effectiveTarget.lowercase()) {
                "service" -> {
                    val defaultName = (project.findProperty("service") as? String) ?: "order-service"
                    val modName = prompt("name", targetModuleName, "Enter name of service module", defaultName)
                    val defaultPkg = "$defaultProjectBasePackage.services.${sanitize(modName)}"
                    val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                    val rawContracts = prompt(
                        "contracts",
                        targetContractTypes,
                        "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                        "openapi"
                    )
                    val contractTypes = parseContractTypes(rawContracts)
                    val (openApiPkg, wsdlPkg, graphqlPkg) = promptGeneratorPackages(contractTypes, modPkg)

                    val sMode = serviceModes.getting(modName).orNull ?: mode.get()
                    val sDir = servicesBase.resolve(modName)

                    scaffoldServiceDirectory(sDir, sMode, modPkg, contractTypes, openApiPkg, wsdlPkg, graphqlPkg)
                    logger.lifecycle("Scaffolded service '${sDir.relativeTo(root).invariantSeparatorsPath}' ($sMode mode, package: $modPkg, contracts: $contractTypes).")
                }
                "shared", "lib", "shared-lib" -> {
                    val defaultName = (project.findProperty("shared") as? String) ?: "common-utils"
                    val modName = prompt("name", targetModuleName, "Enter name of shared library module", defaultName)
                    val defaultPkg = "$defaultProjectBasePackage.shared.${sanitize(modName)}"
                    val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                    val rawContracts = prompt(
                        "contracts",
                        targetContractTypes,
                        "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                        "openapi"
                    )
                    val contractTypes = parseContractTypes(rawContracts)
                    val libDir = sharedBase.resolve(modName)

                    scaffoldSharedModule(libDir, modPkg, contractTypes)
                    logger.lifecycle("Scaffolded shared library '${libDir.relativeTo(root).invariantSeparatorsPath}' (package: $modPkg).")
                }
                "adapter" -> {
                    val defaultServiceName = servicesBase.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                        ?.firstOrNull()?.name ?: "sample-service"
                    val parentService = prompt("service", null, "Enter parent service for adapter", defaultServiceName)
                    val sDir = servicesBase.resolve(parentService)
                    val direction = prompt("adapterType", targetAdapterType, "Enter adapter direction (in / out)", "in").lowercase()
                    val defaultName = if (direction == "in") "web" else "db"
                    val modName = prompt("name", targetModuleName, "Enter name of adapter module", defaultName)
                    val defaultPkg = "$defaultProjectBasePackage.services.${sanitize(parentService)}.adapters.${sanitize(direction)}.${sanitize(modName)}"
                    val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                    val rawContracts = prompt(
                        "contracts",
                        targetContractTypes,
                        "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                        "openapi"
                    )
                    val contractTypes = parseContractTypes(rawContracts)
                    val (openApiPkg, wsdlPkg, graphqlPkg) = promptGeneratorPackages(contractTypes, modPkg)

                    val adapterDir = sDir.resolve("lib/adapters/$direction/$modName")
                    scaffoldAdapterModule(adapterDir, direction, modPkg, contractTypes, openApiPkg, wsdlPkg, graphqlPkg)
                    logger.lifecycle("Scaffolded adapter '${adapterDir.relativeTo(root).invariantSeparatorsPath}' (package: $modPkg).")
                }
                else -> {
                    // 'all' / default bootstrap
                    val defaultName = "sample-service"
                    val modName = prompt("name", targetModuleName, "Enter name of initial service module", defaultName)
                    val defaultPkg = "$defaultProjectBasePackage.services.${sanitize(modName)}"
                    val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                    val rawContracts = prompt(
                        "contracts",
                        targetContractTypes,
                        "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                        "openapi"
                    )
                    val contractTypes = parseContractTypes(rawContracts)
                    val (openApiPkg, wsdlPkg, graphqlPkg) = promptGeneratorPackages(contractTypes, modPkg)

                    val sMode = serviceModes.getting(modName).orNull ?: mode.get()
                    val sDir = servicesBase.resolve(modName)
                    scaffoldServiceDirectory(sDir, sMode, modPkg, contractTypes, openApiPkg, wsdlPkg, graphqlPkg)

                    // Also scaffold starter shared lib
                    val sharedDir = sharedBase.resolve("common")
                    scaffoldSharedModule(sharedDir, "$defaultProjectBasePackage.shared.common", emptyList())
                    logger.lifecycle("Hexagonal architecture monorepo scaffolding completed for '${root.name}'.")
                }
            }
        } else {
            // Standalone service mode
            val effectiveTarget = targetArg ?: if (mode.get() == HexagonalMode.MULTI_MODULE && console != null) {
                prompt("target", null, "What would you like to scaffold? (service / adapter)", "service")
            } else {
                "service"
            }

            if (effectiveTarget.lowercase() == "adapter") {
                val direction = prompt("adapterType", targetAdapterType, "Enter adapter direction (in / out)", "in").lowercase()
                val defaultName = if (direction == "in") "web" else "db"
                val modName = prompt("name", targetModuleName, "Enter name of adapter module", defaultName)
                val defaultPkg = "$defaultProjectBasePackage.adapters.${sanitize(direction)}.${sanitize(modName)}"
                val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                val rawContracts = prompt(
                    "contracts",
                    targetContractTypes,
                    "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                    "openapi"
                )
                val contractTypes = parseContractTypes(rawContracts)
                val (openApiPkg, wsdlPkg, graphqlPkg) = promptGeneratorPackages(contractTypes, modPkg)

                val adapterDir = root.resolve("lib/adapters/$direction/$modName")
                scaffoldAdapterModule(adapterDir, direction, modPkg, contractTypes, openApiPkg, wsdlPkg, graphqlPkg)
                logger.lifecycle("Scaffolded adapter '${adapterDir.relativeTo(root).invariantSeparatorsPath}' (package: $modPkg).")
            } else {
                val defaultName = root.name
                val modName = prompt("name", targetModuleName, "Enter name of module", defaultName)
                val defaultPkg = "$defaultProjectBasePackage.${sanitize(modName)}"
                val modPkg = prompt("package", targetBasePackage, "Enter base package of the module", defaultPkg)
                val rawContracts = prompt(
                    "contracts",
                    targetContractTypes,
                    "Enter comma-separated contract types (options: openapi, wsdl, grpc, graphql)",
                    "openapi"
                )
                val contractTypes = parseContractTypes(rawContracts)
                val (openApiPkg, wsdlPkg, graphqlPkg) = promptGeneratorPackages(contractTypes, modPkg)

                scaffoldServiceDirectory(root, mode.get(), modPkg, contractTypes, openApiPkg, wsdlPkg, graphqlPkg)
                logger.lifecycle("Hexagonal architecture scaffolding completed for '${root.name}' (${mode.get()} mode, package: $modPkg, contracts: $contractTypes).")
            }
        }
    }
}
