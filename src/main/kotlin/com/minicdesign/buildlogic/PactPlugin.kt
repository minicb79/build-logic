package com.minicdesign.buildlogic

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet

interface PactExtension {
    val brokerUrl: Property<String>
    val appVersion: Property<String>
    val branch: Property<String>
}

class PactPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("pact", PactExtension::class.java).apply {
            brokerUrl.convention(project.providers.environmentVariable("PACT_BROKER_BASE_URL").orElse("http://localhost:9292"))
            appVersion.convention(project.providers.gradleProperty("appVersion").orElse("1.0.0"))
            branch.convention(project.providers.gradleProperty("branch").orElse("main"))
        }

        // Configure all Test tasks to inherit the pact broker url and settings as system properties
        project.tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            systemProperty("pactbroker.url", extension.brokerUrl.get())
            systemProperty("pact.broker.url", extension.brokerUrl.get())
            systemProperty("pact.provider.version", extension.appVersion.get())
            systemProperty("pact.provider.branch", extension.branch.get())
            
            val publishResults = project.providers.systemProperty("pact.verifier.publishResults")
                .orElse(project.providers.environmentVariable("PACT_BROKER_PUBLISH_VERIFICATION_RESULTS"))
                .orElse("false")
            systemProperty("pact.verifier.publishResults", publishResults.get())
        }

        // Dynamically register pact test tasks for custom source sets
        val javaExt = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        
        val testPactTask = project.tasks.register("testPact") {
            group = "verification"
            description = "Runs all Pact consumer and provider verification tests."
        }

        javaExt.sourceSets.configureEach(object : Action<SourceSet> {
            override fun execute(sourceSet: SourceSet) {
                val name = sourceSet.name
                if (name.startsWith("testPactConsumer") || name == "testPactProvider") {
                // Configure classpaths
                sourceSet.compileClasspath = project.objects.fileCollection().from(
                    javaExt.sourceSets.getByName("main").output,
                    project.configurations.getByName("testCompileClasspath")
                )
                sourceSet.runtimeClasspath = project.objects.fileCollection().from(
                    sourceSet.output,
                    javaExt.sourceSets.getByName("main").output,
                    project.configurations.getByName("testRuntimeClasspath")
                )

                // Inherit dependencies from main test configurations
                project.configurations.getByName(sourceSet.implementationConfigurationName)
                    .extendsFrom(project.configurations.getByName("testImplementation"))
                project.configurations.getByName(sourceSet.runtimeOnlyConfigurationName)
                    .extendsFrom(project.configurations.getByName("testRuntimeOnly"))

                // Register specific Test execution task
                val specificTestTask = project.tasks.register(name, org.gradle.api.tasks.testing.Test::class.java) {
                    group = "verification"
                    description = "Runs Pact tests for source set $name."
                    testClassesDirs = sourceSet.output.classesDirs
                    classpath = sourceSet.runtimeClasspath
                    useJUnitPlatform()
                    
                    // Enable JUnit 5 parallel execution properties
                    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
                    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")

                    // Propagate system properties
                    systemProperty("pactbroker.url", extension.brokerUrl.get())
                    systemProperty("pact.broker.url", extension.brokerUrl.get())
                    systemProperty("pact.provider.version", extension.appVersion.get())
                    systemProperty("pact.provider.branch", extension.branch.get())
                    
                    val publishResults = project.providers.systemProperty("pact.verifier.publishResults")
                        .orElse(project.providers.environmentVariable("PACT_BROKER_PUBLISH_VERIFICATION_RESULTS"))
                        .orElse("false")
                    systemProperty("pact.verifier.publishResults", publishResults.get())

                    // Finalize by Jacoco reports
                    finalizedBy(project.tasks.withType(org.gradle.testing.jacoco.tasks.JacocoReport::class.java))
                }

                // Make sure Jacoco reports run after specificTestTask
                project.tasks.withType(org.gradle.testing.jacoco.tasks.JacocoReport::class.java).configureEach {
                    mustRunAfter(specificTestTask)
                }

                // Wire up dependencies
                testPactTask.configure {
                    dependsOn(specificTestTask)
                }

                project.tasks.named("check").configure {
                    dependsOn(specificTestTask)
                }
                }
            }
        })

        project.tasks.register("publishConsumerContracts", PublishConsumerContractsTask::class.java) {
            group = "pact"
            description = "Publishes generated consumer contracts to the Pact Broker."
            brokerUrl.set(extension.brokerUrl)
            consumerVersion.set(extension.appVersion)
            branch.set(extension.branch)
            pactFilesDir.convention(project.layout.buildDirectory.dir("pacts"))
        }

        project.tasks.register("canIDeploy", CanIDeployTask::class.java) {
            group = "pact"
            description = "Verifies if the current version is safe to deploy."
            brokerUrl.set(extension.brokerUrl)
            pacticipantName.convention(project.name)
            pacticipantVersion.set(extension.appVersion)
            toEnvironment.convention("production")
        }
    }
}
