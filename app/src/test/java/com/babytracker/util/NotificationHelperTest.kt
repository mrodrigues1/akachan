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

    private fun notificationStringsXml(): String =
        listOf(
            java.io.File("src/main/res/values/strings.xml"),
            java.io.File("app/src/main/res/values/strings.xml")
        ).first { it.exists() }.readText()

    private fun stringsXmlValue(strings: String, name: String): String =
        Regex("""name="$name"[^>]*>\s*([^<]+?)\s*<""")
            .find(strings)?.groupValues?.get(1) ?: error("String '$name' not found in strings.xml")

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
    fun `formatDurationCompact uses minute word consistently`() {
        assertEquals("2 min", invokePrivateStringMethod("formatDurationCompact", 120))
        assertEquals("2 min 5s", invokePrivateStringMethod("formatDurationCompact", 125))
        assertEquals("5s", invokePrivateStringMethod("formatDurationCompact", 5))
    }

    @Test
    fun `active notification refresh interval is thirty seconds`() {
        val field = NotificationHelper::class.java.getDeclaredField("ACTIVE_REFRESH_INTERVAL_MS")
        field.isAccessible = true

        assertEquals(30_000L, field.getLong(NotificationHelper))
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
        val strings = notificationStringsXml()

        assertTrue(source.contains("ACTION_PAUSE"))
        assertTrue(source.contains("ACTION_RESUME"))
        assertTrue(source.contains("notif_action_pause"))
        assertTrue(source.contains("notif_action_resume"))
        assertTrue(source.contains("notif_action_stop_feeding"))
        assertEquals("Pause", stringsXmlValue(strings, "notif_action_pause"))
        assertEquals("Resume", stringsXmlValue(strings, "notif_action_resume"))
        assertEquals("Stop feeding", stringsXmlValue(strings, "notif_action_stop_feeding"))
    }

    // --- copy regression tests ---

    @Test
    fun `feeding limit body uses word-joined duration and both-paths guidance`() {
        val strings = notificationStringsXml()
        val body = stringsXmlValue(strings, "notif_body_feeding_limit")

        assertTrue(body.contains(" min session"), "body must use word-joined duration: got '$body'")
        assertFalse(body.contains("-min session"), "body must match compact minute format without dash: got '$body'")
        assertTrue(body.contains("Tap Stop or Continue"), "body must state both paths with capital C on Continue: got '$body'")
        assertFalse(body.contains("limit reached"), "stale 'limit reached' copy must not appear: got '$body'")
    }

    @Test
    fun `feeding limit action labels are warm-functional`() {
        val source = notificationHelperSource()
        val strings = notificationStringsXml()
        val functionBody = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")

        assertTrue(functionBody.contains("notif_action_stop_feeding"), "feeding-limit stop action must reference R.string.notif_action_stop_feeding")
        assertTrue(functionBody.contains("notif_action_continue"), "feeding-limit keep-going action must reference R.string.notif_action_continue")
        assertEquals("Stop feeding", stringsXmlValue(strings, "notif_action_stop_feeding"), "notif_action_stop_feeding must be 'Stop feeding'")
        assertEquals("Continue", stringsXmlValue(strings, "notif_action_continue"), "notif_action_continue must be 'Continue'")
        assertFalse(functionBody.contains("\"Stop Session\""), "stale 'Stop Session' must not appear in showFeedingLimit")
        assertFalse(functionBody.contains("\"Keep Going\""), "stale 'Keep Going' must not appear in showFeedingLimit")
    }

    @Test
    fun `switch side dismiss action names the current-side choice`() {
        val source = notificationHelperSource()
        val strings = notificationStringsXml()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(
            functionBody.contains("notif_action_keep_current_side"),
            "switch-side dismiss must reference R.string.notif_action_keep_current_side"
        )
        assertEquals(
            "Keep current side",
            stringsXmlValue(strings, "notif_action_keep_current_side"),
            "notif_action_keep_current_side must be self-contained"
        )
        val staleAmbiguousLabel = ">Not" + " yet<"
        assertFalse(strings.contains(staleAmbiguousLabel), "stale ambiguous switch-side label must not appear in strings.xml")
        assertFalse(functionBody.contains("\"Dismiss\""), "stale 'Dismiss' must not appear in showSwitchSide")
    }

    @Test
    fun `active breastfeeding stop action is stop feeding`() {
        val source = notificationHelperSource()
        val strings = notificationStringsXml()
        val functionBody = Regex("applyBreastfeedingActiveRichContent[\\s\\S]*?private fun breastfeedingActiveProgress")
            .find(source)?.value ?: error("applyBreastfeedingActiveRichContent body not found")

        assertTrue(functionBody.contains("notif_action_stop_feeding"), "active feeding stop action must reference R.string.notif_action_stop_feeding")
        assertEquals("Stop feeding", stringsXmlValue(strings, "notif_action_stop_feeding"), "notif_action_stop_feeding must be 'Stop feeding'")
        assertFalse(functionBody.contains("\"Stop Session\""), "stale 'Stop Session' must not appear in active feeding")
    }

    @Test
    fun `sleep stop action is end sleep`() {
        val source = notificationHelperSource()
        val strings = notificationStringsXml()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(functionBody.contains("notif_action_end_sleep"), "sleep stop action must reference R.string.notif_action_end_sleep")
        assertEquals("End sleep", stringsXmlValue(strings, "notif_action_end_sleep"), "notif_action_end_sleep must be 'End sleep'")
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
    fun `collapsed notification layout files exist`() {
        fun layoutExists(name: String): Boolean =
            listOf(
                java.io.File("src/main/res/layout/$name"),
                java.io.File("app/src/main/res/layout/$name")
            ).any { it.exists() }

        assertTrue(layoutExists("notification_collapsed_feeding.xml"), "feeding collapsed layout missing")
        assertTrue(layoutExists("notification_collapsed_feeding_active.xml"), "active feeding collapsed layout missing")
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

        assertTrue(collapsedCall.contains("notification_collapsed_feeding_active"), "wrong layout for active-feeding collapsed view")
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
    fun `active feeding collapsed layout includes chronometer view for live timer`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding_active.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding_active.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("notification_collapsed_timer"),
            "notification_collapsed_feeding_active.xml must contain a Chronometer with id notification_collapsed_timer"
        )
        assertTrue(
            file.contains("notification_title_prefix"),
            "notification_collapsed_feeding_active.xml must contain a prefix TextView with id notification_title_prefix"
        )
    }

    @Test
    fun `simple feeding collapsed layout has no active-session timer views`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding.xml")
        ).first { it.exists() }.readText()

        assertFalse(file.contains("notification_collapsed_timer"), "simple feeding collapsed layout must not contain active timer")
        assertFalse(file.contains("notification_title_prefix"), "simple feeding collapsed layout must not contain active title prefix")
    }

    @Test
    fun `feeding collapsed chronometer has minimum width to prevent layout jumps`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding_active.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding_active.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("android:minWidth=\"52dp\""),
            "notification_collapsed_timer must declare android:minWidth=\"52dp\" — " +
                "without it the chronometer collapses to zero as seconds tick from narrow (7s) to wide (1m 00s) " +
                "and the adjacent title TextView jumps"
        )
    }

    @Test
    fun `feeding collapsed chronometer is width constrained on narrow notification rows`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding_active.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding_active.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("android:maxWidth=\"64dp\""),
            "notification_collapsed_timer must declare android:maxWidth=\"64dp\" so it cannot starve " +
                "the active title on narrow notification rows"
        )
        assertTrue(
            file.contains("android:maxLines=\"1\""),
            "notification_collapsed_timer must stay on one line in the collapsed notification title row"
        )
        assertTrue(
            file.contains("android:ellipsize=\"end\""),
            "notification_collapsed_timer must ellipsize instead of expanding past its constrained width"
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
    fun `collapsed active feeding progress bar height is 4dp`() {
        val block = collapsedLayoutProgressBarHeight("notification_collapsed_feeding_active.xml")
        assertTrue(
            block.contains("android:layout_height=\"4dp\""),
            "active collapsed feeding ProgressBar must be 4dp tall"
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
        val strings = notificationStringsXml()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(
            functionBody.contains("notif_side_right") && functionBody.contains("notif_side_left"),
            "otherSide and sideLabel must be looked up via R.string.notif_side_right / notif_side_left"
        )
        assertEquals("Right", stringsXmlValue(strings, "notif_side_right"), "notif_side_right must be capitalized 'Right'")
        assertEquals("Left", stringsXmlValue(strings, "notif_side_left"), "notif_side_left must be capitalized 'Left'")
    }

    @Test
    fun `switch side primary action uses sentence case`() {
        val source = notificationHelperSource()
        val strings = notificationStringsXml()
        val functionBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(
            functionBody.contains("notif_action_switch_now"),
            "switch-side primary action must reference R.string.notif_action_switch_now"
        )
        assertEquals(
            "Switch now",
            stringsXmlValue(strings, "notif_action_switch_now"),
            "notif_action_switch_now must be 'Switch now' (sentence case) — 'Switch Now' (title case) is inconsistent with all other action labels"
        )
        assertFalse(
            strings.contains(">Switch Now<"),
            "stale title-case 'Switch Now' must not appear in strings.xml"
        )
    }

    @Test
    fun `feeding limit body capitalises Continue to match action button label`() {
        val strings = notificationStringsXml()
        val body = stringsXmlValue(strings, "notif_body_feeding_limit")

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

    // --- lock screen privacy tests ---

    @Test
    fun `applyDesignSystem defaults to VISIBILITY_PRIVATE`() {
        val source = notificationHelperSource()
        assertTrue(
            source.contains("VISIBILITY_PRIVATE"),
            "applyDesignSystem must default to VISIBILITY_PRIVATE — session timing data must not appear verbatim on lock screen"
        )
        assertTrue(
            source.contains("setVisibility"),
            "applyDesignSystem must call setVisibility"
        )
    }

    @Test
    fun `no notification builder overrides visibility to PUBLIC`() {
        val source = notificationHelperSource()
        assertFalse(
            source.contains("VISIBILITY_PUBLIC"),
            "no show*() builder must set VISIBILITY_PUBLIC — feeding and sleep session data is sensitive"
        )
    }

    // --- refresh alarm quality tests ---

    @Test
    fun `scheduleBreastfeedingActiveRefresh guards exact alarm with canScheduleExactAlarms`() {
        val source = notificationHelperSource()
        val body = Regex("fun scheduleBreastfeedingActiveRefresh[\\s\\S]*?private fun sleepActionPi")
            .find(source)?.value ?: error("scheduleBreastfeedingActiveRefresh body not found")

        assertTrue(
            body.contains("canScheduleExactAlarms()"),
            "refresh alarm must check canScheduleExactAlarms() — on API 31+ exact alarms require explicit permission"
        )
        assertTrue(
            body.contains("setExactAndAllowWhileIdle"),
            "refresh alarm must use setExactAndAllowWhileIdle when exact alarms are permitted"
        )
        assertTrue(
            body.contains("setAndAllowWhileIdle"),
            "refresh alarm must fall back to setAndAllowWhileIdle when exact alarms are not available"
        )
    }

    // --- notification group key tests ---

    @Test
    fun `BREASTFEEDING_GROUP_KEY is fully qualified`() {
        assertEquals(
            "com.babytracker.notifications.breastfeeding",
            NotificationHelper.BREASTFEEDING_GROUP_KEY
        )
    }

    @Test
    fun `SLEEP_GROUP_KEY is fully qualified`() {
        assertEquals(
            "com.babytracker.notifications.sleep",
            NotificationHelper.SLEEP_GROUP_KEY
        )
    }

    @Test
    fun `breastfeeding group key and sleep group key are distinct`() {
        assertFalse(
            NotificationHelper.BREASTFEEDING_GROUP_KEY == NotificationHelper.SLEEP_GROUP_KEY,
            "group keys must differ so breastfeeding and sleep notifications do not merge into the same group"
        )
    }

    @Test
    fun `showSwitchSide calls setGroup with BREASTFEEDING_GROUP_KEY`() {
        val source = notificationHelperSource()
        val body = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")

        assertTrue(
            body.contains("setGroup(BREASTFEEDING_GROUP_KEY)"),
            "showSwitchSide must call setGroup(BREASTFEEDING_GROUP_KEY) so the OS groups it with other breastfeeding notifications"
        )
    }

    @Test
    fun `showFeedingLimit calls setGroup with BREASTFEEDING_GROUP_KEY`() {
        val source = notificationHelperSource()
        val body = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")

        assertTrue(
            body.contains("setGroup(BREASTFEEDING_GROUP_KEY)"),
            "showFeedingLimit must call setGroup(BREASTFEEDING_GROUP_KEY)"
        )
    }

    @Test
    fun `showBreastfeedingActive calls setGroup with BREASTFEEDING_GROUP_KEY`() {
        val source = notificationHelperSource()
        val body = Regex("fun showBreastfeedingActive[\\s\\S]*?private fun NotificationCompat")
            .find(source)?.value ?: error("showBreastfeedingActive body not found")

        assertTrue(
            body.contains("setGroup(BREASTFEEDING_GROUP_KEY)"),
            "showBreastfeedingActive must call setGroup(BREASTFEEDING_GROUP_KEY)"
        )
    }

    @Test
    fun `showSleepActive calls setGroup with SLEEP_GROUP_KEY`() {
        val source = notificationHelperSource()
        val body = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(
            body.contains("setGroup(SLEEP_GROUP_KEY)"),
            "showSleepActive must call setGroup(SLEEP_GROUP_KEY)"
        )
    }

    // --- polish regression tests (2025-05 audit) ---

    @Test
    fun `warning expanded layout has no dead Chronometer`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_warning_progress.xml"),
            java.io.File("app/src/main/res/layout/notification_warning_progress.xml")
        ).first { it.exists() }.readText()

        assertFalse(file.contains("<Chronometer"), "warning expanded layout must not keep an unused Chronometer")
    }

    @Test
    fun `sleep expanded layout has no dead progress views`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_sleep_expanded.xml"),
            java.io.File("app/src/main/res/layout/notification_sleep_expanded.xml")
        ).first { it.exists() }.readText()

        assertFalse(file.contains("<ProgressBar"), "sleep expanded layout must not keep an unused ProgressBar")
        assertFalse(file.contains("notification_progress_label"), "sleep expanded layout must not keep an unused progress label")
    }

    @Test
    fun `collapsed feeding layout has symmetric top padding`() {
        val file = listOf(
            java.io.File("src/main/res/layout/notification_collapsed_feeding.xml"),
            java.io.File("app/src/main/res/layout/notification_collapsed_feeding.xml")
        ).first { it.exists() }.readText()

        assertTrue(
            file.contains("android:paddingTop=\"2dp\""),
            "notification_collapsed_feeding.xml root must declare android:paddingTop=\"2dp\" — " +
                "without it the top edge is flush while bottom has 2dp, making the content visually off-center"
        )
    }

    @Test
    fun `showSleepActive sets priority default`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(
            functionBody.contains("PRIORITY_DEFAULT"),
            "showSleepActive must call setPriority(NotificationCompat.PRIORITY_DEFAULT) — " +
                "sleep is ambient state, not an actionable alert; PRIORITY_HIGH would elevate it above feeding alerts"
        )
    }

    @Test
    fun `showSleepActive sets category status`() {
        val source = notificationHelperSource()
        val functionBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(
            functionBody.contains("CATEGORY_STATUS"),
            "showSleepActive must call setCategory(NotificationCompat.CATEGORY_STATUS) — " +
                "CATEGORY_STATUS tells the OS this is ongoing informational state, affecting DND bypass and lock-screen grouping"
        )
    }

    @Test
    fun `all show functions call setTicker for accessibility`() {
        val source = notificationHelperSource()

        val switchSideBody = Regex("fun showSwitchSide[\\s\\S]*?fun showFeedingLimit")
            .find(source)?.value ?: error("showSwitchSide body not found")
        val feedingLimitBody = Regex("fun showFeedingLimit[\\s\\S]*?fun showBreastfeedingActive")
            .find(source)?.value ?: error("showFeedingLimit body not found")
        val bfActiveBody = Regex("fun showBreastfeedingActive[\\s\\S]*?private fun NotificationCompat")
            .find(source)?.value ?: error("showBreastfeedingActive body not found")
        val sleepActiveBody = Regex("fun showSleepActive[\\s\\S]*?fun cancelNotification")
            .find(source)?.value ?: error("showSleepActive body not found")

        assertTrue(
            switchSideBody.contains("setTicker("),
            "showSwitchSide must call setTicker() — status-bar marquee text and TalkBack use this on pre-API-21 and assistive tech"
        )
        assertTrue(
            feedingLimitBody.contains("setTicker("),
            "showFeedingLimit must call setTicker()"
        )
        assertTrue(
            bfActiveBody.contains("setTicker("),
            "showBreastfeedingActive must call setTicker()"
        )
        assertTrue(
            sleepActiveBody.contains("setTicker("),
            "showSleepActive must call setTicker()"
        )
    }

    // --- dark track color regression tests ---

    private fun nightColorsSource(): String =
        listOf(
            java.io.File("src/main/res/values-night/colors.xml"),
            java.io.File("app/src/main/res/values-night/colors.xml")
        ).first { it.exists() }.readText()

    @Test
    fun `dark feeding track maps to Pink900 design-system container token`() {
        assertTrue(
            nightColorsSource().contains("""name="notification_feeding_track">#880E4F"""),
            "dark notification_feeding_track must be #880E4F (Pink900 = dark primaryContainer) — " +
                "custom dark values like #4A2838 have no design-system traceability"
        )
    }

    @Test
    fun `dark warning track maps to Amber800 design-system container token`() {
        assertTrue(
            nightColorsSource().contains("""name="notification_warning_track">#7A4800"""),
            "dark notification_warning_track must be #7A4800 (Amber800 = dark warningContainer) — " +
                "matches WarningContainerAmberDark in Color.kt"
        )
    }

    @Test
    fun `dark sleep track maps to Blue900 design-system container token`() {
        assertTrue(
            nightColorsSource().contains("""name="notification_sleep_track">#0D47A1"""),
            "dark notification_sleep_track must be #0D47A1 (Blue900 = dark secondaryContainer) — " +
                "custom dark values like #0D2440 have no design-system traceability"
        )
    }
}
