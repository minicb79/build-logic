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
    // We add the ArchUnit gradle plugin for architectural validation tasks
    implementation(libs.archunit.gradlePlugin) {
        exclude(group = "com.tngtech.archunit")
    }
    implementation("com.societegenerale.commons:arch-unit-build-plugin-core:2.9.5") {
        exclude(group = "com.tngtech.archunit")
    }
    implementation("com.tngtech.archunit:archunit:1.4.2")
    implementation("com.tngtech.archunit:archunit-junit5-api:1.4.2")
    implementation("org.yaml:snakeyaml:2.2")
    implementation(libs.protobuf.gradlePlugin)
    implementation(libs.dgs.codegen.gradlePlugin)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
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
            description = "Applies standard conventions for Spring Boot Web services in Java."
        }
        register("spring-service-kotlin") {
            id = "com.minicdesign.spring-service-kotlin"
            implementationClass = "com.minicdesign.buildlogic.SpringServiceKotlinPlugin"
            displayName = "MinicDesign Spring Service Kotlin Conventions Plugin"
            description = "Applies standard conventions for Spring Boot Web services in Kotlin."
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
            implementationClass = "com.minicdesign.buildlogic.wiremock.WiremockPlugin"
            displayName = "MinicDesign Wiremock Plugin"
            description = "Applies tasks to merge wiremock mappings/files and start/stop Wiremock server."
        }
        register("openapi-codegen") {
            id = "com.minicdesign.openapi-codegen"
            implementationClass = "com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenPlugin"
            displayName = "MinicDesign OpenAPI Codegen Plugin"
            description = "Automatically generates type-safe Java classes and interfaces from OpenAPI (REST) specifications."
        }
        register("openapi-generation") {
            id = "com.minicdesign.openapi-generation"
            implementationClass = "com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenPlugin"
            displayName = "MinicDesign OpenAPI Generation Plugin (Legacy Alias)"
            description = "Legacy alias for com.minicdesign.openapi-codegen."
        }
        register("api-generation") {
            id = "com.minicdesign.api-generation"
            implementationClass = "com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenPlugin"
            displayName = "MinicDesign API Generation Plugin (Legacy Alias)"
            description = "Legacy alias for com.minicdesign.openapi-codegen."
        }
        register("cxf-codegen") {
            id = "com.minicdesign.cxf-codegen"
            implementationClass = "com.minicdesign.buildlogic.generators.cxf.CxfCodegenPlugin"
            displayName = "MinicDesign CXF Codegen Plugin"
            description = "Generates Java client and model classes from WSDL files using Apache CXF wsdl2java."
        }
        register("grpc-codegen") {
            id = "com.minicdesign.grpc-codegen"
            implementationClass = "com.minicdesign.buildlogic.generators.grpc.GrpcCodegenPlugin"
            displayName = "MinicDesign gRPC Codegen Plugin"
            description = "Generates Protobuf messages and gRPC stubs from .proto contracts."
        }
        register("graphql-codegen") {
            id = "com.minicdesign.graphql-codegen"
            implementationClass = "com.minicdesign.buildlogic.generators.graphql.GraphQLCodegenPlugin"
            displayName = "MinicDesign GraphQL Codegen Plugin"
            description = "Generates Java data types, client query APIs, and interfaces from GraphQL schemas using Netflix DGS Codegen."
        }
        register("docker-compose") {
            id = "com.minicdesign.docker-compose"
            implementationClass = "com.minicdesign.buildlogic.dockercompose.DockerComposePlugin"
            displayName = "MinicDesign Docker Compose Plugin"
            description = "Registers tasks to manage docker-compose configurations per project."
        }
        register("pact") {
            id = "com.minicdesign.pact"
            implementationClass = "com.minicdesign.buildlogic.PactPlugin"
            displayName = "MinicDesign Pact Plugin"
            description = "Provides tasks to manage contracts on an open-source Pact Broker."
        }
        register("archunit") {
            id = "com.minicdesign.archunit"
            implementationClass = "com.minicdesign.buildlogic.archunit.ArchUnitPlugin"
            displayName = "MinicDesign ArchUnit Plugin"
            description = "Applies architectural governance and code quality rules based on Societe Generale ArchUnit rules."
        }
        register("hexagonal-architecture") {
            id = "com.minicdesign.hexagonal-architecture"
            implementationClass = "com.minicdesign.buildlogic.hexagonal.HexagonalArchitecturePlugin"
            displayName = "MinicDesign Hexagonal Architecture Plugin"
            description = "Enforces the standard Hexagonal Architecture directory structure (app/boot, contracts, lib/core, lib/adapters/in, lib/adapters/out, wiremock)."
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.tngtech.archunit") {
            useVersion("1.4.2")
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
