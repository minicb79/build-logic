package com.minicdesign.buildlogic.infra

import org.gradle.api.Plugin
import org.gradle.api.Project

class InfraPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("infra", InfraExtension::class.java).apply {
            environment.convention("dev")
            serviceName.convention(project.name)
            domainName.convention("minicdesign.com")
            flociPort.convention(4566)
            ingressPort.convention(8080)
            val tfPath = if (project.file("terraform").exists()) "terraform" else "infra/terraform"
            val dcPath = if (project.file("docker").exists()) "docker" else "infra/docker"
            terraformDir.convention(project.layout.projectDirectory.dir(tfPath))
            dockerComposeDir.convention(project.layout.projectDirectory.dir(dcPath))
            syncOpenApiScopes.convention(true)
            persistentState.convention(true)
            secretsModule.convention("module.secrets")
            dockerPath.convention("docker")
            terraformPath.convention("terraform")
            awsPath.convention("aws")
        }

        // 1. Docker Compose / Floci Lifecycle Tasks
        val flociStartTask = project.tasks.register("flociStart", FlociStartTask::class.java) {
            dockerComposeDir.set(extension.dockerComposeDir)
            dockerPath.set(extension.dockerPath)
            flociPort.set(extension.flociPort)
        }

        project.tasks.register("flociStop", FlociStopTask::class.java) {
            dockerComposeDir.set(extension.dockerComposeDir)
            dockerPath.set(extension.dockerPath)
        }

        project.tasks.register("flociStatus", FlociStatusTask::class.java) {
            dockerComposeDir.set(extension.dockerComposeDir)
            dockerPath.set(extension.dockerPath)
            flociPort.set(extension.flociPort)
        }

        // 2. Host Aliases Verification
        project.tasks.register("infraConfigureHosts", InfraConfigureHostsTask::class.java) {
            environment.set(extension.environment)
            serviceName.set(extension.serviceName)
            domainName.set(extension.domainName)
            ingressPort.set(extension.ingressPort)
        }

        // 3. Contract Scope Synchronization
        val syncScopesTask = project.tasks.register("syncOpenApiScopes", SyncOpenApiScopesTask::class.java) {
            val contracts = if (project.file("contracts/openapi").exists()) {
                project.layout.projectDirectory.dir("contracts/openapi")
            } else if (project.file("contract/openapi").exists()) {
                project.layout.projectDirectory.dir("contract/openapi")
            } else {
                project.layout.projectDirectory.dir("contracts")
            }
            contractsDir.set(contracts)
            terraformDir.set(extension.terraformDir)
        }

        // 4. Phase 1: Secrets Placeholder & Partial Deployment
        val initSecretsTask = project.tasks.register("infraInitSecrets", InfraInitSecretsTask::class.java) {
            terraformDir.set(extension.terraformDir)
            targetEnvironment.set(extension.environment)
            terraformPath.set(extension.terraformPath)
            secretsModule.set(extension.secretsModule)
            flociPort.set(extension.flociPort)
            dependsOn(flociStartTask)
        }

        // 5. Phase 2: Full Terraform Apply
        val applyTask = project.tasks.register("infraApply", InfraApplyTask::class.java) {
            terraformDir.set(extension.terraformDir)
            targetEnvironment.set(extension.environment)
            terraformPath.set(extension.terraformPath)
            flociPort.set(extension.flociPort)
            dependsOn(flociStartTask)
            dependsOn(project.provider {
                if (extension.syncOpenApiScopes.get()) listOf(syncScopesTask) else emptyList()
            })
        }

        // 6. Terraform Destroy
        project.tasks.register("infraDestroy", InfraDestroyTask::class.java) {
            terraformDir.set(extension.terraformDir)
            targetEnvironment.set(extension.environment)
            terraformPath.set(extension.terraformPath)
            flociPort.set(extension.flociPort)
        }

        // Execution ordering
        syncScopesTask.configure { mustRunAfter(flociStartTask) }
        initSecretsTask.configure { mustRunAfter(syncScopesTask) }
        applyTask.configure { mustRunAfter(initSecretsTask) }

        // 7. Master Deployment Orchestration
        project.tasks.register("infraDeploy") {
            group = "infra"
            description = "Orchestrates full pseudo cloud deployment: starts Floci, syncs scopes, inits secrets, and applies infrastructure."
            dependsOn(flociStartTask)
            dependsOn(project.provider {
                if (extension.syncOpenApiScopes.get()) listOf(syncScopesTask) else emptyList()
            })
            dependsOn(initSecretsTask)
            dependsOn(applyTask)
        }
    }
}
