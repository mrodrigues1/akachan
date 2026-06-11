package com.babytracker.ui.bottlefeed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BottleFeedScreen(
    onNavigateBack: () -> Unit,
    viewModel: BottleFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }
    BottleFeedSheet(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onVolumeChange = viewModel::onVolumeChange,
        onTimeChange = viewModel::onTimeChange,
        onBagSelect = viewModel::onBagSelect,
        onNotesChange = viewModel::onNotesChange,
        onConfirm = viewModel::onSave,
        onDismiss = onNavigateBack,
    )
}
