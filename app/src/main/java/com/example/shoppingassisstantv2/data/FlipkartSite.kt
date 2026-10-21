package com.example.shoppingassisstantv2.data

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FlipkartSite : ProductSite {
    override val label = "Flipkart"
    override val initial = "F"

    override fun matches(host: String) = host == "flipkart.com" || host.endsWith(".flipkart.com")

    private data class StockEvidence(
        val inStock: Boolean,
        val source: String,
        val confidence: Float,
    )

    // Flipkart renders the product's schema.org offer into its page state.
    private val offerPrice = Regex("\"offers\"\\s*:\\s*\\{\\s*\"price\"\\s*:\\s*([\\d.]+)")
    private val availability = Regex("\"availability\"\\s*:\\s*\"[^\"]*?(InStock|OutOfStock)\"")
    private val jsonLdPrice = Regex("\"price\"\\s*:\\s*\"?([\\d.]+)\"?")
    private val productSku = Regex("\"sku\"\\s*:\\s*\"([^\"]+)\"")

    override fun parse(html: String, url: String): Extraction {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Access Denied", ignoreCase = true) ||
            doc.body()?.text()?.contains("Access Denied", ignoreCase = true) == true
        ) {
            throw CheckException(
                "Flipkart blocked the request, so the check will retry later.",
                CheckFailureType.Blocked,
            )
        }

        val state = doc.selectFirst("script#is_script")?.data() ?: html
        val urlId = pidFromUrl(url)
        val pageId = normalizeId(productSku.find(state)?.groupValues?.get(1))
        if (urlId != null && pageId != null && urlId != pageId) {
            throw CheckException(
                "That page is for a different Flipkart product ($pageId, expected $urlId).",
                CheckFailureType.ProductMismatch,
            )
        }

        val priceEvidence = buildList {
            offerPrice.find(state)?.groupValues?.get(1)?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }
                ?.let { add(PriceEvidence(it, "initial-state", 0.9f)) }
            doc.select("script[type=application/ld+json]").firstNotNullOfOrNull { script ->
                jsonLdPrice.find(script.data())?.groupValues?.get(1)?.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
            }?.let { add(PriceEvidence(it, "json-ld", 0.8f)) }
            parsePrice(doc.selectFirst("div.Nx9bqj")?.text())
                ?.let { add(PriceEvidence(it, "price-class", 0.6f)) }
            parsePrice(doc.selectFirst("div._30jeq3")?.text())
                ?.let { add(PriceEvidence(it, "price-class-old", 0.55f)) }
            parsePrice(doc.selectFirst("meta[itemprop=price]")?.attr("content"))
                ?.let { add(PriceEvidence(it, "meta-price", 0.5f)) }
        }
        val price = choosePrice(priceEvidence)

        val stock = when (availability.find(state)?.groupValues?.get(1)) {
            "InStock" -> StockEvidence(true, "initial-state", 0.9f)
            "OutOfStock" -> StockEvidence(false, "initial-state", 0.9f)
            else -> fallbackStock(doc)?.let { StockEvidence(it, "sold-out-text", 0.6f) }
        }

        if (price == null && stock == null) {
            throw CheckException(
                "Couldn't find a reliable price or stock status on that Flipkart page.",
                CheckFailureType.NoData,
            )
        }

        return Extraction(
            title = title(doc)?.take(300),
            price = price?.price,
            inStock = stock?.inStock,
            priceConfidence = price?.confidence ?: 0f,
            stockConfidence = stock?.confidence ?: 0f,
            strategy = listOfNotNull(price?.source, stock?.source).distinct().joinToString("+"),
            productId = pageId ?: urlId,
        )
    }

    private fun title(doc: Document): String? {
        val heading = doc.selectFirst("h1")
        if (heading != null) {
            val copy = heading.clone()
            copy.select("div").remove()
            copy.text().trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" Price in India")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun fallbackStock(doc: Document): Boolean? {
        val text = doc.body()?.text().orEmpty()
        return when {
            text.contains("Sold out", ignoreCase = true) -> false
            text.contains("Currently out of stock", ignoreCase = true) -> false
            else -> null
        }
    }

    private fun pidFromUrl(url: String): String? =
        runCatching { URI(url).query }.getOrNull()
            ?.split('&')
            ?.firstOrNull { it.startsWith("pid=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let(::normalizeId)

    private fun normalizeId(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}
