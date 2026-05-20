package com.minicdesign.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class OtelBasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
        val libs = catalogs?.find("libs")?.orElse(null)

        val otelBom = if (libs != null && libs.findLibrary("opentelemetry-bom").isPresent) {
            libs.findLibrary("opentelemetry-bom").get()
        } else {
            "io.opentelemetry:opentelemetry-bom:1.41.0"
        }

        val otelApi = if (libs != null && libs.findLibrary("opentelemetry-api").isPresent) {
            libs.findLibrary("opentelemetry-api").get()
        } else {
            "io.opentelemetry:opentelemetry-api"
        }

        val otelSdk = if (libs != null && libs.findLibrary("opentelemetry-sdk").isPresent) {
            libs.findLibrary("opentelemetry-sdk").get()
        } else {
            "io.opentelemetry:opentelemetry-sdk"
        }

        val otelLogbackAppender = if (libs != null && libs.findLibrary("opentelemetry-logback-appender").isPresent) {
            libs.findLibrary("opentelemetry-logback-appender").get()
        } else {
            "io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.21.0-alpha"
        }

        project.dependencies.apply {
            val platformBom = project.dependencies.platform(otelBom)
            add("implementation", platformBom)
            add("implementation", otelApi)
            add("implementation", otelSdk)
            add("implementation", otelLogbackAppender)
        }
    }
}
