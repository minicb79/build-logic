package com.minicdesign.buildlogic.infra

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InfraPluginTest {

    @Test
    fun `applying InfraPlugin registers extension and tasks with defaults`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.infra")

        val extension = project.extensions.findByType(InfraExtension::class.java)
        assertNotNull(extension, "InfraExtension should be registered")
        assertEquals("dev", extension!!.environment.get())
        assertEquals(project.name, extension.serviceName.get())
        assertEquals("minicdesign.com", extension.domainName.get())
        assertEquals(4566, extension.flociPort.get())
        assertEquals(8080, extension.ingressPort.get())
        assertTrue(extension.syncOpenApiScopes.get())
        assertTrue(extension.persistentState.get())

        assertNotNull(project.tasks.findByName("flociStart"), "flociStart task should be registered")
        assertNotNull(project.tasks.findByName("flociStop"), "flociStop task should be registered")
        assertNotNull(project.tasks.findByName("flociStatus"), "flociStatus task should be registered")
        assertNotNull(project.tasks.findByName("infraConfigureHosts"), "infraConfigureHosts task should be registered")
        assertNotNull(project.tasks.findByName("syncOpenApiScopes"), "syncOpenApiScopes task should be registered")
        assertNotNull(project.tasks.findByName("infraInitSecrets"), "infraInitSecrets task should be registered")
        assertNotNull(project.tasks.findByName("infraApply"), "infraApply task should be registered")
        assertNotNull(project.tasks.findByName("infraDeploy"), "infraDeploy task should be registered")
        assertNotNull(project.tasks.findByName("infraDestroy"), "infraDestroy task should be registered")
    }

    @Test
    fun `SyncOpenApiScopesTask extracts OAuth2 scopes into scopes auto tfvars json`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.infra")

        val openApiYaml = """
        openapi: 3.0.3
        info:
          title: Order Service API
          version: 1.0.0
        paths:
          /orders:
            get:
              operationId: listOrders
              security:
                - oauth2Auth:
                    - order:read
              responses:
                '200':
                  description: OK
            post:
              operationId: createOrder
              security:
                - oauth2Auth:
                    - order:write
              responses:
                '201':
                  description: Created
        components:
          securitySchemes:
            oauth2Auth:
              type: oauth2
              flows:
                clientCredentials:
                  tokenUrl: http://localhost:4566/oauth2/token
                  scopes:
                    order:read: Read orders
                    order:write: Create or modify orders
        """.trimIndent()

        val contractsDir = tempDir.resolve("contracts/openapi")
        contractsDir.mkdirs()
        contractsDir.resolve("orders.yaml").writeText(openApiYaml)

        val syncTask = project.tasks.getByName("syncOpenApiScopes") as SyncOpenApiScopesTask
        syncTask.syncScopes()

        val tfvarsFile = tempDir.resolve("infra/terraform/scopes.auto.tfvars.json")
        assertTrue(tfvarsFile.exists(), "scopes.auto.tfvars.json should be generated")

        val content = tfvarsFile.readText()
        assertTrue(content.contains("order:read"), "Should contain order:read scope")
        assertTrue(content.contains("order:write"), "Should contain order:write scope")
    }

}

