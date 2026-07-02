package com.babytracker.ui.milestone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.navigation.Routes
import com.babytracker.sharing.usecase.SyncedWrite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MilestoneDetailUiState(
    val milestone: Milestone? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class MilestoneDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val milestoneRepository: MilestoneRepository,
    private val photoCleaner: MilestonePhotoCleaner,
    private val syncedWrite: SyncedWrite,
) : ViewModel() {

    private val milestoneId: Long = savedStateHandle.get<String>(Routes.MILESTONE_DETAIL_ARG)?.toLongOrNull() ?: 0L

    private val milestone: StateFlow<Milestone?> =
        milestoneRepository.getMilestone(milestoneId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val uiState: StateFlow<MilestoneDetailUiState> =
        milestone
            .map { MilestoneDetailUiState(milestone = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MilestoneDetailUiState(),
            )

    fun onSave(updated: Milestone) {
        val previousPhoto = milestone.value?.photoUri
        viewModelScope.launch {
            milestoneRepository.updateMilestone(updated)
            if (previousPhoto != null && previousPhoto != updated.photoUri) {
                photoCleaner.delete(previousPhoto)
            }
            syncedWrite.sync()
        }
    }

    /** Deletes the moment and invokes [onDeleted] once removal is committed. */
    fun onDelete(onDeleted: () -> Unit) {
        val photoUri = milestone.value?.photoUri
        viewModelScope.launch {
            milestoneRepository.deleteMilestone(milestoneId)
            // Photo cleanup is best-effort: once the moment is gone from the database, a failure
            // deleting the orphaned file must not block the sync or strand the caller on its
            // post-delete UI. Always reach onDeleted() so the screen can navigate away.
            runCatching { photoCleaner.delete(photoUri) }
            syncedWrite.sync()
            onDeleted()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
