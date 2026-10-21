package com.example.shoppingassisstantv2.data

import kotlin.math.roundToLong

internal data class PriceEvidence(
    val price: Double,
    val source: String,
    val confidence: Float,
)

internal data class ChosenPrice(
    val price: Double,
    val source: String,
    val confidence: Float,
)

/**
 * Picks the price supported by the strongest set of independent sources. Multiple agreeing
 * sources raise confidence; disagreeing sources lower it so a single bad selector cannot
 * trigger an alert by itself.
 */
internal fun choosePrice(evidence: List<PriceEvidence>): ChosenPrice? {
    val usable = evidence.filter { it.price.isFinite() && it.price > 0.0 && it.confidence > 0f }
    if (usable.isEmpty()) return null

    val groups = usable.groupBy { (it.price * 100.0).roundToLong() }
    val bestGroup = groups.values.maxWithOrNull(
        compareBy<List<PriceEvidence>>(
            { group -> group.sumOf { it.confidence.toDouble() } },
            { group -> group.maxOf { it.confidence } },
        ),
    ) ?: return null

    val strongest = bestGroup.maxByOrNull { it.confidence } ?: return null
    val agreementBoost = if (bestGroup.size > 1) 0.05f else 0f
    val disagreementPenalty = if (groups.size > 1) 0.1f else 0f
    return ChosenPrice(
        price = strongest.price,
        source = strongest.source,
        confidence = (strongest.confidence + agreementBoost - disagreementPenalty)
            .coerceIn(0f, 1f),
    )
}
