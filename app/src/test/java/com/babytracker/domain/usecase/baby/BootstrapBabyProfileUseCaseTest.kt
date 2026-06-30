package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import com.babytracker.domain.repository.BabyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BootstrapBabyProfileUseCaseTest {

    private lateinit var babyRepo: BabyRepository
    private lateinit var profileRepo: BabyProfileRepository
    private val fixedNow = Instant.parse("2026-06-05T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: BootstrapBabyProfileUseCase

    @BeforeEach
    fun setup() {
        babyRepo = mockk()
        profileRepo = mockk()
        useCase = BootstrapBabyProfileUseCase(babyRepo, profileRepo, nowProvider)
    }

    @Test
    fun `inserts Room profile from DataStore when no profile exists`() = runTest {
        val baby = Baby(
            name = "Lila",
            birthDate = LocalDate.of(2025, 12, 1),
            allergies = emptyList(),
        )
        coEvery { babyRepo.getBabyProfile() } returns flowOf(baby)
        coEvery { profileRepo.getProfile() } returns null
        coEvery { profileRepo.upsertProfile(any()) } returns Unit

        useCase()

        val slot = slot<BabyProfile>()
        coVerify { profileRepo.upsertProfile(capture(slot)) }
        val saved = slot.captured
        assertEquals(baby.birthDate, saved.dateOfBirth)
        assertEquals(ZoneId.systemDefault().id, saved.homeTimezoneId)
        assertNull(saved.dueDate)
        assertEquals(fixedNow.toEpochMilli(), saved.createdAtEpochMs)
        assertEquals(fixedNow.toEpochMilli(), saved.updatedAtEpochMs)
    }

    @Test
    fun `updates Room when DataStore birthDate changed (no staleness)`() = runTest {
        val updatedBaby = Baby(
            name = "Lila",
            birthDate = LocalDate.of(2025, 12, 15),
            allergies = emptyList(),
        )
        val existingProfile = BabyProfile(
            dateOfBirth = LocalDate.of(2025, 12, 1),
            dueDate = null,
            isDueDateUserProvided = false,
            homeTimezoneId = "UTC",
            createdAtEpochMs = 1_000_000L,
            updatedAtEpochMs = 1_000_000L,
        )
        coEvery { babyRepo.getBabyProfile() } returns flowOf(updatedBaby)
        coEvery { profileRepo.getProfile() } returns existingProfile
        coEvery { profileRepo.upsertProfile(any()) } returns Unit

        useCase()

        val slot = slot<BabyProfile>()
        coVerify { profileRepo.upsertProfile(capture(slot)) }
        val saved = slot.captured
        assertEquals(updatedBaby.birthDate, saved.dateOfBirth)
        assertEquals("UTC", saved.homeTimezoneId)
        assertEquals(1_000_000L, saved.createdAtEpochMs)
        assertEquals(fixedNow.toEpochMilli(), saved.updatedAtEpochMs)
    }

    @Test
    fun `skips upsert when Room profile exists with same birthDate (idempotent on repeated launch)`() = runTest {
        val baby = Baby(
            name = "Lila",
            birthDate = LocalDate.of(2025, 12, 1),
            allergies = emptyList(),
        )
        val existingProfile = BabyProfile(
            dateOfBirth = LocalDate.of(2025, 12, 1),
            dueDate = null,
            isDueDateUserProvided = false,
            homeTimezoneId = "UTC",
            createdAtEpochMs = 1_000_000L,
            updatedAtEpochMs = 1_000_000L,
        )
        coEvery { babyRepo.getBabyProfile() } returns flowOf(baby)
        coEvery { profileRepo.getProfile() } returns existingProfile

        useCase()

        coVerify(exactly = 0) { profileRepo.upsertProfile(any()) }
    }

    @Test
    fun `skips upsert when DataStore has no baby profile (onboarding not complete)`() = runTest {
        coEvery { babyRepo.getBabyProfile() } returns flowOf(null)

        useCase()

        coVerify(exactly = 0) { profileRepo.upsertProfile(any()) }
    }

    @Test
    fun `uses birthDate override without reading the saved baby (onboarding due-date-first path)`() = runTest {
        val birthDate = LocalDate.of(2026, 5, 1)
        val dueDate = LocalDate.of(2026, 6, 1)
        coEvery { profileRepo.getProfile() } returns null
        coEvery { profileRepo.upsertProfile(any()) } returns Unit

        useCase(userDueDate = dueDate, birthDate = birthDate)

        val slot = slot<BabyProfile>()
        coVerify { profileRepo.upsertProfile(capture(slot)) }
        assertEquals(birthDate, slot.captured.dateOfBirth)
        assertEquals(dueDate, slot.captured.dueDate)
        assertTrue(slot.captured.isDueDateUserProvided)
        coVerify(exactly = 0) { babyRepo.getBabyProfile() }
    }

    @Test
    fun `does not throw when DataStore emits null`() = runTest {
        coEvery { babyRepo.getBabyProfile() } returns flowOf(null)

        useCase()
    }

    @Test
    fun `saved profile has non-null homeTimezoneId on first insert`() = runTest {
        val baby = Baby(
            name = "Test",
            birthDate = LocalDate.of(2025, 6, 1),
            allergies = emptyList(),
        )
        coEvery { babyRepo.getBabyProfile() } returns flowOf(baby)
        coEvery { profileRepo.getProfile() } returns null
        coEvery { profileRepo.upsertProfile(any()) } returns Unit

        useCase()

        val slot = slot<BabyProfile>()
        coVerify { profileRepo.upsertProfile(capture(slot)) }
        assertNotNull(slot.captured.homeTimezoneId)
    }
}
