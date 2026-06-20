package com.babytracker.domain.usecase.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SnapshotUseCasesTest {
    @Test
    fun `attach sets label and timestamp`() = runTest {
        val repository = mockk<DoctorVisitRepository>(relaxed = true)
        coEvery { repository.getVisitById(3) } returns DoctorVisit(id = 3, date = Instant.EPOCH, createdAt = Instant.EPOCH)
        val captured = slot<DoctorVisit>()
        coEvery { repository.updateVisit(capture(captured)) } returns Unit
        AttachSnapshotToVisitUseCase(repository)(3, "  Backup  ", now = Instant.ofEpochMilli(50))
        assertEquals("Backup", captured.captured.snapshotLabel)
        assertEquals(Instant.ofEpochMilli(50), captured.captured.snapshotCreatedAt)
    }

    @Test
    fun `attach no-ops when visit missing`() = runTest {
        val repository = mockk<DoctorVisitRepository>(relaxed = true)
        coEvery { repository.getVisitById(9) } returns null
        AttachSnapshotToVisitUseCase(repository)(9, "x")
        coVerify(exactly = 0) { repository.updateVisit(any()) }
    }
}
