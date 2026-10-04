package com.minicdesign.buildlogic.generators.graphql

import com.netflix.graphql.dgs.codegen.gradle.CodegenPlugin
import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.io.File

class GraphQLCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure java plugin is present
        project.pluginManager.apply("java")
        project.pluginManager.apply(CodegenPlugin::class.java)

        val extension = project.extensions.create("graphqlCodegen", GraphQLCodegenExtension::class.java).apply {
            packageName.convention("com.minicdesign.generated.graphql")
            schemaPaths.convention(listOf("contract/graphql", "contracts/graphql", "src/main/resources/graphql", "src/main/resources/schema"))
            generateClient.convention(false)
            generateDataTypes.convention(true)
            generateInterfaces.convention(true)
            outputDir.convention(project.layout.buildDirectory.dir("generated/sources/graphql/java"))
            typeMapping.convention(emptyMap())
        }

        val generateGraphQLMaster = project.tasks.register("generateGraphQL") {
            group = "graphql"
            description = "Runs all GraphQL code generation tasks."
            dependsOn("generateJava")
        }

        project.tasks.register("graphqlCodeGen") {
            group = "graphql"
            description = "Alias for generateGraphQL matching codegen conventions."
            dependsOn(generateGraphQLMaster)
        }

        project.afterEvaluate {
            val allCandidateDirs = listOf(
                "contract/graphql",
                "contracts/graphql",
                "src/main/resources/graphql",
                "src/main/resources/schema"
            ).map { project.file(it) }

            val customPaths = extension.schemaPaths.get()
                .map { project.file(it) }
                .filter { it.exists() }

            val searchDirs = (allCandidateDirs + customPaths).distinct().filter { it.exists() && it.isDirectory }

            val inboundFiles = mutableListOf<File>()
            val outboundFiles = mutableListOf<File>()
            val flatFiles = mutableListOf<File>()

            searchDirs.forEach { dir ->
                dir.walkTopDown().filter { it.isFile && (it.extension == "graphql" || it.extension == "graphqls") }.forEach { file ->
                    val rel = file.relativeTo(dir).invariantSeparatorsPath
                    if (rel.startsWith("out/") || rel.contains("/out/") || file.nameWithoutExtension.contains("out", ignoreCase = true) || file.nameWithoutExtension.contains("client", ignoreCase = true)) {
                        outboundFiles.add(file)
                    } else if (rel.startsWith("in/") || rel.contains("/in/") || file.nameWithoutExtension.contains("in", ignoreCase = true) || file.nameWithoutExtension.contains("server", ignoreCase = true)) {
                        inboundFiles.add(file)
                    } else {
                        flatFiles.add(file)
                    }
                }
            }

            val hasSplitInOut = inboundFiles.isNotEmpty() || outboundFiles.isNotEmpty()
            val basePkg = extension.packageName.get()
            val baseOutDir = extension.outputDir.get().asFile
            val registeredOutputDirs = mutableListOf<File>()

            if (hasSplitInOut) {
                if (inboundFiles.isNotEmpty()) {
                    val inDir = baseOutDir.resolve("inbound")
                    registeredOutputDirs.add(inDir)
                    project.tasks.named("generateJava", GenerateJavaTask::class.java).configure {
                        schemaPaths = inboundFiles.map { it.absolutePath as Any }.toMutableList()
                        packageName = "$basePkg.inbound"
                        generateClient = false
                        generateDataTypes = extension.generateDataTypes.get()
                        generateInterfaces = extension.generateInterfaces.get()
                        typeMapping = extension.typeMapping.get().toMutableMap()
                        addGeneratedAnnotation = true
                        generatedSourcesDir = inDir.absolutePath
                    }
                }

                if (outboundFiles.isNotEmpty()) {
                    val outDir = baseOutDir.resolve("outbound")
                    registeredOutputDirs.add(outDir)
                    val outboundTask = project.tasks.register("generateGraphQLOutbound", GenerateJavaTask::class.java) {
                        group = "graphql"
                        description = "Generates client query builders for outbound GraphQL schemas."
                        schemaPaths = outboundFiles.map { it.absolutePath as Any }.toMutableList()
                        packageName = "$basePkg.outbound"
                        generateClient = true
                        generateDataTypes = extension.generateDataTypes.get()
                        generateInterfaces = extension.generateInterfaces.get()
                        typeMapping = extension.typeMapping.get().toMutableMap()
                        addGeneratedAnnotation = true
                        generatedSourcesDir = outDir.absolutePath
                    }
                    generateGraphQLMaster.configure { dependsOn(outboundTask) }
                }
            } else {
                registeredOutputDirs.add(baseOutDir)
                val allResolved = searchDirs.map { it.absolutePath as Any }.toMutableList()
                project.tasks.named("generateJava", GenerateJavaTask::class.java).configure {
                    if (allResolved.isNotEmpty()) {
                        schemaPaths = allResolved
                    }
                    packageName = basePkg
                    generateClient = extension.generateClient.get()
                    generateDataTypes = extension.generateDataTypes.get()
                    generateInterfaces = extension.generateInterfaces.get()
                    typeMapping = extension.typeMapping.get().toMutableMap()
                    addGeneratedAnnotation = true
                    generatedSourcesDir = baseOutDir.absolutePath
                }
            }

            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")
                for (dir in registeredOutputDirs) {
                    mainSourceSet.java.srcDir(dir)
                }

                project.tasks.matching { it.name == "compileJava" }.configureEach { dependsOn(generateGraphQLMaster) }
                project.tasks.matching { it.name == "compileKotlin" }.configureEach { dependsOn(generateGraphQLMaster) }
            }

            project.plugins.withType(IdeaPlugin::class.java) {
                val ideaModel = project.extensions.getByType(IdeaModel::class.java)
                for (dir in registeredOutputDirs) {
                    ideaModel.module.generatedSourceDirs.add(dir)
                    ideaModel.module.excludeDirs.remove(dir)
                }
            }
        }
    }
}
