package com.babytracker.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.usecase.milestone.AddMilestoneUseCase
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestonesUseCase
import com.babytracker.domain.usecase.milestone.UpdateMilestoneUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MilestonesUiState(
    val moments: List<Milestone> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    private val getMilestones: GetMilestonesUseCase,
    private val addMilestone: AddMilestoneUseCase,
    private val updateMilestone: UpdateMilestoneUseCase,
    private val deleteMilestone: DeleteMilestoneUseCase,
    private val photoCleaner: MilestonePhotoCleaner,
    private val syncToFirestore: SyncToFirestoreUseCase,
) : ViewModel() {

    val uiState: StateFlow<MilestonesUiState> =
        getMilestones()
            .map { MilestonesUiState(moments = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MilestonesUiState(),
            )

    // No resident cache: photo-cleanup lookups happen only on save/delete (user actions already on
    // a coroutine), so read the currently persisted list once at action time instead of pinning the
    // entire milestone list in memory for the ViewModel's whole lifetime.
    private suspend fun existingPhotoUri(id: Long): String? =
        getMilestones().first().firstOrNull { it.id == id }?.photoUri

    /** Adds a new moment ([Milestone.id] == 0) or updates an existing one. */
    fun onSave(milestone: Milestone) {
        viewModelScope.launch {
            val previousPhoto = if (milestone.id == 0L) null else existingPhotoUri(milestone.id)
            if (milestone.id == 0L) {
                addMilestone(milestone)
            } else {
                updateMilestone(milestone)
            }
            // Clean up the replaced photo file only after the new record is committed.
            if (previousPhoto != null && previousPhoto != milestone.photoUri) {
                photoCleaner.delete(previousPhoto)
            }
            syncSharedSnapshot()
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            val photoUri = existingPhotoUri(id)
            deleteMilestone(id)
            photoCleaner.delete(photoUri)
            syncSharedSnapshot()
        }
    }

    // Milestone edits are infrequent, so a full resync (a no-op unless sharing is the
    // PRIMARY device) keeps the partner snapshot current without partial-sync plumbing.
    private suspend fun syncSharedSnapshot() {
        runCatching { syncToFirestore() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
