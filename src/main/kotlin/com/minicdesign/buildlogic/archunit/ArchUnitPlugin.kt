package com.minicdesign.buildlogic.archunit

import com.societegenerale.commons.plugin.gradle.ArchUnitGradleConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class ArchUnitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Ensure the Java plugin is applied so 'check' and 'test' tasks exist for ArchUnitGradlePlugin
        project.plugins.apply(JavaPlugin::class.java)

        // 2. Apply Societe Generale ArchUnit Gradle plugin
        project.plugins.apply("com.societegenerale.commons.plugin.gradle.ArchUnitGradlePlugin")

        // 3. Register ArchUnit conventions extension
        val archUnitConventions = project.extensions.create(
            "archUnitConventions",
            ArchUnitConventionsExtension::class.java
        ).apply {
            enabled.convention(true)
            noAutowiredFields.convention(true)
            noStandardStreams.convention(true)
            noJunitAsserts.convention(false)
            noJavaUtilDate.convention(true)
            noJodaTime.convention(true)
            noPowerMock.convention(true)
            noPublicFields.convention(false)
            noPrefixForInterfaces.convention(false)
            dontReturnNullCollection.convention(false)
            finalNonStaticFieldsHaveToBeStaticFinal.convention(false)
            constantsAndStaticNonFinalFieldsNames.convention(false)
            testMethodsNaming.convention(false)
            testClassesNaming.convention(false)
            noTestIgnore.convention(false)
            noTestIgnoreWithoutComment.convention(false)
            stringFieldsThatAreActuallyDates.convention(false)
            hexagonalArchitecture.convention(false)
            restrictInternalPackages.convention(false)
            excludedPaths.convention(emptyList())
            additionalRules.convention(emptyList())
            enabledRules.convention(emptySet())
        }

        // 4. Map conventions to ArchUnit config in afterEvaluate
        project.afterEvaluate {
            val archUnit = project.extensions.getByType(ArchUnitGradleConfig::class.java)
            val checkRulesTask = project.tasks.named("checkRules", com.societegenerale.commons.plugin.gradle.ArchUnitRulesTask::class.java)
            val javaExt = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)

            if (javaExt != null) {
                val testSourceSet = javaExt.sourceSets.findByName("test")
                if (testSourceSet != null) {
                    checkRulesTask.configure {
                        classpath.from(testSourceSet.output)
                        dependsOn(testSourceSet.classesTaskName)
                    }
                }
            }

            // Configure skip / enabled state
            archUnit.setSkip(!archUnitConventions.enabled.get())

            // Collect unique rules to execute
            val rules = linkedSetOf<String>()

            if (archUnitConventions.noAutowiredFields.get()) {
                rules.add(SocieteGeneraleRule.NO_INJECTED_FIELDS.ruleClassName)
            }
            if (archUnitConventions.noStandardStreams.get()) {
                rules.add(SocieteGeneraleRule.NO_STANDARD_STREAMS.ruleClassName)
            }
            if (archUnitConventions.noJunitAsserts.get()) {
                rules.add(SocieteGeneraleRule.NO_JUNIT_ASSERTS.ruleClassName)
            }
            if (archUnitConventions.noJavaUtilDate.get()) {
                rules.add(SocieteGeneraleRule.NO_JAVA_UTIL_DATE.ruleClassName)
            }
            if (archUnitConventions.noJodaTime.get()) {
                rules.add(SocieteGeneraleRule.NO_JODA_TIME.ruleClassName)
            }
            if (archUnitConventions.noPowerMock.get()) {
                rules.add(SocieteGeneraleRule.NO_POWERMOCK.ruleClassName)
            }
            if (archUnitConventions.noPublicFields.get()) {
                rules.add(SocieteGeneraleRule.NO_PUBLIC_FIELDS.ruleClassName)
            }
            if (archUnitConventions.noPrefixForInterfaces.get()) {
                rules.add(SocieteGeneraleRule.NO_PREFIX_FOR_INTERFACES.ruleClassName)
            }
            if (archUnitConventions.dontReturnNullCollection.get()) {
                rules.add(SocieteGeneraleRule.DONT_RETURN_NULL_COLLECTION.ruleClassName)
            }
            if (archUnitConventions.finalNonStaticFieldsHaveToBeStaticFinal.get()) {
                rules.add(SocieteGeneraleRule.FINAL_NON_STATIC_FIELDS_HAVE_TO_BE_STATIC_FINAL.ruleClassName)
            }
            if (archUnitConventions.constantsAndStaticNonFinalFieldsNames.get()) {
                rules.add(SocieteGeneraleRule.CONSTANTS_AND_STATIC_NON_FINAL_FIELDS_NAMES.ruleClassName)
            }
            if (archUnitConventions.testMethodsNaming.get()) {
                rules.add(SocieteGeneraleRule.TEST_METHODS_NAMING.ruleClassName)
            }
            if (archUnitConventions.testClassesNaming.get()) {
                rules.add(SocieteGeneraleRule.TEST_CLASSES_NAMING.ruleClassName)
            }
            if (archUnitConventions.noTestIgnore.get()) {
                rules.add(SocieteGeneraleRule.NO_TEST_IGNORE.ruleClassName)
            }
            if (archUnitConventions.noTestIgnoreWithoutComment.get()) {
                rules.add(SocieteGeneraleRule.NO_TEST_IGNORE_WITHOUT_COMMENT.ruleClassName)
            }
            if (archUnitConventions.stringFieldsThatAreActuallyDates.get()) {
                rules.add(SocieteGeneraleRule.STRING_FIELDS_THAT_ARE_ACTUALLY_DATES.ruleClassName)
            }
            if (archUnitConventions.hexagonalArchitecture.get()) {
                rules.add(SocieteGeneraleRule.HEXAGONAL_ARCHITECTURE.ruleClassName)
            }
            if (archUnitConventions.restrictInternalPackages.get()) {
                rules.add(SocieteGeneraleRule.RESTRICT_INTERNAL_PACKAGES.ruleClassName)
            }

            // Add explicitly enabled rules
            for (rule in archUnitConventions.enabledRules.getOrElse(emptySet())) {
                rules.add(rule.ruleClassName)
            }

            // Add any additional custom rule classes
            rules.addAll(archUnitConventions.additionalRules.getOrElse(emptyList()))

            archUnit.preConfiguredRules = rules.toList()
            archUnit.excludedPaths = archUnitConventions.excludedPaths.getOrElse(emptyList())
        }
    }
}
