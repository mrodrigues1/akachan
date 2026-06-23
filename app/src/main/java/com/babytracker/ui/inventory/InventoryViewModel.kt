package com.babytracker.ui.inventory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.MilkBagWithExpiration
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.inventory.AddMilkBagUseCase
import com.babytracker.domain.usecase.inventory.DeleteMilkBagUseCase
import com.babytracker.domain.usecase.inventory.GetInventorySummaryUseCase
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import com.babytracker.domain.usecase.inventory.ObserveInventoryWithExpirationUseCase
import com.babytracker.domain.usecase.inventory.UpdateMilkBagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
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
    val bags: List<MilkBagWithExpiration> = emptyList(),
    val addSheet: AddBagSheetState? = null,
    val editSheet: EditBagSheetState? = null,
    val pendingDeleteBag: MilkBag? = null,
    val error: String? = null,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
)

data class EditBagSheetState(
    val bagId: Long,
    val form: AddBagSheetState,
    val saveToken: Long = 0,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    observeInventory: ObserveInventoryWithExpirationUseCase,
    getSummary: GetInventorySummaryUseCase,
    private val addBag: AddMilkBagUseCase,
    private val updateBag: UpdateMilkBagUseCase,
    private val markUsed: MarkBagUsedUseCase,
    private val deleteBag: DeleteMilkBagUseCase,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    private val now: () -> Instant,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val currentDate = MutableStateFlow(today())

    init {
        viewModelScope.launch {
            combine(
                observeInventory(currentDate),
                getSummary(),
                settingsRepository.getVolumeUnit(),
            ) { bags, summary, unit ->
                Triple(bags, summary, unit)
            }.distinctUntilChanged().collect { (bags, summary, unit) ->
                _uiState.value = _uiState.value.copy(bags = bags, summary = summary, volumeUnit = unit)
            }
        }
    }

    fun onResume() {
        currentDate.value = today()
    }

    fun onAddBagClicked() {
        _uiState.value = _uiState.value.copy(
            addSheet = AddBagSheetState(collectionDate = now()),
            editSheet = null,
        )
    }

    fun onAddBagFieldChange(transform: (AddBagSheetState) -> AddBagSheetState) {
        val current = _uiState.value.addSheet ?: return
        if (current.isSaving) return
        _uiState.value = _uiState.value.copy(addSheet = transform(current))
    }

    fun onAddBagDismiss() {
        if (_uiState.value.addSheet?.isSaving == true) return
        _uiState.value = _uiState.value.copy(addSheet = null)
    }

    fun onAddBagConfirm() {
        val sheet = _uiState.value.addSheet ?: return
        if (sheet.isSaving) return
        val volume = sheet.volumeMl.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.value = _uiState.value.copy(
                addSheet = sheet.copy(validationError = appContext.getString(R.string.error_volume_greater_than_zero)),
            )
            return
        }
        if (sheet.collectionDate.isAfter(now())) {
            _uiState.value = _uiState.value.copy(
                addSheet = sheet.copy(validationError = appContext.getString(R.string.error_collection_date_future)),
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
                    addSheet = sheet.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save)),
                )
            }
        }
    }

    fun onEditBagClicked(bag: MilkBag) {
        _uiState.value = _uiState.value.copy(
            addSheet = null,
            editSheet = EditBagSheetState(
                bagId = bag.id,
                form = AddBagSheetState(
                    collectionDate = bag.collectionDate,
                    volumeMl = bag.volumeMl.toString(),
                    notes = bag.notes.orEmpty(),
                ),
            ),
        )
    }

    fun onEditBagFieldChange(transform: (AddBagSheetState) -> AddBagSheetState) {
        val current = _uiState.value.editSheet ?: return
        if (current.form.isSaving) return
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(form = transform(current.form)),
        )
    }

    fun onEditBagDismiss() {
        if (_uiState.value.editSheet?.form?.isSaving == true) return
        _uiState.value = _uiState.value.copy(editSheet = null)
    }

    fun onEditBagConfirm() {
        val sheet = _uiState.value.editSheet ?: return
        val form = sheet.form
        if (form.isSaving) return
        val volume = form.volumeMl.toIntOrNull()
        if (volume == null || volume <= 0) {
            _uiState.value = _uiState.value.copy(
                editSheet = sheet.copy(form = form.copy(validationError = appContext.getString(R.string.error_volume_greater_than_zero))),
            )
            return
        }
        if (form.collectionDate.isAfter(now())) {
            _uiState.value = _uiState.value.copy(
                editSheet = sheet.copy(form = form.copy(validationError = appContext.getString(R.string.error_collection_date_future))),
            )
            return
        }
        val saveToken = sheet.saveToken + 1
        _uiState.value = _uiState.value.copy(
            editSheet = sheet.copy(
                form = form.copy(isSaving = true),
                saveToken = saveToken,
            ),
        )
        viewModelScope.launch {
            runCatching {
                updateBag(
                    bagId = sheet.bagId,
                    collectionDate = form.collectionDate,
                    volumeMl = volume,
                    notes = form.notes.ifBlank { null },
                )
            }.onSuccess { syncSucceeded ->
                val current = _uiState.value.editSheet
                if (current?.bagId == sheet.bagId && current.saveToken == saveToken) {
                    _uiState.value = _uiState.value.copy(
                        editSheet = null,
                        error = if (syncSucceeded) {
                            _uiState.value.error
                        } else {
                            appContext.getString(R.string.msg_saved_locally)
                        },
                    )
                }
            }.onFailure {
                val current = _uiState.value.editSheet
                if (current?.bagId == sheet.bagId && current.saveToken == saveToken) {
                    _uiState.value = _uiState.value.copy(
                        editSheet = current.copy(
                            form = current.form.copy(isSaving = false, validationError = appContext.getString(R.string.error_could_not_save)),
                        ),
                    )
                }
            }
        }
    }

    fun onMarkUsed(bag: MilkBag) {
        viewModelScope.launch {
            runCatching { markUsed(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_inventory_mark_used)) }
        }
    }

    fun onDeleteRequest(bag: MilkBag) {
        _uiState.value = _uiState.value.copy(pendingDeleteBag = bag)
    }

    fun onDismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteBag = null)
    }

    fun onConfirmDelete() {
        val bag = _uiState.value.pendingDeleteBag ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteBag = null)
        viewModelScope.launch {
            runCatching { deleteBag(bag) }
                .onFailure { _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_inventory_delete_bag)) }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun today() = now().atZone(ZoneId.systemDefault()).toLocalDate()
}
