package com.example.shoppingassisstantv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {

    @Test
    fun normalizesSupportedUrlsToHttps() {
        assertEquals(
            "https://www.amazon.in/dp/B0DGJHBX5Y?tag=abc",
            normalizeProductUrl("http://www.amazon.in/dp/B0DGJHBX5Y?tag=abc#reviews"),
        )
        assertEquals(
            "https://www.flipkart.com/triggr/p/itm123?pid=A1",
            normalizeProductUrl("HTTPS://WWW.FLIPKART.COM/triggr/p/itm123?pid=A1"),
        )
        assertEquals(
            "https://amzn.in/d/0i1AnnLZ",
            normalizeProductUrl("http://amzn.in/d/0i1AnnLZ"),
        )
    }

    @Test
    fun rejectsOtherSitesAndInvalidLinks() {
        assertNull(normalizeProductUrl("https://example.com/product"))
        assertNull(normalizeProductUrl("https://amazon.in.evil.example/dp/B000000000"))
        assertNull(normalizeProductUrl("https://amzn.in.evil.example/d/0i1AnnLZ"))
        assertNull(normalizeProductUrl("https://notamzn.in/d/0i1AnnLZ"))
        assertNull(normalizeProductUrl("not a link"))
        assertNull(normalizeProductUrl("ftp://www.amazon.in/dp/B000000000"))
    }

    @Test
    fun findsProductSite() {
        assertEquals(AmazonInSite, ProductSites.forUrl("https://www.amazon.in/dp/X"))
        assertEquals(AmazonInSite, ProductSites.forUrl("https://amzn.in/d/0i1AnnLZ"))
        assertEquals(FlipkartSite, ProductSites.forUrl("https://flipkart.com/x/p/itm1"))
        assertNull(ProductSites.forUrl("https://www.myntra.com/x"))
    }

    @Test
    fun splitsTitleFromSharedText() {
        val shared = parseSharedText(
            "TRIGGR Trinity 2 headphones https://www.flipkart.com/triggr/p/itm123?pid=A1",
            now = 123L,
        )
        assertNotNull(shared)
        assertEquals(
            "https://www.flipkart.com/triggr/p/itm123?pid=A1",
            shared!!.url,
        )
        assertEquals("TRIGGR Trinity 2 headphones", shared.title)
        assertEquals(123L, shared.receivedAt)
    }

    @Test
    fun shareWithoutUrlStillCarriesTitle() {
        val shared = parseSharedText("Just some text")
        assertNull(shared!!.url)
        assertEquals("Just some text", shared.title)
        assertNull(parseSharedText("   "))
    }

    @Test
    fun urlIsTrimmedOfTrailingPunctuation() {
        assertEquals(
            "https://www.amazon.in/dp/B0DGJHBX5Y",
            firstUrlIn("Check this out: https://www.amazon.in/dp/B0DGJHBX5Y."),
        )
    }
}
