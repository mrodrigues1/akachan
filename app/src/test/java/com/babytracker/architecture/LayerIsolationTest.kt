package com.babytracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class LayerIsolationTest {

    @Test
    fun `UI layer does not import data layer directly`() {
        Konsist.scopeFromProject()
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
        Konsist.scopeFromProject()
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
        Konsist.scopeFromProject()
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
        Konsist.scopeFromProject()
            .classes()
            .withNameEndingWith("ViewModel")
            .assertFalse { clazz ->
                clazz.containingFile.imports.any { import ->
                    import.name.contains(".dao.")
                }
            }
    }
}
