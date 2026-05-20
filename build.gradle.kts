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
    // We add the Spotless plugin so we can configure source formatting conventions
    implementation(libs.spotless.gradlePlugin)
    // We add the Spring Boot and Spring Dependency Management plugins so we can apply them in our spring-service plugin
    implementation(libs.springBoot.gradlePlugin)
    implementation(libs.dependencyManagement.gradlePlugin)
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
    }
}
