package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.data.firebase.FirestoreSharingService
import io.mockk.Runs
import io.mockk.coEvery
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
import java.time.Instant

class EditPartnerFeedUseCaseTest {
    private val service: FirestoreSharingService = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)
    private val fixedNow = Instant.parse("2026-06-01T10:00:00Z")
    private lateinit var useCase: EditPartnerFeedUseCase

    @BeforeEach
    fun setUp() {
        useCase = EditPartnerFeedUseCase(
            SubmitFeedOpUseCase(service, settingsRepository, applicationScope),
        ) { fixedNow }
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { service.signInAnonymously() } returns "partner-uid"
        coEvery { service.writeFeedOp(any(), any(), any()) } just Runs
    }

    @Test
    fun `edit writes update op targeting partner entry without consumed bag`() = runTest {
        val op = slot<FeedOp>()
        coEvery { service.writeFeedOp("CODE1234", capture(op), any()) } just Runs

        useCase(
            entry = partnerEntry(clientId = "entry-1"),
            timestamp = Instant.parse("2026-06-01T09:00:00Z"),
            volumeMl = 125,
            type = FeedType.FORMULA,
            notes = "updated",
        )

        assertEquals(FeedOpAction.UPDATE, op.captured.action)
        assertEquals("entry-1", op.captured.entryClientId)
        assertEquals("partner-uid", op.captured.authorUid)
        assertEquals(fixedNow.toEpochMilli(), op.captured.createdAtMs)
        assertEquals(Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(), op.captured.timestampMs)
        assertEquals(125, op.captured.volumeMl)
        assertEquals(FeedType.FORMULA.name, op.captured.type)
        assertEquals("updated", op.captured.notes)
        assertEquals(null, op.captured.consumedBagId)
    }

    @Test
    fun `edit rejects notes longer than the rules bound`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(
                    partnerEntry(clientId = "entry-1"),
                    Instant.parse("2026-06-01T09:00:00Z"),
                    125,
                    FeedType.FORMULA,
                    "x".repeat(PARTNER_NOTES_MAX_LENGTH + 1),
                )
            }
        }

        coVerify(exactly = 0) { service.signInAnonymously() }
    }

    @Test
    fun `edit refuses owner entry`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(partnerEntry(author = FeedAuthor.OWNER.name), fixedNow, 100, FeedType.FORMULA, null)
            }
        }

        coVerify(exactly = 0) { service.signInAnonymously() }
        coVerify(exactly = 0) { service.writeFeedOp(any(), any(), any()) }
    }

    @Test
    fun `edit refuses empty client id`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(partnerEntry(clientId = ""), fixedNow, 100, FeedType.FORMULA, null)
            }
        }

        coVerify(exactly = 0) { service.signInAnonymously() }
        coVerify(exactly = 0) { service.writeFeedOp(any(), any(), any()) }
    }

    @Test
    fun `edit rejects invalid payload`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(partnerEntry(), fixedNow.plusSeconds(1), 100, FeedType.FORMULA, null)
            }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(partnerEntry(), fixedNow, 0, FeedType.FORMULA, null)
            }
        }

        coVerify(exactly = 0) { service.signInAnonymously() }
    }

    private fun partnerEntry(
        clientId: String = "entry-1",
        author: String = FeedAuthor.PARTNER.name,
    ) = BottleFeedSnapshot(
        timestamp = Instant.parse("2026-06-01T08:00:00Z").toEpochMilli(),
        volumeMl = 90,
        type = FeedType.BREAST_MILK.name,
        clientId = clientId,
        author = author,
        notes = null,
    )
}
