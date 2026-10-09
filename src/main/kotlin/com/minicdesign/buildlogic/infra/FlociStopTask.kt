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

@DisableCachingByDefault(because = "Interacts with external Docker daemon")
abstract class FlociStopTask : DefaultTask() {

    @get:javax.inject.Inject
    abstract val execOperations: org.gradle.process.ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dockerComposeDir: DirectoryProperty

    @get:Input
    abstract val dockerPath: Property<String>

    init {
        group = "infra"
        description = "Stops Floci local cloud emulator and ingress router."
    }

    @TaskAction
    fun stopFloci() {
        val composeDir = dockerComposeDir.get().asFile
        val composeFile = listOf(
            composeDir.resolve("docker-compose.yml"),
            composeDir.resolve("docker-compose.yaml"),
            composeDir.resolve("compose.yml")
        ).firstOrNull { it.exists() && it.isFile }

        if (composeFile == null) {
            logger.lifecycle("No docker-compose.yml found in ${composeDir.absolutePath}. Nothing to stop.")
            return
        }

        val dPath = dockerPath.get()
        logger.lifecycle("Stopping Floci and ingress containers...")

        val output = ByteArrayOutputStream()
        execOperations.exec {
            workingDir = composeDir
            commandLine(dPath, "compose", "-f", composeFile.name, "down")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }

        logger.lifecycle(output.toString().trim())
        logger.lifecycle("[SUCCESS] Floci local mock cloud stopped.")
    }
}
