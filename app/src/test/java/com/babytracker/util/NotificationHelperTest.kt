package com.babytracker.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
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
}
