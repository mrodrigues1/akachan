package com.babytracker.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("architecture")
class AntiPatternTest {

    @Test
    fun `no Mapper classes exist`() {
        productionScope
            .classes()
            .assertFalse { it.name.endsWith("Mapper") }
    }

    @Test
    fun `no BaseViewModel class exists`() {
        productionScope
            .classes()
            .assertFalse { it.name == "BaseViewModel" }
    }

    @Test
    fun `no BaseFragment class exists`() {
        productionScope
            .classes()
            .assertFalse { it.name == "BaseFragment" }
    }

    @Test
    fun `no sealed Result wrapper class exists`() {
        productionScope
            .classes()
            .assertFalse { clazz ->
                clazz.name == "Result" && clazz.hasSealedModifier
            }
    }

    @Test
    fun `ViewModels do not catch exceptions silently`() {
        productionScope
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { clazz ->
                !clazz.containingFile.imports.any { import -> import.name == "kotlin.runCatching" }
            }
    }

    @Test
    fun `firebase imports restricted to sharing package and DI provider`() {
        productionScope
            .classes()
            .filter { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.startsWith("com.google.firebase")
                }
            }
            .assertTrue { clazz ->
                clazz.resideInPackage("com.babytracker.sharing..") ||
                    clazz.name == "SharingModule"
            }
    }
}
