package com.babytracker.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope

/**
 * Lazy, JVM-wide Konsist scope shared across all architecture tests.
 *
 * Building a scope walks every Kotlin file in the project — on `/mnt/c/` NTFS
 * (WSL2) the first call inside a test JVM costs ~14 minutes; cached
 * follow-up calls in the same JVM return in milliseconds. Sharing one scope
 * across the architecture tests means we pay that cost at most once per run.
 *
 * `scopeFromProduction()` excludes test/androidTest sources. Architecture
 * rules only assert on production code, so production scope is the correct
 * one — and skipping test sources keeps the scan faster.
 */
internal val productionScope: KoScope by lazy { Konsist.scopeFromProduction() }
