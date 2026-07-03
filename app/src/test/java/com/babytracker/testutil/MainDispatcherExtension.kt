package com.babytracker.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Installs [testDispatcher] as `Dispatchers.Main` for each test and always resets it afterwards,
 * replacing the hand-rolled setMain/resetMain pairs in every ViewModel test. Extension callbacks
 * run before `@BeforeEach` and after `@AfterEach` methods, so the main dispatcher is in place for
 * all test setup and reset after all test teardown — a missed resetMain in one class can no longer
 * leak `Dispatchers.Main` into the rest of the suite.
 *
 * Register as an instance field so each test gets a fresh dispatcher:
 * ```
 * @JvmField
 * @RegisterExtension
 * val mainDispatcherExtension = MainDispatcherExtension()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(testDispatcher)

    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}
