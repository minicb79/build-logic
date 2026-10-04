package com.minicdesign.buildlogic.generators.cxf

import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class WsdlSpec @Inject constructor(@get:Internal val specName: String) : Named {
    override fun getName(): String = specName

    abstract val wsdlFile: RegularFileProperty
    abstract val wsdlLocation: Property<String>
    abstract val outputDir: DirectoryProperty
    abstract val packageNames: ListProperty<String>
    abstract val markGenerated: Property<Boolean>
    abstract val autoNameResolution: Property<Boolean>
    abstract val outbound: Property<Boolean>
    abstract val generateClient: Property<Boolean>
    abstract val generateServer: Property<Boolean>
    abstract val extraArgs: ListProperty<String>
}
