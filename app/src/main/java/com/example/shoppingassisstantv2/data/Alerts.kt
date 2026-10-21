package com.example.shoppingassisstantv2.data

/** Decides whether a check should alert, and what the next stored priceAlerted flag should be. */
data class AlertDecision(
    val notify: Boolean,
    val priceHit: Boolean,
    val restocked: Boolean,
    val nextPriceAlerted: Boolean,
)

fun alertDecision(product: Product, extraction: Extraction): AlertDecision {
    val price = extraction.trustedPrice
    val inStock = extraction.trustedStock
    val target = product.targetPrice
    val priceHit = price != null && target != null && price <= target
    val restocked = product.watchStock &&
        product.lastInStock == false &&
        inStock == true
    return AlertDecision(
        notify = restocked || (priceHit && !product.priceAlerted),
        priceHit = priceHit,
        restocked = restocked,
        nextPriceAlerted = when {
            price == null -> product.priceAlerted
            priceHit -> true
            else -> false
        },
    )
}
