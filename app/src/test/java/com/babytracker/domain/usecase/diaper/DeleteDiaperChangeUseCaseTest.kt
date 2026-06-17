package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DeleteDiaperChangeUseCaseTest {
    @Test
    fun `delegates to repository and syncs`() = runTest {
        val repository = mockk<DiaperRepository>(relaxed = true)
        val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
        DeleteDiaperChangeUseCase(repository, sync)(5)
        coVerify { repository.deleteById(5) }
        coVerify { sync(SyncToFirestoreUseCase.SyncType.DIAPERS) }
    }
}
