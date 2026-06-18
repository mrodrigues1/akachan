package com.babytracker.ui.settings

import androidx.core.os.LocaleListCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLocaleTest {
    @Test
    fun `SYSTEM maps to empty locale list`() {
        assertTrue(AppLocale.SYSTEM.toLocaleList().isEmpty)
    }

    @Test
    fun `ENGLISH maps to en`() {
        assertEquals("en", AppLocale.ENGLISH.toLocaleList().toLanguageTags())
    }

    @Test
    fun `PORTUGUESE_BR maps to pt-BR`() {
        assertEquals("pt-BR", AppLocale.PORTUGUESE_BR.toLocaleList().toLanguageTags())
    }

    @Test
    fun `fromLocaleList round-trips a selected tag`() {
        val list = LocaleListCompat.forLanguageTags("pt-BR")
        assertEquals(AppLocale.PORTUGUESE_BR, AppLocale.fromLocaleList(list))
    }

    @Test
    fun `fromLocaleList empty maps to SYSTEM`() {
        assertEquals(AppLocale.SYSTEM, AppLocale.fromLocaleList(LocaleListCompat.getEmptyLocaleList()))
    }
}
