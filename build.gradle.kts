plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.minicdesign.buildlogic"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // We add the Kotlin Gradle Plugin so we can use its classes/interfaces in our Kotlin conventions compiled plugin
    implementation(libs.kotlin.gradlePlugin)
    // We add the Kotlin All-Open plugin so we can apply kotlin.plugin.spring programmatically
    implementation(libs.kotlin.allopen)
    // We add the Spotless plugin so we can configure source formatting conventions
    implementation(libs.spotless.gradlePlugin)
    // We add the Spring Boot and Spring Dependency Management plugins so we can apply them in our spring-service plugin
    implementation(libs.springBoot.gradlePlugin)
    implementation(libs.dependencyManagement.gradlePlugin)
    // We add the Wiremock standalone plugin library for our custom tasks
    implementation(libs.wiremock.standalone)
}

gradlePlugin {
    plugins {
        register("java-conventions") {
            id = "com.minicdesign.java-conventions"
            implementationClass = "com.minicdesign.buildlogic.JavaConventionsPlugin"
            displayName = "MinicDesign Java Conventions Plugin"
            description = "Applies standard conventions for Java projects, including toolchains, JUnit 5, Jacoco, and Spotless."
        }
        register("kotlin-conventions") {
            id = "com.minicdesign.kotlin-conventions"
            implementationClass = "com.minicdesign.buildlogic.KotlinConventionsPlugin"
            displayName = "MinicDesign Kotlin Conventions Plugin"
            description = "Applies standard conventions for Kotlin/JVM projects, configuring Kotlin compiler options and compiler arguments."
        }
        register("spring-service") {
            id = "com.minicdesign.spring-service"
            implementationClass = "com.minicdesign.buildlogic.SpringServicePlugin"
            displayName = "MinicDesign Spring Service Conventions Plugin"
            description = "Applies standard conventions for Spring Boot Web services in Java/Kotlin."
        }
        register("otel-base") {
            id = "com.minicdesign.otel-base"
            implementationClass = "com.minicdesign.buildlogic.OtelBasePlugin"
            displayName = "MinicDesign OpenTelemetry Base Plugin"
            description = "Applies base OpenTelemetry API, SDK, and Logback logging dependencies."
        }
        register("spring-otel-logging") {
            id = "com.minicdesign.spring-otel-logging"
            implementationClass = "com.minicdesign.buildlogic.SpringOtelLoggingPlugin"
            displayName = "MinicDesign Spring OpenTelemetry Logging Plugin"
            description = "Applies OpenTelemetry base logging and configures Spring Boot logging autoconfigurations."
        }
        register("wiremock") {
            id = "com.minicdesign.wiremock"
            implementationClass = "com.minicdesign.buildlogic.WiremockPlugin"
            displayName = "MinicDesign Wiremock Plugin"
            description = "Applies tasks to merge wiremock mappings/files and start/stop Wiremock server."
        }
        register("api-generation") {
            id = "com.minicdesign.api-generation"
            implementationClass = "com.minicdesign.buildlogic.ApiGenerationPlugin"
            displayName = "MinicDesign API Generation Plugin"
            description = "Automatically generates restful Java classes from OpenAPI specs and SOAP Java classes from WSDL files."
        }
        register("docker-compose") {
            id = "com.minicdesign.docker-compose"
            implementationClass = "com.minicdesign.buildlogic.DockerComposePlugin"
            displayName = "MinicDesign Docker Compose Plugin"
            description = "Registers tasks to manage docker-compose configurations per project."
        }
        register("pact") {
            id = "com.minicdesign.pact"
            implementationClass = "com.minicdesign.buildlogic.PactPlugin"
            displayName = "MinicDesign Pact Plugin"
            description = "Provides tasks to manage contracts on an open-source Pact Broker."
        }
    }
}
