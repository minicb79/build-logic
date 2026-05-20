package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class SpringServicePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply local convention plugins
        project.plugins.apply("com.minicdesign.java-conventions")
        project.plugins.apply("com.minicdesign.kotlin-conventions")

        // 2. Apply Spring Boot and Spring Dependency Management plugins
        project.plugins.apply("org.springframework.boot")
        project.plugins.apply("io.spring.dependency-management")

        // 3. Apply Kotlin Spring compiler plugin (opens Spring beans)
        project.plugins.apply("org.jetbrains.kotlin.plugin.spring")

        // 4. Configure standard dependencies for Rest APIs
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
        val libs = catalogs?.find("libs")?.orElse(null)

        val springWebDep = if (libs != null && libs.findLibrary("spring-boot-starter-web").isPresent) {
            libs.findLibrary("spring-boot-starter-web").get()
        } else {
            "org.springframework.boot:spring-boot-starter-web"
        }

        val kotlinReflectDep = if (libs != null && libs.findLibrary("kotlin-reflect").isPresent) {
            libs.findLibrary("kotlin-reflect").get()
        } else {
            "org.jetbrains.kotlin:kotlin-reflect"
        }

        val jacksonKotlinDep = if (libs != null && libs.findLibrary("jackson-module-kotlin").isPresent) {
            libs.findLibrary("jackson-module-kotlin").get()
        } else {
            "com.fasterxml.jackson.module:jackson-module-kotlin"
        }

        val springTestDep = if (libs != null && libs.findLibrary("spring-boot-starter-test").isPresent) {
            libs.findLibrary("spring-boot-starter-test").get()
        } else {
            "org.springframework.boot:spring-boot-starter-test"
        }

        project.dependencies.apply {
            add("implementation", springWebDep)
            add("implementation", kotlinReflectDep)
            add("implementation", jacksonKotlinDep)
            add("testImplementation", springTestDep)
        }
    }
}
