package dev.kent.windbook.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun emptyTagsMeanSystem() = assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
    @Test fun vietnameseWithRegion() = assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromTags("vi-VN"))
    @Test fun english() = assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-US"))
    @Test fun unsupportedFallsBackToSystem() = assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("fr"))
}
