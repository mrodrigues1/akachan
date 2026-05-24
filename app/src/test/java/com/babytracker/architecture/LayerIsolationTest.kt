package com.babytracker.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("architecture")
class LayerIsolationTest {

    @Test
    fun `UI layer does not import data layer directly`() {
        productionScope
            .classes()
            .withPackage("com.babytracker.ui..")
            .assertFalse { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.startsWith("com.babytracker.data")
                }
            }
    }

    @Test
    fun `domain layer does not import UI or data layers`() {
        productionScope
            .classes()
            .withPackage("com.babytracker.domain..")
            .assertFalse { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.startsWith("com.babytracker.ui") ||
                        import.name.startsWith("com.babytracker.data")
                }
            }
    }

    @Test
    fun `domain models have no framework imports`() {
        productionScope
            .classes()
            .withPackage("com.babytracker.domain.model..")
            .assertFalse { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.startsWith("androidx") ||
                        import.name.startsWith("com.google") ||
                        import.name.startsWith("dagger")
                }
            }
    }

    @Test
    fun `ViewModels do not import Room DAOs directly`() {
        productionScope
            .classes()
            .withNameEndingWith("ViewModel")
            .assertFalse { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.contains(".dao.")
                }
            }
    }
}
