package com.babytracker.ui.sharing

import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.usecase.ConnectAsPartnerUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

class ConnectPartnerViewModelTest {

    private lateinit var connectAsPartnerUseCase: ConnectAsPartnerUseCase
    private lateinit var viewModel: ConnectPartnerViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        connectAsPartnerUseCase = mockk()
        viewModel = ConnectPartnerViewModel(connectAsPartnerUseCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `onConnect sets isConnected on success`() = runTest {
        val code = "ABCD1234"
        coJustRun { connectAsPartnerUseCase(ShareCode(code)) }
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        assertTrue(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onConnect shows not-found error on IllegalStateException`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws IllegalStateException("not found")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("doesn't exist"))
    }

    @Test
    fun `onConnect shows connection error on generic exception`() = runTest {
        val code = "ABCD1234"
        coEvery { connectAsPartnerUseCase(ShareCode(code)) } throws RuntimeException("Network error")
        viewModel.onCodeChanged(code)

        viewModel.onConnect()

        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("connection"))
    }
}
