package com.babytracker.ui.sharing

import android.content.Context
import com.babytracker.R
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.ConnectAsPartnerUseCase
import com.babytracker.testutil.MainDispatcherExtension
import com.babytracker.widget.WidgetRefreshScheduler
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ConnectPartnerViewModelTest {

    private lateinit var connectAsPartnerUseCase: ConnectAsPartnerUseCase
    private lateinit var widgetRefreshScheduler: WidgetRefreshScheduler
    private lateinit var appContext: Context
    private lateinit var viewModel: ConnectPartnerViewModel

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @BeforeEach
    fun setup() {
        connectAsPartnerUseCase = mockk()
        widgetRefreshScheduler = mockk(relaxUnitFun = true)
        appContext = mockk(relaxed = true)
        every { appContext.getString(R.string.error_connect_code_missing) } returns
            "This code doesn't exist. Check with your partner."
        every { appContext.getString(R.string.error_connect_failed) } returns
            "Couldn't connect. Check your connection."
        viewModel = ConnectPartnerViewModel(connectAsPartnerUseCase, widgetRefreshScheduler, appContext)
    }

    @Test
    fun `onCodeChanged normalizes to uppercase and strips non-alphanumeric`() {
        viewModel.onCodeChanged("abc-12!3")

        assertEquals("ABC123", viewModel.uiState.value.code)
    }

    @Test
    fun `onCodeChanged truncates to 8 characters`() {
        viewModel.onCodeChanged("ABCDEFGH1234")

        assertEquals("ABCDEFGH", viewModel.uiState.value.code)
    }

    @Test
    fun `onConnect calls use case with current code`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        coVerify { connectAsPartnerUseCase(ShareCode(code)) }
    }

    @Test
    fun `onConnect sets isConnected and schedules an immediate widget refresh on success`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        assertTrue(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.error)
        verify(exactly = 1) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `onConnect shows not-found error and does not schedule on IllegalStateException`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws IllegalStateException("not found")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("doesn't exist"))
        verify(exactly = 0) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }

    @Test
    fun `onConnect stays connected when scheduler throws after successful connect`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        every { widgetRefreshScheduler.scheduleImmediateRefresh() } throws RuntimeException("WorkManager not initialized")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        assertTrue(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onConnect shows connection error and does not schedule on generic exception`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws RuntimeException("Network error")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("connection"))
        verify(exactly = 0) { widgetRefreshScheduler.scheduleImmediateRefresh() }
    }
}
