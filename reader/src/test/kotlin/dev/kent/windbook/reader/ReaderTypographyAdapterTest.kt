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
}
