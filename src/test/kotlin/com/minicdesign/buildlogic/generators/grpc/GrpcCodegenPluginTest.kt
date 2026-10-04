package com.minicdesign.buildlogic.generators.grpc

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrpcCodegenPluginTest {

    @Test
    fun `applying GrpcCodegenPlugin registers extension and tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.grpc-codegen")

        val ext = project.extensions.findByType(GrpcCodegenExtension::class.java)
        assertNotNull(ext, "GrpcCodegenExtension should be registered")
        assertNotNull(project.tasks.findByName("generateProto"), "generateProto task should be registered")
        assertNotNull(project.tasks.findByName("grpcCodeGen"), "grpcCodeGen alias should be registered")

        val dirs = ext!!.protoDirectories.get()
        assertTrue(dirs.contains("contract/proto/in"), "Should contain contract/proto/in")
        assertTrue(dirs.contains("contract/proto/out"), "Should contain contract/proto/out")
        assertTrue(dirs.contains("contracts/proto/in"), "Should contain contracts/proto/in")
        assertTrue(dirs.contains("contracts/proto/out"), "Should contain contracts/proto/out")
        assertTrue(dirs.contains("src/main/proto"), "Should contain src/main/proto")
    }
}
