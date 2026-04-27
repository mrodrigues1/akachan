package com.babytracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.UpdateInfo
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.navigation.AppNavGraph
import com.babytracker.ui.theme.BabyTrackerTheme
import com.babytracker.util.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var babyRepository: BabyRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            BabyTrackerTheme(themeConfig = themeConfig) {
                when {
                    isOnboardingComplete == null || appMode == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> {
                        val navController = rememberNavController()
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
}
