package dev.kent.windbook.reader

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderSeekFractionTest {
    private fun at(n: Int)="""{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":$n}}"""
    @Test fun visiblePositionsShareTheSeekEndpoints() {
        assertEquals(0.0,LocatorCodec.seekFraction(at(1),10,.1),0.0)
        assertEquals(1.0,LocatorCodec.seekFraction(at(10),10,1.0),0.0)
        assertEquals(.5,LocatorCodec.seekFraction(at(5),9,.6),0.0)
        assertEquals(0.0,LocatorCodec.seekFraction(at(1),1,1.0),0.0)
    }
    @Test fun invalidOrUnavailableLocatorKeepsFiniteBoundedFallback() {
        assertEquals(.3,LocatorCodec.seekFraction("bad",0,.3),0.0)
        assertEquals(0.0,LocatorCodec.seekFraction(null,0,Double.NaN),0.0)
        assertEquals(1.0,LocatorCodec.seekFraction(at(300),10,2.0),0.0)
    }
}
