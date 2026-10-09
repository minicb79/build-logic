package com.minicdesign.buildlogic.infra

import com.minicdesign.buildlogic.generators.openapi.OpenApiScopeExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Extracts OpenAPI scopes and writes Terraform auto tfvars")
abstract class SyncOpenApiScopesTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val terraformDir: DirectoryProperty

    init {
        group = "infra"
        description = "Extracts OAuth2 scopes from OpenAPI contracts and synchronizes them to Terraform vars."
    }

    @TaskAction
    fun syncScopes() {
        val contracts = contractsDir.get().asFile
        val tfDir = terraformDir.get().asFile

        val openApiExtensions = setOf("yaml", "yml", "json")
        val candidateFiles = if (contracts.exists() && contracts.isDirectory) {
            contracts.walkTopDown()
                .filter { it.isFile && openApiExtensions.contains(it.extension.lowercase()) }
                .toList()
        } else {
            emptyList()
        }

        val allDeclaredScopes = mutableSetOf<String>()
        val allScopedOperations = mutableListOf<Map<String, Any>>()

        candidateFiles.forEach { file ->
            try {
                val catalog = OpenApiScopeExtractor.extract(file.readText())
                allDeclaredScopes.addAll(catalog.declaredScopes)
                catalog.operations.forEach { op ->
                    allScopedOperations.add(
                        mapOf(
                            "method" to op.method,
                            "path" to op.path,
                            "scopes" to op.scopes
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn("Could not parse OAuth2 scopes from ${file.name}: ${e.message}")
            }
        }

        val scopesList = allDeclaredScopes.sorted().map { scope ->
            val description = "OAuth2 scope '$scope' synchronized from OpenAPI contract"
            mapOf("name" to scope, "description" to description)
        }

        val jsonLines = StringBuilder()
        jsonLines.append("{\n")
        jsonLines.append("  \"oauth_scopes\": [\n")
        scopesList.forEachIndexed { index, s ->
            val comma = if (index < scopesList.size - 1) "," else ""
            jsonLines.append("    { \"name\": \"${s["name"]}\", \"description\": \"${s["description"]}\" }$comma\n")
        }
        jsonLines.append("  ]\n")
        jsonLines.append("}\n")

        tfDir.mkdirs()
        val targetFile = tfDir.resolve("scopes.auto.tfvars.json")
        targetFile.writeText(jsonLines.toString())

        logger.lifecycle("Synchronized ${allDeclaredScopes.size} OAuth2 scope(s) from OpenAPI contracts into ${targetFile.relativeTo(project.projectDir)}.")
    }
}
