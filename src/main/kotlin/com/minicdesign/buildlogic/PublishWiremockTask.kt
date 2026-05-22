package com.minicdesign.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@DisableCachingByDefault(because = "Publishes mappings to external WireMock server")
abstract class PublishWiremockTask : DefaultTask() {

    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val checkMissingOnly: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun publish() {
        val baseUrl = serverUrl.get().trimEnd('/')
        val diffMode = checkMissingOnly.get()
        val mappingsDir = rootDir.get().asFile.resolve("mappings")

        if (!mappingsDir.exists() || !mappingsDir.isDirectory) {
            println("No mappings directory found at ${mappingsDir.absolutePath}. Nothing to publish.")
            return
        }

        val jsonFiles = mappingsDir.listFiles { file -> file.isFile && file.extension == "json" } ?: emptyArray()
        if (jsonFiles.isEmpty()) {
            println("No mapping files found to publish.")
            return
        }

        println("Publishing mappings to $baseUrl (checkMissingOnly = $diffMode)...")

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val deployedIds = if (diffMode) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/__admin/mappings"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) {
                    val jsonMap = com.github.tomakehurst.wiremock.common.Json.read(response.body(), Map::class.java)
                    val mappingsList = jsonMap["mappings"] as? List<*> ?: emptyList<Any>()
                    mappingsList.mapNotNull {
                        val mappingObj = it as? Map<*, *>
                        mappingObj?.get("id")?.toString() ?: mappingObj?.get("uuid")?.toString()
                    }.toSet()
                } else {
                    println("Warning: GET /__admin/mappings returned status ${response.statusCode()}. Defaulting to publish all mappings.")
                    emptySet()
                }
            } catch (e: Exception) {
                println("Warning: Failed to fetch deployed mappings (${e.message}). Defaulting to publish all mappings.")
                emptySet()
            }
        } else {
            emptySet()
        }

        var publishedCount = 0
        var skippedCount = 0

        for (file in jsonFiles) {
            try {
                val fileContent = file.readText()
                val jsonMap = com.github.tomakehurst.wiremock.common.Json.read(fileContent, Map::class.java)
                val id = jsonMap["id"]?.toString() ?: jsonMap["uuid"]?.toString()

                if (diffMode && id != null && deployedIds.contains(id)) {
                    skippedCount++
                    continue
                }

                val postRequest = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/__admin/mappings"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(fileContent))
                    .build()

                val postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString())
                if (postResponse.statusCode() in 200..299) {
                    publishedCount++
                } else {
                    System.err.println("Error publishing mapping ${file.name}: ${postResponse.statusCode()} - ${postResponse.body()}")
                }
            } catch (e: Exception) {
                System.err.println("Failed to publish mapping ${file.name}: ${e.message}")
            }
        }

        println("Publication complete. Published: $publishedCount, Skipped (already deployed): $skippedCount")
    }
}
