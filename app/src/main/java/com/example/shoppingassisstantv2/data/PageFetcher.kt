package com.example.shoppingassisstantv2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream

object PageFetcher {
    private const val MAX_CHARS = 4_000_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            instanceFollowRedirects = true
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
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.close()
                throw CheckException(
                    when (code) {
                        404 -> "Product page not found (HTTP 404)."
                        429 -> "The site is limiting requests. It will retry on the next hourly check."
                        else -> "The site returned HTTP $code. It will retry later."
                    },
                )
            }
            val stream = if (connection.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8192)
                while (out.length < MAX_CHARS) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    out.append(buffer, 0, minOf(read, MAX_CHARS - out.length))
                }
                out.toString()
            }
        } catch (e: CheckException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw CheckException("The site took too long to respond. It will retry later.")
        } catch (e: UnknownHostException) {
            throw CheckException("Couldn't reach the site. Check your internet connection.")
        } catch (e: IOException) {
            throw CheckException("Couldn't load the page. It will retry later.")
        } finally {
            connection.disconnect()
        }
    }
}
