package dev.kent.windbook.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressionTest {
    @Test fun lastPageOfShortPdfIsComplete() =
        // Readium's totalProgression for page 50/50 is (50-1)/50 = 0.98, which never reaches "finished".
        assertEquals(1.0, readingProgression(position = 50, positionCount = 50, totalProgression = 0.98), 1e-9)

    @Test fun singlePageDocumentIsComplete() =
        assertEquals(1.0, readingProgression(position = 1, positionCount = 1, totalProgression = 0.0), 1e-9)

    @Test fun middlePositionIsProportional() =
        assertEquals(0.5, readingProgression(position = 10, positionCount = 20, totalProgression = 0.45), 1e-9)

    @Test fun unknownPositionFallsBackToTotalProgression() =
        assertEquals(0.37, readingProgression(position = null, positionCount = 20, totalProgression = 0.37), 1e-9)

    @Test fun unknownEverythingIsZero() =
        assertEquals(0.0, readingProgression(position = null, positionCount = 0, totalProgression = null), 1e-9)
}
