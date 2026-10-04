package com.minicdesign.buildlogic.generators.openapi

/**
 * Renders the Spring configuration derived from a consumed OpenAPI spec.
 *
 * Two things are emitted from one source:
 * 1. A client registration per distinct scope set. Spring Security binds `scope` to a
 *    `ClientRegistration`, not to a request, so per-call scoping needs one registration per scope
 *    set rather than a single registration carrying the union of every scope the consumer might
 *    ever need. The union is both over-privileged and brittle: the authorization server rejects the
 *    whole token request when any one scope in it is unrecognised, which takes out every call
 *    rather than the one that needed the new scope.
 * 2. A catalog mapping method and path template to the registration that serves it, which the
 *    runtime uses to pick a registration per outbound request.
 *
 * Credentials are referenced as property placeholders rather than written literally, so the
 * generated file holds no secrets and the existing environment and Secrets Manager overrides
 * continue to apply.
 */
object OAuth2ScopeConfigWriter {

    /** Property namespace the generated catalog binds to at runtime. */
    const val CATALOG_PROPERTY_NAMESPACE: String = "glide.oauth2.outbound"

    /**
     * Builds the registration id for [scopes] within [catalogName].
     *
     * The id is derived from the scopes themselves so that it stays stable as the spec changes and
     * remains readable in logs and configuration: `retail` plus `retail.accounts.read` gives
     * `retail-accounts-read`. A redundant service prefix on the scope is dropped, and the catalog
     * name always leads, which keeps ids unique across providers even if one of them publishes
     * unprefixed scope names.
     */
    fun registrationId(catalogName: String, scopes: List<String>): String {
        val suffix =
            scopes.sorted().joinToString("__") { scope ->
                scope.removePrefix("$catalogName.").replace('.', '-').replace('/', '-')
            }
        return "$catalogName-$suffix"
    }

    /**
     * Renders the generated configuration for [catalogName] from [catalog].
     *
     * @param catalogName the logical name of the provider being called, which prefixes registration
     *   ids and names the catalog the runtime looks up
     * @param catalog the operations and scopes extracted from the provider's spec
     * @param providerId the `spring.security.oauth2.client.provider` entry supplying the token URI
     * @param clientIdProperty property reference resolved for every generated registration's
     *   client id
     * @param clientSecretProperty property reference resolved for every generated registration's
     *   client secret
     * @param sourceDescription where the configuration came from, recorded in the file header so
     *   the origin is obvious to anyone who opens it
     */
    fun render(
        catalogName: String,
        catalog: SpecScopeCatalog,
        providerId: String,
        clientIdProperty: String,
        clientSecretProperty: String,
        sourceDescription: String,
    ): String {
        val scopeSets = catalog.distinctScopeSets()
        val builder = StringBuilder()

        builder.appendLine("# ==============================================================")
        builder.appendLine("# GENERATED FILE — DO NOT EDIT")
        builder.appendLine("#")
        builder.appendLine("# Derived from: $sourceDescription")
        builder.appendLine("#")
        builder.appendLine("# Regenerate with the owning project's OAuth2 scope config task; the")
        builder.appendLine("# build runs it automatically before processResources. Edit the")
        builder.appendLine("# provider's OpenAPI spec to change anything here.")
        builder.appendLine("#")
        builder.appendLine("# One registration is created per distinct scope set because Spring")
        builder.appendLine("# Security binds scope to a registration rather than to a request. The")
        builder.appendLine("# outbound interceptor selects between them per call, so each request")
        builder.appendLine("# carries only the scope its operation requires.")
        builder.appendLine("# ==============================================================")

        if (scopeSets.isEmpty()) {
            builder.appendLine("# The spec declares no scoped operations, so no registrations are")
            builder.appendLine("# required and no catalog is emitted.")
            return builder.toString()
        }

        builder.appendLine("spring:")
        builder.appendLine("  security:")
        builder.appendLine("    oauth2:")
        builder.appendLine("      client:")
        builder.appendLine("        registration:")
        scopeSets.forEach { scopes ->
            builder.appendLine("          ${registrationId(catalogName, scopes)}:")
            builder.appendLine("            provider: \"$providerId\"")
            builder.appendLine("            authorization-grant-type: \"client_credentials\"")
            builder.appendLine("            client-id: \"$clientIdProperty\"")
            builder.appendLine("            client-secret: \"$clientSecretProperty\"")
            builder.appendLine("            scope: \"${scopes.joinToString(",")}\"")
        }

        builder.appendLine("glide:")
        builder.appendLine("  oauth2:")
        builder.appendLine("    outbound:")
        builder.appendLine("      catalogs:")
        builder.appendLine("        $catalogName:")
        builder.appendLine("          operations:")
        catalog.operations.forEach { operation ->
            builder.appendLine("            - method: \"${operation.method}\"")
            builder.appendLine("              path: \"${operation.path}\"")
            builder.appendLine(
                "              registration-id: \"${registrationId(catalogName, operation.scopes)}\"")
            builder.appendLine("              scopes:")
            operation.scopes.forEach { scope -> builder.appendLine("                - \"$scope\"") }
        }

        return builder.toString()
    }
}
