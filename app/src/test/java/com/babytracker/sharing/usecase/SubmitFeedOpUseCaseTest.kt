package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SubmitFeedOpUseCaseTest {
    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)
    private lateinit var useCase: SubmitFeedOpUseCase

    @BeforeEach
    fun setUp() {
        useCase = SubmitFeedOpUseCase(sharingRepository, settingsRepository, applicationScope)
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { sharingRepository.signInAnonymously() } returns "partner-uid"
        coEvery { sharingRepository.writeFeedOp(any(), any(), any()) } just Runs
    }

    @Test
    fun `submit builds op with resolved uid and writes to share code`() = runTest {
        val op = slot<FeedOp>()
        coEvery { sharingRepository.writeFeedOp(ShareCode("CODE1234"), capture(op), any()) } just Runs

        useCase { authorUid -> feedOp(authorUid) }

        assertEquals("partner-uid", op.captured.authorUid)
        assertEquals("entry-1", op.captured.entryClientId)
    }

    @Test
    fun `submit throws when share code missing`() = runTest {
        every { settingsRepository.getShareCode() } returns flowOf(null)

        assertThrows<IllegalStateException> {
            runBlocking { useCase { authorUid -> feedOp(authorUid) } }
        }

        coVerify(exactly = 0) { sharingRepository.signInAnonymously() }
    }

    @Test
    fun `permission denied write clears partner state and throws revoked`() = runTest {
        val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        coEvery { sharingRepository.writeFeedOp(any(), any(), any()) } throws denied
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true

        assertThrows<PartnerAccessRevokedException> {
            runBlocking { useCase { authorUid -> feedOp(authorUid) } }
        }

        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    @Test
    fun `non permission firestore failure maps to data fetch exception`() = runTest {
        val unavailable = FirebaseFirestoreException("down", FirebaseFirestoreException.Code.UNAVAILABLE)
        coEvery { sharingRepository.writeFeedOp(any(), any(), any()) } throws unavailable

        assertThrows<PartnerDataFetchException> {
            runBlocking { useCase { authorUid -> feedOp(authorUid) } }
        }

        coVerify(exactly = 0) { settingsRepository.clearPartnerStateIfShareCodeMatches(any()) }
    }

    @Test
    fun `late permission denied failure clears partner state`() = runTest {
        val failureHandler = slot<(Throwable) -> Unit>()
        val denied = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        coJustRun { sharingRepository.writeFeedOp(any(), any(), capture(failureHandler)) }
        coEvery { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") } returns true

        useCase { authorUid -> feedOp(authorUid) }
        failureHandler.captured(denied)

        coVerify { settingsRepository.clearPartnerStateIfShareCodeMatches("CODE1234") }
    }

    private fun feedOp(authorUid: String) = FeedOp(
        opId = "op-1",
        action = FeedOpAction.DELETE,
        entryClientId = "entry-1",
        authorUid = authorUid,
        createdAtMs = 1_000L,
    )
}
