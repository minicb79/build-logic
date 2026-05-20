package com.minicdesign.buildlogic

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Interacts with external Docker daemon process")
abstract class CheckDockerComposeTask : BaseDockerComposeTask() {

    @TaskAction
    fun checkStatus() {
        val statuses = retrieveContainersStatus()
        val path = submodulePath.get()

        println()
        println("Docker Compose Status for Component [${componentName.get()}] in Submodule [$path]:")
        println("=".repeat(115))
        println(String.format("%-25s %-20s %-8s %-25s %-30s", "Submodule", "Service", "State", "Configured Ports", "Active Mappings"))
        println("=".repeat(115))

        if (statuses.isEmpty()) {
            println("No services defined in compose file.")
        } else {
            for (status in statuses) {
                val stateLabel = if (status.isUp) "UP" else "DOWN"
                val configuredPortsStr = status.configuredPorts.joinToString(", ")
                println(
                    String.format(
                        "%-25s %-20s %-8s %-25s %-30s",
                        path,
                        status.service,
                        stateLabel,
                        configuredPortsStr,
                        status.activePorts
                    )
                )
            }
        }
        println("=".repeat(115))
        println()
    }
}
