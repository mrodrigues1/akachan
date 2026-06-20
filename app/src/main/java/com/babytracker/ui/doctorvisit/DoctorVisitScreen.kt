package com.babytracker.ui.doctorvisit

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R

@Composable
fun DoctorVisitScreen(
    onDismiss: () -> Unit,
    onManageQuestions: () -> Unit,
    editVisitId: Long? = null,
    viewModel: DoctorVisitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(editVisitId) {
        if (editVisitId != null) viewModel.loadForEdit(editVisitId)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.onSavedConsumed()
            onDismiss()
        }
    }
    LaunchedEffect(state.pendingSnapshotShare) {
        val share = state.pendingSnapshotShare ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = share.mimeType
            putExtra(Intent.EXTRA_STREAM, share.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
        viewModel.onSnapshotShareConsumed()
    }
    val snapshotFailed = stringResource(R.string.doctor_visit_snapshot_failed)
    LaunchedEffect(state.snapshotError) {
        if (state.snapshotError) {
            Toast.makeText(context, snapshotFailed, Toast.LENGTH_SHORT).show()
            viewModel.onSnapshotErrorConsumed()
        }
    }

    DoctorVisitSheet(
        state = state,
        onDateChange = viewModel::onDateChange,
        onProviderChange = viewModel::onProviderChange,
        onNotesChange = viewModel::onNotesChange,
        onToggleQuestion = viewModel::onToggleQuestion,
        onAttachSnapshot = viewModel::onAttachSnapshot,
        onViewSnapshot = viewModel::onViewSnapshot,
        onManageQuestions = onManageQuestions,
        onSave = viewModel::onSave,
        onDismiss = onDismiss,
    )
}
