package com.babytracker.ui.diaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiaperScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiaperViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    DiaperSheet(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onTimeChange = viewModel::onTimeChange,
        onNotesChange = viewModel::onNotesChange,
        onConfirm = viewModel::onSave,
        onDismiss = onNavigateBack,
    )
}
