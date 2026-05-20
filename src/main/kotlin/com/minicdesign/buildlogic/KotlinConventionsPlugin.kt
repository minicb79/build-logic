package com.minicdesign.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply plugins
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("com.diffplug.spotless")

        // 2. Create the configuration extension
        val extension = project.extensions.create(
            "kotlinConventions",
            KotlinConventionsExtension::class.java,
            project
        )

        // 3. Configure repositories
        project.repositories.mavenCentral()

        // 4. Configure Spotless formatting for Kotlin
        val spotless = project.extensions.getByType(SpotlessExtension::class.java)
        spotless.kotlin {
            ktlint("0.50.0")
            trimTrailingWhitespace()
            endWithNewline()
        }

        // 5. Post-evaluation setup (for user-customized extension values)
        project.afterEvaluate {
            // Configure Kotlin Compiler Options
            val kotlinExt = project.extensions.findByType(KotlinJvmProjectExtension::class.java)
            kotlinExt?.compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(extension.jvmTarget.get().toString()))
                freeCompilerArgs.addAll(extension.freeCompilerArgs.get())
            }

            // Configure standard Kotlin testing dependencies
            project.dependencies.apply {
                add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
                add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
            }
        }
    }
}
