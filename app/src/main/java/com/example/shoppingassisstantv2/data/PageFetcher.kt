package com.example.shoppingassisstantv2.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PageFetcher {
    private const val MAX_CHARS = 4_000_000
    private const val MAX_REDIRECTS = 3
    private const val MIN_HOST_INTERVAL_MS = 4_000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    private val pacerMutex = Mutex()
    private val lastRequestAt = mutableMapOf<String, Long>()

    suspend fun fetch(url: String, site: ProductSite): String = withContext(Dispatchers.IO) {
        try {
            fetchFollowingRedirects(url, site)
        } catch (e: CheckException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw CheckException(
                "The site took too long to respond. It will retry automatically.",
                CheckFailureType.Timeout,
            )
        } catch (e: UnknownHostException) {
            throw CheckException(
                "Couldn't reach the site. Check your internet connection.",
                CheckFailureType.Network,
            )
        } catch (e: IOException) {
            throw CheckException(
                "Couldn't load the page. It will retry automatically.",
                CheckFailureType.Network,
            )
        } catch (e: IllegalArgumentException) {
            throw CheckException("That product URL is not valid.", CheckFailureType.HttpError)
        }
    }

    private suspend fun fetchFollowingRedirects(url: String, site: ProductSite): String {
        var currentUrl = url
        var redirectCount = 0
        while (true) {
            val host = URI(currentUrl).host?.lowercase()
                ?: throw CheckException("That product URL is not valid.", CheckFailureType.HttpError)
            pace(host)

            val connection = openConnection(currentUrl)
            try {
                val code = connection.responseCode
                when {
                    code in 200..299 -> return readBody(connection)
                    code in 300..399 -> {
                        if (redirectCount >= MAX_REDIRECTS) {
                            throw CheckException(
                                "The product page redirected too many times.",
                                CheckFailureType.HttpError,
                            )
                        }
                        val next = resolveRedirect(
                            currentUrl,
                            connection.getHeaderField("Location"),
                            site,
                        ) ?: throw CheckException(
                            "The product page redirected somewhere unexpected.",
                            CheckFailureType.HttpError,
                        )
                        currentUrl = next
                        redirectCount++
                    }
                    code == 429 -> throw CheckException(
                        "The site is limiting checks. It will retry shortly.",
                        CheckFailureType.RateLimited,
                        parseRetryAfterMillis(
                            connection.getHeaderField("Retry-After"),
                            System.currentTimeMillis(),
                        ),
                    )
                    code == 403 -> throw CheckException(
                        "The site blocked automated checks. It will retry later.",
                        CheckFailureType.Blocked,
                    )
                    code == 404 || code == 410 -> throw CheckException(
                        "Product page not found (HTTP $code).",
                        CheckFailureType.NotFound,
                    )
                    code in 500..599 -> throw CheckException(
                        "The site returned HTTP $code. It will retry automatically.",
                        CheckFailureType.ServerError,
                        parseRetryAfterMillis(
                            connection.getHeaderField("Retry-After"),
                            System.currentTimeMillis(),
                        ),
                    )
                    else -> throw CheckException(
                        "The site returned HTTP $code.",
                        CheckFailureType.HttpError,
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = if (connection.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(connection.inputStream)
        } else {
            connection.inputStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(8192)
            while (out.length < MAX_CHARS) {
                val read = reader.read(buffer)
                if (read < 0) break
                out.append(buffer, 0, minOf(read, MAX_CHARS - out.length))
            }
            out.toString()
        }
    }

    private suspend fun pace(host: String) = pacerMutex.withLock {
        val waitMs = pacingDelayMillis(
            lastRequestAt = lastRequestAt[host] ?: 0L,
            now = System.currentTimeMillis(),
            minIntervalMs = MIN_HOST_INTERVAL_MS,
            jitterMs = Random.nextLong(750L, 2_000L),
        )
        if (waitMs > 0L) delay(waitMs)
        lastRequestAt[host] = System.currentTimeMillis()
    }
}

/** Resolves a redirect and only accepts an https target belonging to the same store. */
internal fun resolveRedirect(currentUrl: String, location: String?, site: ProductSite): String? {
    if (location.isNullOrBlank()) return null
    val target = runCatching { URI(currentUrl).resolve(location.trim()) }.getOrNull()
        ?: return null
    if (target.scheme?.lowercase() != "https") return null
    if (target.userInfo != null) return null
    val targetSite = ProductSites.forUrl(target.toString()) ?: return null
    return target.toString().takeIf { targetSite === site }
}

/** Parses both forms of Retry-After: delta seconds and an HTTP date. */
internal fun parseRetryAfterMillis(value: String?, now: Long): Long? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toLongOrNull()?.let { return (it * 1000L).coerceAtLeast(0L) }
    val instant = runCatching {
        ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: return null
    return (instant - now).coerceAtLeast(0L)
}
