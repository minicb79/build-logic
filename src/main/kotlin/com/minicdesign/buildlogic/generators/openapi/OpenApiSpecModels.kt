package com.minicdesign.buildlogic.generators.openapi

import org.gradle.api.Named
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class OpenApiSpec @Inject constructor(@get:Internal val specName: String) : Named {
    override fun getName(): String = specName

    /** Directory containing the OpenAPI spec file, relative to project dir. */
    abstract val specPath: Property<String>

    /** Name of the OpenAPI spec file (including extension). */
    abstract val specFilename: Property<String>

    /** API package path (relative or full). */
    abstract val apiPackage: Property<String>

    /** Model package path (relative or full). */
    abstract val modelPackage: Property<String>

    /** Explicitly declare whether this is an outbound (models-only) spec or inbound (server interface). */
    abstract val isOutbound: Property<Boolean>

    /** Whether to derive outbound OAuth2 client configuration from this spec. */
    abstract val oauth2ScopeConfig: Property<Boolean>

    /** The spring.security.oauth2.client.provider entry supplying token endpoint (default: okta). */
    abstract val oauth2ProviderId: Property<String>

    /** Placeholder for client id. */
    abstract val oauth2ClientIdProperty: Property<String>

    /** Placeholder for client secret. */
    abstract val oauth2ClientSecretProperty: Property<String>
}

abstract class OpenApiSpecDirectory @Inject constructor(@get:Internal val discoveryName: String) : Named {
    override fun getName(): String = discoveryName

    /** Directory to scan recursively, relative to the project root. */
    abstract val specPath: Property<String>

    /** Package prefix before the discovered path and filename segments. */
    abstract val packagePrefix: Property<String>

    /** Whether to derive outbound OAuth2 scope configuration for discovered specs. */
    abstract val oauth2ScopeConfig: Property<Boolean>

    /** The spring.security.oauth2.client.provider entry supplying token endpoint (default: okta). */
    abstract val oauth2ProviderId: Property<String>

    abstract val oauth2ClientIdProperty: Property<String>
    abstract val oauth2ClientSecretProperty: Property<String>
}
