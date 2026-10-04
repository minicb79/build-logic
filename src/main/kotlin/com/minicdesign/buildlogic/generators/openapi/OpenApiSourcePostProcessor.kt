package com.minicdesign.buildlogic.generators.openapi

import java.io.File
import java.nio.charset.StandardCharsets

object OpenApiSourcePostProcessor {

    fun processJavaFiles(generatedSourceRoot: File, modelPackage: String?) {
        if (!generatedSourceRoot.exists() || !generatedSourceRoot.isDirectory) return

        // 1. General Java file cleanup across all generated sources
        generatedSourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .forEach { file ->
                val original = file.readText(StandardCharsets.UTF_8)
                var content = original

                // Replace deprecated Spring @Nullable import with JSpecify equivalent
                content = content.replace(
                    "import org.springframework.lang.Nullable;",
                    "import org.jspecify.annotations.Nullable;"
                )

                // Remove org.hibernate.validator.constraints.* unconditionally — all bean
                // validation in this project uses jakarta.validation.constraints.* instead.
                content = content.replace(Regex("import org\\.hibernate\\.validator\\.constraints\\..*;\r?\n"), "")

                // Collapse consecutive blank lines left behind by removed imports
                content = content.replace(Regex("\r?\n{3,}"), "\n\n")

                if (content != original) {
                    file.writeText(content, StandardCharsets.UTF_8)
                }
            }

        // 2. Model-specific annotations: @Nonnull on collection getters and @JsonInclude on classes
        if (!modelPackage.isNullOrBlank()) {
            val modelDir = generatedSourceRoot.resolve(modelPackage.replace('.', '/'))
            if (modelDir.exists() && modelDir.isDirectory) {
                modelDir.listFiles { file -> file.isFile && file.name.endsWith(".java") }?.forEach { file ->
                    var content = file.readText(StandardCharsets.UTF_8)

                    // Add @jakarta.annotation.Nonnull to List getters with @JsonProperty
                    content = content.replace(
                        Regex("""(\s+@JsonProperty\("[^"]+"\)\s+)(public List<)"""),
                        "$1@jakarta.annotation.Nonnull\n  $2"
                    )

                    // Add @JsonInclude(JsonInclude.Include.NON_NULL) to model classes
                    if (!content.contains("@JsonInclude")) {
                        if (!content.contains("import com.fasterxml.jackson.annotation.JsonInclude;")) {
                            content = content.replace(
                                "import com.fasterxml.jackson.annotation.JsonProperty;",
                                "import com.fasterxml.jackson.annotation.JsonInclude;\nimport com.fasterxml.jackson.annotation.JsonProperty;"
                            )
                        }

                        content = content.replace(
                            Regex("""(@Generated[^\n]+\n)(public class )"""),
                            "$1@JsonInclude(JsonInclude.Include.NON_NULL)\n$2"
                        )
                    }

                    file.writeText(content, StandardCharsets.UTF_8)
                }
            }
        }
    }
}
