package com.example.shoppingassisstantv2.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPolicyTest {

    @Test
    fun retryDelaysBackOffAndRespectRetryAfter() {
        assertEquals(15 * 60_000L, retryDelayMillis(failureCount = 1, retryAfterMs = null))
        assertEquals(30 * 60_000L, retryDelayMillis(failureCount = 2, retryAfterMs = null))
        assertEquals(60 * 60_000L, retryDelayMillis(failureCount = 3, retryAfterMs = null))
        assertEquals(600_000L, retryDelayMillis(1, retryAfterMs = 600_000L))
        assertEquals(300_000L, retryDelayMillis(1, retryAfterMs = 10_000L))
        assertEquals(6 * 60 * 60_000L, retryDelayMillis(1, retryAfterMs = 24 * 60 * 60_000L))
        assertEquals(
            15 * 60_000L + 20_000L,
            retryDelayMillis(1, retryAfterMs = null, jitterMs = 20_000L),
        )
    }

    @Test
    fun stuckNotificationNeedsFailuresAndADayWithoutSuccess() {
        val now = 200_000_000L
        val day = 24 * 60 * 60_000L
        assertTrue(
            shouldNotifyStuck(
                lastSuccessAt = now - day,
                createdAt = now - 2 * day,
                now = now,
                failureCount = 3,
                alreadyNotified = false,
            ),
        )
        assertFalse(
            shouldNotifyStuck(
                lastSuccessAt = now - day + 1,
                createdAt = now - 2 * day,
                now = now,
                failureCount = 3,
                alreadyNotified = false,
            ),
        )
        assertFalse(
            shouldNotifyStuck(
                lastSuccessAt = now - day,
                createdAt = now - 2 * day,
                now = now,
                failureCount = 2,
                alreadyNotified = false,
            ),
        )
        assertFalse(
            shouldNotifyStuck(
                lastSuccessAt = now - day,
                createdAt = now - 2 * day,
                now = now,
                failureCount = 3,
                alreadyNotified = true,
            ),
        )
        assertTrue(
            shouldNotifyStuck(
                lastSuccessAt = 0L,
                createdAt = now - day,
                now = now,
                failureCount = 3,
                alreadyNotified = false,
            ),
        )
    }

    @Test
    fun circuitOpensAfterRepeatedBlocks() {
        val now = 1_000L
        assertFalse(CircuitPolicy.isOpen(CircuitPolicy.afterBlocked(CircuitState(), now), now))
        val twoBlocked = CircuitPolicy.afterBlocked(
            CircuitPolicy.afterBlocked(CircuitState(), now),
            now,
        )
        assertFalse(CircuitPolicy.isOpen(twoBlocked, now))
        val open = CircuitPolicy.afterBlocked(twoBlocked, now)
        assertTrue(CircuitPolicy.isOpen(open, now))
        assertEquals(now + CircuitPolicy.COOLDOWN_MS, open.openUntil)
        assertFalse(CircuitPolicy.isOpen(open, now + CircuitPolicy.COOLDOWN_MS + 1))
    }

    @Test
    fun rateLimitPausesTheSiteAndExtendsAnExistingPause() {
        val now = 1_000L
        val paused = CircuitPolicy.afterRateLimited(CircuitState(), now, 120_000L)
        assertTrue(CircuitPolicy.isOpen(paused, now))
        assertEquals(now + 120_000L, paused.openUntil)

        val clamped = CircuitPolicy.afterRateLimited(CircuitState(), now, 10_000L)
        assertEquals(now + 60_000L, clamped.openUntil)

        val existing = CircuitState(openUntil = now + 500_000L)
        val extended = CircuitPolicy.afterRateLimited(existing, now, 60_000L)
        assertEquals(now + 500_000L, extended.openUntil)
    }

    @Test
    fun pacingKeepsAHostGapAndAddsJitter() {
        assertEquals(1_000L, pacingDelayMillis(lastRequestAt = 0L, now = 10_000L, jitterMs = 1_000L))
        assertEquals(
            3_500L,
            pacingDelayMillis(lastRequestAt = 2_000L, now = 3_000L, jitterMs = 500L),
        )
        assertEquals(500L, pacingDelayMillis(lastRequestAt = 10_000L, now = 20_000L, jitterMs = 500L))
    }

    @Test
    fun retryAfterParsesSecondsAndHttpDates() {
        assertEquals(120_000L, parseRetryAfterMillis("120", now = 0L))
        assertNull(parseRetryAfterMillis("not-a-date", now = 0L))
        assertNull(parseRetryAfterMillis(null, now = 0L))

        val date = "Wed, 21 Oct 2026 07:28:00 GMT"
        val dateMillis = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        assertEquals(60_000L, parseRetryAfterMillis(date, now = dateMillis - 60_000L))
        assertEquals(0L, parseRetryAfterMillis(date, now = dateMillis + 1_000L))
    }

    @Test
    fun redirectsMustStayOnTheSameHttpsSite() {
        assertEquals(
            "https://www.amazon.in/dp/B000000000",
            resolveRedirect(
                "https://www.amazon.in/dp/B0DGJHBX5Y",
                "/dp/B000000000",
                AmazonInSite,
            ),
        )
        assertEquals(
            "https://www.amazon.in/dp/B0D424GGGV?ref=share",
            resolveRedirect(
                "https://amzn.in/d/0i1AnnLZ",
                "https://www.amazon.in/dp/B0D424GGGV?ref=share",
                AmazonInSite,
            ),
        )
        assertNull(
            resolveRedirect(
                "https://www.amazon.in/dp/B0DGJHBX5Y",
                "https://www.flipkart.com/x/p/itm1",
                AmazonInSite,
            ),
        )
        assertNull(
            resolveRedirect(
                "https://www.amazon.in/dp/B0DGJHBX5Y",
                "http://www.amazon.in/dp/B000000000",
                AmazonInSite,
            ),
        )
        assertNull(
            resolveRedirect(
                "https://www.amazon.in/dp/B0DGJHBX5Y",
                "https://user@www.amazon.in/dp/B000000000",
                AmazonInSite,
            ),
        )
        assertNull(resolveRedirect("https://www.amazon.in/dp/B0DGJHBX5Y", null, AmazonInSite))
    }

    @Test
    fun storedFailureTypesRoundTrip() {
        assertEquals(CheckFailureType.Blocked, failureTypeFromStored("Blocked"))
        assertNull(failureTypeFromStored("something-old"))
        assertNull(failureTypeFromStored(null))
    }
}
