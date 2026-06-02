package com.babytracker

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.UpdateInfo
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.navigation.AppNavGraph
import com.babytracker.navigation.Routes
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.tile.FeedTileService
import com.babytracker.tile.SleepTileService
import com.babytracker.ui.theme.BabyTrackerTheme
import com.babytracker.util.NotificationHelper
import com.babytracker.util.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

internal val ALLOWED_NAV_ROUTES = setOf(Routes.HOME, Routes.BREASTFEEDING, Routes.SLEEP_TRACKING, Routes.INVENTORY)

internal enum class PendingNavAction { NAVIGATE, CLEAR, WAIT }

internal fun pendingNavAction(
    pending: String?,
    allowedRoutes: Set<String>,
    appMode: AppMode?,
    isOnboardingComplete: Boolean?,
): PendingNavAction {
    pending ?: return PendingNavAction.WAIT
    if (pending !in allowedRoutes) return PendingNavAction.CLEAR
    if (appMode == null) return PendingNavAction.WAIT
    if (appMode == AppMode.PARTNER) return PendingNavAction.CLEAR
    if (isOnboardingComplete != true) return PendingNavAction.WAIT
    return PendingNavAction.NAVIGATE
}

internal fun navRouteFromIntent(intent: Intent): String? =
    intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE)
        ?: tilePreferencesRoute(intent)

private fun tilePreferencesRoute(intent: Intent): String? {
    if (intent.action != TileService.ACTION_QS_TILE_PREFERENCES) return null
    val componentName = IntentCompat.getParcelableExtra(
        intent,
        Intent.EXTRA_COMPONENT_NAME,
        ComponentName::class.java,
    )
    return when (componentName?.className) {
        FeedTileService::class.java.name -> Routes.BREASTFEEDING
        SleepTileService::class.java.name -> Routes.SLEEP_TRACKING
        else -> Routes.HOME
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var babyRepository: BabyRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateChecker: UpdateChecker

    private val pendingNavRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNavRoute.value = navRouteFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            val isOnboardingComplete by babyRepository
                .isOnboardingComplete()
                .collectAsStateWithLifecycle(initialValue = null)

            val appMode by settingsRepository
                .getAppMode()
                .collectAsStateWithLifecycle(initialValue = null)

            val themeConfig by settingsRepository
                .getThemeConfig()
                .collectAsStateWithLifecycle(initialValue = ThemeConfig.SYSTEM)

            val autoUpdateEnabled by settingsRepository
                .getAutoUpdateEnabled()
                .collectAsStateWithLifecycle(initialValue = true)

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

            LaunchedEffect(autoUpdateEnabled) {
                updateInfo = if (autoUpdateEnabled) updateChecker.checkForUpdate() else null
            }

            val navController = rememberNavController()
            val pending by pendingNavRoute
            HandlePendingNavRoute(
                pending = pending,
                isOnboardingComplete = isOnboardingComplete,
                appMode = appMode,
                navController = navController,
                onClear = { pendingNavRoute.value = null },
            )

            BabyTrackerTheme(themeConfig = themeConfig) {
                when {
                    isOnboardingComplete == null || appMode == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> {
                        AppNavGraph(
                            navController = navController,
                            isOnboardingComplete = isOnboardingComplete!!,
                            appMode = appMode!!,
                        )
                    }
                }

                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text("Update available") },
                        text = { Text("Version ${updateInfo!!.versionName} is available. Would you like to download it?") },
                        confirmButton = {
                            TextButton(onClick = {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.releaseUrl))
                                )
                                updateInfo = null
                            }) {
                                Text("Download")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) {
                                Text("Later")
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavRoute.value = navRouteFromIntent(intent)
    }
}

@Composable
private fun HandlePendingNavRoute(
    pending: String?,
    isOnboardingComplete: Boolean?,
    appMode: AppMode?,
    navController: NavController,
    onClear: () -> Unit,
) {
    LaunchedEffect(pending, isOnboardingComplete, appMode) {
        when (pendingNavAction(pending, ALLOWED_NAV_ROUTES, appMode, isOnboardingComplete)) {
            PendingNavAction.WAIT -> Unit
            PendingNavAction.CLEAR -> onClear()
            PendingNavAction.NAVIGATE -> {
                navController.navigate(pending!!) {
                    launchSingleTop = true
                    popUpTo(Routes.HOME) { inclusive = false; saveState = true }
                }
                onClear()
            }
        }
    }
}
