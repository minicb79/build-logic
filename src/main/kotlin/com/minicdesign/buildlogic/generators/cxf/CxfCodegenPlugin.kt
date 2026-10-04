package com.minicdesign.buildlogic.generators.cxf

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.util.Locale

class CxfCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val wsdlContainer = project.objects.domainObjectContainer(WsdlSpec::class.java) { name ->
            project.objects.newInstance(WsdlSpec::class.java, name).apply {
                markGenerated.convention(true)
                autoNameResolution.convention(true)
            }
        }

        val extension = project.extensions.create("cxfCodegen", CxfCodegenExtension::class.java).apply {
            cxfVersion.convention("4.0.5")
            wsdlBasePackage.convention("com.minicdesign.generated.wsdl")
            outputDir.convention(project.layout.buildDirectory.dir("generated/sources/wsdl/java"))
            markGenerated.convention(true)
            autoNameResolution.convention(true)
        }

        // Expose container on extension
        val extObj = extension as? org.gradle.api.plugins.ExtensionAware
        extObj?.extensions?.add("wsdls", wsdlContainer)

        val wsdlConfig = project.configurations.maybeCreate("wsdl2java")
        val cxfToolsConfig = project.configurations.maybeCreate("cxfTools")
        cxfToolsConfig.extendsFrom(wsdlConfig)

        // Master tasks
        val generateWsdlMaster = project.tasks.register("generateWsdl") {
            group = "wsdl"
            description = "Runs all Apache CXF WSDL to Java code generation tasks."
        }

        val cxfCodeGenMaster = project.tasks.register("cxfCodeGen") {
            group = "wsdl"
            description = "Alias for generateWsdl matching cxfcodegen-common."
            dependsOn(generateWsdlMaster)
        }

        project.tasks.register("wsdl2java") {
            group = "wsdl"
            description = "Alias for generateWsdl matching wsdl2java conventions."
            dependsOn(generateWsdlMaster)
        }

        project.afterEvaluate {
            val cxfVer = extension.cxfVersion.get()
            project.dependencies.add("wsdl2java", "org.apache.cxf:cxf-tools-wsdlto-databinding-jaxb:$cxfVer")
            project.dependencies.add("wsdl2java", "org.apache.cxf:cxf-tools-wsdlto-frontend-jaxws:$cxfVer")
            project.dependencies.add("wsdl2java", "jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
            project.dependencies.add("wsdl2java", "jakarta.annotation:jakarta.annotation-api:3.0.0")
            project.dependencies.add("wsdl2java", "org.glassfish.jaxb:jaxb-runtime:4.0.5")

            if (project.configurations.findByName("implementation") != null) {
                project.dependencies.add("implementation", "jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
                project.dependencies.add("implementation", "jakarta.annotation:jakarta.annotation-api:3.0.0")
                project.dependencies.add("implementation", "jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
            }

            // 1. Resolve single wsdlLocation property if configured
            if (extension.wsdlLocation.isPresent && extension.wsdlLocation.get().isNotBlank()) {
                val loc = extension.wsdlLocation.get()
                val candidateFiles = listOf(
                    project.file(loc),
                    project.file("src/main/resources/$loc"),
                    project.file("src/main/resources/wsdl/$loc"),
                    project.file("contract/wsdl/$loc"),
                    project.file("contracts/wsdl/$loc")
                )
                val resolved = candidateFiles.firstOrNull { it.exists() && it.isFile }
                if (resolved != null) {
                    val specName = resolved.nameWithoutExtension
                    if (wsdlContainer.findByName(specName) == null) {
                        wsdlContainer.create(specName).apply {
                            wsdlFile.set(resolved)
                            wsdlLocation.set(loc)
                            if (extension.packageNames.isPresent) {
                                packageNames.set(extension.packageNames.get())
                            }
                        }
                    }
                }
            }

            // 2. Discover convention directories (contract/wsdl, contracts/wsdl, src/main/resources/wsdl)
            val candidateWsdlRoots = listOf(
                "src/main/resources/wsdl",
                "contract/wsdl",
                "contracts/wsdl"
            ).map { project.projectDir.resolve(it) }.filter { it.exists() && it.isDirectory }

            candidateWsdlRoots.forEach { wsdlDir ->
                wsdlDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase(Locale.ROOT) == "wsdl" }
                    .forEach { file ->
                        val relPath = file.relativeTo(wsdlDir).invariantSeparatorsPath
                        val isOutbound = relPath.contains("/out/") || relPath.startsWith("out/") ||
                                file.nameWithoutExtension.contains("out", ignoreCase = true) ||
                                file.nameWithoutExtension.contains("client", ignoreCase = true)
                        val isInbound = relPath.contains("/in/") || relPath.startsWith("in/") ||
                                file.nameWithoutExtension.contains("in", ignoreCase = true) ||
                                file.nameWithoutExtension.contains("server", ignoreCase = true)

                        val specName = if (relPath.contains('/')) {
                            relPath.replace('/', '_').substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_]"), "")
                        } else {
                            file.nameWithoutExtension
                        }

                        if (wsdlContainer.findByName(specName) == null) {
                            wsdlContainer.create(specName).apply {
                                wsdlFile.set(file)
                                wsdlLocation.set(file.relativeTo(project.projectDir).invariantSeparatorsPath)
                                this.outbound.set(isOutbound)
                                this.generateClient.set(isOutbound)
                                this.generateServer.set(!isOutbound)

                                val base = extension.wsdlBasePackage.get()
                                val sanitized = file.nameWithoutExtension.replace("-", "").replace("_", "").lowercase(Locale.ROOT)
                                val pkg = when {
                                    isOutbound -> "$base.outbound.$sanitized"
                                    isInbound -> "$base.inbound.$sanitized"
                                    else -> "$base.$sanitized"
                                }
                                packageNames.set(listOf(pkg))
                            }
                        }
                    }
            }

            // 3. Register tasks for all WSDL specs
            val wsdlTasks = mutableListOf<TaskProvider<JavaExec>>()
            val generatedDirs = mutableListOf<File>()

            wsdlContainer.forEach { spec ->
                val targetFile = if (spec.wsdlFile.isPresent) {
                    spec.wsdlFile.get().asFile
                } else if (spec.wsdlLocation.isPresent) {
                    val loc = spec.wsdlLocation.get()
                    val candidate = listOf(
                        project.file(loc),
                        project.file("src/main/resources/$loc"),
                        project.file("src/main/resources/wsdl/$loc"),
                        project.file("contract/wsdl/$loc"),
                        project.file("contracts/wsdl/$loc")
                    ).firstOrNull { it.exists() && it.isFile }
                    candidate
                } else null

                if (targetFile == null || !targetFile.exists()) return@forEach

                val cleanName = spec.name.split('_', '-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
                val taskName = "generateWsdl$cleanName"
                val specOutputDir = if (spec.outputDir.isPresent) {
                    spec.outputDir.get().asFile
                } else {
                    extension.outputDir.get().asFile.resolve(spec.name)
                }
                generatedDirs.add(specOutputDir)

                val task = project.tasks.register(taskName, JavaExec::class.java) {
                    group = "wsdl"
                    description = "Generates Java classes from WSDL file ${targetFile.name} using Apache CXF."
                    inputs.file(targetFile)
                    outputs.dir(specOutputDir)

                    classpath = wsdlConfig
                    mainClass.set("org.apache.cxf.tools.wsdlto.WSDLToJava")

                    doFirst {
                        specOutputDir.deleteRecursively()
                        specOutputDir.mkdirs()
                    }

                    argumentProviders.add(CommandLineArgumentProvider {
                        val args = mutableListOf(
                            "-d", specOutputDir.absolutePath
                        )

                        val markGen = if (spec.markGenerated.isPresent) spec.markGenerated.get() else extension.markGenerated.get()
                        if (markGen) {
                            args.add("-mark-generated")
                        }

                        val autoName = if (spec.autoNameResolution.isPresent) spec.autoNameResolution.get() else extension.autoNameResolution.get()
                        if (autoName) {
                            args.add("-autoNameResolution")
                        }

                        val pkgs = if (spec.packageNames.isPresent && spec.packageNames.get().isNotEmpty()) {
                            spec.packageNames.get()
                        } else if (extension.packageNames.isPresent && extension.packageNames.get().isNotEmpty()) {
                            extension.packageNames.get()
                        } else {
                            val base = extension.wsdlBasePackage.get()
                            val sanitized = spec.name.replace("-", "").replace("_", "").lowercase(Locale.ROOT)
                            listOf("$base.$sanitized")
                        }

                        pkgs.forEach { pkg ->
                            args.add("-p")
                            args.add(pkg)
                        }

                        if (spec.extraArgs.isPresent) {
                            args.addAll(spec.extraArgs.get())
                        }

                        args.add(targetFile.absolutePath)
                        args
                    })
                }

                wsdlTasks.add(task)
                generateWsdlMaster.configure { dependsOn(task) }
            }

            // 4. SourceSets and IDE wiring
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")
                for (outDir in generatedDirs) {
                    mainSourceSet.java.srcDir(outDir)
                }
                project.tasks.matching { it.name == "compileJava" }.configureEach { dependsOn(generateWsdlMaster) }
                project.tasks.matching { it.name == "compileKotlin" }.configureEach { dependsOn(generateWsdlMaster) }
            }

            project.plugins.withType(IdeaPlugin::class.java) {
                val ideaModel = project.extensions.getByType(IdeaModel::class.java)
                for (outDir in generatedDirs) {
                    ideaModel.module.generatedSourceDirs.add(outDir)
                    ideaModel.module.excludeDirs.remove(outDir)
                }
            }
        }
    }
}
