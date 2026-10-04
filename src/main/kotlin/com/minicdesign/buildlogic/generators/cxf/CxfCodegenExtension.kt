package com.minicdesign.buildlogic.generators.cxf

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface CxfCodegenExtension {
    val cxfVersion: Property<String>
    val wsdlBasePackage: Property<String>
    val outputDir: DirectoryProperty
    val markGenerated: Property<Boolean>
    val autoNameResolution: Property<Boolean>

    /**
     * Single WSDL location convenience property aligned with cxfcodegen-common.gradle.kts.
     * Can be an absolute or relative path, or a filename under src/main/resources, contract/wsdl, or contracts/wsdl.
     */
    val wsdlLocation: Property<String>

    /**
     * Convenience package names configuration for the single wsdlLocation.
     */
    val packageNames: ListProperty<String>

    val wsdls: NamedDomainObjectContainer<WsdlSpec>

    fun wsdls(action: Action<NamedDomainObjectContainer<WsdlSpec>>) {
        action.execute(wsdls)
    }
}
