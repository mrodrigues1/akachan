package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.GrowthRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import javax.inject.Inject

/**
 * Read-side collaborators that produce the partner snapshot payload. Bundled as one injected
 * dependency so [SyncToFirestoreUseCase] stays within the constructor parameter budget.
 */
data class SnapshotSources @Inject constructor(
    val baby: BabyRepository,
    val breastfeeding: BreastfeedingRepository,
    val sleep: SleepRepository,
    val inventory: InventoryRepository,
    val bottleFeeds: BottleFeedRepository,
    val diaper: DiaperRepository,
    val predictSleepWindow: PredictSleepWindowUseCase,
    val growth: GrowthRepository,
    val milestones: MilestoneRepository,
    val doctorVisit: DoctorVisitRepository,
)
