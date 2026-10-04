package com.minicdesign.buildlogic.archunit

import com.societegenerale.commons.plugin.gradle.ArchUnitGradleConfig
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchUnitPluginTest {

    @Test
    fun `applying ArchUnitPlugin registers extension and tasks`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.archunit")

        val extension = project.extensions.findByType(ArchUnitConventionsExtension::class.java)
        assertNotNull(extension, "ArchUnitConventionsExtension should be registered")
        assertTrue(extension!!.enabled.get(), "ArchUnit should be enabled by default")
        assertTrue(extension.noAutowiredFields.get(), "noAutowiredFields should be true by default")
        assertTrue(extension.noStandardStreams.get(), "noStandardStreams should be true by default")
        assertFalse(extension.noJunitAsserts.get(), "noJunitAsserts should be false by default")

        assertNotNull(project.tasks.findByName("checkRules"), "checkRules task should be registered")
    }

    @Test
    fun `ArchUnitPlugin correctly configures ArchUnitGradleConfig in afterEvaluate`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.archunit")

        val extension = project.extensions.getByType(ArchUnitConventionsExtension::class.java)
        extension.noJunitAsserts.set(true)
        extension.enableRule(SocieteGeneraleRule.NO_JAVA_UTIL_DATE)
        extension.excludePath("build/generated")
        extension.addRule("com.example.CustomArchRule")

        // Trigger afterEvaluate via project.getTasks() or evaluating project
        // Note: ProjectBuilder projects can be evaluated using project.evaluationDependsOnChildren() or manually firing afterEvaluate listeners
        // In Gradle ProjectBuilder, project.plugins.apply calls can be evaluated or we can invoke the plugin's evaluation logic.
        // Let's test ProjectBuilder afterEvaluate:
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val archUnit = project.extensions.getByType(ArchUnitGradleConfig::class.java)
        assertFalse(archUnit.isSkip)

        val rules = archUnit.preConfiguredRules
        assertTrue(rules.contains(SocieteGeneraleRule.NO_INJECTED_FIELDS.ruleClassName))
        assertTrue(rules.contains(SocieteGeneraleRule.NO_STANDARD_STREAMS.ruleClassName))
        assertTrue(rules.contains(SocieteGeneraleRule.NO_JUNIT_ASSERTS.ruleClassName))
        assertTrue(rules.contains(SocieteGeneraleRule.NO_JAVA_UTIL_DATE.ruleClassName))
        assertTrue(rules.contains("com.example.CustomArchRule"))

        assertEquals(listOf("build/generated"), archUnit.excludedPaths)

        val checkRulesTask = project.tasks.named("checkRules", com.societegenerale.commons.plugin.gradle.ArchUnitRulesTask::class.java).get()
        val javaExt = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        val testOutput = javaExt.sourceSets.getByName("test").output
        assertTrue(checkRulesTask.classpath.contains(testOutput.classesDirs.singleFile) || checkRulesTask.classpath.files.containsAll(testOutput.files), "checkRules classpath should contain test classes")
        assertTrue(checkRulesTask.dependsOn.contains("testClasses"), "checkRules should depend on testClasses")
    }

    @Test
    fun `SpringServicePlugin applies ArchUnitPlugin seamlessly`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.spring-service")

        val extension = project.extensions.findByType(ArchUnitConventionsExtension::class.java)
        assertNotNull(extension, "ArchUnitConventionsExtension should be present when spring-service is applied")
        assertNotNull(project.tasks.findByName("checkRules"), "checkRules task should be registered")
    }

    @Test
    fun `ArchUnitPlugin correctly configures restrictInternalPackages in afterEvaluate`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("com.minicdesign.archunit")

        val extension = project.extensions.getByType(ArchUnitConventionsExtension::class.java)
        assertFalse(extension.restrictInternalPackages.get(), "restrictInternalPackages should be false by default")
        extension.restrictInternalPackages.set(true)

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val archUnit = project.extensions.getByType(ArchUnitGradleConfig::class.java)
        assertTrue(archUnit.preConfiguredRules.contains(SocieteGeneraleRule.RESTRICT_INTERNAL_PACKAGES.ruleClassName))
    }

    @Test
    fun `SocieteGeneraleRule enum maps all rules correctly`() {
        assertEquals(19, SocieteGeneraleRule.values().size)
        val rule = SocieteGeneraleRule.fromRuleClassName("com.societegenerale.commons.plugin.rules.NoStandardStreamRuleTest")
        assertEquals(SocieteGeneraleRule.NO_STANDARD_STREAMS, rule)

        val internalPkgRule = SocieteGeneraleRule.fromRuleClassName("com.minicdesign.buildlogic.archunit.rules.RestrictInternalPackagesRuleTest")
        assertEquals(SocieteGeneraleRule.RESTRICT_INTERNAL_PACKAGES, internalPkgRule)
    }
}
