package com.babytracker.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.model.MilestoneProgress
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestoneProgressUseCase
import com.babytracker.domain.usecase.milestone.LogMilestoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MilestonesUiState(
    val progress: List<MilestoneProgress> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    getMilestoneProgress: GetMilestoneProgressUseCase,
    private val logMilestone: LogMilestoneUseCase,
    private val deleteMilestone: DeleteMilestoneUseCase,
    private val photoCleaner: MilestonePhotoCleaner,
) : ViewModel() {

    // Eagerly collected so photo-cleanup lookups have the current achievements even
    // when no UI is subscribed to [uiState].
    private val progress: StateFlow<List<MilestoneProgress>> =
        getMilestoneProgress().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    val uiState: StateFlow<MilestonesUiState> =
        progress
            .map { MilestonesUiState(progress = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MilestonesUiState(),
            )

    private fun existingPhotoUri(milestone: Milestone): String? =
        progress.value.firstOrNull { it.milestone == milestone }?.achievement?.photoUri

    fun onLog(milestone: Milestone, achievedOn: LocalDate, photoUri: String?, notes: String?) {
        val previousPhoto = existingPhotoUri(milestone)
        viewModelScope.launch {
            logMilestone(
                MilestoneAchievement(
                    milestone = milestone,
                    achievedOn = achievedOn,
                    photoUri = photoUri,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
            // Clean up the replaced photo file only after the new record is committed.
            if (previousPhoto != null && previousPhoto != photoUri) {
                photoCleaner.delete(previousPhoto)
            }
        }
    }

    fun onDelete(milestone: Milestone) {
        val photoUri = existingPhotoUri(milestone)
        viewModelScope.launch {
            deleteMilestone(milestone)
            photoCleaner.delete(photoUri)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
