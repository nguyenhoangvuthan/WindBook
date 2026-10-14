package dev.kent.windbook.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.kent.windbook.R

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    VIETNAMESE("vi", R.string.language_vi),
    ENGLISH("en", R.string.language_en);

    companion object {
        /** Maps AppCompat's stored language tags ("vi-VN", "en", "") to a choice; unknown → SYSTEM. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').substringBefore('-').lowercase()
            return entries.firstOrNull { it != SYSTEM && it.tag == first } ?: SYSTEM
        }
    }
}

interface AppLocaleController {
    fun current(): AppLanguage
    fun set(language: AppLanguage)
}

/** Per-app language via AppCompat; on Android 12 it is persisted by AppLocalesMetadataHolderService. */
class AppCompatLocaleController : AppLocaleController {
    override fun current(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    override fun set(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language.tag),
        )
    }
}
