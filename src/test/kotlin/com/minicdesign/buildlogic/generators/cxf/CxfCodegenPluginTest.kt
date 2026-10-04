package com.minicdesign.buildlogic.generators.cxf

import org.gradle.api.tasks.JavaExec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CxfCodegenPluginTest {

    @Test
    fun `applying CxfCodegenPlugin registers extension and tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.cxf-codegen")

        val ext = project.extensions.findByType(CxfCodegenExtension::class.java)
        assertNotNull(ext, "CxfCodegenExtension should be registered")
        assertNotNull(project.tasks.findByName("generateWsdl"), "generateWsdl task should be registered")
        assertNotNull(project.tasks.findByName("cxfCodeGen"), "cxfCodeGen alias should be registered")
        assertNotNull(project.tasks.findByName("wsdl2java"), "wsdl2java alias should be registered")
        assertNotNull(project.configurations.findByName("wsdl2java"), "wsdl2java configuration should be registered")
    }

    @Test
    fun `configuring single wsdlLocation creates corresponding task with flags`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.cxf-codegen")

        val wsdlFile = tempDir.resolve("billing-service.wsdl")
        wsdlFile.writeText("<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" name=\"Billing\"/>")

        val ext = project.extensions.getByType(CxfCodegenExtension::class.java)
        ext.wsdlLocation.set(wsdlFile.absolutePath)
        ext.packageNames.set(listOf("com.example.billing"))

        // Trigger afterEvaluate
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val task = project.tasks.findByName("generateWsdlBillingService") as? JavaExec
        assertNotNull(task, "Task for billing-service.wsdl should be created")

        val args = task!!.allJvmArgs + (task.argumentProviders.flatMap { (it as org.gradle.process.CommandLineArgumentProvider).asArguments() })
        assertTrue(args.contains("-mark-generated"), "Should contain -mark-generated flag")
        assertTrue(args.contains("-autoNameResolution"), "Should contain -autoNameResolution flag")
        assertTrue(args.contains("-p"), "Should contain -p flag")
        assertTrue(args.any { it.endsWith("billing-service.wsdl") }, "Should contain path to wsdl file")
    }

    @Test
    fun `convention directory scanning discovers wsdl files under contracts wsdl`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.cxf-codegen")

        val contractsWsdl = tempDir.resolve("contracts/wsdl")
        contractsWsdl.mkdirs()
        val shippingWsdl = contractsWsdl.resolve("shipping.wsdl")
        shippingWsdl.writeText("<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" name=\"Shipping\"/>")

        // Evaluate project
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val task = project.tasks.findByName("generateWsdlShipping") as? JavaExec
        assertNotNull(task, "Task for shipping.wsdl should be auto-discovered and created")
    }

    @Test
    fun `convention scanning handles inbound and outbound wsdl folders with isolated package namespaces`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply("java")
        project.plugins.apply("com.minicdesign.cxf-codegen")

        val inDir = tempDir.resolve("contracts/wsdl/in")
        inDir.mkdirs()
        inDir.resolve("orders.wsdl").writeText("<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" name=\"Orders\"/>")

        val outDir = tempDir.resolve("contracts/wsdl/out")
        outDir.mkdirs()
        outDir.resolve("payments.wsdl").writeText("<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" name=\"Payments\"/>")

        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val inTask = project.tasks.findByName("generateWsdlInOrders") as? JavaExec
        assertNotNull(inTask, "Task for in/orders.wsdl should exist")
        val inArgs = inTask!!.allJvmArgs + (inTask.argumentProviders.flatMap { (it as org.gradle.process.CommandLineArgumentProvider).asArguments() })
        assertTrue(inArgs.contains("com.minicdesign.generated.wsdl.inbound.orders"), "Inbound WSDL should target inbound package")

        val outTask = project.tasks.findByName("generateWsdlOutPayments") as? JavaExec
        assertNotNull(outTask, "Task for out/payments.wsdl should exist")
        val outArgs = outTask!!.allJvmArgs + (outTask.argumentProviders.flatMap { (it as org.gradle.process.CommandLineArgumentProvider).asArguments() })
        assertTrue(outArgs.contains("com.minicdesign.generated.wsdl.outbound.payments"), "Outbound WSDL should target outbound package")
    }
}
