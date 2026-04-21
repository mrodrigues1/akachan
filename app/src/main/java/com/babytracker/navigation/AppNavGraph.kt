package com.babytracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.babytracker.ui.breastfeeding.BreastfeedingHistoryScreen
import com.babytracker.ui.breastfeeding.BreastfeedingScreen
import com.babytracker.ui.home.HomeScreen
import com.babytracker.ui.onboarding.OnboardingScreen
import com.babytracker.ui.settings.SettingsScreen
import com.babytracker.ui.theme.DesignSystemPreviewScreen
import com.babytracker.ui.sleep.SleepHistoryScreen
import com.babytracker.ui.sleep.SleepScheduleScreen
import com.babytracker.ui.sleep.SleepTrackingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val BREASTFEEDING = "breastfeeding"
    const val BREASTFEEDING_HISTORY = "breastfeeding/history"
    const val SLEEP_TRACKING = "sleep"
    const val SLEEP_HISTORY = "sleep/history"
    const val SLEEP_SCHEDULE = "sleep/schedule"
    const val SETTINGS = "settings"
    const val DESIGN_SYSTEM_PREVIEW = "design_system/preview"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    isOnboardingComplete: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToBreastfeeding = { navController.navigate(Routes.BREASTFEEDING) },
                onNavigateToSleep = { navController.navigate(Routes.SLEEP_TRACKING) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.BREASTFEEDING) {
            BreastfeedingScreen(
                onNavigateToHistory = { navController.navigate(Routes.BREASTFEEDING_HISTORY) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BREASTFEEDING_HISTORY) {
            BreastfeedingHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLEEP_TRACKING) {
            SleepTrackingScreen(
                onNavigateToHistory = { navController.navigate(Routes.SLEEP_HISTORY) },
                onNavigateToSchedule = { navController.navigate(Routes.SLEEP_SCHEDULE) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLEEP_HISTORY) {
            SleepHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLEEP_SCHEDULE) {
            SleepScheduleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
            )
        }
        composable(Routes.DESIGN_SYSTEM_PREVIEW) {
            DesignSystemPreviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
