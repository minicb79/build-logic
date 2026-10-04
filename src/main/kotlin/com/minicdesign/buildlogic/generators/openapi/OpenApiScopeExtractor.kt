package com.minicdesign.buildlogic.generators.openapi

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * A single operation in a consumed OpenAPI spec, together with the OAuth2 scopes the provider
 * requires for it.
 *
 * [path] is the spec's path template (for example `/v1/accounts/{accountNumber}/payment-events`),
 * not a resolved request path. Consumers match resolved paths against it at runtime.
 */
data class ScopedOperation(
    val method: String,
    val path: String,
    val scopes: List<String>,
)

/**
 * Everything the outbound OAuth2 client configuration needs from one consumed spec.
 *
 * [operations] covers only operations that require at least one scope; see
 * [OpenApiScopeExtractor.extract] for why unscoped operations are dropped. [declaredScopes] is the
 * full set the security scheme advertises, which is a superset of the scopes actually used by
 * [operations] whenever the provider publishes a scope this consumer never calls.
 */
data class SpecScopeCatalog(
    val operations: List<ScopedOperation>,
    val declaredScopes: List<String>,
) {
    /**
     * The distinct scope sets that must be obtainable as tokens, in a stable order.
     *
     * One client registration is created per entry, because Spring Security binds scope to a
     * registration rather than to a request.
     */
    fun distinctScopeSets(): List<List<String>> =
        operations.map { it.scopes }.distinct().sortedBy { it.joinToString(",") }
}

/**
 * Derives outbound OAuth2 scope requirements from a consumed OpenAPI spec.
 *
 * The provider's contract already states, per operation, which scopes it enforces. Reading that at
 * build time keeps the caller's token requests in step with the callee automatically: when the
 * provider adds an operation or changes a scope, the generated configuration changes with it and
 * the mismatch surfaces in the build rather than as a runtime 403.
 *
 * Only the `oauth2` security scheme is considered. Other schemes carry no scopes, so they cannot
 * contribute to client-credentials token requests.
 */
object OpenApiScopeExtractor {

    private val HTTP_METHODS =
        setOf("get", "put", "post", "delete", "patch", "head", "options", "trace")

    /**
     * Parses [specContent] and returns the operations that require OAuth2 scopes.
     *
     * Operations are dropped when they require no scope — either because they declare
     * `security: []` (explicitly public, such as health endpoints) or because the scheme is
     * referenced with an empty scope list. Neither can produce a useful client-credentials token,
     * and emitting them would let an unscoped request silently acquire a token the provider would
     * reject anyway. A caller that reaches such an operation is better served by the explicit "no
     * scope is declared for this call" failure the runtime raises.
     *
     * @param specContent the raw YAML of the consumed spec
     * @throws IllegalArgumentException if the spec has no `paths`, which means the wrong file was
     *   configured
     */
    fun extract(specContent: String): SpecScopeCatalog {
        val root = loadYaml(specContent)

        val paths =
            root.asMap("paths")
                ?: throw IllegalArgumentException(
                    "The spec declares no 'paths'. Check that the configured file is an OpenAPI spec.")

        val oauthSchemeNames = oauthSchemeNames(root)
        val defaultSecurity = root["security"]

        val operations =
            paths.entries
                .flatMap { (path, pathItem) ->
                    val operationsByMethod = pathItem as? Map<*, *> ?: return@flatMap emptyList()
                    operationsByMethod.entries
                        .filter { (method, _) -> method.toString().lowercase() in HTTP_METHODS }
                        .mapNotNull { (method, operation) ->
                            val operationMap = operation as? Map<*, *> ?: return@mapNotNull null
                            // An operation-level `security` key always wins, including when it is
                            // an empty list, which is how a spec marks one operation as public.
                            val security =
                                if (operationMap.containsKey("security")) operationMap["security"]
                                else defaultSecurity
                            val scopes = scopesFrom(security, oauthSchemeNames)
                            if (scopes.isEmpty()) {
                                null
                            } else {
                                ScopedOperation(
                                    method = method.toString().uppercase(),
                                    path = path.toString(),
                                    scopes = scopes)
                            }
                        }
                }
                .sortedWith(compareBy({ it.path }, { it.method }))

        return SpecScopeCatalog(
            operations = operations, declaredScopes = declaredScopes(root, oauthSchemeNames))
    }

    /**
     * Loads YAML with a constructor that refuses arbitrary type instantiation.
     *
     * The spec is a build input rather than user input, but the safe constructor costs nothing and
     * keeps a malicious or corrupted contract from executing code during the build.
     */
    private fun loadYaml(specContent: String): Map<*, *> {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        return yaml.load<Any?>(specContent) as? Map<*, *>
            ?: throw IllegalArgumentException("The spec is empty or is not a YAML mapping.")
    }

    /** Names of every `oauth2` security scheme, since only those carry scopes. */
    private fun oauthSchemeNames(root: Map<*, *>): Set<String> =
        root.asMap("components")
            ?.asMap("securitySchemes")
            ?.entries
            ?.filter { (_, scheme) -> (scheme as? Map<*, *>)?.get("type") == "oauth2" }
            ?.map { (name, _) -> name.toString() }
            ?.toSet()
            .orEmpty()

    /** Every scope the OAuth2 schemes advertise across all their flows. */
    private fun declaredScopes(root: Map<*, *>, oauthSchemeNames: Set<String>): List<String> =
        root.asMap("components")
            ?.asMap("securitySchemes")
            ?.entries
            ?.filter { (name, _) -> name.toString() in oauthSchemeNames }
            ?.flatMap { (_, scheme) ->
                (scheme as? Map<*, *>)?.asMap("flows")?.values.orEmpty().flatMap { flow ->
                    (flow as? Map<*, *>)?.asMap("scopes")?.keys.orEmpty().map { it.toString() }
                }
            }
            ?.distinct()
            ?.sorted()
            .orEmpty()

    /**
     * Reads the scopes a `security` requirement places on the OAuth2 schemes.
     *
     * A `security` value is a list of alternative requirement objects. Only the OAuth2 entries can
     * contribute scopes, and their scopes are combined because every listed scope is required for
     * the call the consumer makes.
     */
    private fun scopesFrom(security: Any?, oauthSchemeNames: Set<String>): List<String> {
        val requirements = security as? List<*> ?: return emptyList()
        return requirements
            .filterIsInstance<Map<*, *>>()
            .flatMap { requirement ->
                requirement.entries
                    .filter { (scheme, _) -> scheme.toString() in oauthSchemeNames }
                    .flatMap { (_, scopes) ->
                        (scopes as? List<*>).orEmpty().map { it.toString() }
                    }
            }
            .distinct()
            .sorted()
    }

    private fun Map<*, *>.asMap(key: String): Map<*, *>? = this[key] as? Map<*, *>
}
