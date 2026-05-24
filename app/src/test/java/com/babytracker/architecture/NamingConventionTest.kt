package com.babytracker.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("architecture")
class NamingConventionTest {

    @Test
    fun `all ViewModels extend ViewModel`() {
        productionScope
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue {
                it.parents().any { parent -> parent.name == "ViewModel" }
            }
    }

    @Test
    fun `all UiState types are data classes`() {
        productionScope
            .classes()
            .withNameEndingWith("UiState")
            .assertTrue { it.hasDataModifier }
    }

    @Test
    fun `all Repository implementations end with RepositoryImpl`() {
        productionScope
            .classes()
            .filter { clazz ->
                clazz.parents().any { parent -> parent.name.endsWith("Repository") }
                    && clazz.resideInPackage("..repository..")
                    && !clazz.name.endsWith("Repository")
            }
            .assertTrue { it.name.endsWith("RepositoryImpl") }
    }

    @Test
    fun `all UseCase classes have an invoke function`() {
        productionScope
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { clazz ->
                clazz.hasFunction { fn -> fn.name == "invoke" }
            }
    }

    @Test
    fun `all UseCase classes reside in usecase package`() {
        productionScope
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..usecase..") }
    }

    @Test
    fun `all Room DAOs end with Dao`() {
        productionScope
            .interfaces()
            .filter { iface ->
                iface.annotations.any { ann -> ann.name == "Dao" }
            }
            .assertTrue { it.name.endsWith("Dao") }
    }

    @Test
    fun `all Room entities end with Entity`() {
        productionScope
            .classes()
            .filter { clazz ->
                clazz.annotations.any { ann -> ann.name == "Entity" }
            }
            .assertTrue { it.name.endsWith("Entity") }
    }
}
