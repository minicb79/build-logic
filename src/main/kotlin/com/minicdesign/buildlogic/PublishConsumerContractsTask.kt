package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

import org.gradle.work.DisableCachingByDefault
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@DisableCachingByDefault(because = "Not worth caching")
abstract class PublishConsumerContractsTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val brokerUrl: Property<String>

    @get:Input
    abstract val consumerVersion: Property<String>

    @get:Input
    abstract val branch: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pactFilesDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val dir = pactFilesDir.get().asFile
        if (!dir.exists() || dir.listFiles()?.isEmpty() == true) {
            println("No generated pacts found in ${dir.absolutePath}. Skipping publishing.")
            return
        }

        execOperations.exec {
            commandLine(
                "docker", "run", "--rm",
                "-v", "${dir.absolutePath}:/pacts",
                "pactfoundation/pact-cli:latest",
                "pact-broker", "publish",
                "/pacts",
                "--consumer-app-version=${consumerVersion.get()}",
                "--branch=${branch.get()}",
                "--broker-base-url=${brokerUrl.get()}"
            )
        }
    }
}
