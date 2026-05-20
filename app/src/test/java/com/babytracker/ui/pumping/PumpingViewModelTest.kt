package com.babytracker.ui.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.pumping.PausePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.ResumePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.SavePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StartPumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.StopPumpingSessionUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PumpingViewModelTest {

    private lateinit var repository: PumpingRepository
    private lateinit var startUseCase: StartPumpingSessionUseCase
    private lateinit var stopUseCase: StopPumpingSessionUseCase
    private lateinit var pauseUseCase: PausePumpingSessionUseCase
    private lateinit var resumeUseCase: ResumePumpingSessionUseCase
    private lateinit var saveManual: SavePumpingSessionUseCase
    private lateinit var addBag: AddMilkBagUseCase

    private lateinit var viewModel: PumpingViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val activeSessionFlow = MutableStateFlow<PumpingSession?>(null)
    private val fixedNow = Instant.ofEpochSecond(1_700_000_000L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk()
        startUseCase = mockk()
        stopUseCase = mockk()
        pauseUseCase = mockk()
        resumeUseCase = mockk()
        saveManual = mockk()
        addBag = mockk()

        every { repository.getActiveSession() } returns activeSessionFlow

        viewModel = createViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() = PumpingViewModel(
        pumpingRepository = repository,
        startUseCase = startUseCase,
        stopUseCase = stopUseCase,
        pauseUseCase = pauseUseCase,
        resumeUseCase = resumeUseCase,
        saveManual = saveManual,
        addBag = addBag,
        now = { fixedNow },
    )

    @Test
    fun `onStartTimer calls startUseCase with selectedBreast`() = runTest {
        viewModel.onBreastSelected(PumpingBreast.LEFT)
        coJustRun { startUseCase(PumpingBreast.LEFT) }

        viewModel.onStartTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startUseCase(PumpingBreast.LEFT) }
    }

    @Test
    fun `onStartTimer uses default BOTH breast when none selected`() = runTest {
        coJustRun { startUseCase(PumpingBreast.BOTH) }

        viewModel.onStartTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startUseCase(PumpingBreast.BOTH) }
    }

    @Test
    fun `mode toggle to MANUAL initializes manual state with fixed clock`() = runTest {
        viewModel.onModeChange(PumpingMode.MANUAL)

        val manual = viewModel.uiState.value.manual
        assertNotNull(manual)
        assertEquals(fixedNow, manual!!.endTime)
        assertEquals(fixedNow.minusSeconds(15 * 60), manual.startTime)
    }

    @Test
    fun `mode toggle to MANUAL does not reinitialize existing manual state`() = runTest {
        viewModel.onModeChange(PumpingMode.MANUAL)
        val firstManual = viewModel.uiState.value.manual!!

        viewModel.onModeChange(PumpingMode.TIMER)
        viewModel.onModeChange(PumpingMode.MANUAL)

        assertEquals(firstManual, viewModel.uiState.value.manual)
    }

    @Test
    fun `onStopTimer calls stopUseCase and opens bagPrompt on success`() = runTest {
        val session = PumpingSession(
            id = 42L,
            startTime = fixedNow.minusSeconds(600),
            breast = PumpingBreast.BOTH,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        val stoppedSession = session.copy(endTime = fixedNow, volumeMl = 120)
        coEvery { stopUseCase(session, 120) } returns stoppedSession

        viewModel.onStopTimer(120)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { stopUseCase(session, 120) }

        val prompt = viewModel.uiState.value.bagPrompt
        assertNotNull(prompt)
        assertEquals(42L, prompt!!.sessionId)
        assertEquals(120, prompt.volumeMl)
    }

    @Test
    fun `onStopTimer with null volume passes null to stopUseCase and prefills bag prompt with 0`() = runTest {
        val session = PumpingSession(
            id = 7L,
            startTime = fixedNow.minusSeconds(300),
            breast = PumpingBreast.RIGHT,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        val stoppedSession = session.copy(endTime = fixedNow)
        coEvery { stopUseCase(session, null) } returns stoppedSession

        viewModel.onStopTimer(null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { stopUseCase(session, null) }
        assertEquals(0, viewModel.uiState.value.bagPrompt?.volumeMl)
    }

    @Test
    fun `onBagPromptConfirm calls addBag with sourceSessionId and clears bagPrompt on success`() = runTest {
        val collectionDate = fixedNow
        val session = PumpingSession(
            id = 99L,
            startTime = fixedNow.minusSeconds(600),
            breast = PumpingBreast.LEFT,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        val stoppedSession = session.copy(endTime = collectionDate)
        coEvery { stopUseCase(session, null) } returns stoppedSession
        viewModel.onStopTimer(null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBagPromptFieldChange { it.copy(volumeMl = 150) }

        coEvery {
            addBag(
                collectionDate = collectionDate,
                volumeMl = 150,
                sourceSessionId = 99L,
                notes = null,
            )
        } returns 1L

        viewModel.onBagPromptConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            addBag(
                collectionDate = collectionDate,
                volumeMl = 150,
                sourceSessionId = 99L,
                notes = null,
            )
        }
        assertNull(viewModel.uiState.value.bagPrompt)
    }

    @Test
    fun `onBagPromptSkip clears bagPrompt without calling addBag`() = runTest {
        val session = PumpingSession(
            id = 5L,
            startTime = fixedNow.minusSeconds(300),
            breast = PumpingBreast.BOTH,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        val stoppedSession = session.copy(endTime = fixedNow)
        coEvery { stopUseCase(session, null) } returns stoppedSession
        viewModel.onStopTimer(null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.bagPrompt)

        viewModel.onBagPromptSkip()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.bagPrompt)
        coVerify(exactly = 0) { addBag(any(), any(), any(), any()) }
    }

    @Test
    fun `onManualSave validation rejects volumeMl less than or equal to zero`() = runTest {
        viewModel.onModeChange(PumpingMode.MANUAL)
        viewModel.onManualFieldChange { it.copy(volumeMl = "0") }

        viewModel.onManualSave()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.manual?.validationError
        assertTrue(error != null)
        coVerify(exactly = 0) { saveManual(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onManualSave validation rejects endTime before or equal to startTime`() = runTest {
        viewModel.onModeChange(PumpingMode.MANUAL)
        viewModel.onManualFieldChange { manual ->
            manual.copy(
                startTime = fixedNow,
                endTime = fixedNow.minusSeconds(60),
                volumeMl = "100",
            )
        }

        viewModel.onManualSave()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.manual?.validationError
        assertTrue(error != null)
        coVerify(exactly = 0) { saveManual(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onManualSave with valid data calls saveManual and opens bagPrompt`() = runTest {
        viewModel.onModeChange(PumpingMode.MANUAL)
        val start = fixedNow.minusSeconds(900)
        val end = fixedNow
        viewModel.onManualFieldChange { it.copy(startTime = start, endTime = end, volumeMl = "80") }

        val savedSession = PumpingSession(
            id = 11L,
            startTime = start,
            endTime = end,
            breast = PumpingBreast.LEFT,
            volumeMl = 80,
        )
        coEvery {
            saveManual(
                startTime = start,
                endTime = end,
                breast = PumpingBreast.LEFT,
                volumeMl = 80,
                notes = null,
            )
        } returns savedSession

        viewModel.onManualSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            saveManual(startTime = start, endTime = end, breast = PumpingBreast.LEFT, volumeMl = 80, notes = null)
        }
        val resetManual = viewModel.uiState.value.manual
        assertNotNull(resetManual)
        assertEquals(fixedNow.minusSeconds(15 * 60), resetManual!!.startTime)
        assertEquals(fixedNow, resetManual.endTime)
        assertEquals("", resetManual.volumeMl)
        assertNull(resetManual.validationError)
        assertNotNull(viewModel.uiState.value.bagPrompt)
        assertEquals(80, viewModel.uiState.value.bagPrompt?.volumeMl)
    }

    @Test
    fun `onStartTimer is idempotent when already starting`() = runTest {
        coJustRun { startUseCase(PumpingBreast.BOTH) }

        viewModel.onStartTimer()
        viewModel.onStartTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startUseCase(any()) }
    }

    @Test
    fun `onStartTimer does nothing when session already active`() = runTest {
        val session = PumpingSession(
            id = 1L,
            startTime = fixedNow.minusSeconds(300),
            breast = PumpingBreast.BOTH,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { startUseCase(any()) }
    }

    @Test
    fun `onBagPromptConfirm is idempotent when already saving`() = runTest {
        val session = PumpingSession(
            id = 3L,
            startTime = fixedNow.minusSeconds(300),
            breast = PumpingBreast.BOTH,
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        val stoppedSession = session.copy(endTime = fixedNow)
        coEvery { stopUseCase(session, null) } returns stoppedSession
        viewModel.onStopTimer(null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onBagPromptFieldChange { it.copy(volumeMl = 80) }

        coEvery { addBag(any(), any(), any(), any()) } returns 1L

        viewModel.onBagPromptConfirm()
        viewModel.onBagPromptConfirm()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { addBag(any(), any(), any(), any()) }
    }
}
