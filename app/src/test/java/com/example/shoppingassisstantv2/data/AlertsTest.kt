package com.example.shoppingassisstantv2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {

    private fun product(
        target: Double? = null,
        watchStock: Boolean = true,
        lastInStock: Boolean? = null,
        priceAlerted: Boolean = false,
    ) = Product(
        id = 1L,
        url = "https://www.amazon.in/dp/B0DGJHBX5Y",
        title = "Phone",
        targetPrice = target,
        watchStock = watchStock,
        createdAt = 0L,
        lastPrice = null,
        lastInStock = lastInStock,
        lastCheckedAt = 0L,
        lastError = null,
        priceAlerted = priceAlerted,
    )

    @Test
    fun priceAtTargetNotifiesOnlyOnce() {
        val product = product(target = 20_000.0)
        val hit = alertDecision(product, Snapshot(null, 20_000.0, true))
        assertTrue(hit.priceHit)
        assertTrue(hit.notify)
        assertTrue(hit.nextPriceAlerted)

        val alreadyNotified = alertDecision(
            product.copy(priceAlerted = true),
            Snapshot(null, 19_000.0, true),
        )
        assertTrue(alreadyNotified.priceHit)
        assertFalse(alreadyNotified.notify)
        assertTrue(alreadyNotified.nextPriceAlerted)
    }

    @Test
    fun priceAboveTargetResetsAlert() {
        val decision = alertDecision(
            product(target = 20_000.0, priceAlerted = true),
            Snapshot(null, 20_001.0, true),
        )
        assertFalse(decision.priceHit)
        assertFalse(decision.notify)
        assertFalse(decision.nextPriceAlerted)
    }

    @Test
    fun restockNotifiesOnTransitionOnly() {
        val restock = alertDecision(
            product(target = null, lastInStock = false),
            Snapshot(null, 499.0, true),
        )
        assertTrue(restock.restocked)
        assertTrue(restock.notify)

        val stillInStock = alertDecision(
            product(target = null, lastInStock = true),
            Snapshot(null, 499.0, true),
        )
        assertFalse(stillInStock.restocked)
        assertFalse(stillInStock.notify)

        val firstEverCheck = alertDecision(product(target = null), Snapshot(null, 499.0, true))
        assertFalse(firstEverCheck.restocked)
        assertFalse(firstEverCheck.notify)
    }

    @Test
    fun stockAlertsRespectTheToggle() {
        val decision = alertDecision(
            product(target = null, watchStock = false, lastInStock = false),
            Snapshot(null, 499.0, true),
        )
        assertFalse(decision.restocked)
        assertFalse(decision.notify)
    }

    @Test
    fun combinedPriceAndRestockSendsOneAlert() {
        val decision = alertDecision(
            product(target = 800.0, lastInStock = false),
            Snapshot("Phone", 699.0, true),
        )
        assertTrue(decision.priceHit)
        assertTrue(decision.restocked)
        assertTrue(decision.notify)
    }

    @Test
    fun noTargetMeansNoPriceAlert() {
        val decision = alertDecision(product(target = null), Snapshot(null, 100.0, true))
        assertFalse(decision.priceHit)
        assertFalse(decision.notify)
    }

    @Test
    fun missingPriceKeepsTheExistingAlertState() {
        val alreadyAlerted = alertDecision(
            product(target = 700.0, priceAlerted = true),
            Snapshot(null, null, true),
        )
        assertFalse(alreadyAlerted.priceHit)
        assertFalse(alreadyAlerted.notify)
        assertTrue(alreadyAlerted.nextPriceAlerted)

        val notYetAlerted = alertDecision(
            product(target = 700.0, priceAlerted = false),
            Snapshot(null, null, true),
        )
        assertFalse(notYetAlerted.nextPriceAlerted)
    }
}
