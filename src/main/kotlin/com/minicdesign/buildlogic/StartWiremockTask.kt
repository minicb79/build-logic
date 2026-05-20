package com.minicdesign.buildlogic

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Starts a background/blocking server process")
abstract class StartWiremockTask : DefaultTask() {

    @get:Input
    abstract val port: Property<Int>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun start() {
        val rootPath = rootDir.get().asFile.absolutePath
        val portValue = port.get()

        val server = WireMockServer(options().port(portValue).usingFilesUnderDirectory(rootPath))
        server.start()
        println("WireMock server started on port $portValue using root directory $rootPath")

        val background = project.hasProperty("wiremock.background") &&
                project.property("wiremock.background").toString() == "true"

        if (background) {
            WiremockRegistry.register(project.path, server)
            println("WireMock running in background. Run 'stopWiremock' task to stop.")
        } else {
            println("Running in blocking mode.")
            if (System.console() == null) {
                println("No interactive console detected. Keeping server alive. Press Ctrl+C to terminate...")
                try {
                    while (true) {
                        Thread.sleep(1000)
                    }
                } catch (e: InterruptedException) {
                    server.stop()
                    println("WireMock server stopped.")
                }
            } else {
                println("Press Enter to stop...")
                val scanner = java.util.Scanner(System.`in`)
                try {
                    if (scanner.hasNextLine()) {
                        scanner.nextLine()
                    }
                } finally {
                    server.stop()
                    println("WireMock server stopped.")
                }
            }
        }
    }
}
