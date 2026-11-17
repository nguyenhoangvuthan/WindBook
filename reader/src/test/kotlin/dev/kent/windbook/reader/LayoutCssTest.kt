package dev.kent.windbook.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCssTest {
    private fun inject(html: String) = String(injectLayoutCss(html.toByteArray()), Charsets.UTF_8)

    @Test fun insertsTheStyleBeforeTheHeadCloses() {
        val out = inject("<html><head><title>t</title></head><body><p>x</p></body></html>")
        val style = out.indexOf("<style id=\"wb-layout\">")
        assertTrue(style in 0 until out.indexOf("</head>"))
    }

    @Test fun findsAnUppercaseHeadClose() {
        val out = inject("<HTML><HEAD></HEAD><BODY>x</BODY></HTML>")
        assertTrue(out.indexOf("wb-layout") in 0 until out.indexOf("</HEAD>"))
    }

    @Test fun withoutAHeadTheStyleGoesAfterTheBodyOpens() {
        val out = inject("<html><body class=\"x\"><p>x</p></body></html>")
        assertTrue(out.indexOf("wb-layout") > out.indexOf("<body class=\"x\">"))
        assertEquals(1, Regex("wb-layout").findAll(out).count())
    }

    @Test fun isIdempotent() {
        val once = inject("<html><head></head><body>x</body></html>")
        assertEquals(once, inject(once))
    }

    @Test fun leavesNonHtmlAndMarkupWithoutHeadOrBodyAlone() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(png, injectLayoutCss(png))
        assertEquals("plain text", inject("plain text"))
    }

    @Test fun vietnameseBodyBytesSurviveUntouched() {
        val body = "<p>Dế Mèn phiêu lưu ký — Tô Hoài ■ ��</p>"
        val out = inject("<html><head></head><body>$body</body></html>")
        assertTrue(out.endsWith("<body>$body</body></html>"))
    }

    @Test fun cssWrapsEverywhereAndGuardsCleanOnlyRulesWithReadiumsOwnSelector() {
        val css = layoutCss()
        assertTrue(css.contains("overflow-wrap:anywhere"))
        assertTrue(css.contains(":root[style*=readium-advanced-on] a"))
        assertTrue(css.contains(":root[style*=readium-advanced-on] p:has(>img:only-child)"))
        // The wrapping rule must not be limited to clean layout.
        assertTrue(css.substringBefore(":root[style*=readium-advanced-on]").contains("overflow-wrap:anywhere"))
    }
}
