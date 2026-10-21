package com.example.shoppingassisstantv2.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AmazonInSite : ProductSite {
    override val label = "Amazon.in"
    override val initial = "A"

    override fun matches(host: String) =
        host == "amazon.in" || host.endsWith(".amazon.in") ||
            host == "amzn.in" || host.endsWith(".amzn.in")

    private data class PriceSelector(
        val css: String,
        val source: String,
        val confidence: Float,
    )

    private data class StockEvidence(
        val inStock: Boolean,
        val source: String,
        val confidence: Float,
    )

    private val priceSelectors = listOf(
        PriceSelector(
            "#corePriceDisplay_desktop_feature_div .priceToPay",
            "priceToPay",
            0.95f,
        ),
        PriceSelector("#corePrice_feature_div .priceToPay", "priceToPay-mobile", 0.95f),
        PriceSelector(
            "#corePriceDisplay_desktop_feature_div .a-price[data-a-color=base] .a-offscreen",
            "core-price",
            0.9f,
        ),
        PriceSelector(
            "#corePrice_feature_div .a-price[data-a-color=base] .a-offscreen",
            "core-price-mobile",
            0.9f,
        ),
        PriceSelector("#priceblock_ourprice", "priceblock", 0.85f),
        PriceSelector("#priceblock_dealprice", "priceblock", 0.85f),
        PriceSelector("#priceblock_saleprice", "priceblock", 0.85f),
        PriceSelector("#tp_price_block_total_price_ww .a-offscreen", "cart-total", 0.7f),
        PriceSelector("#price_inside_buybox", "buybox", 0.8f),
        PriceSelector("#newBuyBoxPrice", "buybox", 0.8f),
    )

    private val asinInUrl = Regex(
        """/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})""",
        RegexOption.IGNORE_CASE,
    )
    private val asinFormat = Regex("""[A-Z0-9]{10}""")

    override fun parse(html: String, url: String): Extraction {
        val doc = Jsoup.parse(html, url)
        if (isCaptcha(doc)) {
            throw CheckException(
                "Amazon is showing a robot check, so the check will retry later.",
                CheckFailureType.Blocked,
            )
        }

        val urlAsin = asinFromUrl(url)
        val pageAsin = asinFromPage(doc)
        if (urlAsin != null && pageAsin != null && urlAsin != pageAsin) {
            throw CheckException(
                "That page is for a different Amazon product ($pageAsin, expected $urlAsin).",
                CheckFailureType.ProductMismatch,
            )
        }

        val title = doc.selectFirst("#productTitle")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() }

        val priceEvidence = priceSelectors.mapNotNull { selector ->
            parsePrice(doc.selectFirst(selector.css)?.text())?.let {
                PriceEvidence(it, selector.source, selector.confidence)
            }
        }
        val price = choosePrice(priceEvidence)

        val availability = doc.selectFirst("#availability")?.text()?.lowercase()
        val stock = when {
            availability != null &&
                ("unavailable" in availability || "out of stock" in availability) ->
                StockEvidence(false, "availability", 0.95f)
            availability != null && "in stock" in availability ->
                StockEvidence(true, "availability", 0.95f)
            doc.selectFirst("#outOfStock") != null ->
                StockEvidence(false, "out-of-stock", 0.8f)
            else -> null
        }

        if (price == null && stock == null) {
            throw CheckException(
                "Couldn't find a reliable price or stock status on that Amazon page.",
                CheckFailureType.NoData,
            )
        }

        return Extraction(
            title = title?.take(300),
            price = price?.price,
            inStock = stock?.inStock,
            priceConfidence = price?.confidence ?: 0f,
            stockConfidence = stock?.confidence ?: 0f,
            strategy = listOfNotNull(price?.source, stock?.source).distinct().joinToString("+"),
            productId = pageAsin ?: urlAsin,
        )
    }

    private fun isCaptcha(doc: Document): Boolean =
        doc.title().contains("Robot Check", ignoreCase = true) ||
            doc.selectFirst("form[action*=validateCaptcha]") != null ||
            doc.selectFirst("img[src*=captcha]") != null

    private fun asinFromUrl(url: String): String? =
        asinInUrl.find(url)?.groupValues?.get(1)?.uppercase()

    private fun asinFromPage(doc: Document): String? =
        normalizeAsin(doc.selectFirst("input#ASIN")?.attr("value"))
            ?: normalizeAsin(doc.selectFirst("input[name=ASIN]")?.attr("value"))
            ?: normalizeAsin(doc.selectFirst("#ASIN")?.text())
            ?: doc.selectFirst("link[rel=canonical]")?.attr("href")?.let(::asinFromUrl)

    private fun normalizeAsin(value: String?): String? {
        val normalized = value?.trim()?.uppercase() ?: return null
        return normalized.takeIf { it.matches(asinFormat) }
    }
}
