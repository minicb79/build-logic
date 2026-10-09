package com.minicdesign.buildlogic.infra

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Checks system /etc/hosts")
abstract class InfraConfigureHostsTask : DefaultTask() {

    @get:Input
    abstract val environment: Property<String>

    @get:Input
    abstract val serviceName: Property<String>

    @get:Input
    abstract val domainName: Property<String>

    @get:Input
    abstract val ingressPort: Property<Int>

    init {
        group = "infra"
        description = "Verifies or configures /etc/hosts aliases and outputs zero-config localhost endpoints."
    }

    @TaskAction
    fun configureHosts() {
        val env = environment.get()
        val svc = serviceName.get()
        val domain = domainName.get()
        val port = ingressPort.get()

        val serviceHost = "api-$env.$svc.$domain"
        val domainHost = "api-$env.$domain"
        val localhostService = "api-$env.$svc.$domain.localhost"

        val hostsFile = File("/etc/hosts")
        val content = if (hostsFile.exists() && hostsFile.canRead()) hostsFile.readText() else ""

        val hasServiceHost = content.contains(serviceHost)
        val hasDomainHost = content.contains(domainHost)

        logger.lifecycle("================================================================================")
        logger.lifecycle(" Local Cloud Host Aliases & Routing (${env.uppercase()} Environment)")
        logger.lifecycle("================================================================================")
        logger.lifecycle("1. Zero-Config Localhost URLs (RFC 6761, no sudo required):")
        logger.lifecycle("   - Service direct: http://$localhostService:$port/")
        logger.lifecycle("   - Path-based:     http://api-$env.$domain.localhost:$port/$svc/")
        logger.lifecycle("")
        logger.lifecycle("2. Custom Domain Host Aliases:")
        logger.lifecycle("   - Service direct: http://$serviceHost:$port/")
        logger.lifecycle("   - Path-based:     http://$domainHost:$port/$svc/")
        logger.lifecycle("")

        if (hasServiceHost && hasDomainHost) {
            logger.lifecycle("[OK] /etc/hosts already contains mappings for '$serviceHost' and '$domainHost'.")
        } else {
            val missing = mutableListOf<String>()
            if (!hasServiceHost) missing.add(serviceHost)
            if (!hasDomainHost) missing.add(domainHost)

            val cmd = "echo '127.0.0.1 ${missing.joinToString(" ")}' | sudo tee -a /etc/hosts"
            logger.lifecycle("[ACTION REQUIRED] Missing /etc/hosts mapping for: ${missing.joinToString(", ")}")
            logger.lifecycle("Run the following command in your terminal to enable custom domain aliases:")
            logger.lifecycle("   $cmd")
        }
        logger.lifecycle("================================================================================")
    }
}
