package com.minicdesign.buildlogic.dockercompose

import java.io.File
import java.io.Serializable

data class DockerComposeConfig(
    val submodulePath: String,
    val componentName: String,
    val composeFile: File,
    val dockerPath: String?,
    val dockerComposePath: String?
) : Serializable
