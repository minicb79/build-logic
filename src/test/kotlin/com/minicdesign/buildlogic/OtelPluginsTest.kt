package com.minicdesign.buildlogic

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OtelPluginsTest {

    @Test
    fun `applying OtelBasePlugin adds open telemetry dependencies`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(JavaPlugin::class.java)

        project.plugins.apply("com.minicdesign.otel-base")

        val implConfig = project.configurations.getByName("implementation")
        assertNotNull(implConfig)
    }

    @Test
    fun `applying SpringOtelLoggingPlugin registers tasks and generates expected files`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(JavaPlugin::class.java)

        project.plugins.apply("com.minicdesign.spring-otel-logging")

        val generateConfigTask = project.tasks.findByName("generateOtelConfig")
        val generateResourcesTask = project.tasks.findByName("generateOtelResources")

        assertNotNull(generateConfigTask, "generateOtelConfig task must be registered")
        assertNotNull(generateResourcesTask, "generateOtelResources task must be registered")

        generateConfigTask!!.actions.forEach { it.execute(generateConfigTask) }
        generateResourcesTask!!.actions.forEach { it.execute(generateResourcesTask) }

        val javaDir = tempDir.resolve("build/generated/sources/otel/java/com/minicdesign/otel")
        val resourcesDir = tempDir.resolve("build/generated/sources/otel/resources")

        val globalExceptionHandler = javaDir.resolve("GlobalExceptionHandler.java")
        assertTrue(globalExceptionHandler.exists(), "GlobalExceptionHandler.java must be generated")

        val handlerContent = globalExceptionHandler.readText()
        assertTrue(handlerContent.contains("@Order(Ordered.LOWEST_PRECEDENCE)"), "Should contain @Order(Ordered.LOWEST_PRECEDENCE)")
        assertTrue(handlerContent.contains("@ConditionalOnProperty(name = \"logging.otel.global-exception-handler.enabled\", havingValue = \"true\", matchIfMissing = true)"), "Should be conditional on logging.otel.global-exception-handler.enabled")

        val traceIdFilter = javaDir.resolve("TraceIdFilter.java")
        assertTrue(traceIdFilter.exists(), "TraceIdFilter.java must be generated")

        val logbackXml = resourcesDir.resolve("logback-spring.xml")
        assertTrue(logbackXml.exists(), "logback-spring.xml must be generated")
    }
}
