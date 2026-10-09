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
abstract class InfraInitSecretsTask : DefaultTask() {

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
    abstract val secretsModule: Property<String>

    @get:Input
    abstract val flociPort: Property<Int>

    init {
        group = "infra"
        description = "Phase 1: Provisions Secrets Manager placeholders via Terraform and checks secret initialization."
    }

    @TaskAction
    fun initSecrets() {
        val tfDir = terraformDir.get().asFile
        if (!tfDir.exists()) {
            throw GradleException("Terraform directory not found at ${tfDir.absolutePath}.")
        }

        val tf = terraformPath.get()
        val env = targetEnvironment.get()
        val moduleTarget = secretsModule.get()
        val port = flociPort.get()

        val awsEnv = mapOf(
            "AWS_ENDPOINT_URL" to "http://localhost:$port",
            "AWS_DEFAULT_REGION" to "us-east-1",
            "AWS_ACCESS_KEY_ID" to "mock_key",
            "AWS_SECRET_ACCESS_KEY" to "mock_secret"
        )

        // 1. Ensure terraform init is run
        val dotTerraform = tfDir.resolve(".terraform")
        if (!dotTerraform.exists()) {
            logger.lifecycle("Running 'terraform init' in ${tfDir.absolutePath}...")
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

        // 2. Targeted apply for secrets module
        val varFile = tfDir.resolve("environments/$env.tfvars")
        val applyArgs = mutableListOf(tf, "apply", "-target=$moduleTarget", "-auto-approve")
        if (varFile.exists()) {
            applyArgs.addAll(listOf("-var-file", varFile.absolutePath))
        }

        logger.lifecycle("Executing Phase 1 partial deployment: provisioning secret placeholders with '$moduleTarget'...")
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
            throw GradleException("Phase 1 partial deployment failed:\n${applyOut.toString().trim()}")
        }

        logger.lifecycle(applyOut.toString().trim())
        logger.lifecycle("================================================================================")
        logger.lifecycle(" [PHASE 1 COMPLETE] Secret Placeholders Provisioned in Floci AWS Secrets Manager")
        logger.lifecycle("================================================================================")
        logger.lifecycle("To populate or rotate sensitive passwords without leaking them into git:")
        logger.lifecycle("Option A: Run the management script:")
        logger.lifecycle("   ./infra/scripts/manage-secrets.sh $env")
        logger.lifecycle("")
        logger.lifecycle("Option B: Use AWS CLI directly against Floci:")
        logger.lifecycle("   aws --endpoint-url=http://localhost:$port secretsmanager put-secret-value \\")
        logger.lifecycle("       --secret-id \"/$env/${project.name}/database\" \\")
        logger.lifecycle("       --secret-string '{\"username\":\"admin\",\"password\":\"<YOUR_PASSWORD>\"}'")
        logger.lifecycle("================================================================================")
    }
}
