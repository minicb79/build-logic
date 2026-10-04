package com.minicdesign.buildlogic

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JavaConventionsPluginTest {

    @Test
    fun `applying JavaConventionsPlugin registers extension with defaults`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.java-conventions")

        val extension = project.extensions.findByType(JavaConventionsExtension::class.java)
        assertNotNull(extension, "JavaConventionsExtension should be registered")
        assertEquals(25, extension!!.javaVersion.get())
        assertFalse(extension.modular.get(), "modular should be false by default")
        assertEquals(0.90, extension.coverageThreshold.get())
    }

    @Test
    fun `configuring modular sets inferModulePath to true in afterEvaluate`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.java-conventions")

        val extension = project.extensions.getByType(JavaConventionsExtension::class.java)
        extension.modular.set(true)

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        assertTrue(javaExt.modularity.inferModulePath.get(), "inferModulePath should be true when modular is set to true")
    }

    @Test
    fun `detecting module-info java automatically enables inferModulePath in afterEvaluate`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

        val moduleInfo = tempDir.resolve("src/main/java/module-info.java")
        moduleInfo.parentFile.mkdirs()
        moduleInfo.writeText("module com.example.lib {}")

        project.plugins.apply("com.minicdesign.java-conventions")

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        assertTrue(javaExt.modularity.inferModulePath.get(), "inferModulePath should be automatically enabled when module-info.java exists")
    }
}
