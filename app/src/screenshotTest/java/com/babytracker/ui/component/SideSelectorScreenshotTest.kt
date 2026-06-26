package com.babytracker.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.babytracker.domain.model.BreastSide
import com.babytracker.ui.theme.BabyTrackerTheme

// Starter Compose Preview Screenshot test. Each @PreviewTest @Preview here renders a reference
// image. Generate/update them with `gradlew updateDebugScreenshotTest`, validate with
// `gradlew validateDebugScreenshotTest` (HTML diff at build/reports/screenshotTest/preview/debug).
// Grow coverage by adding more @PreviewTest composables in this source set (src/screenshotTest).

@PreviewTest
@Preview(showBackground = true)
@Composable
private fun SideSelectorLightPreview() {
    BabyTrackerTheme {
        SideSelector(
            selectedSide = BreastSide.LEFT,
            onSideSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SideSelectorDarkPreview() {
    BabyTrackerTheme {
        SideSelector(
            selectedSide = BreastSide.LEFT,
            onSideSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun SideSelectorLargeFontPreview() {
    BabyTrackerTheme {
        SideSelector(
            selectedSide = null,
            onSideSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
