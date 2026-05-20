package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Aggregates statuses from other tasks and daemon processes")
abstract class DockerComposeStatusTask : DefaultTask() {

    @TaskAction
    fun checkAll() {
        val checkTasks = project.allprojects.flatMap { proj ->
            proj.tasks.withType(CheckDockerComposeTask::class.java)
        }

        println()
        println("Project-Wide Docker Compose Status:")
        println("=".repeat(125))
        println(String.format("%-25s %-15s %-20s %-8s %-25s %-30s", "Submodule", "Component", "Service", "State", "Configured Ports", "Active Mappings"))
        println("=".repeat(125))

        if (checkTasks.isEmpty()) {
            println("No Docker Compose configurations found in the project.")
        } else {
            for (task in checkTasks) {
                val statuses = try {
                    task.retrieveContainersStatusExternal()
                } catch (e: Exception) {
                    println("Failed to read status for project ${task.project.path} component ${task.componentName.get()}: ${e.message}")
                    emptyList()
                }

                val subPath = task.submodulePath.get()
                val comp = task.componentName.get()

                for (status in statuses) {
                    val stateLabel = if (status.isUp) "UP" else "DOWN"
                    val configuredPortsStr = status.configuredPorts.joinToString(", ")
                    println(
                        String.format(
                            "%-25s %-15s %-20s %-8s %-25s %-30s",
                            subPath,
                            comp,
                            status.service,
                            stateLabel,
                            configuredPortsStr,
                            status.activePorts
                        )
                    )
                }
            }
        }
        println("=".repeat(125))
        println()
    }
}
