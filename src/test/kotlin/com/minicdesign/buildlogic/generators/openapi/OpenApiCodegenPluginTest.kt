package com.minicdesign.buildlogic.generators.openapi

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenApiCodegenPluginTest {

    @Test
    fun `applying OpenApiCodegenPlugin registers extension containers and lifecycle tasks without bundling other generators`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.openapi-codegen")

        val ext = project.extensions.findByType(OpenApiCodegenExtension::class.java)
        assertNotNull(ext, "OpenApiCodegenExtension should be registered")
        assertNotNull(project.extensions.findByName("openApiSpecs"), "openApiSpecs container should be registered")
        assertNotNull(project.extensions.findByName("openApiSpecDirectories"), "openApiSpecDirectories container should be registered")

        // OpenAPI tasks should be registered
        assertNotNull(project.tasks.findByName("openApiGenerate"), "openApiGenerate task should be registered")
        assertNotNull(project.tasks.findByName("generateOpenApi"), "generateOpenApi task should be registered")
        assertNotNull(project.tasks.findByName("openapiCodeGen"), "openapiCodeGen task should be registered")
        assertNotNull(project.tasks.findByName("generateApi"), "generateApi task should be registered")

        // Bundling check: WSDL, Protobuf, and GraphQL tasks must NOT be registered by OpenApiCodegenPlugin
        assertNull(project.tasks.findByName("generateWsdl"), "generateWsdl task must NOT be registered")
        assertNull(project.tasks.findByName("generateProto"), "generateProto task must NOT be registered")
        assertNull(project.tasks.findByName("generateGraphQL"), "generateGraphQL task must NOT be registered")
    }

    @Test
    fun `legacy plugin ids com minicdesign openapi-generation and api-generation apply OpenApiCodegenPlugin cleanly`() {
        val p1 = ProjectBuilder.builder().build()
        p1.plugins.apply("java")
        p1.plugins.apply("com.minicdesign.openapi-generation")
        assertNotNull(p1.extensions.findByType(OpenApiCodegenExtension::class.java), "Extension should be found via openapi-generation")

        val p2 = ProjectBuilder.builder().build()
        p2.plugins.apply("java")
        p2.plugins.apply("com.minicdesign.api-generation")
        assertNotNull(p2.extensions.findByType(OpenApiCodegenExtension::class.java), "Extension should be found via api-generation")
    }

    @Test
    fun `OpenApiSourcePostProcessor cleans annotations and enriches models`(@TempDir tempDir: File) {
        val modelDir = tempDir.resolve("com/example/model")
        modelDir.mkdirs()

        val sampleJava = """
            package com.example.model;

            import org.springframework.lang.Nullable;
            import org.hibernate.validator.constraints.*;
            import com.fasterxml.jackson.annotation.JsonProperty;
            import javax.annotation.processing.Generated;

            @Generated("openapi-generator")
            public class PaymentDto {

                @JsonProperty("items")
                public List<String> getItems() {
                    return items;
                }
            }
        """.trimIndent()

        val file = modelDir.resolve("PaymentDto.java")
        file.writeText(sampleJava)

        OpenApiSourcePostProcessor.processJavaFiles(tempDir, "com.example.model")

        val processed = file.readText()
        assertFalse(processed.contains("org.springframework.lang.Nullable"), "Should not contain Spring Nullable")
        assertTrue(processed.contains("org.jspecify.annotations.Nullable"), "Should contain JSpecify Nullable")
        assertFalse(processed.contains("org.hibernate.validator.constraints"), "Should strip Hibernate validator")
        assertTrue(processed.contains("@jakarta.annotation.Nonnull\n  public List<String> getItems()"), "Should annotate collection getters")
        assertTrue(processed.contains("@JsonInclude(JsonInclude.Include.NON_NULL)"), "Should add JsonInclude annotation")
    }

    @Test
    fun `OpenApiScopeExtractor extracts OAuth2 scopes and renders configuration`() {
        val sampleYaml = """
            openapi: 3.0.3
            info:
              title: Sample API
              version: 1.0.0
            components:
              securitySchemes:
                oauth2:
                  type: oauth2
                  flows:
                    clientCredentials:
                      tokenUrl: https://auth.example.com/token
                      scopes:
                        payments:read: Read payments
                        payments:write: Write payments
            paths:
              /payments:
                get:
                  operationId: getPayments
                  security:
                    - oauth2:
                        - payments:read
                post:
                  operationId: createPayment
                  security:
                    - oauth2:
                        - payments:write
        """.trimIndent()

        val catalog = OpenApiScopeExtractor.extract(sampleYaml)
        assertEquals(2, catalog.operations.size)
        assertEquals(2, catalog.distinctScopeSets().size)

        val rendered = OAuth2ScopeConfigWriter.render(
            catalogName = "payments",
            catalog = catalog,
            providerId = "okta",
            clientIdProperty = "\${oauth2.client-id}",
            clientSecretProperty = "\${oauth2.client-secret}",
            sourceDescription = "contracts/openapi/out/payments.yml"
        )

        assertTrue(rendered.contains("spring:"), "Should contain spring config")
        assertTrue(rendered.contains("provider: \"okta\""), "Should specify provider")
        assertTrue(rendered.contains("catalogs:"), "Should contain operation catalogs")
        assertTrue(rendered.contains("payments:"), "Should contain payments catalog")
        assertTrue(rendered.contains("method: \"GET\""), "Should contain GET operation")
        assertTrue(rendered.contains("method: \"POST\""), "Should contain POST operation")
        assertTrue(rendered.contains("path: \"/payments\""), "Should contain /payments path")
        assertTrue(rendered.contains("payments:read"), "Should contain payments:read scope")
        assertTrue(rendered.contains("payments:write"), "Should contain payments:write scope")
    }
}
