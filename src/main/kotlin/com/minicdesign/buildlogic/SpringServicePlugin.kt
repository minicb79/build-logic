package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface ArchUnitConventionsExtension {
    val enabled: Property<Boolean>
    val noAutowiredFields: Property<Boolean>
    val noStandardStreams: Property<Boolean>
    val noJunitAsserts: Property<Boolean>
    val excludedPaths: ListProperty<String>
    val additionalRules: ListProperty<String>
}

class SpringServicePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply local convention plugins (Java default)
        project.plugins.apply("com.minicdesign.java-conventions")

        // 2. Apply Spring Boot and Spring Dependency Management plugins
        project.plugins.apply("org.springframework.boot")
        project.plugins.apply("io.spring.dependency-management")

        // 3. Apply ArchUnit Gradle plugin
        project.plugins.apply("com.societegenerale.commons.plugin.gradle.ArchUnitGradlePlugin")

        // 4. Register ArchUnit conventions extension
        val archUnitConventions = project.extensions.create("archUnitConventions", ArchUnitConventionsExtension::class.java).apply {
            enabled.convention(true)
            noAutowiredFields.convention(true)
            noStandardStreams.convention(true)
            noJunitAsserts.convention(false)
        }

        // 5. Map conventions to ArchUnit config in afterEvaluate
        project.afterEvaluate {
            val archUnit = project.extensions.getByType(com.societegenerale.commons.plugin.gradle.ArchUnitGradleConfig::class.java)
            
            // Map enable/skip
            archUnit.setSkip(!archUnitConventions.enabled.get())

            // Map rules list
            val rules = mutableListOf<String>()
            if (archUnitConventions.noAutowiredFields.get()) {
                rules.add("com.societegenerale.commons.plugin.rules.NoInjectedFieldTest")
            }
            if (archUnitConventions.noStandardStreams.get()) {
                rules.add("com.societegenerale.commons.plugin.rules.NoStandardStreamRuleTest")
            }
            if (archUnitConventions.noJunitAsserts.get()) {
                rules.add("com.societegenerale.commons.plugin.rules.NoJunitAssertRuleTest")
            }
            rules.addAll(archUnitConventions.additionalRules.getOrElse(emptyList()))
            archUnit.preConfiguredRules = rules

            // Map exclusions
            archUnit.excludedPaths = archUnitConventions.excludedPaths.getOrElse(emptyList())
        }

        // 6. Configure standard dependencies for Rest APIs
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
        val libs = catalogs?.find("libs")?.orElse(null)

        val springWebDep = if (libs != null && libs.findLibrary("spring-boot-starter-web").isPresent) {
            libs.findLibrary("spring-boot-starter-web").get()
        } else {
            "org.springframework.boot:spring-boot-starter-web"
        }

        val springRestClientDep = if (libs != null && libs.findLibrary("spring-boot-starter-restclient").isPresent) {
            libs.findLibrary("spring-boot-starter-restclient").get()
        } else {
            "org.springframework.boot:spring-boot-starter-restclient"
        }

        val springTestDep = if (libs != null && libs.findLibrary("spring-boot-starter-test").isPresent) {
            libs.findLibrary("spring-boot-starter-test").get()
        } else {
            "org.springframework.boot:spring-boot-starter-test"
        }

        project.dependencies.apply {
            add("implementation", springWebDep)
            add("implementation", springRestClientDep)
            add("testImplementation", springTestDep)
        }
    }
}
