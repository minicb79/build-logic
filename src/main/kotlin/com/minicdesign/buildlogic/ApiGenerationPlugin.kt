package com.minicdesign.buildlogic

import com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenExtension
import com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenPlugin

@Deprecated("Renamed to OpenApiCodegenPlugin", ReplaceWith("com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenPlugin"))
open class ApiGenerationPlugin : OpenApiCodegenPlugin()

@Deprecated("Renamed to OpenApiCodegenExtension", ReplaceWith("com.minicdesign.buildlogic.generators.openapi.OpenApiCodegenExtension"))
typealias ApiGenerationExtension = OpenApiCodegenExtension
