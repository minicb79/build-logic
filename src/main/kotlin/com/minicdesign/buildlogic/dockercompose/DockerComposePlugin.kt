package com.minicdesign.buildlogic.dockercompose

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

interface DockerComposeExtension {
    val dockerPath: Property<String>
    val dockerComposePath: Property<String>
}

class DockerComposePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("dockerCompose", DockerComposeExtension::class.java).apply {
            dockerPath.convention("docker")
        }

        // Always register the status task on the root project so it can collect configs from all subprojects
        val rootProj = project.rootProject
        val statusTaskProvider = if (rootProj.tasks.findByName("dockerComposeStatus") == null) {
            rootProj.tasks.register("dockerComposeStatus", DockerComposeStatusTask::class.java) {
                group = "docker"
                description = "Displays unified project-wide Docker compose container status and port mappings."
            }
        } else {
            rootProj.tasks.named("dockerComposeStatus", DockerComposeStatusTask::class.java)
        }

        val dockerDir = project.projectDir.resolve("docker")
        if (dockerDir.exists() && dockerDir.isDirectory) {
            dockerDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                val composeFile = subDir.listFiles()?.firstOrNull {
                    it.isFile && (it.name == "docker-compose.yml" || it.name == "docker-compose.yaml" ||
                            it.name == "compose.yml" || it.name == "compose.yaml")
                }
                if (composeFile != null) {
                    val componentName = subDir.name
                    val capitalizedComponent = componentName.replaceFirstChar { it.uppercase() }

                    project.tasks.register("check${capitalizedComponent}", CheckDockerComposeTask::class.java) {
                        group = "docker"
                        description = "Checks status of Docker Compose services for $componentName."
                        this.composeFile.set(composeFile)
                        this.submodulePath.set(project.path)
                        this.componentName.set(componentName)
                        this.dockerPath.set(extension.dockerPath)
                        this.dockerComposePath.set(extension.dockerComposePath)
                    }

                    project.tasks.register("start${capitalizedComponent}", StartDockerComposeTask::class.java) {
                        group = "docker"
                        description = "Starts Docker Compose services for $componentName."
                        this.composeFile.set(composeFile)
                        this.submodulePath.set(project.path)
                        this.componentName.set(componentName)
                        this.dockerPath.set(extension.dockerPath)
                        this.dockerComposePath.set(extension.dockerComposePath)
                    }

                    project.tasks.register("stop${capitalizedComponent}", StopDockerComposeTask::class.java) {
                        group = "docker"
                        description = "Stops Docker Compose services for $componentName."
                        this.composeFile.set(composeFile)
                        this.submodulePath.set(project.path)
                        this.componentName.set(componentName)
                        this.dockerPath.set(extension.dockerPath)
                        this.dockerComposePath.set(extension.dockerComposePath)
                    }

                    // Feed this component's configuration to the status task
                    statusTaskProvider.configure {
                        composeConfigs.add(DockerComposeConfig(
                            submodulePath = project.path,
                            componentName = componentName,
                            composeFile = composeFile,
                            dockerPath = extension.dockerPath.orNull,
                            dockerComposePath = extension.dockerComposePath.orNull
                        ))
                    }
                }
            }
        }
    }
}
