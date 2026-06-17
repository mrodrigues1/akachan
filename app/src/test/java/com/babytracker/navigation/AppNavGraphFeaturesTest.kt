package com.babytracker.navigation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets.UTF_8

class AppNavGraphFeaturesTest {

    private val source = String(
        Files.readAllBytes(Paths.get("src/main/java/com/babytracker/navigation/AppNavGraph.kt")),
        UTF_8,
    )

    @Test
    fun `settings route wires the what-you-track navigation action`() {
        val settingsRouteStart = source.indexOf("composable(Routes.SETTINGS)")
        val settingsRoute = source.substring(settingsRouteStart).substringBefore("}\n    }")

        assertTrue(
            settingsRoute.contains("onNavigateToFeatures = { navController.navigate(Routes.FEATURES) }"),
            "SettingsScreen route must wire onNavigateToFeatures to Routes.FEATURES",
        )
    }

    @Test
    fun `features route renders the FeaturesScreen with back navigation`() {
        val featuresRouteStart = source.indexOf("composable(Routes.FEATURES)")
        assertTrue(featuresRouteStart >= 0, "AppNavGraph must register Routes.FEATURES")
        val featuresRoute = source.substring(featuresRouteStart).substringBefore("}\n    }")

        assertTrue(
            featuresRoute.contains("FeaturesScreen(onNavigateBack = { navController.popBackStack() })"),
            "Routes.FEATURES must render FeaturesScreen with a back action",
        )
    }
}
