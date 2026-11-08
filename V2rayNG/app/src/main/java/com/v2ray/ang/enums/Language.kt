package com.v2ray.ang.enums

import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Languages shipped with Sahand VPN. The set is deliberately small: Persian, English,
 * Simplified/Traditional Chinese and Russian. Everything else falls back to English so the
 * interface never mixes a half-translated locale with the simple screen.
 */
enum class Language(val code: String, val languageTag: String) {
    AUTO("auto", ""),
    PERSIAN("fa", "fa"),
    ENGLISH("en", "en"),
    CHINA("zh-rCN", "zh-CN"),
    TRADITIONAL_CHINESE("zh-rTW", "zh-TW"),
    RUSSIAN("ru", "ru");

    val locale: Locale?
        get() = languageTag.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)

    fun toLocaleList(): LocaleListCompat =
        if (languageTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: AUTO
        }

        fun fromLanguageTag(languageTag: String?): Language {
            if (languageTag.isNullOrBlank()) return AUTO

            val locale = Locale.forLanguageTag(languageTag)
            return when (locale.language) {
                "en" -> ENGLISH
                "fa" -> PERSIAN
                "ru" -> RUSSIAN
                "zh" -> if (
                    locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
                ) {
                    TRADITIONAL_CHINESE
                } else {
                    CHINA
                }
                // Unsupported phone languages stay on English instead of a half-translated locale.
                else -> ENGLISH
            }
        }
    }
}
