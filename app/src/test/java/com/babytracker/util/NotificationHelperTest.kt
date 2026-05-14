package com.babytracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationHelperTest {

    private val lightColor = Color(0xFFC2185B) // Pink700
    private val darkColor = Color(0xFFF48FB1)  // PrimaryPinkDark

    // resolveAccent(Context, Color, Color) compiles to a name-mangled method
    // because Color is a Compose @JvmInline value class — its JVM ABI is
    // `resolveAccent-<hash>(Context, long, long)`. Find it by name prefix and
    // pass the unboxed ULong.toLong() for each Color.
    private fun invokeResolveAccent(context: Context, light: Color, dark: Color): Int {
        val method = NotificationHelper::class.java.declaredMethods
            .first { it.name.startsWith("resolveAccent") }
        method.isAccessible = true
        return method.invoke(
            NotificationHelper,
            context,
            light.value.toLong(),
            dark.value.toLong()
        ) as Int
    }

    private fun contextWithUiMode(uiMode: Int): Context {
        val config = Configuration().apply { this.uiMode = uiMode }
        val resources = mockk<Resources>()
        every { resources.configuration } returns config
        val context = mockk<Context>()
        every { context.resources } returns resources
        return context
    }

    private fun invokePrivateStringMethod(name: String, value: Int): String {
        val method = NotificationHelper::class.java.declaredMethods
            .first { it.name == name }
        method.isAccessible = true
        return method.invoke(NotificationHelper, value) as String
    }

    private fun notificationHelperSource(): String =
        listOf(
            java.io.File("src/main/java/com/babytracker/util/NotificationHelper.kt"),
            java.io.File("app/src/main/java/com/babytracker/util/NotificationHelper.kt")
        ).first { it.exists() }.readText()

    @Test
    fun `resolveAccent returns dark color when uiMode is night`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_YES)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(darkColor.toArgb(), result)
    }

    @Test
    fun `resolveAccent returns light color when uiMode is not night`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_NO)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(lightColor.toArgb(), result)
    }

    @Test
    fun `resolveAccent returns light color when uiMode is undefined`() {
        val context = contextWithUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED)
        val result = invokeResolveAccent(context, lightColor, darkColor)
        assertEquals(lightColor.toArgb(), result)
    }

    @Test
    fun `activeProgressText shows only max feeding time`() {
        assertEquals("2 min", invokePrivateStringMethod("activeProgressText", 2))
    }

    @Test
    fun `limitProgressText shows only max feeding time`() {
        assertEquals("2 min", invokePrivateStringMethod("limitProgressText", 2))
    }

    @Test
    fun `active notification refresh interval is five seconds`() {
        val field = NotificationHelper::class.java.getDeclaredField("ACTIVE_REFRESH_INTERVAL_MS")
        field.isAccessible = true

        assertEquals(5_000L, field.getLong(NotificationHelper))
    }

    @Test
    fun `rich breastfeeding notifications do not use system progress or subtext`() {
        val source = notificationHelperSource()

        assertEquals(0, Regex("\\.setProgress\\(").findAll(source).count())
        assertEquals(0, Regex("\\.setSubText\\(").findAll(source).count())
    }

    @Test
    fun `active breastfeeding notification exposes pause and resume actions`() {
        val source = notificationHelperSource()

        assertTrue(source.contains("ACTION_PAUSE"))
        assertTrue(source.contains("ACTION_RESUME"))
        assertTrue(source.contains("\"Pause\""))
        assertTrue(source.contains("\"Resume\""))
        assertTrue(source.contains("\"Stop feeding\""))
    }

    // --- copy regression tests ---

    @Test
    fun `feeding limit body uses dash-joined duration and both-paths guidance`() {
        val source = notificationHelperSource()
        val body = Regex("""val body = "(.+?)"""")
            .findAll(Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive").find(source)!!.value)
            .first().groupValues[1]

        assertTrue(body.contains("-min session"), "body must use dash-joined duration: got '$body'")
        assertTrue(body.contains("Tap Stop or Continue"), "body must state both paths with capital C on Continue: got '$body'")
        assertFalse(body.contains("limit reached"), "stale 'limit reached' copy must not appear: got '$body'")
    }

    @Test
    fun `feeding limit action labels are warm-functional`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")

        assertTrue(functionBody.contains("\"Stop feeding\""), "feeding-limit stop action must be 'Stop feeding'")
        assertTrue(functionBody.contains("\"Continue\""), "feeding-limit keep-going action must be 'Continue'")
        assertFalse(functionBody.contains("\"Stop Session\""), "stale 'Stop Session' must not appear in showFeedingLimit")
        assertFalse(functionBody.contains("\"Keep Going\""), "stale 'Keep Going' must not appear in showFeedingLimit")
    }

    @Test
    fun `switch side dismiss action is not yet`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(functionBody.contains("\"Not yet\""), "switch-side dismiss must be 'Not yet'")
        assertFalse(functionBody.contains("\"Dismiss\""), "stale 'Dismiss' must not appear in showSwitchSide")
    }

    @Test
    fun `active breastfeeding stop action is stop feeding`() {
        val source = notificationHelperSource()
        val functionBody = Regex("applyBreastfeedingActiveRichContent[\\s\\S]*?private fun breastfeedingActiveProgress")
            .find(source)?.value ?: error("applyBreastfeedingActiveRichContent body not found")

        assertTrue(functionBody.contains("\"Stop feeding\""), "active feeding stop action must be 'Stop feeding'")
        assertFalse(functionBody.contains("\"Stop Session\""), "stale 'Stop Session' must not appear in active feeding")
    }

    @Test
    fun `sleep stop action is end sleep`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(functionBody.contains("\"End sleep\""), "sleep stop action must be 'End sleep'")
        assertFalse(functionBody.contains("\"Stop Sleep\""), "stale 'Stop Sleep' must not appear in showSleepActive")
    }

    @Test
    fun `no stale notification copy remains in source`() {
        val source = notificationHelperSource()

        assertFalse(source.contains("\"Stop Session\""), "stale 'Stop Session' label found")
        assertFalse(source.contains("\"Keep Going\""), "stale 'Keep Going' label found")
        assertFalse(source.contains("\"Stop Sleep\""), "stale 'Stop Sleep' label found")
        assertFalse(source.contains("\"Dismiss\""), "stale 'Dismiss' label found")
        assertFalse(source.contains("min limit reached"), "stale 'min limit reached' body copy found")
    }

    @Test
    fun `switch side rich notification hides custom progress row in both collapsed and expanded views`() {
        val source = notificationHelperSource()
        val showSwitchSideBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)
            ?.value
            ?: error("showSwitchSide body not found")

        assertEquals(2, Regex("showProgress = false").findAll(showSwitchSideBody).count())
    }

    @Test
    fun `sleep progress drawable file exists`() {
        val file = listOf(
            java.io.File("src/main/res/drawable/notification_progress_sleep.xml"),
            java.io.File("app/src/main/res/drawable/notification_progress_sleep.xml")
        ).firstOrNull { it.exists() }
        assertTrue(file != null, "notification_progress_sleep.xml must exist in drawable/")
    }

    @Test
    fun `all three collapsed notification layout files exist`() {
        fun layoutExists(name: String): Boolean =
            listOf(
                java.io.File("src/main/res/layout/$name"),
                java.io.File("app/src/main/res/layout/$name")
            ).any { it.exists() }

        assertTrue(layoutExists("notification_collapsed_feeding.xml"), "feeding collapsed layout missing")
        assertTrue(layoutExists("notification_collapsed_warning.xml"), "warning collapsed layout missing")
        assertTrue(layoutExists("notification_collapsed_sleep.xml"), "sleep collapsed layout missing")
    }

    @Test
    fun `buildCollapsedView function exists in NotificationHelper`() {
        val source = notificationHelperSource()
        assertTrue(
            source.contains("fun buildCollapsedView("),
            "buildCollapsedView must be defined in NotificationHelper"
        )
    }

    // Extracts the first setCustomContentView(...) call block (up to 500 chars) from a
    // function body slice so assertions are scoped to the collapsed view only,
    // not the setCustomBigContentView call that follows.
    private fun extractCollapsedViewArgs(functionBody: String): String {
        val start = functionBody.indexOf("setCustomContentView(")
        require(start >= 0) { "setCustomContentView( not found in slice" }
        return functionBody.substring(start, minOf(start + 750, functionBody.length))
    }

    @Test
    fun `showSwitchSide collapsed view uses feeding layout at 100 percent`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")
        val collapsedCall = extractCollapsedViewArgs(functionBody)

        assertTrue(collapsedCall.contains("notification_collapsed_feeding"), "wrong layout for switch-side collapsed view")
        assertTrue(collapsedCall.contains("progress = 1"), "switch-side collapsed progress must be 1 (100%)")
        assertTrue(collapsedCall.contains("maxProgress = 1"), "switch-side collapsed maxProgress must be 1")
        assertTrue(collapsedCall.contains("showProgress = false"), "switch-side collapsed progress bar must be hidden")
    }

    @Test
    fun `showFeedingLimit collapsed view uses warning layout at 100 percent`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")
        val collapsedCall = extractCollapsedViewArgs(functionBody)

        assertTrue(collapsedCall.contains("notification_collapsed_warning"), "wrong layout for feeding-limit collapsed view")
        assertTrue(collapsedCall.contains("progress = maxTotalMinutes"), "feeding-limit progress must equal maxTotalMinutes")
        assertTrue(collapsedCall.contains("maxProgress = maxTotalMinutes"), "feeding-limit maxProgress must equal maxTotalMinutes")
        assertTrue(collapsedCall.contains("showProgress = true"), "feeding-limit collapsed progress bar must be visible")
    }

    @Test
    fun `showBreastfeedingActive collapsed view uses feeding layout with dynamic progress`() {
        val source = notificationHelperSource()
        val functionBody = Regex("applyBreastfeedingActiveRichContent[\\s\\S]*?private fun breastfeedingActiveProgress")
            .find(source)?.value ?: error("applyBreastfeedingActiveRichContent body not found")
        val collapsedCall = extractCollapsedViewArgs(functionBody)

        assertTrue(collapsedCall.contains("notification_collapsed_feeding"), "wrong layout for active-feeding collapsed view")
        assertTrue(collapsedCall.contains("progress = progress.current"), "active-feeding progress must be progress.current")
        assertTrue(collapsedCall.contains("maxProgress = progress.max"), "active-feeding maxProgress must be progress.max")
        assertTrue(collapsedCall.contains("showProgress = progress.isEnabled"), "active-feeding progress visibility must follow progress.isEnabled")
    }

    @Test
    fun `active breastfeeding collapsed view passes chronometer base for live timer`() {
        val source = notificationHelperSource()
        val functionBody = Regex("applyBreastfeedingActiveRichContent[\\s\\S]*?private fun breastfeedingActiveProgress")
            .find(source)?.value ?: error("applyBreastfeedingActiveRichContent body not found")
        val collapsedCall = extractCollapsedViewArgs(functionBody)

        assertTrue(collapsedCall.contains("chronometerBaseElapsedMs"), "collapsed view must receive chronometerBaseElapsedMs for live timer")
    }

    @Test
    fun `feeding collapsed layout includes chronometer view for live timer`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("notification_collapsed_timer"),
            "notification_collapsed_feeding.xml must contain a Chronometer with id notification_collapsed_timer"
        )
        assertTrue(
            file.contains("notification_title_prefix"),
            "notification_collapsed_feeding.xml must contain a prefix TextView with id notification_title_prefix"
        )
    }

    @Test
    fun `feeding collapsed chronometer has minimum width to prevent layout jumps`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("android:minWidth=\"52dp\""),
            "notification_collapsed_timer must declare android:minWidth=\"52dp\" — " +
                "without it the chronometer collapses to zero as seconds tick from narrow (7s) to wide (1m 00s) " +
                "and the adjacent title TextView jumps"
        )
    }

    @Test
    fun `showSleepActive collapsed view uses sleep layout with hidden progress`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")
        val collapsedCall = extractCollapsedViewArgs(functionBody)

        assertTrue(collapsedCall.contains("notification_collapsed_sleep"), "wrong layout for sleep collapsed view")
        assertTrue(collapsedCall.contains("progress = 0"), "sleep collapsed progress must be 0")
        assertTrue(collapsedCall.contains("maxProgress = 1"), "sleep collapsed maxProgress must be 1")
        assertTrue(collapsedCall.contains("showProgress = false"), "sleep collapsed progress bar must be hidden")
    }

    @Test
    fun `showSleepActive uses setOngoing true to prevent accidental dismissal`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(
            functionBody.contains("setOngoing(true)"),
            "showSleepActive must use setOngoing(true) — active sleep session must not be swipe-dismissible"
        )
    }

    @Test
    fun `showSleepActive rich path uses DecoratedCustomViewStyle not BigTextStyle`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertFalse(
            functionBody.contains("BigTextStyle"),
            "showSleepActive must not use BigTextStyle — it overrides setCustomContentView, breaking the rich layout"
        )
        assertTrue(
            functionBody.contains("DecoratedCustomViewStyle"),
            "showSleepActive rich path must use DecoratedCustomViewStyle so setCustomContentView renders"
        )
    }

    // --- color token regression tests ---

    @Test
    fun `notification_on_surface_variant is warm muted grey not neutral grey`() {
        val file = listOf(
            java.io.File("src/main/res/values/colors.xml"),
            java.io.File("app/src/main/res/values/colors.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("name=\"notification_on_surface_variant\">#6D6A64"),
            "notification_on_surface_variant must be #6D6A64 (Akachan muted-grey token) — " +
                "#757575 is a neutral grey that ignores the warm surface tint"
        )
    }

    // --- collapsed ProgressBar height regression tests ---

    private fun collapsedLayoutProgressBarHeight(name: String): String {
        val file = listOf(
            java.io.File("src/main/res/layout/$name"),
            java.io.File("app/src/main/res/layout/$name")
        ).first { it.exists() }.readText()
        return Regex("<ProgressBar[\\s\\S]*?/>")
            .find(file)?.value ?: error("ProgressBar not found in $name")
    }

    @Test
    fun `collapsed feeding progress bar height is 4dp`() {
        val block = collapsedLayoutProgressBarHeight("notification_collapsed_feeding.xml")
        assertTrue(
            block.contains("android:layout_height=\"4dp\""),
            "collapsed feeding ProgressBar must be 4dp tall — 2dp is too thin to read at a glance"
        )
    }

    @Test
    fun `collapsed warning progress bar height is 4dp`() {
        val block = collapsedLayoutProgressBarHeight("notification_collapsed_warning.xml")
        assertTrue(
            block.contains("android:layout_height=\"4dp\""),
            "collapsed warning ProgressBar must be 4dp tall — 2dp is too thin to read at a glance"
        )
    }

    @Test
    fun `collapsed sleep progress bar height is 4dp`() {
        val block = collapsedLayoutProgressBarHeight("notification_collapsed_sleep.xml")
        assertTrue(
            block.contains("android:layout_height=\"4dp\""),
            "collapsed sleep ProgressBar must be 4dp tall — 2dp is too thin to read at a glance"
        )
    }

    // --- polish regression tests ---

    @Test
    fun `switch side body uses capitalized side label for the target breast`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertFalse(
            functionBody.contains("\"right\"") || functionBody.contains("\"left\""),
            "otherSide variable must be capitalized ('Right'/'Left') to match sideLabel casing in the same body string"
        )
        assertTrue(
            functionBody.contains("\"Right\"") && functionBody.contains("\"Left\""),
            "otherSide must produce 'Right' for LEFT current side and 'Left' for RIGHT current side"
        )
    }

    @Test
    fun `switch side primary action uses sentence case`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(
            functionBody.contains("\"Switch now\""),
            "switch-side primary action must be 'Switch now' (sentence case) — 'Switch Now' (title case) is inconsistent with all other action labels"
        )
        assertFalse(
            functionBody.contains("\"Switch Now\""),
            "stale title-case 'Switch Now' must not appear in showSwitchSide"
        )
    }

    @Test
    fun `feeding limit body capitalises Continue to match action button label`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")
        val body = Regex("""val body = "(.+?)"""")
            .findAll(functionBody).first().groupValues[1]

        assertTrue(
            body.contains("Continue"),
            "body must use capital-C 'Continue' to match the 'Continue' action button label: got '$body'"
        )
        assertFalse(
            body.contains("continue"),
            "lowercase 'continue' in body mismatches the 'Continue' action label and creates visual inconsistency: got '$body'"
        )
    }

    @Test
    fun `feeding expanded chronometer uses feeding accent color not surface color`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_breastfeeding_progress.xml"),
            java.io.File("app/src/main/res/layout/notification_breastfeeding_progress.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("notification_feeding_accent"),
            "feeding expanded Chronometer must use @color/notification_feeding_accent — " +
                "sleep expanded uses notification_sleep_accent; both timers should follow the same semantic color pattern"
        )
        assertFalse(
            file.contains("notification_timer") && file.contains("notification_on_surface") &&
                !file.contains("notification_feeding_accent"),
            "feeding expanded Chronometer must not fall back to notification_on_surface — that neutral color breaks the semantic pairing with sleep"
        )
    }

    private fun collapsedBodyTextSize(name: String): String {
        val content = listOf(
            java.io.File("src/main/res/layout/$name"),
            java.io.File("app/src/main/res/layout/$name")
        ).first { it.exists() }.readText()
        val bodyBlock = Regex("<TextView[\\s\\S]*?notification_body[\\s\\S]*?/>")
            .find(content)?.value ?: error("notification_body TextView not found in $name")
        return bodyBlock
    }

    @Test
    fun `collapsed feeding layout body text is 12sp`() {
        val block = collapsedBodyTextSize("notification_collapsed_feeding.xml")
        assertTrue(
            block.contains("android:textSize=\"12sp\""),
            "notification_collapsed_feeding body must be 12sp (labelSmall token) — 11.5sp is non-standard and not in the type scale"
        )
        assertFalse(block.contains("11.5sp"), "non-standard 11.5sp must not appear in collapsed feeding layout")
    }

    @Test
    fun `collapsed warning layout body text is 12sp`() {
        val block = collapsedBodyTextSize("notification_collapsed_warning.xml")
        assertTrue(
            block.contains("android:textSize=\"12sp\""),
            "notification_collapsed_warning body must be 12sp (labelSmall token) — 11.5sp is non-standard and not in the type scale"
        )
        assertFalse(block.contains("11.5sp"), "non-standard 11.5sp must not appear in collapsed warning layout")
    }

    @Test
    fun `collapsed sleep layout body text is 12sp`() {
        val block = collapsedBodyTextSize("notification_collapsed_sleep.xml")
        assertTrue(
            block.contains("android:textSize=\"12sp\""),
            "notification_collapsed_sleep body must be 12sp (labelSmall token) — 11.5sp is non-standard and not in the type scale"
        )
        assertFalse(block.contains("11.5sp"), "non-standard 11.5sp must not appear in collapsed sleep layout")
    }
}
