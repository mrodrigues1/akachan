package com.babytracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.ui.bottlefeed.BottleFeedScreen
import com.babytracker.ui.diaper.DiaperHistoryScreen
import com.babytracker.ui.diaper.DiaperScreen
import com.babytracker.ui.doctorvisit.DoctorVisitDashboardScreen
import com.babytracker.ui.doctorvisit.DoctorVisitHistoryScreen
import com.babytracker.ui.doctorvisit.DoctorVisitScreen
import com.babytracker.ui.doctorvisit.DoctorVisitSettingsScreen
import com.babytracker.ui.doctorvisit.VisitQuestionsScreen
import com.babytracker.ui.vaccine.VaccineDashboardScreen
import com.babytracker.ui.vaccine.VaccineHistoryScreen
import com.babytracker.ui.vaccine.VaccineSettingsScreen
import com.babytracker.ui.features.FeaturesScreen
import com.babytracker.ui.breastfeeding.BreastfeedingHistoryScreen
import com.babytracker.ui.breastfeeding.BreastfeedingScreen
import com.babytracker.ui.breastfeeding.FeedSettingsScreen
import com.babytracker.ui.feeding.UnifiedFeedingHistoryScreen
import com.babytracker.ui.growth.GrowthScreen
import com.babytracker.ui.home.HomeScreen
import com.babytracker.ui.milestone.MilestoneDetailScreen
import com.babytracker.ui.milestone.MilestonesScreen
import com.babytracker.ui.onboarding.OnboardingScreen
import com.babytracker.ui.partner.PartnerDashboardScreen
import com.babytracker.ui.partner.PartnerFeedHistoryScreen
import com.babytracker.ui.settings.SettingsScreen
import com.babytracker.ui.sharing.ConnectPartnerScreen
import com.babytracker.ui.sharing.ManageSharingScreen
import com.babytracker.ui.sleep.SleepHistoryScreen
import com.babytracker.ui.sleep.SleepScheduleScreen
import com.babytracker.ui.sleep.SleepSettingsScreen
import com.babytracker.ui.sleep.SleepTrackingScreen
import com.babytracker.ui.inventory.InventoryScreen
import com.babytracker.ui.inventory.InventorySettingsScreen
import com.babytracker.ui.pumping.PumpingHistoryScreen
import com.babytracker.ui.pumping.PumpingScreen
import com.babytracker.ui.theme.DesignSystemPreviewScreen
import com.babytracker.ui.trends.TrendsScreen

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
                onNavigateToDiaper = { navController.navigate(Routes.DIAPER) },
                onNavigateToVaccine = { navController.navigate(Routes.VACCINE) },
                onNavigateToDoctorVisit = { navController.navigate(Routes.DOCTOR_VISIT_DASHBOARD) },
                onNavigateToFeedingHistory = { navController.navigate(Routes.FEEDING_HISTORY) },
                onNavigateToGrowth = { navController.navigate(Routes.GROWTH) },
                onNavigateToMilestones = { navController.navigate(Routes.MILESTONES) },
                onNavigateToTrends = { navController.navigate(Routes.TRENDS) },
            )
        }
        insightsGraph(navController)
        composable(Routes.BREASTFEEDING) {
            BreastfeedingScreen(
                onNavigateToHistory = { navController.navigate(Routes.BREASTFEEDING_HISTORY) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.BREASTFEEDING_SETTINGS) }
            )
        }
        composable(Routes.BREASTFEEDING_HISTORY) {
            BreastfeedingHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.BREASTFEEDING_SETTINGS) {
            FeedSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.FEEDING_HISTORY) {
            UnifiedFeedingHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SLEEP_TRACKING) {
            SleepTrackingScreen(
                onNavigateToHistory = { navController.navigate(Routes.SLEEP_HISTORY) },
                onNavigateToSchedule = { navController.navigate(Routes.SLEEP_SCHEDULE) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.SLEEP_SETTINGS) }
            )
        }
        composable(Routes.SLEEP_HISTORY) {
            SleepHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SLEEP_SCHEDULE) {
            SleepScheduleScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SLEEP_SETTINGS) {
            SleepSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        settingsGraph(navController, isOnboardingComplete)
        pumpingGraph(navController)
    }
}

private fun NavGraphBuilder.insightsGraph(navController: NavHostController) {
    composable(Routes.GROWTH) {
        GrowthScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
        )
    }
    composable(Routes.MILESTONES) {
        MilestonesScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDetail = { id -> navController.navigate(Routes.milestoneDetail(id)) },
        )
    }
    composable(
        route = Routes.MILESTONE_DETAIL,
        arguments = listOf(navArgument(Routes.MILESTONE_DETAIL_ARG) { type = NavType.StringType }),
    ) {
        MilestoneDetailScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.TRENDS) {
        TrendsScreen(onNavigateBack = { navController.popBackStack() })
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
            onNavigateToFeatures = { navController.navigate(Routes.FEATURES) },
            onDisconnect = { navController.navigateAfterDisconnect(isOnboardingComplete) },
        )
    }
    composable(Routes.FEATURES) {
        FeaturesScreen(onNavigateBack = { navController.popBackStack() })
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
            onNavigateToFeedHistory = { navController.navigate(Routes.PARTNER_FEED_HISTORY) },
        )
    }
    composable(Routes.PARTNER_FEED_HISTORY) {
        PartnerFeedHistoryScreen(onNavigateBack = { navController.popBackStack() })
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
    composable(Routes.DIAPER) {
        DiaperScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHistory = { navController.navigate(Routes.DIAPER_HISTORY) },
        )
    }
    composable(Routes.DIAPER_HISTORY) {
        DiaperHistoryScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.VACCINE) {
        VaccineDashboardScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHistory = { navController.navigate(Routes.VACCINE_HISTORY) },
            onNavigateToSettings = { navController.navigate(Routes.VACCINE_SETTINGS) },
        )
    }
    composable(Routes.VACCINE_HISTORY) {
        VaccineHistoryScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.VACCINE_SETTINGS) {
        VaccineSettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
    doctorVisitGraph(navController)
}

private fun NavGraphBuilder.doctorVisitGraph(navController: NavHostController) {
    composable(Routes.VISIT_QUESTIONS) {
        VisitQuestionsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(Routes.DOCTOR_VISIT_DASHBOARD) {
        DoctorVisitDashboardScreen(
            onAddVisit = { navController.navigate(Routes.doctorVisit()) },
            onEditVisit = { id -> navController.navigate(Routes.doctorVisit(id)) },
            onNavigateToHistory = { navController.navigate(Routes.DOCTOR_VISIT_HISTORY) },
            onManageQuestions = { navController.navigate(Routes.VISIT_QUESTIONS) },
            onNavigateToSettings = { navController.navigate(Routes.DOCTOR_VISIT_SETTINGS) },
            onNavigateBack = { navController.popBackStack() },
        )
    }
    composable(
        route = Routes.DOCTOR_VISIT,
        arguments = listOf(
            navArgument(Routes.DOCTOR_VISIT_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
        ),
    ) { entry ->
        val visitId = entry.arguments?.getLong(Routes.DOCTOR_VISIT_ARG) ?: -1L
        DoctorVisitScreen(
            editVisitId = visitId.takeIf { it >= 0 },
            onManageQuestions = { navController.navigate(Routes.VISIT_QUESTIONS) },
            onNavigateToHistory = { navController.navigate(Routes.DOCTOR_VISIT_HISTORY) },
            onNavigateToSettings = { navController.navigate(Routes.DOCTOR_VISIT_SETTINGS) },
            onDismiss = { navController.popBackStack() },
        )
    }
    composable(Routes.DOCTOR_VISIT_HISTORY) {
        DoctorVisitHistoryScreen(
            onAddOrEdit = { id -> navController.navigate(Routes.doctorVisit(id)) },
            onNavigateBack = { navController.popBackStack() },
        )
    }
    composable(Routes.DOCTOR_VISIT_SETTINGS) {
        DoctorVisitSettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
}

private fun NavHostController.navigateAfterDisconnect(isOnboardingComplete: Boolean) {
    val destination = if (isOnboardingComplete) Routes.HOME else Routes.ONBOARDING
    navigate(destination) { popUpTo(0) { inclusive = true } }
}
