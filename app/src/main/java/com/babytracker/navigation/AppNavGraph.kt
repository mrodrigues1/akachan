package com.babytracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.bottlefeed.BottleFeedScreen
import com.babytracker.ui.breastfeeding.BreastfeedingHistoryScreen
import com.babytracker.ui.breastfeeding.BreastfeedingScreen
import com.babytracker.ui.feeding.UnifiedFeedingHistoryScreen
import com.babytracker.ui.home.HomeScreen
import com.babytracker.ui.onboarding.OnboardingScreen
import com.babytracker.ui.partner.PartnerDashboardScreen
import com.babytracker.ui.settings.SettingsScreen
import com.babytracker.ui.sharing.ConnectPartnerScreen
import com.babytracker.ui.sharing.ManageSharingScreen
import com.babytracker.ui.sleep.SleepHistoryScreen
import com.babytracker.ui.sleep.SleepScheduleScreen
import com.babytracker.ui.sleep.SleepTrackingScreen
import com.babytracker.ui.inventory.InventoryScreen
import com.babytracker.ui.inventory.InventorySettingsScreen
import com.babytracker.ui.pumping.PumpingHistoryScreen
import com.babytracker.ui.pumping.PumpingScreen
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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToConnectPartner = { navController.navigate(Routes.CONNECT_PARTNER) },
                onNavigateToPumping = { navController.navigate(Routes.PUMPING) },
                onNavigateToInventory = { navController.navigate(Routes.INVENTORY) },
                onNavigateToBottleFeed = { navController.navigate(Routes.BOTTLE_FEED) },
                onNavigateToFeedingHistory = { navController.navigate(Routes.FEEDING_HISTORY) },
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
        composable(Routes.FEEDING_HISTORY) {
            UnifiedFeedingHistoryScreen(onNavigateBack = { navController.popBackStack() })
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
        settingsGraph(navController, isOnboardingComplete)
        pumpingGraph(navController)
    }
}

private fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    isOnboardingComplete: Boolean,
) {
    composable(Routes.SETTINGS) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDesignSystem = { navController.navigate(Routes.DESIGN_SYSTEM_PREVIEW) },
            onNavigateToManageSharing = { navController.navigate(Routes.MANAGE_SHARING) },
            onNavigateToConnectPartner = { navController.navigate(Routes.CONNECT_PARTNER) },
            onDisconnect = { navController.navigateAfterDisconnect(isOnboardingComplete) },
        )
    }
    composable(Routes.DESIGN_SYSTEM_PREVIEW) {
        DesignSystemPreviewScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.CONNECT_PARTNER) {
        ConnectPartnerScreen(
            onNavigateBack = { navController.popBackStack() },
            onConnected = { navController.navigate(Routes.PARTNER_DASHBOARD) { popUpTo(0) { inclusive = true } } },
        )
    }
    composable(Routes.PARTNER_DASHBOARD) {
        PartnerDashboardScreen(
            onDisconnected = { navController.navigateAfterDisconnect(isOnboardingComplete) },
            onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
        )
    }
    composable(Routes.MANAGE_SHARING) {
        ManageSharingScreen(onNavigateBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.pumpingGraph(navController: NavHostController) {
    composable(Routes.PUMPING) {
        PumpingScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHistory = { navController.navigate(Routes.PUMPING_HISTORY) },
            onNavigateToInventory = { navController.navigate(Routes.INVENTORY) },
        )
    }
    composable(Routes.PUMPING_HISTORY) {
        PumpingHistoryScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.INVENTORY) {
        InventoryScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = { navController.navigate(Routes.INVENTORY_SETTINGS) },
        )
    }
    composable(Routes.INVENTORY_SETTINGS) {
        InventorySettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.BOTTLE_FEED) {
        BottleFeedScreen(onNavigateBack = { navController.popBackStack() })
    }
}

private fun NavHostController.navigateAfterDisconnect(isOnboardingComplete: Boolean) {
    val destination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
    navigate(destination) { popUpTo(0) { inclusive = true } }
}
