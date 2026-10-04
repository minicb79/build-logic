package com.minicdesign.buildlogic.generators.graphql

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLCodegenPluginTest {

    @Test
    fun `applying GraphQLCodegenPlugin registers extension and tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.graphql-codegen")

        val ext = project.extensions.findByType(GraphQLCodegenExtension::class.java)
        assertNotNull(ext, "GraphQLCodegenExtension should be registered")
        assertNotNull(project.tasks.findByName("generateGraphQL"), "generateGraphQL task should be registered")
        assertNotNull(project.tasks.findByName("graphqlCodeGen"), "graphqlCodeGen alias should be registered")

        assertEquals("com.minicdesign.generated.graphql", ext!!.packageName.get())
        assertTrue(ext.schemaPaths.get().contains("contract/graphql"))
        assertTrue(ext.schemaPaths.get().contains("contracts/graphql"))
    }
}
