package com.babytracker.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.babytracker.domain.model.ExpirationStatus
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.util.formatDateTime
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.formatElapsedCompact
import com.babytracker.util.formatVolume
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorDismissed()
    }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Milk Stash") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Milk stash settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddBagClicked) {
                Icon(Icons.Default.Add, contentDescription = "Add bag")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        InventoryContent(
            state = state,
            onEdit = viewModel::onEditBagClicked,
            onMarkUsed = viewModel::onMarkUsed,
            onDelete = viewModel::onDeleteRequest,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        )
    }

    state.addSheet?.let { sheet ->
        AddBagSheet(
            state = sheet,
            onFieldChange = viewModel::onAddBagFieldChange,
            onConfirm = viewModel::onAddBagConfirm,
            onDismiss = viewModel::onAddBagDismiss,
        )
    }

    state.editSheet?.let { sheet ->
        AddBagSheet(
            state = sheet.form,
            onFieldChange = viewModel::onEditBagFieldChange,
            onConfirm = viewModel::onEditBagConfirm,
            onDismiss = viewModel::onEditBagDismiss,
            title = "Edit milk bag",
            confirmLabel = "Save changes",
        )
    }

    state.pendingDeleteBag?.let { bag ->
        MilkBagDeleteConfirmationDialog(
            bag = bag,
            volumeUnit = state.volumeUnit,
            onDismiss = viewModel::onDismissDelete,
            onConfirm = viewModel::onConfirmDelete,
        )
    }
}

@Composable
internal fun InventoryContent(
    state: InventoryUiState,
    onEdit: (MilkBag) -> Unit,
    onMarkUsed: (MilkBag) -> Unit,
    onDelete: (MilkBag) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.bags.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "🧊", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No bags in your stash yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Tap + to add a bag manually",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "summary") {
                SummaryCard(summary = state.summary, volumeUnit = state.volumeUnit)
                Spacer(Modifier.height(8.dp))
            }
            items(state.bags, key = { it.bag.id }) { item ->
                val isOldest = item.bag.id == state.bags.first().bag.id
                MilkBagRow(
                    bag = item.bag,
                    expirationStatus = item.status,
                    volumeUnit = state.volumeUnit,
                    isOldest = isOldest,
                    onEdit = { onEdit(item.bag) },
                    onMarkUsed = { onMarkUsed(item.bag) },
                    onDelete = { onDelete(item.bag) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: InventorySummary,
    volumeUnit: VolumeUnit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatColumn(
                value = formatVolume(summary.totalMl, volumeUnit),
                label = "TOTAL",
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            StatColumn(
                value = "${summary.bagCount}",
                label = "BAGS",
                modifier = Modifier.weight(1f),
            )
            if (summary.oldestBagDate != null) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                StatColumn(
                    value = Duration.between(summary.oldestBagDate, Instant.now()).formatElapsedCompact(),
                    label = "OLDEST",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MilkBagRow(
    bag: MilkBag,
    expirationStatus: ExpirationStatus,
    volumeUnit: VolumeUnit,
    isOldest: Boolean,
    onEdit: () -> Unit,
    onMarkUsed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val warningContainerColor = if (isDark) {
        WarningContainerAmberDark
    } else {
        WarningContainerAmber
    }
    val onWarningContainerColor = if (isDark) {
        OnWarningContainerAmberDark
    } else {
        OnWarningContainerAmber
    }
    val expiringSoonContainerColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = warningContainerColor,
        fraction = 0.45f,
    )
    val containerColor = when (expirationStatus) {
        ExpirationStatus.EXPIRING_OR_EXPIRED -> warningContainerColor
        ExpirationStatus.EXPIRING_SOON -> expiringSoonContainerColor
        ExpirationStatus.NONE -> if (isOldest) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    }
    val contentColor = when (expirationStatus) {
        ExpirationStatus.NONE -> if (isOldest) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        else -> onWarningContainerColor
    }
    val supportingColor = when {
        expirationStatus != ExpirationStatus.NONE -> contentColor.copy(alpha = 0.82f)
        isOldest -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val markUsedColor = when {
        expirationStatus != ExpirationStatus.NONE -> contentColor
        isOldest -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatVolume(bag.volumeMl, volumeUnit),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = bag.collectionDate.formatDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
                Text(
                    text = "Pumped ${Duration.between(bag.collectionDate, Instant.now()).formatElapsedAgo()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }

            IconButton(onClick = onMarkUsed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Mark used",
                    tint = markUsedColor,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = contentColor,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MilkBagDeleteConfirmationDialog(
    bag: MilkBag,
    volumeUnit: VolumeUnit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete bag?") },
        text = { Text("${formatVolume(bag.volumeMl, volumeUnit)} bag will be removed.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
