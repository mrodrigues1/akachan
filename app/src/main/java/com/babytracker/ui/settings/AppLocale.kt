package com.babytracker.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.babytracker.R

/**
 * In-app language options. [SYSTEM] follows the device language (empty locale list);
 * the others force a specific tag via [AppCompatDelegate.setApplicationLocales], which is
 * backported to minSdk 26 by the AppCompat library.
 */
enum class AppLocale(@StringRes val labelRes: Int, val tag: String?) {
    SYSTEM(R.string.settings_language_system, null),
    ENGLISH(R.string.settings_language_english, "en"),
    PORTUGUESE_BR(R.string.settings_language_pt_br, "pt-BR"),
    ;

    fun toLocaleList(): LocaleListCompat =
        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)

    companion object {
        fun fromLocaleList(list: LocaleListCompat): AppLocale {
            if (list.isEmpty) return SYSTEM
            val tag = list.toLanguageTags()
            return entries.firstOrNull { it.tag != null && it.tag.equals(tag, ignoreCase = true) }
                ?: entries.firstOrNull {
                    it.tag != null && tag.startsWith(it.tag.substringBefore('-'), ignoreCase = true)
                }
                ?: SYSTEM
        }

        fun current(): AppLocale = fromLocaleList(AppCompatDelegate.getApplicationLocales())

        fun apply(locale: AppLocale) {
            AppCompatDelegate.setApplicationLocales(locale.toLocaleList())
        }
    }
}
