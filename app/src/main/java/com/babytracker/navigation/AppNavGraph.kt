package com.babytracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.breastfeeding.BreastfeedingHistoryScreen
import com.babytracker.ui.breastfeeding.BreastfeedingScreen
import com.babytracker.ui.home.HomeScreen
import com.babytracker.ui.onboarding.OnboardingScreen
import com.babytracker.ui.partner.PartnerDashboardScreen
import com.babytracker.ui.settings.SettingsScreen
import com.babytracker.ui.sharing.ConnectPartnerScreen
import com.babytracker.ui.sharing.ManageSharingScreen
import com.babytracker.ui.sleep.SleepHistoryScreen
import com.babytracker.ui.sleep.SleepScheduleScreen
import com.babytracker.ui.sleep.SleepTrackingScreen
import com.babytracker.ui.theme.DesignSystemPreviewScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    isOnboardingComplete: Boolean,
    appMode: AppMode,
) {
    NavHost(
        navController = navController,
        startDestination = when {
            appMode == AppMode.PARTNER -> Routes.PARTNER_DASHBOARD
            isOnboardingComplete -> Routes.HOME
            else -> Routes.ONBOARDING
        },
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
            BreastfeedingHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SLEEP_TRACKING) {
            SleepTrackingScreen(
                onNavigateToHistory = { navController.navigate(Routes.SLEEP_HISTORY) },
                onNavigateToSchedule = { navController.navigate(Routes.SLEEP_SCHEDULE) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLEEP_HISTORY) {
            SleepHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SLEEP_SCHEDULE) {
            SleepScheduleScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
            )
        }
        composable(Routes.DESIGN_SYSTEM_PREVIEW) {
            DesignSystemPreviewScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.CONNECT_PARTNER) {
            ConnectPartnerScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.PARTNER_DASHBOARD) {
            PartnerDashboardScreen()
        }
        composable(Routes.MANAGE_SHARING) {
            ManageSharingScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
