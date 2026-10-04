package com.minicdesign.buildlogic.generators.grpc

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface GrpcCodegenExtension {
    val protobufVersion: Property<String>
    val grpcVersion: Property<String>
    val generateGrpc: Property<Boolean>
    val outputDir: DirectoryProperty
    val grpcOutputDir: DirectoryProperty
    val protoDirectories: ListProperty<String>
}
