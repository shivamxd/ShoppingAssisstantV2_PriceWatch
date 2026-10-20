package com.example.shoppingassisstantv2.data

/** A product the user watches. The last* fields hold the most recent successfully parsed values. */
data class Product(
    val id: Long,
    val url: String,
    val title: String,
    val targetPrice: Double?,
    val watchStock: Boolean,
    val createdAt: Long,
    val lastPrice: Double?,
    val lastInStock: Boolean?,
    val lastCheckedAt: Long,
    val lastError: String?,
    val priceAlerted: Boolean,
)

/** What a site adapter was able to read from a product page. Null means "not found". */
data class Snapshot(val title: String?, val price: Double?, val inStock: Boolean?)

/** One stored check. Stock-only checks have no price. */
data class PricePoint(val at: Long, val price: Double?, val inStock: Boolean?)

class CheckException(message: String) : Exception(message)

class DuplicateProductException : Exception("This product is already on your watchlist.")
