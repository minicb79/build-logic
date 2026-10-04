package com.minicdesign.buildlogic.archunit

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

interface ArchUnitConventionsExtension {
    /**
     * Whether ArchUnit rule verification is enabled. Defaults to true.
     */
    val enabled: Property<Boolean>

    /**
     * Disallow field injection (@Autowired / @Inject); enforces constructor injection.
     */
    val noAutowiredFields: Property<Boolean>

    /**
     * Disallow System.out and System.err calls, enforcing structured logging.
     */
    val noStandardStreams: Property<Boolean>

    /**
     * Disallow JUnit assertions (org.junit.Assert, org.junit.jupiter.api.Assertions), enforcing AssertJ.
     */
    val noJunitAsserts: Property<Boolean>

    /**
     * Disallow usage of legacy java.util.Date in favor of java.time.*.
     */
    val noJavaUtilDate: Property<Boolean>

    /**
     * Disallow usage of Joda-Time in favor of java.time.*.
     */
    val noJodaTime: Property<Boolean>

    /**
     * Disallow PowerMock in favor of modular design and standard Mockito.
     */
    val noPowerMock: Property<Boolean>

    /**
     * Disallow public non-static fields to maintain proper class encapsulation.
     */
    val noPublicFields: Property<Boolean>

    /**
     * Disallow interface names starting with 'I' (e.g. IService).
     */
    val noPrefixForInterfaces: Property<Boolean>

    /**
     * Enforce methods returning collections do not return null (should return empty collection).
     */
    val dontReturnNullCollection: Property<Boolean>

    /**
     * Enforce constant fields (UPPER_CASE) that are final should also be static.
     */
    val finalNonStaticFieldsHaveToBeStaticFinal: Property<Boolean>

    /**
     * Enforce naming conventions for constants (UPPER_CASE) and static non-final fields.
     */
    val constantsAndStaticNonFinalFieldsNames: Property<Boolean>

    /**
     * Enforce naming conventions for test methods.
     */
    val testMethodsNaming: Property<Boolean>

    /**
     * Enforce naming conventions for test classes (e.g., ending with Test, Tests, or TestCase).
     */
    val testClassesNaming: Property<Boolean>

    /**
     * Disallow @Ignore or @Disabled on tests entirely.
     */
    val noTestIgnore: Property<Boolean>

    /**
     * Disallow @Ignore or @Disabled without documenting an explicit reason/comment.
     */
    val noTestIgnoreWithoutComment: Property<Boolean>

    /**
     * Disallow string fields that represent dates without proper date types.
     */
    val stringFieldsThatAreActuallyDates: Property<Boolean>

    /**
     * Enforce Hexagonal Architecture package boundaries (domain, port, adapter).
     */
    val hexagonalArchitecture: Property<Boolean>

    /**
     * Restrict access to '..internal..' packages so that internal classes cannot be accessed from outside.
     */
    val restrictInternalPackages: Property<Boolean>

    /**
     * List of path patterns to exclude from ArchUnit analysis (e.g. generated sources).
     */
    val excludedPaths: ListProperty<String>

    /**
     * List of fully qualified class names for additional custom or 3rd-party ArchUnit rules.
     */
    val additionalRules: ListProperty<String>

    /**
     * Explicit set of SocieteGeneraleRule instances to execute.
     */
    val enabledRules: SetProperty<SocieteGeneraleRule>

    fun enableRule(rule: SocieteGeneraleRule) {
        enabledRules.add(rule)
    }

    fun enableRules(vararg rules: SocieteGeneraleRule) {
        enabledRules.addAll(*rules)
    }

    fun excludePath(path: String) {
        excludedPaths.add(path)
    }

    fun excludePaths(vararg paths: String) {
        excludedPaths.addAll(*paths)
    }

    fun addRule(ruleClassName: String) {
        additionalRules.add(ruleClassName)
    }

    fun addRules(vararg ruleClassNames: String) {
        additionalRules.addAll(*ruleClassNames)
    }
}
