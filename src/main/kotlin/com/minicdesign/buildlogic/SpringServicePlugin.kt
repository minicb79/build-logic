package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class SpringServicePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply local convention plugins (Java default)
        project.plugins.apply("com.minicdesign.java-conventions")

        // 2. Apply Spring Boot and Spring Dependency Management plugins
        project.plugins.apply("org.springframework.boot")
        project.plugins.apply("io.spring.dependency-management")

        // 3. Configure standard dependencies for Rest APIs
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
