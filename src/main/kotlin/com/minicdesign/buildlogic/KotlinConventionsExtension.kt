package com.minicdesign.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

open class KotlinConventionsExtension(project: Project) {
    val jvmTarget: Property<Int> = project.objects.property(Int::class.java).convention(25)
    val freeCompilerArgs: ListProperty<String> = project.objects.listProperty(String::class.java).convention(
        listOf("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    )
}
