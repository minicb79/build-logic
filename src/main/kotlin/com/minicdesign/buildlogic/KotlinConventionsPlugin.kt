package com.minicdesign.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
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

        // 5. Configure separate source set for integration tests (testIntegration)
        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val testIntegration = javaExt.sourceSets.create("testIntegration")

        // Set compile and runtime classpaths for integration tests to include main classes and unit test configurations
        testIntegration.compileClasspath = project.objects.fileCollection().from(
            javaExt.sourceSets.getByName("main").output,
            project.configurations.getByName("testCompileClasspath")
        )
        testIntegration.runtimeClasspath = project.objects.fileCollection().from(
            testIntegration.output,
            javaExt.sourceSets.getByName("main").output,
            project.configurations.getByName("testRuntimeClasspath")
        )

        // Inherit dependencies from test configurations
        val configurations = project.configurations
        configurations.getByName("testIntegrationImplementation")
            .extendsFrom(configurations.getByName("testImplementation"))
        configurations.getByName("testIntegrationRuntimeOnly")
            .extendsFrom(configurations.getByName("testRuntimeOnly"))

        // Register integrationTest execution task
        project.tasks.register("integrationTest", Test::class.java, object : Action<Test> {
            override fun execute(task: Test) {
                task.description = "Runs integration tests."
                task.group = "verification"
                task.testClassesDirs = testIntegration.output.classesDirs
                task.classpath = testIntegration.runtimeClasspath
                task.useJUnitPlatform()
            }
        })

        // 6. Post-evaluation setup (for user-customized extension values)
        project.afterEvaluate {
            // Configure Kotlin Compiler Options
            val kotlinExt = project.extensions.findByType(KotlinJvmProjectExtension::class.java)
            kotlinExt?.compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(extension.jvmTarget.get().toString()))
                freeCompilerArgs.addAll(extension.freeCompilerArgs.get())
            }

            // Configure library catalog dependencies (or fallback to defaults)
            val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
            val libs = catalogs?.find("libs")?.orElse(null)

            val kotlinTestDep = if (libs != null && libs.findLibrary("kotlin-test-junit5").isPresent) {
                libs.findLibrary("kotlin-test-junit5").get()
            } else {
                "org.jetbrains.kotlin:kotlin-test-junit5"
            }

            val junitPlatformLauncherDep = if (libs != null && libs.findLibrary("junit-platform-launcher").isPresent) {
                libs.findLibrary("junit-platform-launcher").get()
            } else {
                "org.junit.platform:junit-platform-launcher"
            }

            project.dependencies.apply {
                add("testImplementation", kotlinTestDep)
                add("testRuntimeOnly", junitPlatformLauncherDep)
            }

            // Finalize unit tests and integration tests by generating report if Jacoco is applied
            if (project.plugins.hasPlugin("jacoco")) {
                project.tasks.named("integrationTest", Test::class.java).configure(object : Action<Test> {
                    override fun execute(testTask: Test) {
                        testTask.finalizedBy(project.tasks.withType(org.gradle.testing.jacoco.tasks.JacocoReport::class.java))
                    }
                })
            }

            // Hook integrationTest and check task lifecycle
            project.tasks.named("check").configure(object : Action<Task> {
                override fun execute(checkTask: Task) {
                    checkTask.dependsOn(project.tasks.named("integrationTest"))
                }
            })
        }
    }
}
