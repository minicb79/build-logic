package com.minicdesign.buildlogic.infra

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

interface InfraExtension {
    /**
     * Target environment name (e.g. "dev", "test", "staging"). Defaults to "dev".
     */
    val environment: Property<String>

    /**
     * Service name used for naming cloud resources and host aliases. Defaults to project name.
     */
    val serviceName: Property<String>

    /**
     * Domain name for host aliases (e.g. "minicdesign.com"). Defaults to "minicdesign.com".
     */
    val domainName: Property<String>

    /**
     * Port where Floci AWS emulator listens. Defaults to 4566.
     */
    val flociPort: Property<Int>

    /**
     * Port where local cloud ingress (Traefik/Nginx) listens. Defaults to 8080.
     */
    val ingressPort: Property<Int>

    /**
     * Directory containing Terraform configurations. Defaults to "infra/terraform".
     */
    val terraformDir: DirectoryProperty

    /**
     * Directory containing Docker Compose configurations. Defaults to "infra/docker".
     */
    val dockerComposeDir: DirectoryProperty

    /**
     * Whether to automatically extract OAuth2 scopes from OpenAPI specs into Terraform vars. Defaults to true.
     */
    val syncOpenApiScopes: Property<Boolean>

    /**
     * Whether Floci should persist its cloud state across container restarts in infra/.floci-data. Defaults to true.
     */
    val persistentState: Property<Boolean>

    /**
     * Target module for Phase 1 secrets placeholder creation. Defaults to "module.secrets".
     */
    val secretsModule: Property<String>

    /**
     * Path to docker executable. Defaults to "docker".
     */
    val dockerPath: Property<String>

    /**
     * Path to terraform / opentofu executable. Defaults to "terraform".
     */
    val terraformPath: Property<String>

    /**
     * Path to aws CLI executable. Defaults to "aws".
     */
    val awsPath: Property<String>

    /**
     * Host where Floci AWS emulator runs (e.g. "localhost", "10.0.1.50", "floci.lab.corp").
     * Defaults to "localhost".
     */
    val flociHost: Property<String>

    /**
     * Complete endpoint URL where Floci AWS emulator is reachable.
     * Defaults to "http://${flociHost.get()}:${flociPort.get()}".
     */
    val flociEndpoint: Property<String>

    /**
     * Host where Traefik ingress proxy runs. Defaults to "localhost".
     */
    val ingressHost: Property<String>

    /**
     * Whether Floci runs on a remote external server. When true, local Docker Compose startup is skipped.
     * Defaults to false.
     */
    val remoteServer: Property<Boolean>

    /**
     * SSH connection target (e.g. "user@remote-server.lab") used for port forwarding tunnels and remote Docker.
     */
    val sshTarget: Property<String>
}
