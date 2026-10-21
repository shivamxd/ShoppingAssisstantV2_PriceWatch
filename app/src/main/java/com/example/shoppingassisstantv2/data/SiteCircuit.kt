package com.example.shoppingassisstantv2.data

import android.content.Context
import androidx.core.content.edit

data class CircuitState(
    val consecutiveBlocks: Int = 0,
    val openUntil: Long = 0L,
)

object CircuitPolicy {
    const val BLOCK_THRESHOLD = 3
    const val COOLDOWN_MS = 6 * 60 * 60_000L
    const val RATE_LIMIT_PAUSE_MS = 15 * 60_000L
    const val MAX_RATE_LIMIT_PAUSE_MS = 6 * 60 * 60_000L

    fun afterBlocked(
        state: CircuitState,
        now: Long,
        threshold: Int = BLOCK_THRESHOLD,
        cooldownMs: Long = COOLDOWN_MS,
    ): CircuitState {
        val blocks = state.consecutiveBlocks + 1
        val opensAt = if (blocks >= threshold) now + cooldownMs else 0L
        return state.copy(
            consecutiveBlocks = blocks,
            openUntil = maxOf(state.openUntil, opensAt),
        )
    }

    fun afterRateLimited(
        state: CircuitState,
        now: Long,
        retryAfterMs: Long?,
    ): CircuitState {
        val pause = (retryAfterMs ?: RATE_LIMIT_PAUSE_MS)
            .coerceIn(60_000L, MAX_RATE_LIMIT_PAUSE_MS)
        return state.copy(openUntil = maxOf(state.openUntil, now + pause))
    }

    fun isOpen(state: CircuitState, now: Long): Boolean = state.openUntil > now
}

/** Persistent per-site breaker: after repeated blocks, pause that site for a while. */
class SiteCircuitBreaker(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("site_health", Context.MODE_PRIVATE)

    fun isOpen(site: ProductSite, now: Long = System.currentTimeMillis()): Boolean =
        CircuitPolicy.isOpen(load(site), now)

    fun openUntil(site: ProductSite): Long = load(site).openUntil

    fun onBlocked(site: ProductSite, now: Long = System.currentTimeMillis()) {
        save(site, CircuitPolicy.afterBlocked(load(site), now))
    }

    fun onRateLimited(
        site: ProductSite,
        retryAfterMs: Long?,
        now: Long = System.currentTimeMillis(),
    ) {
        save(site, CircuitPolicy.afterRateLimited(load(site), now, retryAfterMs))
    }

    fun onSuccess(site: ProductSite) {
        prefs.edit {
            remove(blocksKey(site))
            remove(openUntilKey(site))
        }
    }

    private fun load(site: ProductSite): CircuitState = CircuitState(
        consecutiveBlocks = prefs.getInt(blocksKey(site), 0),
        openUntil = prefs.getLong(openUntilKey(site), 0L),
    )

    private fun save(site: ProductSite, state: CircuitState) {
        prefs.edit {
            putInt(blocksKey(site), state.consecutiveBlocks)
            putLong(openUntilKey(site), state.openUntil)
        }
    }

    private fun blocksKey(site: ProductSite) = "blocks_${site.label}"
    private fun openUntilKey(site: ProductSite) = "open_until_${site.label}"
}
