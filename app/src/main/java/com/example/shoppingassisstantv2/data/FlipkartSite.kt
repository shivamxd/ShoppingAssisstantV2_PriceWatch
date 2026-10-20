package com.example.shoppingassisstantv2.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FlipkartSite : ProductSite {
    override val label = "Flipkart"
    override val initial = "F"

    override fun matches(host: String) = host == "flipkart.com" || host.endsWith(".flipkart.com")

    // Flipkart renders the product's schema.org offer into its page state.
    private val offerPrice = Regex("\"offers\"\\s*:\\s*\\{\\s*\"price\"\\s*:\\s*([\\d.]+)")
    private val availability = Regex("\"availability\"\\s*:\\s*\"[^\"]*?(InStock|OutOfStock)\"")

    override fun parse(html: String, url: String): Snapshot {
        val doc = Jsoup.parse(html, url)
        if (doc.title().contains("Access Denied", ignoreCase = true)) {
            throw CheckException("Flipkart blocked the request, so it will retry on the next hourly check.")
        }

        val state = doc.selectFirst("script#is_script")?.data() ?: html
        val price = offerPrice.find(state)?.groupValues?.get(1)?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: parsePrice(doc.selectFirst("div.Nx9bqj")?.text())
            ?: parsePrice(doc.selectFirst("div._30jeq3")?.text())
            ?: parsePrice(doc.selectFirst("meta[itemprop=price]")?.attr("content"))

        val inStock = when (availability.find(state)?.groupValues?.get(1)) {
            "InStock" -> true
            "OutOfStock" -> false
            else -> fallbackStock(doc)
        }

        if (price == null && inStock == null) {
            throw CheckException("Couldn't find a price or stock status on that Flipkart page.")
        }
        return Snapshot(title = title(doc)?.take(300), price = price, inStock = inStock)
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
}
