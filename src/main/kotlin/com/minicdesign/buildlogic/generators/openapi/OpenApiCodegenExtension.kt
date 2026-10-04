package com.minicdesign.buildlogic.generators.openapi

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

interface OpenApiCodegenExtension {
    val openApiBasePackage: Property<String>
    val openApiVersion: Property<String>
    val springBootVersion: Property<Int>
    val enableSourcePostProcessing: Property<Boolean>

    val oauth2ScopeConfig: Property<Boolean>
    val oauth2ProviderId: Property<String>
    val oauth2ClientIdProperty: Property<String>
    val oauth2ClientSecretProperty: Property<String>

    val openApiSpecs: NamedDomainObjectContainer<OpenApiSpec>
    val openApiSpecDirectories: NamedDomainObjectContainer<OpenApiSpecDirectory>

    fun specs(action: Action<NamedDomainObjectContainer<OpenApiSpec>>) {
        action.execute(openApiSpecs)
    }

    fun specDirectories(action: Action<NamedDomainObjectContainer<OpenApiSpecDirectory>>) {
        action.execute(openApiSpecDirectories)
    }
}
