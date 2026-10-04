package com.minicdesign.buildlogic.hexagonal

import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test as GradleTestTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HexagonalArchitecturePluginTest {

    @Test
    fun `applying HexagonalArchitecturePlugin registers extension and tasks with single module default`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.findByType(HexagonalArchitectureExtension::class.java)
        assertNotNull(ext, "HexagonalArchitectureExtension should be registered")
        assertEquals(HexagonalMode.SINGLE_MODULE, ext!!.mode.get(), "Default mode should be SINGLE_MODULE")
        assertTrue(ext.contractsAsModule.get(), "contractsAsModule should default to true")
        assertNotNull(project.tasks.findByName("checkHexagonalArchitecture"), "checkHexagonalArchitecture task should be registered")
        assertNotNull(project.tasks.findByName("scaffoldHexagonalArchitecture"), "scaffoldHexagonalArchitecture task should be registered")
    }

    @Test
    fun `scaffoldHexagonalArchitecture in single module mode creates standard src, contracts and wiremock`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        assertTrue(tempDir.resolve("src/main/java").exists(), "src/main/java directory should exist")
        assertTrue(tempDir.resolve("src/main/resources").exists(), "src/main/resources directory should exist")
        assertTrue(tempDir.resolve("src/testIntegration/resources").exists(), "src/testIntegration/resources directory should exist")
        assertTrue(tempDir.resolve("contracts/openapi/in").exists(), "contracts/openapi/in directory should exist")
        assertTrue(tempDir.resolve("contracts/openapi/out").exists(), "contracts/openapi/out directory should exist")
        assertTrue(tempDir.resolve("wiremock/mappings").exists(), "wiremock/mappings directory should exist")
        assertTrue(tempDir.resolve("wiremock/__files").exists(), "wiremock/__files directory should exist")

        // checkHexagonalArchitecture should pass cleanly in SINGLE_MODULE mode
        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `checkHexagonalArchitecture in single module mode detects missing contracts or wiremock`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        tempDir.resolve("src/main").mkdirs()
        // Missing contracts and wiremock

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        val exception = assertThrows(GradleException::class.java) {
            checkTask.checkStructure()
        }

        assertTrue(exception.message!!.contains("Missing API contracts directory"), "Should flag missing contracts")
        assertTrue(exception.message!!.contains("Missing WireMock stubs directory"), "Should flag missing wiremock")
    }

    @Test
    fun `scaffoldHexagonalArchitecture in multi module mode creates contracts as its own module and subprojects`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        assertTrue(tempDir.resolve("app/boot/build.gradle.kts").exists(), "app/boot build script should exist")
        assertTrue(tempDir.resolve("contracts/build.gradle.kts").exists(), "contracts build script should exist as its own module")
        assertTrue(tempDir.resolve("contracts/openapi/in").exists(), "contracts/openapi/in directory should exist")
        assertTrue(tempDir.resolve("contracts/openapi/out").exists(), "contracts/openapi/out directory should exist")
        assertTrue(tempDir.resolve("lib/core/build.gradle.kts").exists(), "lib/core build script should exist")
        assertTrue(tempDir.resolve("lib/adapters/in").exists(), "lib/adapters/in directory should exist")
        assertTrue(tempDir.resolve("lib/adapters/out").exists(), "lib/adapters/out directory should exist")
        assertTrue(tempDir.resolve("wiremock/mappings").exists(), "wiremock/mappings directory should exist")
        assertTrue(tempDir.resolve("wiremock/__files").exists(), "wiremock/__files directory should exist")

        // checkHexagonalArchitecture should pass cleanly in MULTI_MODULE mode
        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `checkHexagonalArchitecture in multi module mode validates contracts module build script`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        // Delete contracts build.gradle.kts to simulate contracts not being a Gradle module
        tempDir.resolve("contracts/build.gradle.kts").delete()

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        val exception = assertThrows(GradleException::class.java) {
            checkTask.checkStructure()
        }

        assertTrue(exception.message!!.contains("contracts"), "Should flag contracts directory not being a Gradle module")
    }

    @Test
    fun `checkHexagonalArchitecture in multi module mode validates adapter module build scripts`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        // Create an adapter folder without build.gradle[.kts]
        val invalidAdapter = tempDir.resolve("lib/adapters/in/invalid-adapter")
        invalidAdapter.mkdirs()

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        val exception = assertThrows(GradleException::class.java) {
            checkTask.checkStructure()
        }

        assertTrue(exception.message!!.contains("invalid-adapter"), "Should flag invalid adapter missing build script")
    }

    @Test
    fun `plugin wires service wiremock directory into testIntegration resources and task properties`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.java-conventions")
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val wiremockDir = tempDir.resolve("wiremock/mappings")
        wiremockDir.mkdirs()

        // Trigger afterEvaluate
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val testInt = javaExt.sourceSets.getByName("testIntegration")
        val resourceDirs = testInt.resources.srcDirs
        assertTrue(resourceDirs.any { it.name == "wiremock" }, "testIntegration resources should include wiremock dir")

        val intTestTask = project.tasks.findByName("integrationTest") as? GradleTestTask
        assertNotNull(intTestTask, "integrationTest task should exist")
        val rootDirProp = intTestTask!!.systemProperties["wiremock.root-dir"]
        assertNotNull(rootDirProp, "wiremock.root-dir system property should be set")
        assertEquals(tempDir.resolve("wiremock").canonicalPath, File(rootDirProp.toString()).canonicalPath)

        val testIntegrationAlias = project.tasks.findByName("testIntegration")
        assertNotNull(testIntegrationAlias, "testIntegration task alias should exist")
    }

    @Test
    fun `monorepo mode scaffolding creates services and shared directories with starter modules`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.monorepo.set(true)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        assertTrue(tempDir.resolve("services/sample-service/src/main/java").exists(), "sample-service src/main should exist")
        assertTrue(tempDir.resolve("services/sample-service/contracts/openapi/in").exists(), "sample-service contracts should exist")
        assertTrue(tempDir.resolve("services/sample-service/wiremock/mappings").exists(), "sample-service wiremock should exist")
        assertTrue(tempDir.resolve("shared/common/build.gradle.kts").exists(), "shared/common build script should exist")

        // checkHexagonalArchitecture should pass cleanly in monorepo mode
        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `monorepo mode with multi-module services scaffolds and validates complete multi-module layout`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.monorepo.set(true)
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        assertTrue(tempDir.resolve("services/sample-service/app/boot/build.gradle.kts").exists(), "app/boot should exist")
        assertTrue(tempDir.resolve("services/sample-service/contracts/build.gradle.kts").exists(), "contracts module should exist")
        assertTrue(tempDir.resolve("services/sample-service/lib/core/build.gradle.kts").exists(), "lib/core should exist")
        assertTrue(tempDir.resolve("shared/common/build.gradle.kts").exists(), "shared/common should exist")

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `monorepo mode detects shared directory containing invalid module`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.monorepo.set(true)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        // Create an invalid shared module without a build script
        tempDir.resolve("shared/invalid-lib").mkdirs()

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        val exception = assertThrows(GradleException::class.java) {
            checkTask.checkStructure()
        }

        assertTrue(exception.message!!.contains("invalid-lib"), "Should flag invalid shared library missing build script")
    }

    @Test
    fun `monorepo mode supports mixed modes with serviceModes map`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.monorepo.set(true)
        ext.mode.set(HexagonalMode.SINGLE_MODULE)
        ext.serviceModes.put("order-service", HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        // Scaffold order-service as multi-module
        val orderDir = tempDir.resolve("services/order-service")
        orderDir.resolve("app/boot").mkdirs()
        orderDir.resolve("app/boot/build.gradle.kts").writeText("")
        orderDir.resolve("contracts").mkdirs()
        orderDir.resolve("contracts/build.gradle.kts").writeText("")
        orderDir.resolve("lib/core").mkdirs()
        orderDir.resolve("lib/core/build.gradle.kts").writeText("")
        orderDir.resolve("lib/adapters/in").mkdirs()
        orderDir.resolve("lib/adapters/out").mkdirs()
        orderDir.resolve("wiremock").mkdirs()

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `parseContractTypes correctly parses valid types, ignores misspellings, and defaults to openapi`() {
        assertEquals(listOf("openapi"), ScaffoldHexagonalArchitectureTask.parseContractTypes(""))
        assertEquals(listOf("openapi"), ScaffoldHexagonalArchitectureTask.parseContractTypes(null))
        assertEquals(listOf("openapi", "grpc"), ScaffoldHexagonalArchitectureTask.parseContractTypes("openapi, grpc"))
        assertEquals(listOf("grpc"), ScaffoldHexagonalArchitectureTask.parseContractTypes("openpi, grpc, invalid"))
        assertEquals(listOf("wsdl", "graphql"), ScaffoldHexagonalArchitectureTask.parseContractTypes("WSDL, GRAPHQL, typo"))
        assertEquals(listOf("openapi"), ScaffoldHexagonalArchitectureTask.parseContractTypes("foo, bar, baz"))
    }

    @Test
    fun `scaffolding with custom module name, base package, and contract types creates package and specs`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.basePackage.set("com.custom.company")

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.targetModuleName.set("billing-service")
        scaffoldTask.targetBasePackage.set("com.custom.company.billing")
        scaffoldTask.targetContractTypes.set("grpc, graphql, typo")
        scaffoldTask.scaffold()

        assertTrue(tempDir.resolve("src/main/java/com/custom/company/billing").exists(), "Custom package path should exist")
        assertTrue(tempDir.resolve("contracts/grpc/in").exists(), "contracts/grpc/in should exist")
        assertTrue(tempDir.resolve("contracts/grpc/out").exists(), "contracts/grpc/out should exist")
        assertTrue(tempDir.resolve("contracts/graphql/in").exists(), "contracts/graphql/in should exist")
        assertTrue(tempDir.resolve("contracts/graphql/out").exists(), "contracts/graphql/out should exist")
        // Misspelled 'typo' should be ignored
        assertTrue(!tempDir.resolve("contracts/typo").exists(), "Misspelled contract type should not be created")

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `scaffoldAdapter task creates adapter module under adapters in or out with package`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.scaffold()

        val scaffoldAdapterTask = project.tasks.getByName("scaffoldAdapter") as ScaffoldHexagonalArchitectureTask
        scaffoldAdapterTask.targetAdapterType.set("in")
        scaffoldAdapterTask.targetModuleName.set("web-rest")
        scaffoldAdapterTask.targetBasePackage.set("com.example.adapters.in.webrest")
        scaffoldAdapterTask.scaffold()

        assertTrue(tempDir.resolve("lib/adapters/in/web-rest/build.gradle.kts").exists(), "Adapter build script should exist")
        assertTrue(tempDir.resolve("lib/adapters/in/web-rest/src/main/java/com/example/adapters/in/webrest").exists(), "Adapter package should exist")

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }

    @Test
    fun `scaffolding configures output packages for openapi, wsdl, and graphql, and documents grpc in build script`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("com.minicdesign.hexagonal-architecture")

        val ext = project.extensions.getByType(HexagonalArchitectureExtension::class.java)!!
        ext.mode.set(HexagonalMode.MULTI_MODULE)

        val scaffoldTask = project.tasks.getByName("scaffoldHexagonalArchitecture") as ScaffoldHexagonalArchitectureTask
        scaffoldTask.targetModuleName.set("order-service")
        scaffoldTask.targetBasePackage.set("com.mycorp.order")
        scaffoldTask.targetContractTypes.set("openapi, wsdl, graphql, grpc")
        scaffoldTask.targetOpenApiPackage.set("com.mycorp.order.api")
        scaffoldTask.targetWsdlPackage.set("com.mycorp.order.soap")
        scaffoldTask.targetGraphqlPackage.set("com.mycorp.order.graphql")
        scaffoldTask.scaffold()

        val contractsBuild = tempDir.resolve("contracts/build.gradle.kts")
        assertTrue(contractsBuild.exists(), "contracts/build.gradle.kts should exist")
        val content = contractsBuild.readText()

        // 1. Verify generator plugins applied
        assertTrue(content.contains("com.minicdesign.openapi-codegen"), "OpenAPI plugin should be applied")
        assertTrue(content.contains("com.minicdesign.cxf-codegen"), "CXF plugin should be applied")
        assertTrue(content.contains("com.minicdesign.graphql-codegen"), "GraphQL plugin should be applied")
        assertTrue(content.contains("com.minicdesign.grpc-codegen"), "gRPC plugin should be applied")

        // 2. Verify generator configurations
        assertTrue(content.contains("openapiCodegen {"), "openapiCodegen configuration block should exist")
        assertTrue(content.contains("openApiBasePackage.set(\"com.mycorp.order.api\")"), "openApiBasePackage should be configured")

        assertTrue(content.contains("cxfCodegen {"), "cxfCodegen configuration block should exist")
        assertTrue(content.contains("wsdlBasePackage.set(\"com.mycorp.order.soap\")"), "wsdlBasePackage should be configured")

        assertTrue(content.contains("graphqlCodegen {"), "graphqlCodegen configuration block should exist")
        assertTrue(content.contains("packageName.set(\"com.mycorp.order.graphql\")"), "graphql packageName should be configured")

        // 3. Verify gRPC documentation comment in build script
        assertTrue(content.contains("NOTE for gRPC: The output package is configured directly in your .proto file(s)"), "gRPC documentation comment should exist")
        assertTrue(content.contains("option java_package = \"com.yourcompany.package\""), "gRPC instruction snippet should exist")

        // 4. Verify sample.proto created with java_package
        val protoFile = tempDir.resolve("contracts/grpc/in/sample.proto")
        assertTrue(protoFile.exists(), "sample.proto should be created")
        val protoContent = protoFile.readText()
        assertTrue(protoContent.contains("option java_package = \"com.mycorp.order.grpc\";"), "sample.proto should have java_package configured")

        val checkTask = project.tasks.getByName("checkHexagonalArchitecture") as CheckHexagonalArchitectureTask
        checkTask.checkStructure()
    }
}
