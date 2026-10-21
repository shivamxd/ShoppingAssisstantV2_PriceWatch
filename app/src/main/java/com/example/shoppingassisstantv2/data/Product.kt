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
    val failureCount: Int = 0,
    val lastSuccessAt: Long = 0L,
    val lastErrorType: String? = null,
    val lastStrategy: String? = null,
    val lastConfidence: Float? = null,
    val failureNotified: Boolean = false,
)

/**
 * The result of one page extraction. Price and stock carry separate confidence because one
 * can be read reliably while the other is missing.
 */
data class Extraction(
    val title: String? = null,
    val price: Double? = null,
    val inStock: Boolean? = null,
    val priceConfidence: Float = 0f,
    val stockConfidence: Float = 0f,
    val strategy: String = "",
    val productId: String? = null,
) {
    val trustedPrice: Double?
        get() = price?.takeIf {
            it.isFinite() && it > 0.0 && priceConfidence >= MIN_CONFIDENCE
        }

    val trustedStock: Boolean?
        get() = inStock?.takeIf { stockConfidence >= MIN_CONFIDENCE }

    val confidence: Float
        get() = when {
            trustedPrice != null && trustedStock != null ->
                minOf(priceConfidence, stockConfidence)
            trustedPrice != null -> priceConfidence
            trustedStock != null -> stockConfidence
            else -> 0f
        }

    companion object {
        const val MIN_CONFIDENCE = 0.6f
    }
}

/** One stored check. Stock-only checks have no price. */
data class PricePoint(val at: Long, val price: Double?, val inStock: Boolean?)

/** Failure categories that decide retry and circuit-breaker behaviour. */
enum class CheckFailureType(val label: String, val retryable: Boolean) {
    Network("Network error", true),
    Timeout("Request timed out", true),
    RateLimited("Site is rate-limiting checks", true),
    ServerError("Site server error", true),
    HttpError("Page request failed", false),
    Blocked("Blocked by the site", false),
    NotFound("Product page not found", false),
    NoData("No reliable price or stock found", false),
    ProductMismatch("Page is for a different product", false),
    Unsupported("Unsupported product link", false),
    Unknown("Unexpected error", false),
}

class CheckException(
    message: String,
    val type: CheckFailureType = CheckFailureType.Unknown,
    val retryAfterMs: Long? = null,
) : Exception(message)

class DuplicateProductException : Exception("This product is already on your watchlist.")

fun failureTypeFromStored(value: String?): CheckFailureType? =
    value?.let { stored -> CheckFailureType.entries.firstOrNull { it.name == stored } }
