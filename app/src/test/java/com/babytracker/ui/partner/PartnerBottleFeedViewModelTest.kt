package com.babytracker.ui.partner

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.usecase.EditPartnerFeedUseCase
import com.babytracker.sharing.usecase.LogPartnerFeedUseCase
import com.babytracker.sharing.usecase.PartnerAccessRevokedException
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PartnerBottleFeedViewModelTest {

    private val logPartnerFeed = mockk<LogPartnerFeedUseCase>()
    private val editPartnerFeed = mockk<EditPartnerFeedUseCase>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val appContext = mockk<Context>()
    private val dispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(dispatcher)
    private val now = Instant.ofEpochMilli(10_000)

    @BeforeEach
    fun setup() {
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        coEvery { logPartnerFeed(any(), any(), any(), any(), any()) } returns "entry-1"
        coJustRun { editPartnerFeed(any(), any(), any(), any(), any()) }
        every { appContext.getString(R.string.error_volume_positive) } returns "Enter a volume greater than 0"
        every { appContext.getString(R.string.error_could_not_save) } returns "Could not save"
    }

    private fun viewModel() = PartnerBottleFeedViewModel(
        logPartnerFeed = logPartnerFeed,
        editPartnerFeed = editPartnerFeed,
        settingsRepository = settingsRepository,
        appContext = appContext,
        now = { now },
    )

    @Test
    fun `confirm in log mode invokes LogPartnerFeedUseCase with selected bag`() = runTest {
        val bag = milkBag(id = 7, volumeMl = 120)
        val viewModel = viewModel()
        viewModel.onBagsAvailable(listOf(bag))
        viewModel.onBagSelect(7)

        viewModel.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            logPartnerFeed(
                timestamp = now,
                volumeMl = 120,
                type = FeedType.BREAST_MILK,
                selectedBag = bag,
                notes = null,
            )
        }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `confirm in edit mode invokes EditPartnerFeedUseCase with original entry`() = runTest {
        val entry = feed(author = FeedAuthor.PARTNER.name)
        val viewModel = viewModel()
        viewModel.startEditing(entry)
        viewModel.onVolumeChange("95")
        viewModel.onTypeChange(FeedType.FORMULA)
        viewModel.onNotesChange("afternoon")

        viewModel.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify {
            editPartnerFeed(
                entry = entry,
                timestamp = Instant.ofEpochMilli(entry.timestamp),
                volumeMl = 95,
                type = FeedType.FORMULA,
                notes = "afternoon",
            )
        }
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `startEditing on owner entry throws`() {
        val viewModel = viewModel()

        assertThrows(IllegalStateException::class.java) {
            viewModel.startEditing(feed(author = FeedAuthor.OWNER.name))
        }
    }

    @Test
    fun `invalid volume sets validation error and does not log`() = runTest {
        val viewModel = viewModel()

        viewModel.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter a volume greater than 0", viewModel.uiState.value.validationError)
        coVerify(exactly = 0) { logPartnerFeed(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `use case failure clears saving and surfaces error`() = runTest {
        coEvery { logPartnerFeed(any(), any(), any(), any(), any()) } throws IllegalStateException("No share code")
        val viewModel = viewModel()
        viewModel.onVolumeChange("80")

        viewModel.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("Could not save", viewModel.uiState.value.validationError)
    }

    @Test
    fun `revoked access write failure does not mark feed saved`() = runTest {
        coEvery { logPartnerFeed(any(), any(), any(), any(), any()) } throws
            PartnerAccessRevokedException("Partner access revoked")
        val viewModel = viewModel()
        viewModel.onVolumeChange("80")

        viewModel.onConfirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("Could not save", viewModel.uiState.value.validationError)
    }

    @Test
    fun `startLogging after edit resets edit fields and keeps bags and unit`() = runTest {
        val viewModel = viewModel()
        val bag = milkBag(id = 3, volumeMl = 75)
        viewModel.onBagsAvailable(listOf(bag))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.startEditing(feed(author = FeedAuthor.PARTNER.name, volumeMl = 90))

        viewModel.startLogging()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertEquals("", state.volumeText)
        assertEquals(listOf(3L), state.activeBags.map { it.id })
        assertEquals(VolumeUnit.ML, state.volumeUnit)
    }

    private fun feed(
        author: String,
        volumeMl: Int = 90,
        clientId: String = "entry-1",
    ) = BottleFeedSnapshot(
        timestamp = 2_000L,
        volumeMl = volumeMl,
        type = FeedType.BREAST_MILK.name,
        clientId = clientId,
        author = author,
        notes = null,
    )

    private fun milkBag(
        id: Long,
        volumeMl: Int,
    ) = MilkBagSnapshot(
        id = id,
        collectionDateMs = 1_000L,
        volumeMl = volumeMl,
        notes = null,
    )
}
