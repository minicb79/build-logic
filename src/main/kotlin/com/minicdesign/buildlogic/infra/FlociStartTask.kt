package com.minicdesign.buildlogic.infra

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

@DisableCachingByDefault(because = "Interacts with external Docker and Floci processes")
abstract class FlociStartTask : DefaultTask() {

    @get:javax.inject.Inject
    abstract val execOperations: org.gradle.process.ExecOperations

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dockerComposeDir: DirectoryProperty

    @get:Input
    abstract val dockerPath: Property<String>

    @get:Input
    abstract val flociPort: Property<Int>

    @get:Input
    abstract val flociEndpoint: Property<String>

    @get:Input
    abstract val remoteServer: Property<Boolean>

    init {
        group = "infra"
        description = "Starts Floci local cloud emulator and ingress router via Docker Compose, or verifies remote Floci readiness."
    }

    @TaskAction
    fun startFloci() {
        val endpoint = flociEndpoint.get().trimEnd('/')
        val isRemote = remoteServer.get()

        if (isRemote) {
            logger.lifecycle("Floci is configured as a remote server ($endpoint). Skipping local Docker Compose startup.")
        } else {
            val composeDir = dockerComposeDir.orNull?.asFile
            if (composeDir == null || !composeDir.exists()) {
                throw GradleException("No dockerComposeDir configured or found. Run scaffolding or configure infra.dockerComposeDir.")
            }
            val composeFile = listOf(
                composeDir.resolve("docker-compose.yml"),
                composeDir.resolve("docker-compose.yaml"),
                composeDir.resolve("compose.yml")
            ).firstOrNull { it.exists() && it.isFile }

            if (composeFile == null) {
                throw GradleException("No docker-compose.yml found in ${composeDir.absolutePath}. Run scaffolding or create infra/docker/docker-compose.yml first.")
            }

            val dPath = dockerPath.get()
            logger.lifecycle("Starting Floci and ingress containers using $dPath compose in ${composeDir.absolutePath}...")

            val output = ByteArrayOutputStream()
            val result = execOperations.exec {
                workingDir = composeDir
                commandLine(dPath, "compose", "-f", composeFile.name, "up", "-d")
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }

            if (result.exitValue != 0) {
                throw GradleException("Failed to start Floci containers:\n${output.toString().trim()}")
            }

            logger.lifecycle(output.toString().trim())
        }

        // Wait for Floci to become healthy
        logger.lifecycle("Waiting for Floci cloud emulator to become ready at $endpoint...")

        val maxAttempts = 30
        var ready = false
        val candidateEndpoints = listOf(
            "$endpoint/_floci/health",
            "$endpoint/_localstack/health",
            "$endpoint/"
        )

        for (attempt in 1..maxAttempts) {
            for (endpoint in candidateEndpoints) {
                try {
                    val conn = URI(endpoint).toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code in 200..499) {
                        ready = true
                        break
                    }
                } catch (_: Exception) {
                    // ignore and retry
                }
            }
            if (ready) break
            Thread.sleep(1000)
        }

        val port = flociPort.get()
        if (ready) {
            logger.lifecycle("[SUCCESS] Floci local mock cloud is ready and listening on port $port.")
        } else {
            logger.warn("[WARNING] Floci port $port did not respond within $maxAttempts seconds. Containers are running, but cloud services may still be initializing.")
        }
    }
}
