package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class SpringServiceKotlinPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply the base Spring Service (Java) plugin
        project.plugins.apply("com.minicdesign.spring-service")

        // 2. Apply Kotlin conventions and Spring Kotlin plugin
        project.plugins.apply("com.minicdesign.kotlin-conventions")
        project.plugins.apply("org.jetbrains.kotlin.plugin.spring")

        // 3. Configure Kotlin specific dependencies
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
        val libs = catalogs?.find("libs")?.orElse(null)

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

        project.dependencies.apply {
            add("implementation", kotlinReflectDep)
            add("implementation", jacksonKotlinDep)
        }
    }
}
