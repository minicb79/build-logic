package com.minicdesign.buildlogic.generators.graphql

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

interface GraphQLCodegenExtension {
    val packageName: Property<String>
    val schemaPaths: ListProperty<String>
    val generateClient: Property<Boolean>
    val generateDataTypes: Property<Boolean>
    val generateInterfaces: Property<Boolean>
    val outputDir: DirectoryProperty
    val typeMapping: MapProperty<String, String>
}
