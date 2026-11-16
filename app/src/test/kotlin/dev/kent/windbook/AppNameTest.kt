package dev.kent.windbook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppNameTest {
    private fun context(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }

    @Test @Config(qualifiers = "en") fun appIsCalledUpdraft() = assertEquals("Updraft", context("en").getString(R.string.app_name))

    @Test fun noVisibleStringStillUsesTheOldName() {
        for (language in listOf("en", "vi")) {
            val resources = context(language).resources
            val offenders = R.string::class.java.fields.filter { field ->
                runCatching { resources.getString(field.getInt(null)) }.getOrNull()?.contains("WindBook") == true
            }.map { it.name }
            assertEquals("Strings in $language still say WindBook", emptyList<String>(), offenders)
        }
    }
}
