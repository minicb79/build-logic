plugins {
    alias(libs.plugins.openapi.generator)
    java
}

dependencies {
    implementation(libs.jakarta.annotation)
    implementation(libs.hibernate.validator)
}

// ---------------------------------------------------------------------------
// Extension — named container of spec configurations
// ---------------------------------------------------------------------------

/**
 * Per-spec configuration for OpenAPI code generation.
 *
 * Each explicit `openApiSpecs` entry and each discovered file gets its own generation task.
 * The `"inbound"` entry configures the plugin's `openApiGenerate` task; other entries get
 * `openApiGenerate<Name>` tasks. All are wired into `compileJava` and `processResources`,
 * and invoking `openApiGenerate` also runs the other generation tasks.
 *
 * **Server/model-only selection:**
 * - `"inbound"` and other specs without an outbound path/package generate Spring server interfaces.
 * - Non-inbound specs whose path contains `/out` or `.out`, or whose API package contains `.out.`,
 *   generate models only (no client/API classes or supporting files).
 *
 * **Outbound Specs (Models Only):**
 * - Generate model classes (DTOs) for outbound adapters, not adapter or mapper implementations.
 * - Write the adapter yourself using RestClient and the generated models.
 *
 * Configure in the consuming project's build.gradle.kts:
 *
 * ```kotlin
 * openApiSpecs {
 *     // Optional: the inbound spec is also auto-registered when the default file exists,
 *     // even if outbound specs are configured.
 *     create("inbound") {
 *         specPath     = "contracts/openapi/in"   // optional, defaults shown
 *         specFilename = "payment.yml"             // optional; defaults to an existing <project.name>.yml/.yaml/.json
 *         // apiPackage and modelPackage use project-level defaults if omitted
 *     }
 * }
 *
 * // Opt in to recursively discovering .yml, .yaml and .json specs. Directory segments and the
 * // filename become lowercase snake_case package segments below packagePrefix; models go in .model.
 * openApiSpecDirectories {
 *     create("outboundContracts") {
 *         specPath = "contracts/openapi/out"
 *         packagePrefix = "infrastructure.out"
 *     }
 * }
 * ```
 *
 * Explicit file entries remain available and take precedence over discovery of the same file.
 * When no inbound file is present, the built-in `openApiGenerate` action is skipped, but its
 * dependencies still generate all configured or discovered specs.
 *
 * Package resolution (per spec):
 *   - If `apiPackage` is set → uses `<rootPackage>.<projectName>.<apiPackage>`
 *   - If `apiPackage` is not set → uses project-level `apiPackage` property (default: "infrastructure.in.rest")
 *   - Same logic applies to `modelPackage` (default: "infrastructure.in.rest.model")
 *
 * Defaults per entry:
 *   specPath     → `<openapi.generator.root.dir>/in`  (fallback: `contracts/openapi/in`)
 *   specFilename → first existing `<project.name>.yml`, `.yaml`, or `.json` (fallback: `.yml`)
 *   apiPackage   → project-level `apiPackage` property or "infrastructure.in.rest"
 *   modelPackage → project-level `modelPackage` property or "infrastructure.in.rest.model"
 */
abstract class OpenApiSpec @Inject constructor(@get:Internal val specName: String) : Named {
    override fun getName(): String = specName

    /**
     * Directory containing the OpenAPI spec file, relative to the project root.
     * Defaults to `<openapi.generator.root.dir>/in` (fallback: `contracts/openapi/in`).
     */
    abstract val specPath: Property<String>

    /**
     * Name of the OpenAPI spec file (including extension).
     * Defaults to the first existing `<project.name>.yml`, `.yaml`, or `.json`
     * (falls back to `<project.name>.yml` when none exists).
     */
    abstract val specFilename: Property<String>

    /**
     * API package path (relative to base package).
     * Example: "infrastructure.out.ccbr.client"
     * 
     * Will be prefixed with `<rootPackage>.<projectName>.` to form the full package:
     * `com.transurban.glide.integration.api.payment.infrastructure.out.ccbr.client`
     * 
     * If not set, uses project-level `apiPackage` property (default: "infrastructure.in.rest").
     */
    abstract val apiPackage: Property<String>

    /**
     * Model package path (relative to base package).
     * Example: "infrastructure.out.ccbr.client.model"
     * 
     * Will be prefixed with `<rootPackage>.<projectName>.` to form the full package:
     * `com.transurban.glide.integration.api.payment.infrastructure.out.ccbr.client.model`
     * 
     * If not set, uses project-level `modelPackage` property (default: "infrastructure.in.rest.model").
     */
    abstract val modelPackage: Property<String>

    /**
     * Whether to derive outbound OAuth2 client configuration from this spec.
     *
     * Only meaningful for a consumed (outbound) spec. When enabled, the provider's per-operation
     * `security` requirements are turned into one client registration per distinct scope set, plus
     * a catalog mapping each operation to the registration that serves it. The outbound interceptor
     * uses the catalog to request only the scope a given call needs.
     *
     * This exists so the caller cannot drift from the callee. A single registration carrying the
     * union of every scope is both over-privileged and brittle — an authorization server rejects
     * the entire token request when one scope in it is unrecognised, which breaks every call rather
     * than only the one needing the new scope.
     *
     * Defaults to `false`.
     */
    abstract val oauth2ScopeConfig: Property<Boolean>

    /**
     * The `spring.security.oauth2.client.provider` entry supplying the token endpoint for the
     * generated registrations. Defaults to `okta`.
     */
    abstract val oauth2ProviderId: Property<String>

    /**
     * Property placeholder resolved for every generated registration's client id.
     *
     * A placeholder rather than a literal keeps credentials out of the generated file and leaves
     * existing environment and Secrets Manager overrides in control.
     */
    abstract val oauth2ClientIdProperty: Property<String>

    /** Property placeholder resolved for every generated registration's client secret. */
    abstract val oauth2ClientSecretProperty: Property<String>
}

/**
 * Configuration for recursively discovering OpenAPI specifications below a directory.
 *
 * Each discovered file is assigned a namespace from its directory path and filename, appended to
 * [packagePrefix]. Generated model classes are placed below that namespace's `.model` package.
 */
abstract class OpenApiSpecDirectory @Inject constructor(@get:Internal val discoveryName: String) :
    Named {
    override fun getName(): String = discoveryName

    /** Directory to scan recursively, relative to the project root. */
    abstract val specPath: Property<String>

    /** Package prefix before the discovered path and filename segments. */
    abstract val packagePrefix: Property<String>

    /** Whether to derive outbound OAuth2 scope configuration for discovered specs. */
    abstract val oauth2ScopeConfig: Property<Boolean>

    abstract val oauth2ProviderId: Property<String>
    abstract val oauth2ClientIdProperty: Property<String>
    abstract val oauth2ClientSecretProperty: Property<String>
}

// ---------------------------------------------------------------------------
// Spec filename resolution
// ---------------------------------------------------------------------------

/**
 * Supported OpenAPI spec extensions, in preference order. `.yml` is the repository's standard
 * suffix; `.yaml` remains supported so existing specs keep working and can be renamed one at a
 * time rather than in a single breaking change; `.json` is the format generally supplied by
 * external parties.
 */
val openApiSpecExtensions = listOf("yml", "yaml", "json")

/**
 * Resolves the default inbound spec filename for this project, matching whichever supported
 * extension is actually present on disk. Falls back to the preferred extension when no spec
 * exists, in which case the spec is skipped later by the existence check.
 *
 * Only the default matters here — an explicit `specFilename` in a consuming build script always
 * wins, because this value is applied as a Gradle convention.
 */
fun defaultInboundSpecFilename(rootDir: String): String {
    val candidates = openApiSpecExtensions.map { "${project.name}.$it" }
    return candidates.firstOrNull { file("$projectDir/$rootDir/in/$it").exists() }
        ?: candidates.first()
}

val openApiSpecs = objects.domainObjectContainer(OpenApiSpec::class.java) { specName ->
    objects.newInstance(OpenApiSpec::class.java, specName).apply {
        val rootDir = project.findProperty("openapi.generator.root.dir") as? String
            ?: "contracts/openapi"
        specPath.convention("$rootDir/in")
        specFilename.convention(defaultInboundSpecFilename(rootDir))
        
        // Package defaults — fall back to project-level properties if not set
        val defaultApiPackage = project.findProperty("apiPackage") as? String ?: "infrastructure.in.rest"
        val defaultModelPackage = project.findProperty("modelPackage") as? String ?: "infrastructure.in.rest.model"
        apiPackage.convention(defaultApiPackage)
        modelPackage.convention(defaultModelPackage)

        // Opt-in: only a consumed spec whose provider enforces OAuth2 scopes needs this.
        oauth2ScopeConfig.convention(false)
        oauth2ProviderId.convention("okta")
        oauth2ClientIdProperty.convention("\${glide.oauth2.outbound.client-id}")
        oauth2ClientSecretProperty.convention("\${glide.oauth2.outbound.client-secret}")
    }
}
extensions.add("openApiSpecs", openApiSpecs)

val openApiSpecDirectories =
    objects.domainObjectContainer(OpenApiSpecDirectory::class.java) { discoveryName ->
        objects.newInstance(OpenApiSpecDirectory::class.java, discoveryName).apply {
            specPath.convention("contracts/openapi/out")

            val defaultApiPackage =
                project.findProperty("apiPackage") as? String ?: "infrastructure.in.rest"
            packagePrefix.convention(defaultApiPackage)

            oauth2ScopeConfig.convention(false)
            oauth2ProviderId.convention("okta")
            oauth2ClientIdProperty.convention("\${glide.oauth2.outbound.client-id}")
            oauth2ClientSecretProperty.convention("\${glide.oauth2.outbound.client-secret}")
        }
    }
extensions.add("openApiSpecDirectories", openApiSpecDirectories)

private fun normalizeOpenApiPackageSegment(value: String): String {
    val normalized =
        value
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .lowercase()

    return normalized
}

private fun normalizeOpenApiPackagePath(value: String): String =
    value.split('.').joinToString(".") { segment ->
        normalizeOpenApiPackageSegment(segment)
    }

private fun discoveredNamespace(relativePath: String): List<String> {
    val normalizedPath = relativePath.replace('\\', '/')
    val segments = normalizedPath.split('/').filter { it.isNotBlank() }
    val lastSegment = segments.lastOrNull()
        ?: throw GradleException("[openapi-common] Cannot derive a package from '$relativePath'.")
    val filename = lastSegment.substringBeforeLast('.', lastSegment)
    return (segments.dropLast(1) + filename).map(::normalizeOpenApiPackageSegment)
}

// ---------------------------------------------------------------------------
// Task registration (deferred to afterEvaluate so container entries are set)
// ---------------------------------------------------------------------------

afterEvaluate {
    val rootPackage = rootProject.property("rootPackage") as String
    val buildDirectory = layout.buildDirectory
    val projectName = name

    // Always check if inbound spec should be auto-registered, even when other specs are configured.
    // This ensures backward compatibility and prevents unconfigured built-in task issues.
    if (openApiSpecs.findByName("inbound") == null) {
        val rootDir = project.findProperty("openapi.generator.root.dir") as? String
            ?: "contracts/openapi"
        val inboundSpecPath = "$rootDir/in"
        val inboundSpecFile = defaultInboundSpecFilename(rootDir)
        
        // Only create if the file actually exists
        if (file("$projectDir/$inboundSpecPath/$inboundSpecFile").exists()) {
            openApiSpecs.create("inbound")
            logger.info("[openapi-common] Auto-registered inbound spec from $inboundSpecPath/$inboundSpecFile")
        }
    }

    val explicitSpecFiles =
        openApiSpecs
            .mapNotNull { spec ->
                val specFile = file("${spec.specPath.get()}/${spec.specFilename.get()}")
                if (specFile.isFile) specFile.toPath().toAbsolutePath().normalize() else null
            }
            .toSet()
    val discoveredFiles = mutableListOf<Pair<OpenApiSpecDirectory, File>>()
    val discoveredSourceFiles = mutableMapOf<java.nio.file.Path, Pair<OpenApiSpecDirectory, File>>()

    openApiSpecDirectories.forEach { discovery ->
        val discoveryRoot = file(discovery.specPath.get())
        if (!discoveryRoot.isDirectory) {
            throw GradleException(
                "[openapi-common] Discovery directory '${discovery.specPath.get()}' " +
                    "for '${discovery.name}' does not exist or is not a directory."
            )
        }

        val files =
            discoveryRoot
                .walkTopDown()
                .filter { candidate ->
                    candidate.isFile &&
                        openApiSpecExtensions.contains(candidate.extension.lowercase()) &&
                        candidate.toPath().toAbsolutePath().normalize() !in explicitSpecFiles
                }
                .sortedBy { it.relativeTo(discoveryRoot).invariantSeparatorsPath }
                .toList()

        files.forEach { specFile ->
            val sourcePath = specFile.toPath().toAbsolutePath().normalize()
            val previous = discoveredSourceFiles[sourcePath]
            if (previous != null) {
                throw GradleException(
                    "[openapi-common] Spec '${specFile.relativeTo(projectDir).invariantSeparatorsPath}' " +
                        "is discovered by both '${previous.first.name}' and '${discovery.name}'. " +
                        "Remove the overlapping directory configuration."
                )
            }
            discoveredSourceFiles[sourcePath] = discovery to specFile
            discoveredFiles.add(discovery to specFile)
        }
    }

    val discoveredPackageOwners = mutableMapOf<String, MutableList<File>>()
    openApiSpecs.forEach { spec ->
        val source = file("${spec.specPath.get()}/${spec.specFilename.get()}")
        if (source.isFile) {
            val fullPackage =
                "${rootPackage}.${projectName.replace("-", "")}.${spec.modelPackage.get()}"
            discoveredPackageOwners.getOrPut(fullPackage) { mutableListOf() }.add(source)
        }
    }
    discoveredFiles.forEach { (discovery, specFile) ->
        val relativePath = specFile.relativeTo(file(discovery.specPath.get())).invariantSeparatorsPath
        val namespace = discoveredNamespace(relativePath)
        val packagePrefix = normalizeOpenApiPackagePath(discovery.packagePrefix.get())
        val apiPackageName = "$packagePrefix.${namespace.joinToString(".")}"
        val packageName = "$apiPackageName.model"
        val fullPackage =
            "${rootPackage}.${projectName.replace("-", "")}.$packageName"
        val owners = discoveredPackageOwners.getOrPut(fullPackage) { mutableListOf() }
        owners.add(specFile)
        if (owners.size > 1) {
            throw GradleException(
                "[openapi-common] Specs ${owners.joinToString { it.relativeTo(projectDir).invariantSeparatorsPath }} " +
                    "resolve to the same model package '$fullPackage'. Rename a file or directory, or remove the overlap."
            )
        }

        val taskSegments =
            (listOf(normalizeOpenApiPackageSegment(discovery.name)) + namespace)
                .joinToString("") { segment ->
                    segment.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }
                }
        val specName = "discovered$taskSegments"
        if (openApiSpecs.findByName(specName) != null) {
            throw GradleException(
                "[openapi-common] Discovered spec '${specFile.relativeTo(projectDir).invariantSeparatorsPath}' " +
                    "maps to existing spec name '$specName'. Rename the spec or use an explicit declaration."
            )
        }

        openApiSpecs.create(specName).apply {
            specPath.set(specFile.parentFile.relativeTo(projectDir).invariantSeparatorsPath)
            specFilename.set(specFile.name)
            apiPackage.set(apiPackageName)
            modelPackage.set(packageName)
            oauth2ScopeConfig.set(discovery.oauth2ScopeConfig.get())
            oauth2ProviderId.set(discovery.oauth2ProviderId.get())
            oauth2ClientIdProperty.set(discovery.oauth2ClientIdProperty.get())
            oauth2ClientSecretProperty.set(discovery.oauth2ClientSecretProperty.get())
        }
    }

    val generatedTaskOwners = mutableMapOf<String, String>()
    var inboundTaskConfigured = false
    openApiSpecs.forEach { spec ->
        val specDir  = "$projectDir/${spec.specPath.get()}".replace("\\", "/")
        val specFile = spec.specFilename.get()

        if (!file("$specDir/$specFile").exists()) {
            // Warn rather than inform: an explicitly configured spec that cannot be found is
            // almost always a mistake (commonly a filename or extension typo), and generation
            // would otherwise be skipped silently at the default log level.
            logger.warn("[openapi-common] Spec not found at $specDir/$specFile — skipping '${spec.name}'.")
            return@forEach
        }

        // For the "inbound" spec, configure the plugin's built-in `openApiGenerate` task directly
        // so that `./gradlew openApiGenerate` continues to work without modification.
        // For all other specs, register a dedicated `openApiGenerate<Name>` task.
        val taskName = if (spec.name == "inbound") "openApiGenerate"
                       else "openApiGenerate${spec.name.replaceFirstChar { it.uppercaseChar() }}"
        val sourceDescription = "$specDir/$specFile"
        val previousTaskOwner = generatedTaskOwners.putIfAbsent(taskName, sourceDescription)
        if (previousTaskOwner != null) {
            throw GradleException(
                "[openapi-common] Specs '$previousTaskOwner' and '$sourceDescription' " +
                    "resolve to the same generation task '$taskName'. Rename one spec."
            )
        }
        if (taskName != "openApiGenerate" && tasks.names.contains(taskName)) {
            throw GradleException(
                "[openapi-common] Spec '$sourceDescription' needs task '$taskName', " +
                    "but a task with that name is already registered."
            )
        }

        dependencies {
            implementation(libs.bundles.jaxws.libs)
            implementation(libs.swagger.annotations)
        }

        fun org.openapitools.generator.gradle.plugin.tasks.GenerateTask.configureSpec() {
            inputSpec.set(file("$specDir/$specFile"))
            outputDir.set(buildDirectory.dir("generated/$taskName"))

            // The generator only ever writes files; it never removes ones it no longer emits.
            // Deleting a schema from a spec would therefore leave its previously generated class
            // behind, and Gradle would then store that stale file as part of the task's output in
            // the build cache — after which every FROM-CACHE restore resurrects it. Each spec owns
            // its output directory exclusively, so wiping it before generation is safe.
            cleanupOutput.set(true)

            // Use per-spec package configuration (with defaults from spec conventions)
            apiPackage = "${rootPackage}.${project.name.replace("-", "")}.${spec.apiPackage.get()}"
            val effectiveModelPackage =
                "${rootPackage}.${project.name.replace("-", "")}.${spec.modelPackage.get()}"
            modelPackage = effectiveModelPackage

            // Detect if this is an outbound (client) spec based on spec name or path
            val isOutboundSpec = spec.name != "inbound" && 
                                (spec.specPath.get().contains("/out") || 
                                 spec.specPath.get().contains(".out") ||
                                 spec.apiPackage.get().contains(".out."))

            if (isOutboundSpec) {
                // Model-only generation for outbound specs (no client/API classes)
                generatorName.set("java")
                library.set("restclient")
                
                configOptions = mapOf(
                    "useSpringBoot4" to "true",
                    "library" to "restclient",
                    "gradleBuildFile" to "false",
                    "documentationProvider" to "none",
                    "useBeanValidation" to "true",
                    "performBeanValidation" to "true",
                    "openApiNullable" to "false",
                    "useJakartaEe" to "true",
                    "useEnumCaseInsensitive" to "true",
                    "additionalModelTypeAnnotations" to "@jakarta.annotation.Nonnull",
                    "dateLibrary" to "java8",
                    "serializationLibrary" to "jackson"
                )
                
                logger.info("[openapi-common] Generating MODELS ONLY for outbound spec '${spec.name}'")
            } else {
                // Server interface generation for inbound specs
                generatorName.set("spring")
                
                configOptions = mapOf(
                    "useSpringBoot4" to "true",
                    "serviceInterface" to "true",
                    "interfaceOnly" to "true",
                    "useTags" to "true",
                    "gradleBuildFile" to "false",
                    "documentationProvider" to "none",
                    "skipDefaultInterface" to "true",
                    "useBeanValidation" to "true",
                    "performBeanValidation" to "true",
                    "openApiNullable" to "false",
                    "useSpringController" to "true",
                    "useJakartaEe" to "true",
                    "useEnumCaseInsensitive" to "true",
                    "additionalModelTypeAnnotations" to "@jakarta.annotation.Nonnull",
                    "configPackage" to "${rootPackage}.${project.name.replace("-", "")}.application.config"
                )
                
                logger.info("[openapi-common] Generating SERVER interfaces for inbound spec '${spec.name}'")
            }

            // Common generation flags for both inbound and outbound
            if (isOutboundSpec) {
                // Outbound: models only, no APIs or supporting files
                globalProperties.set(mapOf(
                    "models" to "",
                    "apis" to "false",
                    "supportingFiles" to "false"
                ))
            }
            // else: Inbound uses defaults (generates APIs)
            
            generateApiTests = false
            generateModelTests = false
            generateApiDocumentation = false
            generateModelDocumentation = false

            doFirst {
                outputDir.get().asFile.mkdirs()
            }

            doLast {
                val generatedSourceRoot = buildDirectory.dir("generated/$taskName/src/main/java").get().asFile

                if (generatedSourceRoot.exists()) {
                    generatedSourceRoot.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".java") }
                        .forEach { file ->
                            val original = file.readText(Charsets.UTF_8)
                            var content = original

                            // Replace deprecated Spring @Nullable import with JSpecify equivalent.
                            content = content.replace(
                                "import org.springframework.lang.Nullable;",
                                "import org.jspecify.annotations.Nullable;"
                            )

                            // Remove org.hibernate.validator.constraints.* unconditionally — all bean
                            // validation in this project uses jakarta.validation.constraints.* instead.
                            content = content.replace("import org.hibernate.validator.constraints.*;\r?\n", "")

                            // Collapse consecutive blank lines left behind by removed import lines.
                            content = content.replace(Regex("\r?\n{3,}"), "\n\n")

                            if (content != original) file.writeText(content, Charsets.UTF_8)
                        }
                }

                // Model-only post-processing: add @Nonnull to collection getters and @JsonInclude.
                val modelDir =
                    buildDirectory.dir(
                        "generated/$taskName/src/main/java/${effectiveModelPackage.replace(".", "/")}"
                    ).get().asFile
                if (modelDir.exists()) {
                    modelDir.listFiles { file -> file.isFile && file.name.endsWith(".java") }?.forEach { file ->
                        var content = file.readText(Charsets.UTF_8)

                        content = content.replace(
                            Regex("""(\s+@JsonProperty\("[^"]+"\)\s+)(public List<)"""),
                            "$1@jakarta.annotation.Nonnull\n  $2"
                        )

                        if (!content.contains("@JsonInclude")) {
                            if (!content.contains("import com.fasterxml.jackson.annotation.JsonInclude;")) {
                                content = content.replace(
                                    "import com.fasterxml.jackson.annotation.JsonProperty;",
                                    "import com.fasterxml.jackson.annotation.JsonInclude;\nimport com.fasterxml.jackson.annotation.JsonProperty;"
                                )
                            }

                            content = content.replace(
                                Regex("""(@Generated[^\n]+\n)(public class )"""),
                                "$1@JsonInclude(JsonInclude.Include.NON_NULL)\n$2"
                            )
                        }

                        file.writeText(content, Charsets.UTF_8)
                    }
                }
            }
        }

        val generateTask = if (spec.name == "inbound") {
            inboundTaskConfigured = true
            tasks.named("openApiGenerate", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class.java) {
                configureSpec()
            }
        } else {
            tasks.register(taskName, org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class.java) {
                configureSpec()
            }
        }

        // Wire each generated task into compilation.
        tasks.named("compileJava") { dependsOn(generateTask) }
        tasks.named("processResources") { dependsOn(generateTask) }
        if (taskName != "openApiGenerate") {
            tasks.named("openApiGenerate") { dependsOn(generateTask) }
        }

        // Add the generated sources to the main source set so the compiler picks them up.
        the<SourceSetContainer>()["main"].java.srcDir(
            buildDirectory.dir("generated/$taskName/src/main/java")
        )

        // ------------------------------------------------------------------
        // Outbound OAuth2 scope configuration, derived from the same spec
        // ------------------------------------------------------------------
        if (spec.oauth2ScopeConfig.get()) {
            val catalogName = spec.name
            val scopeTaskName = "generateOAuth2ScopeConfig${catalogName.replaceFirstChar { it.uppercaseChar() }}"
            val specFileOnDisk = file("$specDir/$specFile")
            val scopeOutputDir = buildDirectory.dir("generated/$scopeTaskName/resources")

            // Read into locals so the task action holds no reference to the project, keeping it
            // compatible with Gradle's configuration cache.
            val providerId = spec.oauth2ProviderId.get()
            val clientIdProperty = spec.oauth2ClientIdProperty.get()
            val clientSecretProperty = spec.oauth2ClientSecretProperty.get()
            val relativeSpecPath = "${spec.specPath.get()}/$specFile"

            val scopeTask = tasks.register(scopeTaskName) {
                group = "openapi"
                description = "Derives outbound OAuth2 client registrations and the per-operation " +
                    "scope catalog for '$catalogName' from $relativeSpecPath"

                inputs.file(specFileOnDisk)
                outputs.dir(scopeOutputDir)

                doLast {
                    val extracted = com.transurban.glide.buildlogic.openapi.OpenApiScopeExtractor
                        .extract(specFileOnDisk.readText(Charsets.UTF_8))

                    // Opting in means "this provider enforces scopes". Finding none almost always
                    // means the spec's security blocks moved or were dropped, which would otherwise
                    // surface as an unauthorized call at runtime instead of here.
                    if (extracted.operations.isEmpty()) {
                        throw GradleException(
                            "[openapi-common] oauth2ScopeConfig is enabled for '$catalogName' but " +
                                "$relativeSpecPath declares no scoped operations. Either the spec no " +
                                "longer enforces OAuth2 scopes, or its security blocks have changed shape."
                        )
                    }

                    val rendered = com.transurban.glide.buildlogic.openapi.OAuth2ScopeConfigWriter.render(
                        catalogName = catalogName,
                        catalog = extracted,
                        providerId = providerId,
                        clientIdProperty = clientIdProperty,
                        clientSecretProperty = clientSecretProperty,
                        sourceDescription = relativeSpecPath
                    )

                    val target = scopeOutputDir.get().asFile.resolve("oauth2/$catalogName-oauth2-scopes.yml")
                    target.parentFile.mkdirs()
                    target.writeText(rendered, Charsets.UTF_8)

                    logger.lifecycle(
                        "[openapi-common] Derived ${extracted.distinctScopeSets().size} OAuth2 " +
                            "registration(s) covering ${extracted.operations.size} operation(s) for " +
                            "'$catalogName' from $relativeSpecPath"
                    )
                }
            }

            tasks.named("processResources") { dependsOn(scopeTask) }
            the<SourceSetContainer>()["main"].resources.srcDir(scopeOutputDir)
        }
    }

    // Without an inbound spec, the plugin's built-in task has no input; its dependencies still run.
    if (!inboundTaskConfigured) {
        tasks.named("openApiGenerate") { enabled = false }
    }
}