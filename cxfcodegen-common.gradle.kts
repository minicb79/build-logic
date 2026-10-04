/*
 * WARNING: This file is experimental and non-functional.
 * It is a placeholder to evaluate the cxf codegen plugin versus wsdl2java.
 * This code is not implemented or usable yet.
 * Do NOT use this file in production or rely on its contents.
 */
import io.mateo.cxf.codegen.wsdl2java.Wsdl2Java

val rootPackage: String by rootProject
val wsdlLocation: String? by project

plugins {
    alias(libs.plugins.cxfcodegen)
    java
}


val wsdlSpecLocation = "$projectDir/src/main/resources/${wsdlLocation}"
val hasWsdl = !wsdlLocation.equals("")
if (hasWsdl && file(wsdlSpecLocation).exists()) {
    tasks.register("cxfCodeGen", Wsdl2Java::class) {
        toolOptions {
            wsdl.set(wsdlLocation)
            outputDir.set(layout.projectDirectory.dir("build/generated/client"))
            markGenerated.set(true)
            packageNames.set(listOf(rootPackage))
            packageNames.set(listOf("${rootPackage}.generated.client"))
        }
    }
   cxfCodegen {

   }
}
if (hasWsdl) {
    tasks.named("compileJava") {
        dependsOn("wsdl2java")
    }
}

// Ensure compilation depends on code generation
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "${project.layout.buildDirectory.asFile.get()}/generated/src/main/java",
                "${project.layout.buildDirectory.asFile.get()}/generated/client"
            )
        }
    }
}
