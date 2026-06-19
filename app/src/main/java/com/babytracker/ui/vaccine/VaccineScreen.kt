package com.babytracker.ui.vaccine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VaccineScreen(
    onNavigateBack: () -> Unit,
    viewModel: VaccineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    VaccineSheet(
        state = state,
        onNameChange = viewModel::onNameChange,
        onDoseChange = viewModel::onDoseChange,
        onModeChange = viewModel::onModeChange,
        onDateChange = viewModel::onDateChange,
        onNotesChange = viewModel::onNotesChange,
        onConfirm = viewModel::onSave,
        onDismiss = onNavigateBack,
    )
}
