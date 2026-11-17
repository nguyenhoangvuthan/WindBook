package dev.kent.windbook.reader

import dev.kent.windbook.core.model.ReaderTypography
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderTypographyAdapterTest {
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    @Test fun percentageUsesReadiumMultiplierAndPublisherResetClearsOverride() {
        assertEquals(1.5,typographyPreferences(ReaderTypography(size=150),false).fontSize!!,0.0001)
        assertEquals(.8,typographyPreferences(ReaderTypography(size=80),false).fontSize!!,0.0001)
        assertNull(typographyPreferences(ReaderTypography(),false).fontSize)
    }

    @Test fun cleanLayoutIsTheDefaultAndSetsAppSpacing() {
        val prefs=typographyPreferences(ReaderTypography(),false)
        assertEquals(false,prefs.publisherStyles)
        assertEquals(org.readium.r2.navigator.preferences.TextAlign.JUSTIFY,prefs.textAlign)
        assertEquals(1.5,prefs.lineHeight!!,0.0001)
        assertEquals(.5,prefs.paragraphSpacing!!,0.0001)
        assertEquals(0.0,prefs.paragraphIndent!!,0.0001)
        assertEquals(true,prefs.hyphens)
    }
    @Test fun explicitLineHeightWinsInCleanLayoutAndZeroFallsBackToTheAppDefault() {
        assertEquals(1.8,typographyPreferences(ReaderTypography(lineHeight=1.8),false).lineHeight!!,0.0001)
        assertEquals(1.5,typographyPreferences(ReaderTypography(lineHeight=0.0),false).lineHeight!!,0.0001)
    }
    @Test fun originalLayoutLeavesPublisherStylesAlone() {
        val prefs=typographyPreferences(ReaderTypography(originalLayout=true),false)
        assertNull(prefs.publisherStyles)
        assertNull(prefs.textAlign)
        assertNull(prefs.lineHeight)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.paragraphIndent)
        assertNull(prefs.hyphens)
        assertNull(typographyPreferences(ReaderTypography(originalLayout=true),false).fontSize)
    }
}
