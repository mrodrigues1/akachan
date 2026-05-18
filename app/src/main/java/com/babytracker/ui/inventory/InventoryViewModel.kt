package com.babytracker.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.inventory.DeleteMilkBagUseCase
import com.babytracker.domain.usecase.inventory.GetInventorySummaryUseCase
import com.babytracker.domain.usecase.inventory.GetInventoryUseCase
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class AddBagSheetState(
    val collectionDate: Instant,
    val volumeMl: String = "",
    val notes: String = "",
    val validationError: String? = null,
    val isSaving: Boolean = false,
)

data class InventoryUiState(
    val summary: InventorySummary = InventorySummary.Empty,
    val bags: List<MilkBag> = emptyList(),
    val addSheet: AddBagSheetState? = null,
    val error: String? = null,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    getInventory: GetInventoryUseCase,
    getSummary: GetInventorySummaryUseCase,
    private val addBag: AddMilkBagUseCase,
    private val markUsed: MarkBagUsedUseCase,
    private val deleteBag: DeleteMilkBagUseCase,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getInventory(), getSummary()) { bags, summary -> bags to summary }
                .collect { (bags, summary) ->
                    _uiState.value = _uiState.value.copy(bags = bags, summary = summary)
                }
        }
    }

    fun onAddBagClicked() {
        _uiState.value = _uiState.value.copy(addSheet = AddBagSheetState(collectionDate = now()))
    }

    fun onAddBagFieldChange(transform: (AddBagSheetState) -> AddBagSheetState) {
        val current = _uiState.value.addSheet ?: return
        _uiState.value = _uiState.value.copy(addSheet = transform(current))
    }

    fun onAddBagDismiss() {
        _uiState.value = _uiState.value.copy(addSheet = null)
    }

    fun onAddBagConfirm() {
        val sheet = _uiState.value.addSheet ?: return
        if (sheet.isSaving) return
        val volume = sheet.volumeMl.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.value = _uiState.value.copy(
                addSheet = sheet.copy(validationError = "Volume must be greater than 0"),
            )
            return
        }
        if (sheet.collectionDate.isAfter(now())) {
            _uiState.value = _uiState.value.copy(
                addSheet = sheet.copy(validationError = "Collection date cannot be in the future"),
            )
            return
        }
        _uiState.value = _uiState.value.copy(addSheet = sheet.copy(isSaving = true))
        viewModelScope.launch {
            runCatching {
                addBag(
                    collectionDate = sheet.collectionDate,
                    volumeMl = volume,
                    sourceSessionId = null,
                    notes = sheet.notes.ifBlank { null },
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(addSheet = null)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    addSheet = sheet.copy(isSaving = false, validationError = "Could not save"),
                )
            }
        }
    }

    fun onMarkUsed(bag: MilkBag) {
        viewModelScope.launch {
            runCatching { markUsed(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not mark used") }
        }
    }

    fun onDelete(bag: MilkBag) {
        viewModelScope.launch {
            runCatching { deleteBag(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Could not delete bag") }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
