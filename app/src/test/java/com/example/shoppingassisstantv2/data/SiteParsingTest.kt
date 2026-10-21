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
          <input id="ASIN" value="B0DGJHBX5Y" />
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
        val result = AmazonInSite.parse(
            amazonHtml,
            "https://www.amazon.in/dp/B0DGJHBX5Y",
        )
        assertEquals("Phone XL 128 GB", result.title)
        assertEquals(76999.0, result.trustedPrice!!, 0.01)
        assertTrue(result.priceConfidence >= 0.9f)
        assertEquals(true, result.trustedStock)
        assertTrue(result.stockConfidence >= 0.9f)
        assertEquals("B0DGJHBX5Y", result.productId)
        assertTrue(result.strategy.contains("priceToPay"))
        assertTrue(result.strategy.contains("availability"))
    }

    @Test
    fun amazonAgreeingSourcesRaiseConfidence() {
        val html = """
            <html><body>
              <span id="productTitle">Phone XL</span>
              <input id="ASIN" value="B0DGJHBX5Y" />
              <div id="corePriceDisplay_desktop_feature_div">
                <span class="a-price priceToPay" data-a-color="base">
                  <span aria-hidden="true">₹76,999</span>
                </span>
              </div>
              <span id="tp_price_block_total_price_ww" class="a-price">
                <span class="a-offscreen">₹76,999.00</span>
              </span>
              <div id="availability"><span>In stock</span></div>
            </body></html>
        """.trimIndent()
        val result = AmazonInSite.parse(html, "https://www.amazon.in/dp/B0DGJHBX5Y")
        assertEquals(76999.0, result.trustedPrice!!, 0.01)
        assertTrue(result.priceConfidence >= 0.99f)
    }

    @Test
    fun amazonShortLinkUsesThePageIdentity() {
        val result = AmazonInSite.parse(
            amazonHtml,
            "https://amzn.in/d/0i1AnnLZ",
        )
        assertEquals("B0DGJHBX5Y", result.productId)
        assertEquals(76999.0, result.trustedPrice!!, 0.01)
        assertEquals(true, result.trustedStock)
    }

    @Test
    fun amazonRejectsAPageForAnotherProduct() {
        val html = amazonHtml.replace("value=\"B0DGJHBX5Y\"", "value=\"B000000000\"")
        val error = assertThrows(CheckException::class.java) {
            AmazonInSite.parse(html, "https://www.amazon.in/dp/B0DGJHBX5Y")
        }
        assertEquals(CheckFailureType.ProductMismatch, error.type)
    }

    @Test
    fun amazonReadsOutOfStock() {
        val html = """
            <span id="productTitle">Old Phone</span>
            <div id="availability"><span>Currently unavailable.</span></div>
            <div id="outOfStock"></div>
        """.trimIndent()
        val result = AmazonInSite.parse(html, "https://www.amazon.in/dp/B000000000")
        assertNull(result.price)
        assertEquals(false, result.trustedStock)
        assertNull(result.trustedPrice)
    }

    @Test
    fun amazonDetectsCaptchaPage() {
        val html = "<html><head><title>Robot Check</title></head><body>" +
            "<form action=\"/errors/validateCaptcha\"></form></body></html>"
        val error = assertThrows(CheckException::class.java) {
            AmazonInSite.parse(html, "https://www.amazon.in/dp/B000000000")
        }
        assertEquals(CheckFailureType.Blocked, error.type)
    }

    @Test
    fun amazonReportsNoData() {
        val error = assertThrows(CheckException::class.java) {
            AmazonInSite.parse("<html><body>Something changed</body></html>", "https://www.amazon.in/dp/B000000000")
        }
        assertEquals(CheckFailureType.NoData, error.type)
    }

    @Test
    fun flipkartReadsEmbeddedOffer() {
        val html = """
            <html><head><title>TRIGGR Trinity 2</title></head><body>
              <h1>TRIGGR Trinity 2 with Dual Pairing<div>more</div></h1>
              <script id="is_script">
                window.__INITIAL_STATE__ = {"product":{"name":"TRIGGR Trinity 2",
                "sku":"ACCH8Q2VF8FB3BVZ",
                "offers":{"price":699,"priceCurrency":"INR",
                "availability":"https://schema.org/InStock"}}};
              </script>
            </body></html>
        """.trimIndent()
        val result = FlipkartSite.parse(
            html,
            "https://www.flipkart.com/triggr-trinity-2/p/itm7069ab0b0c2fc?pid=ACCH8Q2VF8FB3BVZ",
        )
        assertEquals("TRIGGR Trinity 2 with Dual Pairing", result.title)
        assertEquals(699.0, result.trustedPrice!!, 0.01)
        assertEquals(true, result.trustedStock)
        assertEquals("ACCH8Q2VF8FB3BVZ", result.productId)
        assertTrue(result.strategy.contains("initial-state"))
        assertTrue(result.priceConfidence >= 0.9f)
    }

    @Test
    fun flipkartRejectsAPageForAnotherProduct() {
        val html = """
            <h1>Some Headphones</h1>
            <script id="is_script">
              {"product":{"sku":"DIFFERENTSKU","offers":{"price":699,
              "availability":"https://schema.org/InStock"}}}
            </script>
        """.trimIndent()
        val error = assertThrows(CheckException::class.java) {
            FlipkartSite.parse(
                html,
                "https://www.flipkart.com/some-headphones/p/itm123?pid=ACCH8Q2VF8FB3BVZ",
            )
        }
        assertEquals(CheckFailureType.ProductMismatch, error.type)
    }

    @Test
    fun flipkartHandlesEscapedAvailabilityAndOutOfStock() {
        val html = """
            <h1>Some Headphones</h1>
            <script id="is_script">
              {"offers":{"price":1299,"availability":"https:\u002f\u002fschema.org\u002fOutOfStock"}}
            </script>
        """.trimIndent()
        val result = FlipkartSite.parse(html, "https://www.flipkart.com/some-headphones/p/itm123")
        assertEquals(1299.0, result.trustedPrice!!, 0.01)
        assertEquals(false, result.trustedStock)
    }

    @Test
    fun flipkartFallsBackToDomWithLowerConfidence() {
        val html = """
            <html><body>
              <h1>Sold Out Thing</h1>
              <div class="Nx9bqj">₹599</div>
              <div>Sold Out</div>
            </body></html>
        """.trimIndent()
        val result = FlipkartSite.parse(html, "https://www.flipkart.com/sold-out/p/itm123")
        assertEquals(599.0, result.trustedPrice!!, 0.01)
        assertEquals(false, result.trustedStock)
        assertEquals(0.6f, result.priceConfidence, 0.001f)
        assertEquals(0.6f, result.stockConfidence, 0.001f)
    }

    @Test
    fun flipkartReportsNoData() {
        val error = assertThrows(CheckException::class.java) {
            FlipkartSite.parse(
                "<html><body><h1>Something changed</h1></body></html>",
                "https://www.flipkart.com/x/p/itm123",
            )
        }
        assertEquals(CheckFailureType.NoData, error.type)
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
        val result = FlipkartSite.parse(html, "https://www.flipkart.com/sold-out/p/itm123")
        assertEquals(false, result.trustedStock)
        assertFalse(result.trustedStock!!)
    }
}
