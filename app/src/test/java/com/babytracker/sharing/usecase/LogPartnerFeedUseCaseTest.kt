package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class LogPartnerFeedUseCaseTest {
    private val sharingRepository: SharingRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val fixedNow = Instant.parse("2026-06-01T10:00:00Z")
    private lateinit var useCase: LogPartnerFeedUseCase

    @BeforeEach
    fun setUp() {
        useCase = LogPartnerFeedUseCase(sharingRepository, settingsRepository) { fixedNow }
        every { settingsRepository.getShareCode() } returns flowOf("CODE1234")
        coEvery { sharingRepository.signInAnonymously() } returns "partner-uid"
        every { sharingRepository.writeFeedOp(any(), any()) } just Runs
    }

    @Test
    fun `log writes create op with bag and returns entry client id`() = runTest {
        val op = slot<FeedOp>()
        val bag = MilkBagSnapshot(id = 7L, collectionDateMs = 1_000L, volumeMl = 120, notes = null)
        every { sharingRepository.writeFeedOp(ShareCode("CODE1234"), capture(op)) } just Runs

        val entryClientId = useCase(
            timestamp = Instant.parse("2026-06-01T09:00:00Z"),
            volumeMl = 110,
            type = FeedType.BREAST_MILK,
            selectedBag = bag,
            notes = "warm",
        )

        assertEquals(entryClientId, op.captured.entryClientId)
        assertNotEquals(entryClientId, op.captured.opId)
        assertEquals(FeedOpAction.CREATE, op.captured.action)
        assertEquals("partner-uid", op.captured.authorUid)
        assertEquals(fixedNow.toEpochMilli(), op.captured.createdAtMs)
        assertEquals(Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(), op.captured.timestampMs)
        assertEquals(110, op.captured.volumeMl)
        assertEquals(FeedType.BREAST_MILK.name, op.captured.type)
        assertEquals("warm", op.captured.notes)
        assertEquals(7L, op.captured.consumedBagId)
    }

    @Test
    fun `log without bag omits consumed bag id`() = runTest {
        val op = slot<FeedOp>()
        every { sharingRepository.writeFeedOp(any(), capture(op)) } just Runs

        useCase(
            timestamp = Instant.parse("2026-06-01T09:00:00Z"),
            volumeMl = 110,
            type = FeedType.FORMULA,
            selectedBag = null,
            notes = null,
        )

        assertEquals(null, op.captured.consumedBagId)
    }

    @Test
    fun `log rejects non-positive volume`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(Instant.parse("2026-06-01T09:00:00Z"), 0, FeedType.FORMULA, null, null)
            }
        }

        coVerify(exactly = 0) { sharingRepository.signInAnonymously() }
    }

    @Test
    fun `log rejects future timestamp`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(Instant.parse("2026-06-01T11:00:00Z"), 110, FeedType.FORMULA, null, null)
            }
        }

        coVerify(exactly = 0) { sharingRepository.signInAnonymously() }
    }
}
