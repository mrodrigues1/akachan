package com.babytracker.navigation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets.UTF_8

class AppNavGraphInventoryTest {

    @Test
    fun `inventory route wires settings navigation action`() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/com/babytracker/navigation/AppNavGraph.kt")),
            UTF_8,
        )

        val inventoryRouteStart = source.indexOf("composable(Routes.INVENTORY)")
        val inventoryRoute = source.substring(inventoryRouteStart).substringBefore("}\n}")

        assertTrue(
            inventoryRoute.contains("onNavigateToSettings = { navController.navigate(Routes.INVENTORY_SETTINGS) }"),
            "InventoryScreen route must wire the settings gear to Routes.INVENTORY_SETTINGS",
        )
    }
}
