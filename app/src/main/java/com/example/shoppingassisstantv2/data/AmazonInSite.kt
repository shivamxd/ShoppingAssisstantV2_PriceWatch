package com.example.shoppingassisstantv2.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AmazonInSite : ProductSite {
    override val label = "Amazon.in"
    override val initial = "A"

    override fun matches(host: String) = host == "amazon.in" || host.endsWith(".amazon.in")

    private val priceSelectors = listOf(
        "#corePriceDisplay_desktop_feature_div .priceToPay",
        "#corePrice_feature_div .priceToPay",
        "#corePriceDisplay_desktop_feature_div .a-price[data-a-color=base] .a-offscreen",
        "#corePrice_feature_div .a-price[data-a-color=base] .a-offscreen",
        "#priceblock_ourprice",
        "#priceblock_dealprice",
        "#priceblock_saleprice",
        "#tp_price_block_total_price_ww .a-offscreen",
        "#price_inside_buybox",
        "#newBuyBoxPrice",
    )

    override fun parse(html: String, url: String): Snapshot {
        val doc = Jsoup.parse(html, url)
        if (isCaptcha(doc)) {
            throw CheckException("Amazon is showing a robot check, so it will retry on the next hourly check.")
        }

        val title = doc.selectFirst("#productTitle")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() }

        val price = priceSelectors.firstNotNullOfOrNull { selector ->
            parsePrice(doc.selectFirst(selector)?.text())
        }

        val availability = doc.selectFirst("#availability")?.text()?.lowercase()
        val inStock = when {
            availability != null && "in stock" in availability -> true
            availability != null &&
                ("out of stock" in availability || "unavailable" in availability) -> false
            doc.selectFirst("#outOfStock") != null -> false
            else -> null
        }

        if (price == null && inStock == null) {
            throw CheckException("Couldn't find a price or stock status on that Amazon page.")
        }
        return Snapshot(title = title?.take(300), price = price, inStock = inStock)
    }

    private fun isCaptcha(doc: Document): Boolean =
        doc.title().contains("Robot Check", ignoreCase = true) ||
            doc.selectFirst("form[action*=validateCaptcha]") != null ||
            doc.selectFirst("img[src*=captcha]") != null
}
