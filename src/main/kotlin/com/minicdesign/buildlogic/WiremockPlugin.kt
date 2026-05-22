package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

interface WiremockExtension {
    val port: Property<Int>
    val rootDir: DirectoryProperty
    val serverUrl: Property<String>
    val checkMissingOnly: Property<Boolean>
}

class WiremockPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("wiremock", WiremockExtension::class.java).apply {
            port.convention(8080)
            rootDir.convention(project.layout.buildDirectory.dir("wiremock/merged"))
            serverUrl.convention(port.map { "http://localhost:$it" })
            checkMissingOnly.convention(serverUrl.map { url ->
                !(url.contains("localhost") || url.contains("127.0.0.1"))
            })
        }

        val mergeWiremockSources = project.tasks.register("mergeWiremockSources", MergeWiremockSourcesTask::class.java) {
            outputDir.set(extension.rootDir)
        }

        project.tasks.register("startWiremock", StartWiremockTask::class.java) {
            port.set(extension.port)
            rootDir.set(extension.rootDir)
            dependsOn(mergeWiremockSources)
        }

        project.tasks.register("stopWiremock", StopWiremockTask::class.java)

        project.tasks.register("publishWiremock", PublishWiremockTask::class.java) {
            val urlProp = project.providers.gradleProperty("wiremock.serverUrl")
            val diffProp = project.providers.gradleProperty("wiremock.checkMissingOnly").map { it.toBoolean() }

            serverUrl.set(urlProp.orElse(extension.serverUrl))
            checkMissingOnly.set(diffProp.orElse(extension.checkMissingOnly))
            rootDir.set(extension.rootDir)
            dependsOn(mergeWiremockSources)
        }
    }
}
