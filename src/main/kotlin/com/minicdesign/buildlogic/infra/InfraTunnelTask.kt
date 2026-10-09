package com.minicdesign.buildlogic.infra

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Network tunnel command interacts with external SSH processes")
abstract class InfraTunnelTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val flociPort: Property<Int>

    @get:Input
    abstract val ingressPort: Property<Int>

    @get:Input
    @get:Optional
    abstract val sshTarget: Property<String>

    init {
        group = "infra"
        description = "Establishes or displays an SSH port forwarding tunnel to an external Floci & Traefik server."
    }

    @TaskAction
    fun tunnel() {
        val fPort = flociPort.get()
        val iPort = ingressPort.get()
        val target = sshTarget.orNull

        if (target.isNullOrBlank()) {
            logger.lifecycle("================================================================================")
            logger.lifecycle("SSH Tunnel Configuration Guide")
            logger.lifecycle("================================================================================")
            logger.lifecycle("To forward external Floci ($fPort) and Traefik ($iPort) ports to your local machine,")
            logger.lifecycle("configure your SSH target in build.gradle.kts:")
            logger.lifecycle("")
            logger.lifecycle("infra {")
            logger.lifecycle("    sshTarget.set(\"user@your-remote-server.com\")")
            logger.lifecycle("}")
            logger.lifecycle("")
            logger.lifecycle("Or run manual SSH tunnel:")
            logger.lifecycle("ssh -N -L $fPort:localhost:$fPort -L $iPort:localhost:$iPort user@<remote-server-ip>")
            logger.lifecycle("================================================================================")
            return
        }

        val tunnelCmd = "ssh -N -L $fPort:localhost:$fPort -L $iPort:localhost:$iPort $target"

        val shouldConnect = project.hasProperty("connect") || project.hasProperty("execute")
        if (shouldConnect) {
            logger.lifecycle("Establishing SSH port forwarding tunnel to $target...")
            logger.lifecycle("Running: $tunnelCmd")
            logger.lifecycle("Press Ctrl+C to disconnect the tunnel.")
            execOperations.exec {
                commandLine("ssh", "-N", "-L", "$fPort:localhost:$fPort", "-L", "$iPort:localhost:$iPort", target)
            }
        } else {
            logger.lifecycle("================================================================================")
            logger.lifecycle("SSH Port-Forwarding Tunnel to External Floci Server")
            logger.lifecycle("================================================================================")
            logger.lifecycle("Target Server : $target")
            logger.lifecycle("Floci Port    : $fPort -> localhost:$fPort")
            logger.lifecycle("Ingress Port  : $iPort -> localhost:$iPort")
            logger.lifecycle("")
            logger.lifecycle("To launch the tunnel in the foreground:")
            logger.lifecycle("  ./gradlew infraTunnel -Pconnect")
            logger.lifecycle("")
            logger.lifecycle("Or run directly in a background terminal:")
            logger.lifecycle("  $tunnelCmd")
            logger.lifecycle("================================================================================")
        }
    }
}
