package com.example.shoppingassisstantv2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteParsingTest {

    private val amazonHtml = """
        <html><head><title>Buy Phone XL</title></head><body>
          <span id="productTitle">  Phone XL 128 GB  </span>
          <div id="corePriceDisplay_desktop_feature_div">
            <span class="a-price a-text-price"><span class="a-offscreen">₹89,900</span></span>
            <span class="a-price priceToPay apex-pricetopay-value" data-a-color="base">
              <span class="a-offscreen"> </span>
              <span aria-hidden="true">
                <span class="a-price-symbol">₹</span><span class="a-price-whole">76,999</span>
              </span>
            </span>
          </div>
          <div id="availability"><span class="a-color-success">In stock</span></div>
        </body></html>
    """.trimIndent()

    @Test
    fun amazonReadsSellingPriceAndStock() {
        val snapshot = AmazonInSite.parse(amazonHtml, "https://www.amazon.in/dp/B0DGJHBX5Y")
        assertEquals("Phone XL 128 GB", snapshot.title)
        assertEquals(76999.0, snapshot.price!!, 0.01)
        assertEquals(true, snapshot.inStock)
    }

    @Test
    fun amazonReadsOutOfStock() {
        val html = """
            <span id="productTitle">Old Phone</span>
            <div id="availability"><span>Currently unavailable.</span></div>
            <div id="outOfStock"></div>
        """.trimIndent()
        val snapshot = AmazonInSite.parse(html, "https://www.amazon.in/dp/B000000000")
        assertNull(snapshot.price)
        assertEquals(false, snapshot.inStock)
    }

    @Test
    fun amazonDetectsCaptchaPage() {
        val html = "<html><head><title>Robot Check</title></head><body>" +
            "<form action=\"/errors/validateCaptcha\"></form></body></html>"
        assertThrows(CheckException::class.java) {
            AmazonInSite.parse(html, "https://www.amazon.in/dp/B000000000")
        }
    }

    @Test
    fun flipkartReadsEmbeddedOffer() {
        val html = """
            <html><head><title>TRIGGR Trinity 2</title></head><body>
              <h1>TRIGGR Trinity 2 with Dual Pairing<div>more</div></h1>
              <script id="is_script">
                window.__INITIAL_STATE__ = {"product":{"name":"TRIGGR Trinity 2",
                "offers":{"price":699,"priceCurrency":"INR",
                "availability":"https://schema.org/InStock"}}};
              </script>
            </body></html>
        """.trimIndent()
        val snapshot = FlipkartSite.parse(
            html,
            "https://www.flipkart.com/triggr-trinity-2/p/itm7069ab0b0c2fc?pid=ACCH8Q2VF8FB3BVZ",
        )
        assertEquals("TRIGGR Trinity 2 with Dual Pairing", snapshot.title)
        assertEquals(699.0, snapshot.price!!, 0.01)
        assertEquals(true, snapshot.inStock)
    }

    @Test
    fun flipkartHandlesEscapedAvailabilityAndOutOfStock() {
        val html = """
            <h1>Some Headphones</h1>
            <script id="is_script">
              {"offers":{"price":1299,"availability":"https:\u002f\u002fschema.org\u002fOutOfStock"}}
            </script>
        """.trimIndent()
        val snapshot = FlipkartSite.parse(html, "https://www.flipkart.com/some-headphones/p/itm123")
        assertEquals(1299.0, snapshot.price!!, 0.01)
        assertEquals(false, snapshot.inStock)
    }

    @Test
    fun priceParsingHandlesIndianGrouping() {
        assertEquals(129999.0, parsePrice("₹1,29,999.00")!!, 0.01)
        assertEquals(699.0, parsePrice("₹699")!!, 0.01)
        assertNull(parsePrice("Currently unavailable"))
        assertNull(parseTargetPrice("0"))
        assertEquals(1299.5, parseTargetPrice("₹1,299.50")!!, 0.01)
    }

    @Test
    fun priceFormattingUsesRupees() {
        val text = 129999.0.asPrice()
        assertTrue(text.startsWith("₹"))
        assertEquals("129999", text.removePrefix("₹").replace(",", ""))
        assertEquals("1299", 1299.0.toPriceInput())
        assertTrue(0.0.asPrice().startsWith("₹"))
    }

    @Test
    fun flipkartOutOfStockFallback() {
        val html = "<html><body><h1>Sold Out Thing</h1><div>Sold Out</div></body></html>"
        val snapshot = FlipkartSite.parse(html, "https://www.flipkart.com/sold-out/p/itm123")
        assertEquals(false, snapshot.inStock)
        assertFalse(snapshot.inStock!!)
    }
}
