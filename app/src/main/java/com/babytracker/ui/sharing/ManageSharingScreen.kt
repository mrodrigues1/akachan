package com.babytracker.ui.sharing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.R
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.util.formatElapsedAgo
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSharingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageSharingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStopDialog by remember { mutableStateOf(false) }
    var showNewCodeDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shareCode = uiState.shareCode.orEmpty()
    val codeCopiedMessage = stringResource(R.string.msg_code_copied)
    val shareCodeMessage = stringResource(R.string.share_code_message, shareCode)
    val noShareAppMessage = stringResource(R.string.error_no_share_app)
    val clipboard = LocalClipboardManager.current
    val onCopyCode: () -> Unit = {
        clipboard.setText(AnnotatedString(shareCode))
        scope.launch { snackbarHostState.showSnackbar(codeCopiedMessage) }
    }
    val context = LocalContext.current
    val onShareCode: () -> Unit = {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareCodeMessage)
        }
        try {
            context.startActivity(Intent.createChooser(sendIntent, null))
        } catch (e: ActivityNotFoundException) {
            Log.d("ManageSharingScreen", "No app available to share code", e)
            scope.launch { snackbarHostState.showSnackbar(noShareAppMessage) }
        }
    }

    LaunchedEffect(uiState.appMode, uiState.shareCode) { viewModel.refresh() }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_partner_sharing)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (uiState.appMode) {
                AppMode.NONE -> SetupContent(
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onStartSharing = viewModel::startSharing,
                    onClearError = viewModel::clearError,
                )
                AppMode.PRIMARY -> PrimaryContent(
                    shareCode = shareCode,
                    partners = uiState.partners,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onCopyCode = onCopyCode,
                    onShareCode = onShareCode,
                    onStopSharing = { showStopDialog = true },
                    onGenerateNewCode = { showNewCodeDialog = true },
                    onRevokePartner = viewModel::revokePartner,
                    onClearError = viewModel::clearError,
                )
                AppMode.PARTNER -> Unit
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.sharing_stop_title)) },
            text = { Text(stringResource(R.string.sharing_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stopSharing()
                }) { Text(stringResource(R.string.sharing_stop_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showNewCodeDialog) {
        AlertDialog(
            onDismissRequest = { showNewCodeDialog = false },
            title = { Text(stringResource(R.string.sharing_new_code_title)) },
            text = { Text(stringResource(R.string.sharing_new_code_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNewCodeDialog = false
                    viewModel.generateNewCode()
                }) { Text(stringResource(R.string.sharing_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewCodeDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SetupContent(
    isLoading: Boolean,
    error: String?,
    onStartSharing: () -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.sharing_share_journey),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sharing_share_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStartSharing,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.sharing_start))
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun PrimaryContent(
    shareCode: String,
    partners: List<PartnerInfo>,
    isLoading: Boolean,
    error: String?,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onStopSharing: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onRevokePartner: (String) -> Unit,
    onClearError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sharing_share_code_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = shareCode,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    letterSpacing = 4.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sharing_share_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCopyCode,
                enabled = !isLoading && shareCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sharing_copy))
            }
            OutlinedButton(
                onClick = onShareCode,
                enabled = !isLoading && shareCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sharing_share))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.sharing_connected_partners),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading && partners.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (partners.isEmpty()) {
            Text(
                text = stringResource(R.string.sharing_no_partners),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            partners.forEach { partner ->
                PartnerRow(partner = partner, onRevoke = { onRevokePartner(partner.uid) })
                HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onGenerateNewCode,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sharing_generate_new))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onStopSharing,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.sharing_stop_sharing))
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: PartnerInfo,
    onRevoke: () -> Unit,
) {
    val context = LocalContext.current
    val connectedAgo = remember(partner.connectedAt, context) {
        Duration.between(partner.connectedAt, Instant.now()).formatElapsedAgo(context)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.sharing_partner_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.sharing_connected_ago, connectedAgo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRevoke) {
            Text(text = stringResource(R.string.sharing_remove), color = MaterialTheme.colorScheme.error)
        }
    }
}
