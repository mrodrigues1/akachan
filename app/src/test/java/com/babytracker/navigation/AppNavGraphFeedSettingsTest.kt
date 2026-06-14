package com.babytracker.navigation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets.UTF_8

class AppNavGraphFeedSettingsTest {

    private val source = String(
        Files.readAllBytes(Paths.get("src/main/java/com/babytracker/navigation/AppNavGraph.kt")),
        UTF_8,
    )

    @Test
    fun `breastfeeding route wires settings navigation action`() {
        val routeStart = source.indexOf("composable(Routes.BREASTFEEDING)")
        val route = source.substring(routeStart).substringBefore("}\n}")

        assertTrue(
            route.contains("onNavigateToSettings = { navController.navigate(Routes.BREASTFEEDING_SETTINGS) }"),
            "BreastfeedingScreen route must wire the settings gear to Routes.BREASTFEEDING_SETTINGS",
        )
    }

    @Test
    fun `feed settings route is registered`() {
        assertTrue(
            source.contains("composable(Routes.BREASTFEEDING_SETTINGS)"),
            "AppNavGraph must register a composable for Routes.BREASTFEEDING_SETTINGS",
        )
        assertTrue(
            source.contains("FeedSettingsScreen(onNavigateBack = { navController.popBackStack() })"),
            "BREASTFEEDING_SETTINGS route must show FeedSettingsScreen with a back action",
        )
    }
}
