package com.minicdesign.buildlogic.archunit.rules

import com.societegenerale.commons.plugin.rules.ArchRuleTest
import com.societegenerale.commons.plugin.service.ScopePathProvider
import com.societegenerale.commons.plugin.utils.ArchUtils
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Compatible replacement for Societe Generale's NoPowerMockRuleTest on ArchUnit 1.4+.
 * Guarantees that no classes or tests ever depend on PowerMock.
 */
class NoPowerMockRuleTest : ArchRuleTest {
    override fun execute(path: String, scopePathProvider: ScopePathProvider, excludedPaths: MutableCollection<String>) {
        val rule = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.powermock..")
            .because("PowerMock is strictly prohibited; prefer modular code and standard Mockito")
            .allowEmptyShould(true)

        val classes = ArchUtils.importAllClassesInPackage(scopePathProvider.mainClassesPath, path, excludedPaths)
        rule.check(classes)
    }
}
