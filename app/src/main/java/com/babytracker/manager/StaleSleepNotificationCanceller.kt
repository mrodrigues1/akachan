package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cancels the ongoing sleep notification when the active record stops being active through any
 * path other than [SleepSessionController.stop] — a partner remote stop ([ApplySleepOpUseCase]),
 * the owner editing the active record to an ended state (`SleepViewModel.onSaveEntry`), or the
 * owner deleting the active record (`SleepViewModel.onConfirmDelete`). [SleepSessionController.stop]
 * already cancels on its own happy path; this is a single data-layer backstop for every other one,
 * so mutation sites do not each need to remember to cancel.
 */
@Singleton
class StaleSleepNotificationCanceller @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    fun start() {
        applicationScope.launch {
            var previousWasActive: Boolean? = null
            sleepRepository.observeActiveRecord()
                .map { it?.id }
                .distinctUntilChanged()
                .collect { currentId ->
                    if (previousWasActive == true && currentId == null) {
                        runCatching { sleepNotificationScheduler.cancel() }
                    }
                    previousWasActive = currentId != null
                }
        }
    }
}
