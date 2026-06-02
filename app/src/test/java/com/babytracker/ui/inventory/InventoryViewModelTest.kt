package com.babytracker.ui.inventory

import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.inventory.DeleteMilkBagUseCase
import com.babytracker.domain.usecase.inventory.GetInventorySummaryUseCase
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import com.babytracker.domain.usecase.inventory.ObserveInventoryWithExpirationUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private lateinit var observeInventory: ObserveInventoryWithExpirationUseCase
    private lateinit var getSummary: GetInventorySummaryUseCase
    private lateinit var addBag: AddMilkBagUseCase
    private lateinit var markUsed: MarkBagUsedUseCase
    private lateinit var deleteBag: DeleteMilkBagUseCase
    private lateinit var viewModel: InventoryViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val bagsFlow = MutableStateFlow<List<MilkBagWithExpiration>>(emptyList())
    private val summaryFlow = MutableStateFlow(InventorySummary.Empty)
    private val fixedNow = Instant.ofEpochSecond(1_700_000_000L)

    private val sampleBag = MilkBag(
        id = 1L,
        collectionDate = fixedNow.minusSeconds(3600),
        volumeMl = 120,
        createdAt = fixedNow.minusSeconds(3600),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        observeInventory = mockk()
        getSummary = mockk()
        addBag = mockk()
        markUsed = mockk()
        deleteBag = mockk()
        every { observeInventory(any<Flow<LocalDate>>()) } returns bagsFlow
        every { getSummary() } returns summaryFlow
        viewModel = InventoryViewModel(
            observeInventory = observeInventory,
            getSummary = getSummary,
            addBag = addBag,
            markUsed = markUsed,
            deleteBag = deleteBag,
            now = { fixedNow },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `combined flow updates bags and summary when both repositories emit`() = runTest {
        val summary = InventorySummary(totalMl = 120, bagCount = 1, oldestBagDate = sampleBag.collectionDate)
        val bagWithExpiration = MilkBagWithExpiration(sampleBag, ExpirationStatus.EXPIRING_OR_EXPIRED)
        bagsFlow.value = listOf(bagWithExpiration)
        summaryFlow.value = summary
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(bagWithExpiration), viewModel.uiState.value.bags)
        assertEquals(summary, viewModel.uiState.value.summary)
    }

    @Test
    fun `onResume does not clear expiration-aware bags`() = runTest {
        val bagWithExpiration = MilkBagWithExpiration(sampleBag, ExpirationStatus.EXPIRING_SOON)
        bagsFlow.value = listOf(bagWithExpiration)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResume()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(bagWithExpiration), viewModel.uiState.value.bags)
    }

    @Test
    fun `onAddBagClicked opens sheet with collectionDate set to now`() = runTest {
        viewModel.onAddBagClicked()

        val sheet = viewModel.uiState.value.addSheet
        assertNotNull(sheet)
        assertEquals(fixedNow, sheet!!.collectionDate)
        assertEquals("", sheet.volumeMl)
    }

    @Test
    fun `onAddBagConfirm rejects empty volume with validationError`() = runTest {
        viewModel.onAddBagClicked()

        viewModel.onAddBagConfirm()

        val sheet = viewModel.uiState.value.addSheet
        assertNotNull(sheet!!.validationError)
    }

    @Test
    fun `onAddBagConfirm rejects zero volume with validationError`() = runTest {
        viewModel.onAddBagClicked()
        viewModel.onAddBagFieldChange { it.copy(volumeMl = "0") }

        viewModel.onAddBagConfirm()

        val sheet = viewModel.uiState.value.addSheet
        assertEquals("Volume must be greater than 0", sheet!!.validationError)
    }

    @Test
    fun `onAddBagConfirm calls addBag with parsed values and clears sheet on success`() = runTest {
        viewModel.onAddBagClicked()
        viewModel.onAddBagFieldChange { it.copy(volumeMl = "120") }
        coEvery {
            addBag(
                collectionDate = fixedNow,
                volumeMl = 120,
                sourceSessionId = null,
                notes = null,
            )
        } returns 1L

        viewModel.onAddBagConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.addSheet)
        coVerify(exactly = 1) {
            addBag(
                collectionDate = fixedNow,
                volumeMl = 120,
                sourceSessionId = null,
                notes = null,
            )
        }
    }

    @Test
    fun `onAddBagConfirm sets validationError when addBag throws`() = runTest {
        viewModel.onAddBagClicked()
        viewModel.onAddBagFieldChange { it.copy(volumeMl = "120") }
        coEvery {
            addBag(collectionDate = any(), volumeMl = any(), sourceSessionId = any(), notes = any())
        } throws RuntimeException("db error")

        viewModel.onAddBagConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.addSheet)
        assertEquals("Could not save", viewModel.uiState.value.addSheet!!.validationError)
    }

    @Test
    fun `onAddBagConfirm while already saving is ignored - addBag called only once`() = runTest {
        viewModel.onAddBagClicked()
        viewModel.onAddBagFieldChange { it.copy(volumeMl = "120") }
        coEvery {
            addBag(collectionDate = any(), volumeMl = any(), sourceSessionId = any(), notes = any())
        } returns 1L

        viewModel.onAddBagConfirm()
        viewModel.onAddBagConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            addBag(collectionDate = any(), volumeMl = any(), sourceSessionId = any(), notes = any())
        }
    }

    @Test
    fun `onAddBagConfirm rejects future collection date with validationError`() = runTest {
        viewModel.onAddBagClicked()
        viewModel.onAddBagFieldChange { it.copy(volumeMl = "120", collectionDate = fixedNow.plusSeconds(3600)) }

        viewModel.onAddBagConfirm()

        val sheet = viewModel.uiState.value.addSheet
        assertEquals("Collection date cannot be in the future", sheet!!.validationError)
    }

    @Test
    fun `onAddBagDismiss clears sheet`() = runTest {
        viewModel.onAddBagClicked()
        assertNotNull(viewModel.uiState.value.addSheet)

        viewModel.onAddBagDismiss()

        assertNull(viewModel.uiState.value.addSheet)
    }

    @Test
    fun `onMarkUsed calls markUsed use case`() = runTest {
        coJustRun { markUsed(sampleBag) }

        viewModel.onMarkUsed(sampleBag)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { markUsed(sampleBag) }
    }

    @Test
    fun `onDelete calls deleteBag use case`() = runTest {
        coJustRun { deleteBag(sampleBag) }

        viewModel.onDelete(sampleBag)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { deleteBag(sampleBag) }
    }

    @Test
    fun `onMarkUsed sets error when markUsed throws`() = runTest {
        coEvery { markUsed(sampleBag) } throws RuntimeException("db error")

        viewModel.onMarkUsed(sampleBag)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not mark used", viewModel.uiState.value.error)
    }

    @Test
    fun `onDelete sets error when deleteBag throws`() = runTest {
        coEvery { deleteBag(sampleBag) } throws RuntimeException("db error")

        viewModel.onDelete(sampleBag)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not delete bag", viewModel.uiState.value.error)
    }

    @Test
    fun `onErrorDismissed clears error`() = runTest {
        coEvery { markUsed(sampleBag) } throws RuntimeException("db error")
        viewModel.onMarkUsed(sampleBag)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onErrorDismissed()

        assertNull(viewModel.uiState.value.error)
    }
}
