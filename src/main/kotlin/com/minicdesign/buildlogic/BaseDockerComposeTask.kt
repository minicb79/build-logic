package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.process.ExecOperations

@DisableCachingByDefault(because = "Interacts with external Docker daemon process")
abstract class BaseDockerComposeTask : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    @get:Optional
    abstract val dockerPath: Property<String>

    @get:Input
    @get:Optional
    abstract val dockerComposePath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composeFile: RegularFileProperty

    @get:Input
    abstract val submodulePath: Property<String>

    @get:Input
    abstract val componentName: Property<String>

    protected fun resolveDockerComposeCommand(): List<String> {
        val configuredPath = dockerComposePath.orNull
        if (!configuredPath.isNullOrBlank()) {
            return configuredPath.split("\\s+".toRegex())
        }

        // Auto-detect 1: docker-compose --version
        if (isCommandAvailable(listOf("docker-compose", "--version"))) {
            return listOf("docker-compose")
        }

        // Auto-detect 2: docker compose version
        val dockerBin = dockerPath.getOrElse("docker")
        if (isCommandAvailable(listOf(dockerBin, "compose", "version"))) {
            return listOf(dockerBin, "compose")
        }

        return listOf(dockerBin, "compose")
    }

    private fun isCommandAvailable(cmd: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(cmd).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    protected fun executeCommand(args: List<String>, workingDir: File = composeFile.get().asFile.parentFile): String {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(args)
            workingDir(workingDir)
            standardOutput = outputStream
            errorOutput = errorStream
            isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            val errMsg = errorStream.toString().trim()
            throw RuntimeException("Command failed: ${args.joinToString(" ")}\nExit Value: ${result.exitValue}\nError: $errMsg")
        }

        return outputStream.toString().trim()
    }

    data class ContainerStatus(
        val service: String,
        val state: String,
        val isUp: Boolean,
        val configuredPorts: List<String>,
        val activePorts: String
    )

    protected fun retrieveContainersStatus(): List<ContainerStatus> {
        val composeCmd = resolveDockerComposeCommand()
        val file = composeFile.get().asFile

        // 1. Get defined services
        val servicesListOutput = executeCommand(composeCmd + listOf("-f", file.absolutePath, "config", "--services"))
        val services = servicesListOutput.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (services.isEmpty()) {
            return emptyList()
        }

        // 2. Get configured ports using JsonSlurper
        val portsMap = mutableMapOf<String, List<String>>()
        try {
            val jsonStr = executeCommand(composeCmd + listOf("-f", file.absolutePath, "config", "--format", "json"))
            val slurper = JsonSlurper()
            val result = slurper.parseText(jsonStr) as? Map<*, *>
            val servicesConfig = result?.get("services") as? Map<*, *>
            if (servicesConfig != null) {
                for ((serviceName, configObj) in servicesConfig) {
                    val name = serviceName.toString()
                    val config = configObj as? Map<*, *> ?: continue
                    val portsList = config["ports"] as? List<*> ?: continue
                    val mappedPorts = mutableListOf<String>()
                    for (portEntry in portsList) {
                        if (portEntry is Map<*, *>) {
                            val published = portEntry["published"]
                            val target = portEntry["target"]
                            if (published != null && target != null) {
                                mappedPorts.add("$published->$target")
                            }
                        } else if (portEntry is String) {
                            mappedPorts.add(portEntry)
                        }
                    }
                    portsMap[name] = mappedPorts
                }
            }
        } catch (e: Exception) {
            // Silence parsing errors, fallback to empty list of configured ports
        }

        // 3. Get running state and active mappings
        val psOutput = executeCommand(composeCmd + listOf("-f", file.absolutePath, "ps", "-a", "--format", "{{.Service}} {{.State}} {{.Ports}}"))
        val activeStateMap = mutableMapOf<String, Pair<String, String>>()
        if (psOutput.isNotEmpty()) {
            psOutput.split("\n").forEach { line ->
                val parts = line.trim().split("\\s+".toRegex(), 3)
                if (parts.size >= 2) {
                    val serviceName = parts[0]
                    val state = parts[1]
                    val ports = if (parts.size == 3) parts[2] else ""
                    activeStateMap[serviceName] = Pair(state, ports)
                }
            }
        }

        // 4. Build output statuses
        return services.map { service ->
            val activePair = activeStateMap[service]
            val state = activePair?.first ?: "down"
            val activePorts = activePair?.second ?: "-"
            val isUp = state.lowercase().startsWith("running")
            val configured = portsMap[service] ?: emptyList()

            ContainerStatus(
                service = service,
                state = state,
                isUp = isUp,
                configuredPorts = configured,
                activePorts = activePorts
            )
        }
    }

    fun retrieveContainersStatusExternal(): List<ContainerStatus> {
        return retrieveContainersStatus()
    }
}
