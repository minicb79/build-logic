package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import java.util.Locale

interface ApiGenerationExtension {
    val openApiBasePackage: Property<String>
    val wsdlBasePackage: Property<String>
    val openApiVersion: Property<String>
    val cxfVersion: Property<String>
}

class ApiGenerationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("apiGeneration", ApiGenerationExtension::class.java).apply {
            openApiBasePackage.convention("com.minicdesign.generated.openapi")
            wsdlBasePackage.convention("com.minicdesign.generated.wsdl")
            openApiVersion.convention("7.11.0")
            cxfVersion.convention("4.0.5")
        }

        val openapiConfig = project.configurations.create("openapiGenerator")
        val wsdlConfig = project.configurations.create("wsdl2java")

        project.afterEvaluate {
            project.dependencies.add("openapiGenerator", "org.openapitools:openapi-generator-cli:${extension.openApiVersion.get()}")
            project.dependencies.add("wsdl2java", "org.apache.cxf:cxf-tools-wsdlto-databinding-jaxb:${extension.cxfVersion.get()}")
            project.dependencies.add("wsdl2java", "org.apache.cxf:cxf-tools-wsdlto-frontend-jaxws:${extension.cxfVersion.get()}")
            project.dependencies.add("wsdl2java", "jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")
            project.dependencies.add("wsdl2java", "jakarta.annotation:jakarta.annotation-api:3.0.0")
            project.dependencies.add("wsdl2java", "org.glassfish.jaxb:jaxb-runtime:4.0.5")
        }

        val openapiDir = project.projectDir.resolve("api-specs/openapi")
        val wsdlDir = project.projectDir.resolve("api-specs/wsdl")

        val apiGenTasks = mutableListOf<TaskProvider<JavaExec>>()

        if (openapiDir.exists() && openapiDir.isDirectory) {
            openapiDir.listFiles()?.filter { it.isFile && (it.extension == "yaml" || it.extension == "yml" || it.extension == "json") }?.forEach { specFile ->
                val specName = specFile.nameWithoutExtension.replaceFirstChar { it.uppercase() }
                val taskName = "generateOpenApi$specName"
                val outputDir = project.layout.buildDirectory.dir("generated/sources/openapi/java/${specFile.nameWithoutExtension}")

                val task = project.tasks.register(taskName, JavaExec::class.java) {
                    inputs.file(specFile)
                    outputs.dir(outputDir)

                    classpath = openapiConfig
                    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")

                    argumentProviders.add(CommandLineArgumentProvider {
                        val basePkg = extension.openApiBasePackage.get()
                        val sanitizedSpecName = specFile.nameWithoutExtension.replace("-", "").replace("_", "").lowercase(Locale.getDefault())
                        val apiPkg = "$basePkg.$sanitizedSpecName.api"
                        val modelPkg = "$basePkg.$sanitizedSpecName.model"

                        listOf(
                            "generate",
                            "-g", "spring",
                            "-i", specFile.absolutePath,
                            "-o", outputDir.get().asFile.absolutePath,
                            "--api-package", apiPkg,
                            "--model-package", modelPkg,
                            "--additional-properties", "interfaceOnly=true,useSpringBoot3=true,useTags=true"
                        )
                    })
                }
                apiGenTasks.add(task)
            }
        }

        if (wsdlDir.exists() && wsdlDir.isDirectory) {
            wsdlDir.listFiles()?.filter { it.isFile && it.extension == "wsdl" }?.forEach { wsdlFile ->
                val wsdlName = wsdlFile.nameWithoutExtension.replaceFirstChar { it.uppercase() }
                val taskName = "generateWsdl$wsdlName"
                val outputDir = project.layout.buildDirectory.dir("generated/sources/wsdl/java/${wsdlFile.nameWithoutExtension}")

                val task = project.tasks.register(taskName, JavaExec::class.java) {
                    inputs.file(wsdlFile)
                    outputs.dir(outputDir)

                    classpath = wsdlConfig
                    mainClass.set("org.apache.cxf.tools.wsdlto.WSDLToJava")

                    argumentProviders.add(CommandLineArgumentProvider {
                        val basePkg = extension.wsdlBasePackage.get()
                        val sanitizedWsdlName = wsdlFile.nameWithoutExtension.replace("-", "").replace("_", "").lowercase(Locale.getDefault())
                        val targetPkg = "$basePkg.$sanitizedWsdlName"

                        listOf(
                            "-d", outputDir.get().asFile.absolutePath,
                            "-p", targetPkg,
                            wsdlFile.absolutePath
                        )
                    })
                }
                apiGenTasks.add(task)
            }
        }

        project.afterEvaluate {
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")
                for (taskProvider in apiGenTasks) {
                    mainSourceSet.java.srcDir(taskProvider)

                    project.tasks.matching { it.name == "compileJava" }.configureEach {
                        dependsOn(taskProvider)
                    }
                    project.tasks.matching { it.name == "compileKotlin" }.configureEach {
                        dependsOn(taskProvider)
                    }
                }
            }
        }
    }
}
