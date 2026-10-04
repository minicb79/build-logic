package com.minicdesign.buildlogic.dockercompose

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Aggregates statuses from other tasks and daemon processes")
abstract class DockerComposeStatusTask : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val composeConfigs: ListProperty<DockerComposeConfig>

    @TaskAction
    fun checkAll() {
        println()
        println("Project-Wide Docker Compose Status:")
        println("=".repeat(125))
        println(String.format("%-25s %-15s %-20s %-8s %-25s %-30s", "Submodule", "Component", "Service", "State", "Configured Ports", "Active Mappings"))
        println("=".repeat(125))

        val configs = composeConfigs.getOrElse(emptyList())

        if (configs.isEmpty()) {
            println("No Docker Compose configurations found in the project.")
        } else {
            for (config in configs) {
                val statuses = try {
                    retrieveContainersStatus(config)
                } catch (e: Exception) {
                    println("Failed to read status for project ${config.submodulePath} component ${config.componentName}: ${e.message}")
                    emptyList()
                }

                val subPath = config.submodulePath
                val comp = config.componentName

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

    private fun retrieveContainersStatus(config: DockerComposeConfig): List<ContainerStatus> {
        val composeCmd = resolveDockerComposeCommand(config)
        val file = config.composeFile

        // 1. Get defined services
        val servicesListOutput = executeCommand(config, composeCmd + listOf("-f", file.absolutePath, "config", "--services"))
        val services = servicesListOutput.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (services.isEmpty()) {
            return emptyList()
        }

        // 2. Get configured ports using JsonSlurper
        val portsMap = mutableMapOf<String, List<String>>()
        try {
            val jsonStr = executeCommand(config, composeCmd + listOf("-f", file.absolutePath, "config", "--format", "json"))
            val slurper = JsonSlurper()
            val result = slurper.parseText(jsonStr) as? Map<*, *>
            val servicesConfig = result?.get("services") as? Map<*, *>
            if (servicesConfig != null) {
                for ((serviceName, configObj) in servicesConfig) {
                    val name = serviceName.toString()
                    val serviceCfg = configObj as? Map<*, *> ?: continue
                    val portsList = serviceCfg["ports"] as? List<*> ?: continue
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
        val psOutput = executeCommand(config, composeCmd + listOf("-f", file.absolutePath, "ps", "-a", "--format", "{{.Service}} {{.State}} {{.Ports}}"))
        val activeStateMap = mutableMapOf<String, Pair<String, String>>()
        if (psOutput.isNotEmpty()) {
            psOutput.split("\n").forEach { line ->
                val parts = line.trim().split("\\s+".toRegex(), 3)
                if (parts.size >= 2) {
                    val serviceName = parts[0]
                    val state = parts[1]
                    val ports = if (parts.size == 3) parts[2] else ""
                    
                    val existing = activeStateMap[serviceName]
                    val isNewUp = state.lowercase().startsWith("running") || state.lowercase().startsWith("up")
                    val isExistingUp = existing?.first?.lowercase()?.startsWith("running") == true || existing?.first?.lowercase()?.startsWith("up") == true
                    
                    if (existing == null || (isNewUp && !isExistingUp)) {
                        activeStateMap[serviceName] = Pair(state, ports)
                    }
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

    private fun resolveDockerComposeCommand(config: DockerComposeConfig): List<String> {
        val configuredPath = config.dockerComposePath
        if (!configuredPath.isNullOrBlank()) {
            return configuredPath.split("\\s+".toRegex())
        }

        // Auto-detect 1: docker-compose --version
        if (isCommandAvailable(listOf("docker-compose", "--version"))) {
            return listOf("docker-compose")
        }

        // Auto-detect 2: docker compose version
        val dockerBin = config.dockerPath ?: "docker"
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

    private fun executeCommand(config: DockerComposeConfig, args: List<String>, workingDir: File = config.composeFile.parentFile): String {
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
}
