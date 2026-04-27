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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.PartnerInfo
import com.babytracker.util.formatElapsedAgo
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSharingScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageSharingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStopDialog by remember { mutableStateOf(false) }
    var showNewCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partner Sharing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
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
                    shareCode = uiState.shareCode.orEmpty(),
                    partners = uiState.partners,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
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
            title = { Text("Stop sharing?") },
            text = { Text("This will disconnect all connected partners.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stopSharing()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showNewCodeDialog) {
        AlertDialog(
            onDismissRequest = { showNewCodeDialog = false },
            title = { Text("Generate new code?") },
            text = { Text("This will disconnect all current partners. They'll need the new code to reconnect.") },
            confirmButton = {
                TextButton(onClick = {
                    showNewCodeDialog = false
                    viewModel.generateNewCode()
                }) { Text("Generate") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCodeDialog = false }) { Text("Cancel") }
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
            text = "Share your journey",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Share read-only access to your baby's data with a partner. " +
                "They'll see feeding sessions, sleep records, and baby info.",
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
            Text("Start Sharing")
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onClearError) { Text("Dismiss") }
        }
    }
}

@Composable
private fun PrimaryContent(
    shareCode: String,
    partners: List<PartnerInfo>,
    isLoading: Boolean,
    error: String?,
    onStopSharing: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onRevokePartner: (String) -> Unit,
    onClearError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SHARE CODE",
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
                    text = "Share this code with your partner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "CONNECTED PARTNERS",
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
                text = "No partners connected yet.",
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
            Text("Generate New Code")
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
            Text("Stop Sharing")
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onClearError) { Text("Dismiss") }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: PartnerInfo,
    onRevoke: () -> Unit,
) {
    val connectedAgo = remember(partner.connectedAt) {
        Duration.between(partner.connectedAt, Instant.now()).formatElapsedAgo()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Partner", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Connected $connectedAgo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRevoke) {
            Text(text = "Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}
