package com.example.shoppingassisstantv2.data

import java.net.URI

interface ProductSite {
    val label: String
    val initial: String

    fun matches(host: String): Boolean

    fun parse(html: String, url: String): Extraction
}

/**
 * Add a new store by implementing ProductSite and adding it to [all]; the rest of the app
 * (storage, checks, UI badges, validation) picks it up from there.
 */
object ProductSites {
    val all: List<ProductSite> = listOf(AmazonInSite, FlipkartSite)
    val supportedLabels: String = all.joinToString(" or ") { it.label }

    fun forHost(host: String): ProductSite? = all.firstOrNull { it.matches(host) }

    fun forUrl(raw: String): ProductSite? =
        runCatching { URI(raw.trim()).host?.lowercase() }.getOrNull()?.let(::forHost)

    fun labelFor(url: String): String = forUrl(url)?.label ?: "Product"
}

/** Returns a canonical https URL, or null when the link is not a valid supported product URL. */
fun normalizeProductUrl(raw: String): String? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    if (ProductSites.forHost(host) == null) return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return buildString {
        append("https://").append(host).append(port)
        uri.rawPath?.let { append(it) }
        uri.rawQuery?.let { append('?').append(it) }
    }
}

private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

fun firstUrlIn(text: String): String? =
    URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')

data class SharedLink(val url: String?, val title: String, val receivedAt: Long)

/** Browsers usually share "Product title https://product-url"; pull both pieces apart. */
fun parseSharedText(text: String?, now: Long = System.currentTimeMillis()): SharedLink? {
    val body = text?.trim().orEmpty()
    if (body.isEmpty()) return null
    val url = firstUrlIn(body)
    val title = (if (url != null) body.replace(url, " ") else body)
        .trim()
        .trim('-', '–', '—', '|', ':', '.', '·')
        .trim()
        .replace(Regex("""\s+"""), " ")
        .take(200)
    return SharedLink(url = url, title = title, receivedAt = now)
}
