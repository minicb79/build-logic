package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class CanIDeployTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val brokerUrl: Property<String>

    @get:Input
    abstract val pacticipantName: Property<String>

    @get:Input
    abstract val pacticipantVersion: Property<String>

    @get:Input
    abstract val toEnvironment: Property<String>

    @TaskAction
    fun execute() {
        execOperations.exec {
            commandLine(
                "docker", "run", "--rm",
                "pactfoundation/pact-cli:latest",
                "pact-broker", "can-i-deploy",
                "--pacticipant=${pacticipantName.get()}",
                "--version=${pacticipantVersion.get()}",
                "--to-environment=${toEnvironment.get()}",
                "--broker-base-url=${brokerUrl.get()}"
            )
        }
    }
}
