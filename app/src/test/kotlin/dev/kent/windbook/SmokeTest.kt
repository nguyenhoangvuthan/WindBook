package dev.kent.windbook

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SmokeTest {
    @Test
    fun appNameIsUpdraft() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Updraft", context.getString(R.string.app_name))
    }
}
