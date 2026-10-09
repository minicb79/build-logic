package com.minicdesign.buildlogic.archunit

/**
 * Catalog of pre-configured architecture and code quality rules provided by Societe Generale's
 * ArchUnit rule base (com.societegenerale.commons.plugin.rules).
 */
enum class SocieteGeneraleRule(
    val ruleClassName: String,
    val description: String
) {
    NO_AUTOWIRED_FIELDS(
        "com.societegenerale.commons.plugin.rules.NoAutowiredFieldTest",
        "Fields should not be annotated with @Autowired; prefer constructor injection."
    ),
    NO_INJECTED_FIELDS(
        "com.societegenerale.commons.plugin.rules.NoInjectedFieldTest",
        "Fields should not be injected directly using @Autowired or @Inject; prefer constructor injection."
    ),
    NO_STANDARD_STREAMS(
        "com.societegenerale.commons.plugin.rules.NoStandardStreamRuleTest",
        "Do not use System.out, System.err, or printStackTrace; use a structured logger instead."
    ),
    NO_JUNIT_ASSERTS(
        "com.societegenerale.commons.plugin.rules.NoJunitAssertRuleTest",
        "Do not use JUnit assertions (org.junit.Assert or org.junit.jupiter.api.Assertions); use AssertJ instead."
    ),
    NO_JAVA_UTIL_DATE(
        "com.societegenerale.commons.plugin.rules.NoJavaUtilDateRuleTest",
        "Do not use legacy java.util.Date; use java.time types instead."
    ),
    NO_JODA_TIME(
        "com.societegenerale.commons.plugin.rules.NoJodaTimeRuleTest",
        "Do not use Joda-Time; use java.time instead."
    ),
    NO_POWERMOCK(
        "com.minicdesign.buildlogic.archunit.rules.NoPowerMockRuleTest",
        "Do not use PowerMock; write modular and testable code."
    ),
    NO_PUBLIC_FIELDS(
        "com.societegenerale.commons.plugin.rules.NoPublicFieldRuleTest",
        "Classes should not have public non-static fields; maintain encapsulation."
    ),
    NO_PREFIX_FOR_INTERFACES(
        "com.societegenerale.commons.plugin.rules.NoPrefixForInterfacesRuleTest",
        "Interface names should not be prefixed with 'I' (e.g. IService)."
    ),
    DONT_RETURN_NULL_COLLECTION(
        "com.societegenerale.commons.plugin.rules.DontReturnNullCollectionTest",
        "Methods returning collections must not return null; return empty collections instead."
    ),
    FINAL_NON_STATIC_FIELDS_HAVE_TO_BE_STATIC_FINAL(
        "com.societegenerale.commons.plugin.rules.FinalNonStaticFieldsHaveToBeStaticFinalFieldsRuleTest",
        "Final non-static fields that look like constants (UPPER_CASE) should be declared static."
    ),
    CONSTANTS_AND_STATIC_NON_FINAL_FIELDS_NAMES(
        "com.societegenerale.commons.plugin.rules.ConstantsAndStaticNonFinalFieldsNamesRuleTest",
        "Constants must be in UPPER_CASE and static non-final fields must follow standard naming conventions."
    ),
    TEST_METHODS_NAMING(
        "com.societegenerale.commons.plugin.rules.TestMethodsNamingRuleTest",
        "Test methods should follow standardized naming conventions."
    ),
    TEST_CLASSES_NAMING(
        "com.societegenerale.commons.plugin.rules.TestClassesNamingRuleTest",
        "Test classes should follow naming conventions (e.g. ending in Test, Tests, or TestCase)."
    ),
    NO_TEST_IGNORE(
        "com.societegenerale.commons.plugin.rules.NoTestIgnoreRuleTest",
        "Do not ignore tests with @Ignore or @Disabled."
    ),
    NO_TEST_IGNORE_WITHOUT_COMMENT(
        "com.societegenerale.commons.plugin.rules.NoTestIgnoreWithoutCommentRuleTest",
        "Tests cannot be ignored without providing a documented reason/comment."
    ),
    STRING_FIELDS_THAT_ARE_ACTUALLY_DATES(
        "com.societegenerale.commons.plugin.rules.StringFieldsThatAreActuallyDatesRuleTest",
        "Fields representing dates should not be typed as String."
    ),
    HEXAGONAL_ARCHITECTURE(
        "com.societegenerale.commons.plugin.rules.HexagonalArchitectureTest",
        "Enforces Hexagonal Architecture boundaries between domain, ports, and adapters."
    ),
    RESTRICT_INTERNAL_PACKAGES(
        "com.minicdesign.buildlogic.archunit.rules.RestrictInternalPackagesRuleTest",
        "Classes in '..internal..' packages must only be accessed by other classes in '..internal..' packages."
    );

    companion object {
        fun fromRuleClassName(name: String): SocieteGeneraleRule? =
            values().firstOrNull { it.ruleClassName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
    }
}
