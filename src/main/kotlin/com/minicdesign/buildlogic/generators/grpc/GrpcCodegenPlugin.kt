package com.minicdesign.buildlogic.generators.grpc

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.ProtobufPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

class GrpcCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Ensure java plugin is applied for source sets and compiling
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProtobufPlugin::class.java)

        val extension = project.extensions.create("grpcCodegen", GrpcCodegenExtension::class.java).apply {
            protobufVersion.convention("3.25.5")
            grpcVersion.convention("1.68.1")
            generateGrpc.convention(true)
            outputDir.convention(project.layout.buildDirectory.dir("generated/sources/proto/main/java"))
            grpcOutputDir.convention(project.layout.buildDirectory.dir("generated/sources/proto/main/grpc"))
            protoDirectories.convention(listOf(
                "contract/proto/in",
                "contract/proto/out",
                "contract/proto",
                "contracts/proto/in",
                "contracts/proto/out",
                "contracts/proto",
                "src/main/proto"
            ))
        }

        // Register alias lifecycle task
        project.tasks.register("grpcCodeGen") {
            group = "grpc"
            description = "Alias for generateProto matching codegen conventions."
            dependsOn("generateProto")
        }

        project.afterEvaluate {
            val protoVer = extension.protobufVersion.get()
            val grpcVer = extension.grpcVersion.get()
            val genGrpc = extension.generateGrpc.get()

            // 1. Configure Protobuf Gradle Plugin
            val protobuf = project.extensions.getByType(ProtobufExtension::class.java)
            protobuf.protoc {
                artifact = "com.google.protobuf:protoc:$protoVer"
            }

            if (genGrpc) {
                protobuf.plugins {
                    create("grpc") {
                        artifact = "io.grpc:protoc-gen-grpc-java:$grpcVer"
                    }
                }
            }

            protobuf.generateProtoTasks {
                all().forEach { task ->
                    task.builtins {
                        named("java")
                    }
                    if (genGrpc) {
                        task.plugins {
                            create("grpc")
                        }
                    }
                }
            }

            // 2. Add dependencies to implementation configuration
            if (project.configurations.findByName("implementation") != null) {
                project.dependencies.add("implementation", "com.google.protobuf:protobuf-java:$protoVer")
                project.dependencies.add("implementation", "jakarta.annotation:jakarta.annotation-api:3.0.0")
                if (genGrpc) {
                    project.dependencies.add("implementation", "io.grpc:grpc-stub:$grpcVer")
                    project.dependencies.add("implementation", "io.grpc:grpc-protobuf:$grpcVer")
                }
            }

            // 3. Configure convention directories for proto source set
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val mainSourceSet = javaExt.sourceSets.getByName("main")
                val protoSourceSet = (mainSourceSet as? ExtensionAware)?.extensions?.findByName("proto") as? SourceDirectorySet

                if (protoSourceSet != null) {
                    for (dirPath in extension.protoDirectories.get()) {
                        val dir = project.file(dirPath)
                        if (dir.exists() && dir.isDirectory) {
                            protoSourceSet.srcDir(dir)
                        }
                    }
                }

                // Ensure compiler tasks depend on generateProto
                project.tasks.matching { it.name == "compileJava" }.configureEach { dependsOn("generateProto") }
                project.tasks.matching { it.name == "compileKotlin" }.configureEach { dependsOn("generateProto") }
            }

            // 4. IntelliJ IDEA generated source directory registration
            project.plugins.withType(IdeaPlugin::class.java) {
                val ideaModel = project.extensions.getByType(IdeaModel::class.java)
                val outJava = extension.outputDir.get().asFile
                val outGrpc = extension.grpcOutputDir.get().asFile
                ideaModel.module.generatedSourceDirs.add(outJava)
                ideaModel.module.excludeDirs.remove(outJava)
                if (genGrpc) {
                    ideaModel.module.generatedSourceDirs.add(outGrpc)
                    ideaModel.module.excludeDirs.remove(outGrpc)
                }
            }
        }
    }
}
