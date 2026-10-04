package com.minicdesign.buildlogic.archunit.rules

import com.societegenerale.commons.plugin.rules.ArchRuleTest
import com.societegenerale.commons.plugin.service.ScopePathProvider
import com.societegenerale.commons.plugin.utils.ArchUtils
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * ArchUnit rule that restricts classes residing in '..internal..' packages so that they can
 * only be accessed by classes that also reside in '..internal..' packages.
 *
 * This provides compiler-like package closure without the reflection friction of JPMS module-info.
 */
class RestrictInternalPackagesRuleTest : ArchRuleTest {
    override fun execute(path: String, scopePathProvider: ScopePathProvider, excludedPaths: MutableCollection<String>) {
        val rule = classes()
            .that().resideInAPackage("..internal..")
            .should().onlyBeAccessed().byClassesThat().resideInAPackage("..internal..")
            .because("Classes in '..internal..' packages are encapsulated implementation details and must not be accessed from other packages")
            .allowEmptyShould(true)

        val classes = ArchUtils.importAllClassesInPackage(scopePathProvider.mainClassesPath, path, excludedPaths)
        rule.check(classes)
    }
}
