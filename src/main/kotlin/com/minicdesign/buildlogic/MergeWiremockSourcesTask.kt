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

        println("Found ${wiremockDirs.size} wiremock directory/directories:")
        for (dir in wiremockDirs) {
            println("  - ${dir.absolutePath}")

            val relativePathPrefix = dir.parentFile.relativeTo(projectDir).path
                .replace(File.separatorChar, '-')
                .replace(File.pathSeparatorChar, '-')
                .replace(":", "-")
                .trim('-')

            val prefix = if (relativePathPrefix.isEmpty()) "root" else relativePathPrefix

            // 1. Merge mappings with prefix to avoid collision
            val mappingsSrc = dir.resolve("mappings")
            if (mappingsSrc.exists() && mappingsSrc.isDirectory) {
                mappingsDest.mkdirs()
                mappingsSrc.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension == "json") {
                        val relativeFile = file.relativeTo(mappingsSrc)
                        val targetName = "${prefix}-${relativeFile.path.replace(File.separatorChar, '-')}"
                        val targetFile = mappingsDest.resolve(targetName)
                        file.copyTo(targetFile, overwrite = true)
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
