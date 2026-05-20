package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Stops a background server process")
abstract class StopWiremockTask : DefaultTask() {

    @TaskAction
    fun stop() {
        WiremockRegistry.stop(project.path)
        println("WireMock server for project ${project.path} stopped.")
    }
}
