package com.minicdesign.buildlogic.infra

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream

@DisableCachingByDefault(because = "Destroys resources in external Floci mock cloud")
abstract class InfraDestroyTask : DefaultTask() {

    @get:javax.inject.Inject
    abstract val execOperations: org.gradle.process.ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val terraformDir: DirectoryProperty

    @get:Input
    abstract val targetEnvironment: Property<String>

    @get:Input
    abstract val terraformPath: Property<String>

    @get:Input
    abstract val flociPort: Property<Int>

    init {
        group = "infra"
        description = "Destroys Terraform-managed mock cloud resources in Floci."
    }

    @TaskAction
    fun destroyInfra() {
        val tfDir = terraformDir.get().asFile
        if (!tfDir.exists()) {
            logger.lifecycle("No terraform directory at ${tfDir.absolutePath}. Nothing to destroy.")
            return
        }

        val tf = terraformPath.get()
        val env = targetEnvironment.get()
        val port = flociPort.get()

        val awsEnv = mapOf(
            "AWS_ENDPOINT_URL" to "http://localhost:$port",
            "AWS_DEFAULT_REGION" to "us-east-1",
            "AWS_ACCESS_KEY_ID" to "mock_key",
            "AWS_SECRET_ACCESS_KEY" to "mock_secret"
        )

        val varFile = tfDir.resolve("environments/$env.tfvars")
        val destroyArgs = mutableListOf(tf, "destroy", "-auto-approve")
        if (varFile.exists()) {
            destroyArgs.addAll(listOf("-var-file", varFile.absolutePath))
        }

        logger.lifecycle("Executing Terraform destroy for environment '$env'...")
        val out = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir = tfDir
            commandLine(destroyArgs)
            environment(awsEnv)
            standardOutput = out
            errorOutput = out
            isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            throw GradleException("Terraform destroy failed:\n${out.toString().trim()}")
        }

        logger.lifecycle(out.toString().trim())
        logger.lifecycle("[SUCCESS] Cloud resources for environment '$env' destroyed in Floci.")
    }
}
