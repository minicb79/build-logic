package com.minicdesign.buildlogic.hexagonal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import java.io.File

class HexagonalArchitecturePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("hexagonalArchitecture", HexagonalArchitectureExtension::class.java).apply {
            mode.convention(HexagonalMode.SINGLE_MODULE)
            serviceDir.convention(project.layout.projectDirectory)
            basePackage.convention(
                project.provider {
                    project.group.toString().takeIf { it.isNotBlank() && it != "unspecified" }
                        ?: project.rootProject.group.toString().takeIf { it.isNotBlank() && it != "unspecified" }
                        ?: "com.example"
                }
            )
            monorepo.convention(false)
            servicesDir.convention(serviceDir.dir("services"))
            sharedDir.convention(serviceDir.dir("shared"))
            enforceServices.convention(true)
            enforceShared.convention(true)
            enforceContracts.convention(true)
            contractsAsModule.convention(true)
            enforceWiremock.convention(true)
            enforceAppBoot.convention(true)
            enforceCore.convention(true)
            enforceAdapters.convention(true)
            requireInboundAdapters.convention(false)
            requireOutboundAdapters.convention(false)
            failOnViolation.convention(true)
        }

        // 1. Register verification task
        val checkHexagonal = project.tasks.register("checkHexagonalArchitecture", CheckHexagonalArchitectureTask::class.java) {
            group = "verification"
            description = "Enforces the standard Hexagonal Architecture directory structure."
            mode.set(extension.mode)
            serviceDir.set(extension.serviceDir)
            monorepo.set(extension.monorepo)
            servicesDir.set(extension.servicesDir)
            sharedDir.set(extension.sharedDir)
            enforceServices.set(extension.enforceServices)
            enforceShared.set(extension.enforceShared)
            serviceModes.set(extension.serviceModes)
            enforceContracts.set(extension.enforceContracts)
            contractsAsModule.set(extension.contractsAsModule)
            enforceWiremock.set(extension.enforceWiremock)
            enforceAppBoot.set(extension.enforceAppBoot)
            enforceCore.set(extension.enforceCore)
            enforceAdapters.set(extension.enforceAdapters)
            requireInboundAdapters.set(extension.requireInboundAdapters)
            requireOutboundAdapters.set(extension.requireOutboundAdapters)
            failOnViolation.set(extension.failOnViolation)
        }

        // Hook into lifecycle check task
        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn(checkHexagonal)
        }

        // 2. Register scaffolding tasks
        project.tasks.register("scaffoldHexagonalArchitecture", ScaffoldHexagonalArchitectureTask::class.java) {
            group = "build setup"
            description = "Scaffolds standard Hexagonal Architecture directory layout and placeholder build files."
            mode.set(extension.mode)
            monorepo.set(extension.monorepo)
            basePackage.set(extension.basePackage)
            serviceDir.set(extension.serviceDir)
            servicesDir.set(extension.servicesDir)
            sharedDir.set(extension.sharedDir)
            serviceModes.set(extension.serviceModes)
        }

        project.tasks.register("scaffoldService", ScaffoldHexagonalArchitectureTask::class.java) {
            group = "build setup"
            description = "Scaffolds a new hexagonal service module with interactive prompts."
            mode.set(extension.mode)
            monorepo.set(extension.monorepo)
            basePackage.set(extension.basePackage)
            serviceDir.set(extension.serviceDir)
            servicesDir.set(extension.servicesDir)
            sharedDir.set(extension.sharedDir)
            serviceModes.set(extension.serviceModes)
            scaffoldType.set("service")
        }

        project.tasks.register("scaffoldSharedLibrary", ScaffoldHexagonalArchitectureTask::class.java) {
            group = "build setup"
            description = "Scaffolds a new shared library module in a monorepo with interactive prompts."
            mode.set(extension.mode)
            monorepo.set(extension.monorepo)
            basePackage.set(extension.basePackage)
            serviceDir.set(extension.serviceDir)
            servicesDir.set(extension.servicesDir)
            sharedDir.set(extension.sharedDir)
            serviceModes.set(extension.serviceModes)
            scaffoldType.set("shared")
        }

        project.tasks.register("scaffoldAdapter", ScaffoldHexagonalArchitectureTask::class.java) {
            group = "build setup"
            description = "Scaffolds a new hexagonal adapter module with interactive prompts."
            mode.set(extension.mode)
            monorepo.set(extension.monorepo)
            basePackage.set(extension.basePackage)
            serviceDir.set(extension.serviceDir)
            servicesDir.set(extension.servicesDir)
            sharedDir.set(extension.sharedDir)
            serviceModes.set(extension.serviceModes)
            scaffoldType.set("adapter")
        }

        // 3. Configure WireMock search paths for testIntegration across project and subprojects
        val configureWiremockForProject: (Project) -> Unit = { p ->
            p.afterEvaluate {
                val serviceRoot = extension.serviceDir.get().asFile
                val isMonorepo = extension.monorepo.get()
                val servicesBase = if (extension.servicesDir.isPresent) extension.servicesDir.get().asFile else serviceRoot.resolve("services")

                // Find service root for p (either serviceRoot, or enclosing directory in services/)
                val effectiveServiceDir: File = if (isMonorepo) {
                    var curr: File? = p.projectDir
                    var foundService: File? = null
                    while (curr != null && curr.parentFile != null) {
                        if (curr.parentFile == servicesBase) {
                            foundService = curr
                            break
                        }
                        if (curr == serviceRoot) break
                        curr = curr.parentFile
                    }
                    foundService ?: p.projectDir
                } else {
                    serviceRoot
                }

                val serviceWiremockDir = effectiveServiceDir.resolve("wiremock")
                val rootWiremockDir = serviceRoot.resolve("wiremock")

                // A. Add service wiremock directory to testIntegration resources if present
                val javaExt = p.extensions.findByType(JavaPluginExtension::class.java)
                val testIntSourceSet = javaExt?.sourceSets?.findByName("testIntegration")
                if (testIntSourceSet != null) {
                    if (serviceWiremockDir.exists() && serviceWiremockDir.isDirectory) {
                        testIntSourceSet.resources.srcDir(serviceWiremockDir)
                    }
                    if (isMonorepo && rootWiremockDir.exists() && rootWiremockDir.isDirectory && rootWiremockDir != serviceWiremockDir) {
                        testIntSourceSet.resources.srcDir(rootWiremockDir)
                    }
                }

                // B. Configure WireMock properties on integrationTest and testIntegration tasks
                val configureTestTask: (Test) -> Unit = { testTask ->
                    val searchDirs = mutableListOf<File>()

                    // Service-level wiremock directory
                    if (serviceWiremockDir.exists() && serviceWiremockDir.isDirectory) {
                        searchDirs.add(serviceWiremockDir)
                    }

                    // Monorepo root wiremock directory
                    if (isMonorepo && rootWiremockDir.exists() && rootWiremockDir.isDirectory && !searchDirs.contains(rootWiremockDir)) {
                        searchDirs.add(rootWiremockDir)
                    }

                    // Project-local wiremock directory (if subproject has its own)
                    val localWm = p.file("wiremock")
                    if (localWm.exists() && localWm.isDirectory && !searchDirs.contains(localWm)) {
                        searchDirs.add(localWm)
                    }

                    // testIntegration/resources and testIntegration/resources/wiremock
                    val testIntRes = p.file("src/testIntegration/resources")
                    if (testIntRes.exists() && testIntRes.isDirectory) {
                        val testIntWm = testIntRes.resolve("wiremock")
                        if (testIntWm.exists() && testIntWm.isDirectory) {
                            searchDirs.add(testIntWm)
                        } else if (testIntRes.resolve("mappings").exists()) {
                            searchDirs.add(testIntRes)
                        }
                    }

                    if (searchDirs.isNotEmpty()) {
                        val primary = searchDirs.first()
                        testTask.systemProperty("wiremock.root-dir", primary.absolutePath)
                        testTask.systemProperty("wiremock.dir", primary.absolutePath)
                        testTask.systemProperty("wiremock.search-dirs", searchDirs.joinToString(",") { it.absolutePath })
                    }
                }

                p.tasks.withType(Test::class.java).configureEach {
                    if (name == "integrationTest" || name == "testIntegration") {
                        configureTestTask(this)
                    }
                }
                listOf("integrationTest", "testIntegration").forEach { taskName ->
                    (p.tasks.findByName(taskName) as? Test)?.let { configureTestTask(it) }
                }
            }
        }

        configureWiremockForProject(project)
        project.subprojects {
            configureWiremockForProject(this)
        }
    }
}
