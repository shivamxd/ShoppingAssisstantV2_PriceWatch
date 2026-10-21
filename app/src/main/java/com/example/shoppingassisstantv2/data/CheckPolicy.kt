package com.example.shoppingassisstantv2.data

const val MAX_AUTO_RETRIES = 3

private const val MIN_RETRY_DELAY_MS = 5 * 60_000L
private const val MAX_RETRY_DELAY_MS = 6 * 60 * 60_000L
private const val STUCK_AFTER_MS = 24 * 60 * 60_000L
private const val MIN_FAILURES_FOR_STUCK = 3
private const val MAX_JITTER_MS = 30_000L

/** Delay before the next targeted retry, including Retry-After and a small jitter. */
fun retryDelayMillis(
    failureCount: Int,
    retryAfterMs: Long?,
    jitterMs: Long = 0L,
): Long {
    val base = retryAfterMs?.coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
        ?: when {
            failureCount <= 1 -> 15 * 60_000L
            failureCount == 2 -> 30 * 60_000L
            else -> 60 * 60_000L
        }
    return (base + jitterMs.coerceIn(0L, MAX_JITTER_MS))
        .coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
}

/**
 * Notify once when a product has had at least three failed checks and no success for a day.
 * A brand-new product uses its creation time as the baseline.
 */
fun shouldNotifyStuck(
    lastSuccessAt: Long,
    createdAt: Long,
    now: Long,
    failureCount: Int,
    alreadyNotified: Boolean,
    staleAfterMs: Long = STUCK_AFTER_MS,
): Boolean {
    if (alreadyNotified || failureCount < MIN_FAILURES_FOR_STUCK) return false
    val baseline = if (lastSuccessAt > 0L) lastSuccessAt else createdAt
    if (baseline <= 0L) return false
    return now - baseline >= staleAfterMs
}

/** Wait needed before a request, keeping a minimum gap per host plus jitter. */
fun pacingDelayMillis(
    lastRequestAt: Long,
    now: Long,
    minIntervalMs: Long = 4_000L,
    jitterMs: Long = 0L,
): Long = (lastRequestAt + minIntervalMs - now).coerceAtLeast(0L) +
    jitterMs.coerceAtLeast(0L)
