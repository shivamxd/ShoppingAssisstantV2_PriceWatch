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

    private fun extraction(
        price: Double? = null,
        inStock: Boolean? = null,
        priceConfidence: Float = if (price != null) 0.95f else 0f,
        stockConfidence: Float = if (inStock != null) 0.95f else 0f,
    ) = Extraction(
        title = "Phone",
        price = price,
        inStock = inStock,
        priceConfidence = priceConfidence,
        stockConfidence = stockConfidence,
        strategy = "test",
    )

    @Test
    fun priceAtTargetNotifiesOnlyOnce() {
        val product = product(target = 20_000.0)
        val hit = alertDecision(product, extraction(price = 20_000.0, inStock = true))
        assertTrue(hit.priceHit)
        assertTrue(hit.notify)
        assertTrue(hit.nextPriceAlerted)

        val alreadyNotified = alertDecision(
            product.copy(priceAlerted = true),
            extraction(price = 19_000.0, inStock = true),
        )
        assertTrue(alreadyNotified.priceHit)
        assertFalse(alreadyNotified.notify)
        assertTrue(alreadyNotified.nextPriceAlerted)
    }

    @Test
    fun priceAboveTargetResetsAlert() {
        val decision = alertDecision(
            product(target = 20_000.0, priceAlerted = true),
            extraction(price = 20_001.0, inStock = true),
        )
        assertFalse(decision.priceHit)
        assertFalse(decision.notify)
        assertFalse(decision.nextPriceAlerted)
    }

    @Test
    fun restockNotifiesOnTransitionOnly() {
        val restock = alertDecision(
            product(target = null, lastInStock = false),
            extraction(price = 499.0, inStock = true),
        )
        assertTrue(restock.restocked)
        assertTrue(restock.notify)

        val stillInStock = alertDecision(
            product(target = null, lastInStock = true),
            extraction(price = 499.0, inStock = true),
        )
        assertFalse(stillInStock.restocked)
        assertFalse(stillInStock.notify)

        val firstEverCheck = alertDecision(
            product(target = null),
            extraction(price = 499.0, inStock = true),
        )
        assertFalse(firstEverCheck.restocked)
        assertFalse(firstEverCheck.notify)
    }

    @Test
    fun stockAlertsRespectTheToggle() {
        val decision = alertDecision(
            product(target = null, watchStock = false, lastInStock = false),
            extraction(price = 499.0, inStock = true),
        )
        assertFalse(decision.restocked)
        assertFalse(decision.notify)
    }

    @Test
    fun combinedPriceAndRestockSendsOneAlert() {
        val decision = alertDecision(
            product(target = 800.0, lastInStock = false),
            extraction(price = 699.0, inStock = true),
        )
        assertTrue(decision.priceHit)
        assertTrue(decision.restocked)
        assertTrue(decision.notify)
    }

    @Test
    fun noTargetMeansNoPriceAlert() {
        val decision = alertDecision(
            product(target = null),
            extraction(price = 100.0, inStock = true),
        )
        assertFalse(decision.priceHit)
        assertFalse(decision.notify)
    }

    @Test
    fun missingPriceKeepsTheExistingAlertState() {
        val alreadyAlerted = alertDecision(
            product(target = 700.0, priceAlerted = true),
            extraction(inStock = true),
        )
        assertFalse(alreadyAlerted.priceHit)
        assertFalse(alreadyAlerted.notify)
        assertTrue(alreadyAlerted.nextPriceAlerted)

        val notYetAlerted = alertDecision(
            product(target = 700.0, priceAlerted = false),
            extraction(inStock = true),
        )
        assertFalse(notYetAlerted.nextPriceAlerted)
    }

    @Test
    fun lowConfidencePriceNeverAlerts() {
        val decision = alertDecision(
            product(target = 700.0),
            extraction(price = 500.0, inStock = true, priceConfidence = 0.5f),
        )
        assertFalse(decision.priceHit)
        assertFalse(decision.notify)
        assertFalse(decision.nextPriceAlerted)
    }

    @Test
    fun lowConfidenceStockDoesNotLookLikeARestock() {
        val decision = alertDecision(
            product(lastInStock = false),
            extraction(inStock = true, stockConfidence = 0.5f),
        )
        assertFalse(decision.restocked)
        assertFalse(decision.notify)
    }
}
