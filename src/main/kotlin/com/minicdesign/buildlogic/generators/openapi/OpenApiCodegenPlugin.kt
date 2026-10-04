package com.minicdesign.buildlogic.generators.openapi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.util.Locale

open class OpenApiCodegenPlugin : Plugin<Project> {

    companion object {
        val SUPPORTED_OPENAPI_EXTENSIONS = setOf("yaml", "yml", "json")
    }

    override fun apply(project: Project) {
        // Ensure java plugin is applied for source sets and compilation
        project.pluginManager.apply("java")

        val openApiSpecsContainer = project.objects.domainObjectContainer(OpenApiSpec::class.java) { specName ->
            project.objects.newInstance(OpenApiSpec::class.java, specName).apply {
                val defaultRoot = if (project.file("contract/openapi").exists()) "contract/openapi" else "contracts/openapi"
                specPath.convention("$defaultRoot/in")
                specFilename.convention("${project.name}.yml")
                apiPackage.convention("api")
                modelPackage.convention("model")
                isOutbound.convention(false)
                oauth2ScopeConfig.convention(false)
                oauth2ProviderId.convention("okta")
                oauth2ClientIdProperty.convention("\${oauth2.client-id}")
                oauth2ClientSecretProperty.convention("\${oauth2.client-secret}")
            }
        }

        val openApiSpecDirectoriesContainer = project.objects.domainObjectContainer(OpenApiSpecDirectory::class.java) { discoveryName ->
            project.objects.newInstance(OpenApiSpecDirectory::class.java, discoveryName).apply {
                val defaultOut = if (project.file("contract/openapi/out").exists()) "contract/openapi/out" else "contracts/openapi/out"
                specPath.convention(defaultOut)
                packagePrefix.convention("out")
                oauth2ScopeConfig.convention(false)
                oauth2ProviderId.convention("okta")
                oauth2ClientIdProperty.convention("\${oauth2.client-id}")
                oauth2ClientSecretProperty.convention("\${oauth2.client-secret}")
            }
        }

        // Register top-level containers for script compatibility
        project.extensions.add("openApiSpecs", openApiSpecsContainer)
        project.extensions.add("openApiSpecDirectories", openApiSpecDirectoriesContainer)

        val extension = project.extensions.create("openapiCodegen", OpenApiCodegenExtension::class.java).apply {
            openApiBasePackage.convention("com.minicdesign.generated.openapi")
            openApiVersion.convention("7.11.0")
            springBootVersion.convention(3)
            enableSourcePostProcessing.convention(true)
            oauth2ScopeConfig.convention(false)
            oauth2ProviderId.convention("okta")
            oauth2ClientIdProperty.convention("\${oauth2.client-id}")
            oauth2ClientSecretProperty.convention("\${oauth2.client-secret}")
        }

        // Convenience / legacy extension name aliases for backwards compatibility
        project.extensions.add("openApiCodegen", extension)
        project.extensions.add("openApiGeneration", extension)
        project.extensions.add("apiGeneration", extension)

        // Expose containers on extension as well
        val extObj = extension as? ExtensionAware
        extObj?.extensions?.add("specs", openApiSpecsContainer)
        extObj?.extensions?.add("specDirectories", openApiSpecDirectoriesContainer)

        val openapiConfig = project.configurations.create("openapiGenerator")

        // 1. Configure dependencies in afterEvaluate
        project.afterEvaluate {
            project.dependencies.add("openapiGenerator", "org.openapitools:openapi-generator-cli:${extension.openApiVersion.get()}")

            // Automatically provide common compilation annotations for generated code
            project.dependencies.add("implementation", "jakarta.annotation:jakarta.annotation-api:3.0.0")
            project.dependencies.add("implementation", "jakarta.validation:jakarta.validation-api:3.1.0")
            project.dependencies.add("implementation", "org.jspecify:jspecify:1.0.0")
            project.dependencies.add("implementation", "io.swagger.core.v3:swagger-annotations:2.2.28")
        }

        // Master aggregation tasks
        val openApiGenerateMaster = project.tasks.register("openApiGenerate") {
            group = "openapi"
            description = "Runs all OpenAPI code generation tasks."
        }

        // Compatibility aliases
        project.tasks.register("generateOpenApi") {
            group = "openapi"
            description = "Alias for openApiGenerate."
            dependsOn(openApiGenerateMaster)
        }

        project.tasks.register("openapiCodeGen") {
            group = "openapi"
            description = "Alias for openApiGenerate matching codegen conventions."
            dependsOn(openApiGenerateMaster)
        }

        project.tasks.register("generateApi") {
            group = "openapi"
            description = "Runs all OpenAPI code generation tasks."
            dependsOn(openApiGenerateMaster)
        }

        // 2. Discover and configure specs during afterEvaluate
        project.afterEvaluate {
            val openApiTasks = mutableListOf<TaskProvider<JavaExec>>()
            val resourceDirs = mutableListOf<File>()

            // ---------------------------------------------------------------------
            // A. Process Directory Discovery from openApiSpecDirectories container
            // ---------------------------------------------------------------------
            openApiSpecDirectoriesContainer.forEach { discovery ->
                val discoveryDir = project.file(discovery.specPath.get())
                if (discoveryDir.exists() && discoveryDir.isDirectory) {
                    discoveryDir.walkTopDown()
                        .filter { it.isFile && SUPPORTED_OPENAPI_EXTENSIONS.contains(it.extension.lowercase(Locale.ROOT)) }
                        .forEach { file ->
                            val relativePath = file.relativeTo(discoveryDir).invariantSeparatorsPath
                            val segments = relativePath.split('/').filter { it.isNotBlank() }
                            val filenameWithoutExt = file.nameWithoutExtension
                            val namespace = (segments.dropLast(1) + filenameWithoutExt).map(::sanitizePackageSegment)

                            val pkgPrefix = sanitizePackageSegment(discovery.packagePrefix.get())
                            val apiPkg = "$pkgPrefix.${namespace.joinToString(".")}"
                            val modelPkg = "$apiPkg.model"

                            val specName = "discovered_${discovery.name}_${namespace.joinToString("_")}"
                            if (openApiSpecsContainer.findByName(specName) == null) {
                                openApiSpecsContainer.create(specName).apply {
                                    specPath.set(file.parentFile.relativeTo(project.projectDir).invariantSeparatorsPath)
                                    specFilename.set(file.name)
                                    apiPackage.set(apiPkg)
                                    modelPackage.set(modelPkg)
                                    isOutbound.set(true)
                                    oauth2ScopeConfig.set(discovery.oauth2ScopeConfig.get())
                                    oauth2ProviderId.set(discovery.oauth2ProviderId.get())
                                    oauth2ClientIdProperty.set(discovery.oauth2ClientIdProperty.get())
                                    oauth2ClientSecretProperty.set(discovery.oauth2ClientSecretProperty.get())
                                }
                            }
                        }
                }
            }

            // ---------------------------------------------------------------------
            // B. Scan Convention Directories (contracts/openapi and contract/openapi)
            // ---------------------------------------------------------------------
            val candidateOpenApiRoots = listOf("contracts/openapi", "contract/openapi")
                .map { project.projectDir.resolve(it) }
                .filter { it.exists() && it.isDirectory }

            candidateOpenApiRoots.forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && SUPPORTED_OPENAPI_EXTENSIONS.contains(it.extension.lowercase(Locale.ROOT)) }
                    .forEach { file ->
                        val relPath = file.relativeTo(root).invariantSeparatorsPath
                        val isOutbound = relPath.contains("/out/") || relPath.startsWith("out/") ||
                                file.nameWithoutExtension.contains("out", ignoreCase = true) ||
                                file.nameWithoutExtension.contains("client", ignoreCase = true)

                        val sanitizedName = file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9]"), "")
                        val specName = if (relPath.contains('/')) {
                            relPath.replace('/', '_').substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_]"), "")
                        } else {
                            sanitizedName
                        }

                        if (openApiSpecsContainer.findByName(specName) == null) {
                            openApiSpecsContainer.create(specName).apply {
                                specPath.set(file.parentFile.relativeTo(project.projectDir).invariantSeparatorsPath)
                                specFilename.set(file.name)
                                this.isOutbound.set(isOutbound)
                                if (isOutbound) {
                                    val segments = relPath.split('/').filter { it.isNotBlank() }
                                    val subPkg = segments.dropLast(1).joinToString(".") { sanitizePackageSegment(it) }
                                    val filePkg = sanitizePackageSegment(file.nameWithoutExtension)
                                    val fullSub = if (subPkg.isNotEmpty()) "$subPkg.$filePkg" else filePkg
                                    apiPackage.set("out.$fullSub")
                                    modelPackage.set("out.$fullSub.model")
                                } else {
                                    apiPackage.set("$sanitizedName.api")
                                    modelPackage.set("$sanitizedName.model")
                                }
                            }
                        }
                    }
            }

            // ---------------------------------------------------------------------
            // C. Register OpenAPI Code Generation Tasks for each Spec
            // ---------------------------------------------------------------------
            val basePkg = extension.openApiBasePackage.get()
            val bootVersion = extension.springBootVersion.get()
            val postProcess = extension.enableSourcePostProcessing.get()

            openApiSpecsContainer.forEach { spec ->
                val specDirFile = project.projectDir.resolve(spec.specPath.get())
                val specFile = specDirFile.resolve(spec.specFilename.get())
                if (!specFile.exists()) return@forEach

                val cleanSpecName = spec.name.split('_', '-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
                val taskName = "generateOpenApi$cleanSpecName"
                val outputDir = project.layout.buildDirectory.dir("generated/sources/openapi/java/${spec.name}")

                val effectiveApiPkg = resolvePackage(basePkg, spec.apiPackage.get())
                val effectiveModelPkg = resolvePackage(basePkg, spec.modelPackage.get())
                val isOutbound = spec.isOutbound.get() ||
                        spec.specPath.get().contains("/out") ||
                        spec.specPath.get().endsWith("/out") ||
                        spec.apiPackage.get().contains(".out.") ||
                        spec.apiPackage.get().startsWith("out.")

                val task = project.tasks.register(taskName, JavaExec::class.java) {
                    group = "openapi"
                    description = "Generates Java sources for OpenAPI spec: ${spec.name}"
                    classpath = openapiConfig
                    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")

                    inputs.file(specFile).withPropertyName("specFile")
                    outputs.dir(outputDir).withPropertyName("outputDir")

                    val baseArgs = mutableListOf(
                        "generate",
                        "-i", specFile.absolutePath,
                        "-o", outputDir.get().asFile.absolutePath,
                        "-g", "spring",
                        "--library", "spring-boot",
                        "--api-package", effectiveApiPkg,
                        "--model-package", effectiveModelPkg,
                        "--skip-validate-spec"
                    )

                    val addProps = mutableListOf(
                        "useSpringBoot3=true",
                        "openApiNullable=false",
                        "documentationProvider=none",
                        "annotationLibrary=swagger2",
                        "useTags=true"
                    )

                    if (isOutbound) {
                        baseArgs.addAll(listOf("--global-property", "models,modelDocs=false,supportingFiles=false"))
                        addProps.addAll(listOf(
                            "generateApis=false",
                            "generateModels=true",
                            "generateSupportingFiles=false"
                        ))
                    } else {
                        addProps.addAll(listOf(
                            "interfaceOnly=true",
                            "skipDefaultInterface=true",
                            "useBeanValidation=true",
                            "performBeanValidation=true"
                        ))
                    }

                    baseArgs.add("--additional-properties")
                    baseArgs.add(addProps.joinToString(","))

                    argumentProviders.add(CommandLineArgumentProvider { baseArgs })

                    if (postProcess) {
                        doLast {
                            val javaRoot = outputDir.get().asFile.resolve("src/main/java")
                            OpenApiSourcePostProcessor.processJavaFiles(javaRoot, effectiveModelPkg)
                        }
                    }
                }

                openApiTasks.add(task)
                openApiGenerateMaster.configure { dependsOn(task) }

                // ---------------------------------------------------------------------
                // D. OAuth2 Scope Extraction & Config Generation (Outbound Specs)
                // ---------------------------------------------------------------------
                val shouldGenerateOAuth = spec.oauth2ScopeConfig.get() ||
                        (extension.oauth2ScopeConfig.get() && isOutbound)

                if (shouldGenerateOAuth && isOutbound) {
                    val oauthOutputDir = project.layout.buildDirectory.dir("generated/resources/openapi/oauth2/${spec.name}")
                    val oauthTaskName = "generateOAuth2Config$cleanSpecName"

                    val oauthTask = project.tasks.register(oauthTaskName) {
                        group = "openapi"
                        description = "Extracts OAuth2 scopes and generates Spring Security config for: ${spec.name}"
                        inputs.file(specFile).withPropertyName("specFile")
                        outputs.dir(oauthOutputDir).withPropertyName("oauthOutputDir")

                        doLast {
                            val catalogName = spec.name.removePrefix("discovered_").replace(Regex("[^A-Za-z0-9]"), "")
                            val rawYaml = specFile.readText()
                            val catalog = OpenApiScopeExtractor.extract(rawYaml)

                            val pId = if (spec.oauth2ProviderId.isPresent) spec.oauth2ProviderId.get() else extension.oauth2ProviderId.get()
                            val cIdProp = if (spec.oauth2ClientIdProperty.isPresent) spec.oauth2ClientIdProperty.get() else extension.oauth2ClientIdProperty.get()
                            val cSecProp = if (spec.oauth2ClientSecretProperty.isPresent) spec.oauth2ClientSecretProperty.get() else extension.oauth2ClientSecretProperty.get()

                            val rendered = OAuth2ScopeConfigWriter.render(
                                catalogName = catalogName,
                                catalog = catalog,
                                providerId = pId,
                                clientIdProperty = cIdProp,
                                clientSecretProperty = cSecProp,
                                sourceDescription = specFile.relativeTo(project.projectDir).invariantSeparatorsPath
                            )

                            val targetFile = oauthOutputDir.get().asFile.resolve("application-oauth2-catalogs.yml")
                            targetFile.parentFile.mkdirs()
                            targetFile.writeText(rendered)
                        }
                    }

                    resourceDirs.add(oauthOutputDir.get().asFile)
                    project.tasks.matching { it.name == "processResources" }.configureEach { dependsOn(oauthTask) }
                }
            }

            // ---------------------------------------------------------------------
            // E. Wire Output Directories into Java SourceSets and IntelliJ IDEA
            // ---------------------------------------------------------------------
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")

                for (taskProvider in openApiTasks) {
                    val javaSourceDir = taskProvider.map { it.outputs.files.singleFile.resolve("src/main/java") }
                    mainSourceSet.java.srcDir(javaSourceDir)
                }

                if (resourceDirs.isNotEmpty()) {
                    mainSourceSet.resources.srcDir(resourceDirs)
                }

                project.tasks.matching { it.name == "compileJava" }.configureEach { dependsOn(openApiGenerateMaster) }
                project.tasks.matching { it.name == "compileKotlin" }.configureEach { dependsOn(openApiGenerateMaster) }
            }

            // IntelliJ IDEA configuration
            project.plugins.withType(IdeaPlugin::class.java) {
                val ideaModel = project.extensions.getByType(IdeaModel::class.java)

                for (taskProvider in openApiTasks) {
                    val javaSourceDir = taskProvider.get().outputs.files.singleFile.resolve("src/main/java")
                    ideaModel.module.generatedSourceDirs.add(javaSourceDir)
                    ideaModel.module.excludeDirs.remove(javaSourceDir)
                }

                for (resDir in resourceDirs) {
                    ideaModel.module.resourceDirs.add(resDir)
                }
            }
        }
    }

    private fun resolvePackage(basePackage: String, relativeOrFullPackage: String): String {
        return if (relativeOrFullPackage.startsWith(basePackage)) {
            relativeOrFullPackage
        } else {
            "$basePackage.$relativeOrFullPackage".trimEnd('.')
        }
    }

    private fun sanitizePackageSegment(segment: String): String {
        return segment
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase(Locale.ROOT)
    }
}
