package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AddDoctorVisitUseCaseTest {
    private lateinit var repository: DoctorVisitRepository
    private lateinit var scheduler: DoctorVisitReminderScheduler
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var useCase: AddDoctorVisitUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        useCase = AddDoctorVisitUseCase(repository, scheduler, SyncedWrite(syncToFirestore))
        coEvery { repository.insertVisitWithAttachments(any(), any()) } returns 11
    }

    @Test
    fun `inserts blanks-to-null, attaches questions atomically, schedules reminder`() = runTest {
        val captured = slot<DoctorVisit>()
        coEvery { repository.insertVisitWithAttachments(capture(captured), eq(listOf(1, 2))) } returns 11
        val id = useCase(
            date = Instant.ofEpochMilli(5_000),
            providerName = "   ",
            notes = "  Bring chart  ",
            attachQuestionIds = listOf(1, 2),
            now = Instant.ofEpochMilli(1_000),
        )
        assertEquals(11, id)
        assertNull(captured.captured.providerName)
        assertEquals("Bring chart", captured.captured.notes)
        coVerify { repository.insertVisitWithAttachments(any(), listOf(1, 2)) }
        coVerify { scheduler.schedule(match { it.id == 11L }) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.FULL) }
    }
}
