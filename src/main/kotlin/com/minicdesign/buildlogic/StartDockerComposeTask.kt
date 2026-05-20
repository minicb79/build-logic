package com.minicdesign.buildlogic

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Interacts with external Docker daemon process")
abstract class StartDockerComposeTask : BaseDockerComposeTask() {

    @TaskAction
    fun start() {
        val statuses = retrieveContainersStatus()
        val downServices = statuses.filter { !it.isUp }.map { it.service }

        if (downServices.isEmpty()) {
            println("All containers for component [${componentName.get()}] in submodule [${submodulePath.get()}] are already UP. Skipping start.")
            return
        }

        println("Starting DOWN containers for component [${componentName.get()}] in submodule [${submodulePath.get()}]: $downServices")
        val composeCmd = resolveDockerComposeCommand()
        val file = composeFile.get().asFile

        val upArgs = composeCmd + listOf("-f", file.absolutePath, "up", "-d") + downServices
        val output = executeCommand(upArgs)
        if (output.isNotEmpty()) {
            println(output)
        }
        println("Component [${componentName.get()}] containers started successfully.")
    }
}
