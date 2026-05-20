package com.minicdesign.buildlogic

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Interacts with external Docker daemon process")
abstract class StopDockerComposeTask : BaseDockerComposeTask() {

    @TaskAction
    fun stop() {
        val statuses = retrieveContainersStatus()
        val upServices = statuses.filter { it.isUp }.map { it.service }

        if (upServices.isEmpty()) {
            println("All containers for component [${componentName.get()}] in submodule [${submodulePath.get()}] are already stopped. Skipping stop.")
            return
        }

        println("Stopping running containers for component [${componentName.get()}] in submodule [${submodulePath.get()}]: $upServices")
        val composeCmd = resolveDockerComposeCommand()
        val file = composeFile.get().asFile

        val stopArgs = composeCmd + listOf("-f", file.absolutePath, "stop") + upServices
        val output = executeCommand(stopArgs)
        if (output.isNotEmpty()) {
            println(output)
        }
        println("Component [${componentName.get()}] containers stopped successfully.")
    }
}
