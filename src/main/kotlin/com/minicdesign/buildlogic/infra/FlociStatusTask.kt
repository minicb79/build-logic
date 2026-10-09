package com.minicdesign.buildlogic.infra

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

@DisableCachingByDefault(because = "Queries external Docker and Floci processes")
abstract class FlociStatusTask : DefaultTask() {

    @get:javax.inject.Inject
    abstract val execOperations: org.gradle.process.ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dockerComposeDir: DirectoryProperty

    @get:Input
    abstract val dockerPath: Property<String>

    @get:Input
    abstract val flociPort: Property<Int>

    init {
        group = "infra"
        description = "Displays the status of Floci mock cloud and ingress services."
    }

    @TaskAction
    fun checkStatus() {
        val composeDir = dockerComposeDir.get().asFile
        val composeFile = listOf(
            composeDir.resolve("docker-compose.yml"),
            composeDir.resolve("docker-compose.yaml"),
            composeDir.resolve("compose.yml")
        ).firstOrNull { it.exists() && it.isFile }

        val dPath = dockerPath.get()
        val port = flociPort.get()

        logger.lifecycle("================================================================================")
        logger.lifecycle(" Floci Mock Cloud Status Dashboard")
        logger.lifecycle("================================================================================")

        // 1. Docker Compose process status
        if (composeFile != null) {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                workingDir = composeDir
                commandLine(dPath, "compose", "-f", composeFile.name, "ps")
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            logger.lifecycle("Containers:")
            logger.lifecycle(output.toString().trim())
        } else {
            logger.lifecycle("Containers: No docker-compose.yml found in ${composeDir.absolutePath}")
        }

        // 2. Query Floci Health
        logger.lifecycle("\nFloci Cloud Endpoint Health (http://localhost:$port):")
        var isHealthy = false
        val endpoints = listOf("http://localhost:$port/_floci/health", "http://localhost:$port/_localstack/health")
        for (ep in endpoints) {
            try {
                val conn = URI(ep).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    logger.lifecycle("[ONLINE] Floci responded on $ep:\n$body")
                    isHealthy = true
                    break
                }
            } catch (_: Exception) {
            }
        }

        if (!isHealthy) {
            logger.lifecycle("[OFFLINE] Floci port $port is not responding. Run './gradlew flociStart' to launch it.")
        }
        logger.lifecycle("================================================================================")
    }
}
