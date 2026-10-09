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

@DisableCachingByDefault(because = "Executes Terraform against external Floci mock cloud")
abstract class InfraApplyTask : DefaultTask() {

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

    @get:Input
    abstract val flociEndpoint: Property<String>

    init {
        group = "infra"
        description = "Phase 2: Executes full Terraform apply against Floci cloud."
    }

    @TaskAction
    fun applyInfra() {
        val tfDir = terraformDir.get().asFile
        if (!tfDir.exists()) {
            throw GradleException("Terraform directory not found at ${tfDir.absolutePath}.")
        }

        val tf = terraformPath.get()
        val env = targetEnvironment.get()
        val port = flociPort.get()
        val endpoint = flociEndpoint.get().trimEnd('/')

        val awsEnv = mapOf(
            "AWS_ENDPOINT_URL" to endpoint,
            "AWS_DEFAULT_REGION" to "us-east-1",
            "AWS_ACCESS_KEY_ID" to "mock_key",
            "AWS_SECRET_ACCESS_KEY" to "mock_secret"
        )

        val dotTerraform = tfDir.resolve(".terraform")
        if (!dotTerraform.exists()) {
            val initOut = ByteArrayOutputStream()
            val initResult = execOperations.exec {
                workingDir = tfDir
                commandLine(tf, "init")
                environment(awsEnv)
                standardOutput = initOut
                errorOutput = initOut
                isIgnoreExitValue = true
            }
            if (initResult.exitValue != 0) {
                throw GradleException("Failed to run terraform init:\n${initOut.toString().trim()}")
            }
        }

        val varFile = tfDir.resolve("environments/$env.tfvars")
        val applyArgs = mutableListOf(
            tf, "apply", "-auto-approve",
            "-var=floci_endpoint=$endpoint"
        )
        if (varFile.exists()) {
            applyArgs.addAll(listOf("-var-file", varFile.absolutePath))
        }

        logger.lifecycle("Executing Phase 2 full Terraform apply for environment '$env' against Floci ($endpoint)...")
        val applyOut = ByteArrayOutputStream()
        val applyResult = execOperations.exec {
            workingDir = tfDir
            commandLine(applyArgs)
            environment(awsEnv)
            standardOutput = applyOut
            errorOutput = applyOut
            isIgnoreExitValue = true
        }

        if (applyResult.exitValue != 0) {
            throw GradleException("Terraform apply failed:\n${applyOut.toString().trim()}")
        }

        logger.lifecycle(applyOut.toString().trim())
        logger.lifecycle("[SUCCESS] Cloud infrastructure for '${project.name}' successfully applied in Floci ($env environment).")
    }
}
