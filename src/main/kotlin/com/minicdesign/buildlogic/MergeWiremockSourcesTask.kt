package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Merges mock source files on demand")
abstract class MergeWiremockSourcesTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class MappingRecord(
        val filePath: String,
        val requestJson: String,
        val responseJson: String,
        val mapping: com.github.tomakehurst.wiremock.stubbing.StubMapping,
        val targetName: String
    )

    private sealed interface MappingCollision {
        data class IdCollision(
            val id: java.util.UUID,
            val firstPath: String,
            val secondPath: String,
            val firstRequest: String,
            val secondRequest: String
        ) : MappingCollision

        data class ResponseCollision(
            val id: java.util.UUID,
            val request: String,
            val firstPath: String,
            val secondPath: String,
            val firstResponse: String,
            val secondResponse: String
        ) : MappingCollision
    }

    private data class MergedMapping(
        val requestSignature: String,
        val sourcePath: String,
        val targetPath: String
    )

    @TaskAction
    fun merge() {
        val dest = outputDir.get().asFile
        dest.deleteRecursively()
        dest.mkdirs()

        val mappingsDest = dest.resolve("mappings")
        val filesDest = dest.resolve("__files")

        val projectDir = project.projectDir
        val wiremockDirs = mutableListOf<File>()

        // Find all directories named 'wiremock' recursively
        findWiremockDirs(projectDir, wiremockDirs)

        val seenMappings = mutableMapOf<java.util.UUID, MappingRecord>()
        val conflicts = mutableListOf<MappingCollision>()
        val mergedList = mutableListOf<MergedMapping>()

        println("Found ${wiremockDirs.size} wiremock directory/directories:")
        for (dir in wiremockDirs) {
            println("  - ${dir.absolutePath}")

            val relativePathPrefix = dir.parentFile.relativeTo(projectDir).path
                .replace(File.separatorChar, '-')
                .replace(File.pathSeparatorChar, '-')
                .replace(":", "-")
                .trim('-')

            val prefix = if (relativePathPrefix.isEmpty()) "root" else relativePathPrefix

            // 1. Merge mappings with prefix to avoid collision and inject deterministic UUIDs
            val mappingsSrc = dir.resolve("mappings")
            if (mappingsSrc.exists() && mappingsSrc.isDirectory) {
                mappingsSrc.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension == "json") {
                        val relativeFile = file.relativeTo(mappingsSrc)
                        val targetName = "${prefix}-${relativeFile.path.replace(File.separatorChar, '-')}"
                        try {
                            val fileContent = file.readText()
                            val mapping = com.github.tomakehurst.wiremock.common.Json.read(
                                fileContent,
                                com.github.tomakehurst.wiremock.stubbing.StubMapping::class.java
                            )
                            val requestJson = com.github.tomakehurst.wiremock.common.Json.write(mapping.request)
                            val responseJson = com.github.tomakehurst.wiremock.common.Json.write(mapping.response)

                            val jsonMap = com.github.tomakehurst.wiremock.common.Json.read(
                                fileContent,
                                Map::class.java
                            )
                            val hasExplicitId = jsonMap.containsKey("id") || jsonMap.containsKey("uuid")

                            val id = if (hasExplicitId) {
                                mapping.id ?: java.util.UUID.nameUUIDFromBytes(
                                    requestJson.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                )
                            } else {
                                java.util.UUID.nameUUIDFromBytes(
                                    requestJson.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                )
                            }
                            
                            val existing = seenMappings[id]
                            if (existing != null) {
                                if (existing.requestJson != requestJson) {
                                    conflicts.add(
                                        MappingCollision.IdCollision(
                                            id = id,
                                            firstPath = existing.filePath,
                                            secondPath = file.absolutePath,
                                            firstRequest = existing.requestJson,
                                            secondRequest = requestJson
                                        )
                                    )
                                } else if (existing.responseJson != responseJson) {
                                    conflicts.add(
                                        MappingCollision.ResponseCollision(
                                            id = id,
                                            request = requestJson,
                                            firstPath = existing.filePath,
                                            secondPath = file.absolutePath,
                                            firstResponse = existing.responseJson,
                                            secondResponse = responseJson
                                        )
                                    )
                                } else {
                                    mergedList.add(
                                        MergedMapping(
                                            requestSignature = requestJson,
                                            sourcePath = file.absolutePath,
                                            targetPath = existing.filePath
                                        )
                                    )
                                }
                            } else {
                                mapping.id = id
                                seenMappings[id] = MappingRecord(
                                    filePath = file.absolutePath,
                                    requestJson = requestJson,
                                    responseJson = responseJson,
                                    mapping = mapping,
                                    targetName = targetName
                                )
                            }
                        } catch (e: Exception) {
                            println("  [Warning] Failed to parse/hash mapping file '${file.absolutePath}', falling back to direct copy: ${e.message}")
                            mappingsDest.mkdirs()
                            file.copyTo(mappingsDest.resolve(targetName), overwrite = true)
                        }
                    }
                }
            }

            // 2. Merge __files directly (to preserve reference path in mapping bodies)
            val filesSrc = dir.resolve("__files")
            if (filesSrc.exists() && filesSrc.isDirectory) {
                filesDest.mkdirs()
                filesSrc.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativeFile = file.relativeTo(filesSrc)
                        val targetFile = filesDest.resolve(relativeFile)
                        if (targetFile.exists()) {
                            println("  [Warning] Duplicate file '${relativeFile.path}' from '${dir.absolutePath}' will overwrite existing.")
                        }
                        targetFile.parentFile.mkdirs()
                        file.copyTo(targetFile, overwrite = true)
                    }
                }
            }
        }

        // Process conflicts and write successful mappings
        if (conflicts.isNotEmpty()) {
            val sb = java.lang.StringBuilder()
            sb.append("\n======================================================================\n")
            sb.append("   WIREMOCK MAPPING CONFLICTS DETECTED\n")
            sb.append("======================================================================\n")
            conflicts.forEachIndexed { index, conflict ->
                sb.append("${index + 1}. ")
                when (conflict) {
                    is MappingCollision.IdCollision -> {
                        sb.append("ID Collision (Same ID/Request-Hash, Different Requests)\n")
                        sb.append("   Mapping ID: ${conflict.id}\n")
                        sb.append("   File 1: ${conflict.firstPath}\n")
                        sb.append("   File 2: ${conflict.secondPath}\n")
                        sb.append("   Request 1: ${conflict.firstRequest}\n")
                        sb.append("   Request 2: ${conflict.secondRequest}\n")
                    }
                    is MappingCollision.ResponseCollision -> {
                        sb.append("Response Collision (Same Request, Different Responses)\n")
                        sb.append("   Mapping ID: ${conflict.id}\n")
                        sb.append("   Request: ${conflict.request}\n")
                        sb.append("   File 1: ${conflict.firstPath}\n")
                        sb.append("   File 2: ${conflict.secondPath}\n")
                        sb.append("   Response 1: ${conflict.firstResponse}\n")
                        sb.append("   Response 2: ${conflict.secondResponse}\n")
                    }
                }
                sb.append("----------------------------------------------------------------------\n")
            }
            val errorMsg = sb.toString()
            System.err.println(errorMsg)
            throw org.gradle.api.GradleException("Build halted due to WireMock mapping conflicts. See details in log above.")
        }

        // Notify user of merged mappings
        if (mergedList.isNotEmpty()) {
            println("\n======================================================================")
            println("   WIREMOCK IDENTICAL MAPPINGS MERGED")
            println("======================================================================")
            mergedList.forEach { merged ->
                println("Merged duplicate mapping:")
                println("   From: ${merged.sourcePath}")
                println("   Into: ${merged.targetPath}")
            }
            println("======================================================================\n")
        }

        // Write all unique mappings to build directory
        if (seenMappings.isNotEmpty()) {
            mappingsDest.mkdirs()
            seenMappings.values.forEach { record ->
                val targetFile = mappingsDest.resolve(record.targetName)
                targetFile.writeText(com.github.tomakehurst.wiremock.common.Json.write(record.mapping))
            }
        }
    }

    private fun findWiremockDirs(current: File, result: MutableList<File>) {
        if (!current.exists() || !current.isDirectory) return

        // Skip build directories and hidden folders
        if (current.name == "build" || current.name == ".gradle" || current.name == ".git") return

        if (current.name == "wiremock") {
            result.add(current)
            return
        }

        current.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                findWiremockDirs(file, result)
            }
        }
    }
}
