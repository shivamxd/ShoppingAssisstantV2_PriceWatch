package com.example.shoppingassisstantv2.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.shoppingassisstantv2.data.CheckException
import com.example.shoppingassisstantv2.data.CheckFailureType
import com.example.shoppingassisstantv2.data.MAX_AUTO_RETRIES
import com.example.shoppingassisstantv2.data.PageFetcher
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.ProductSite
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.data.SiteCircuitBreaker
import com.example.shoppingassisstantv2.data.WatchlistRepository
import com.example.shoppingassisstantv2.data.alertDecision
import com.example.shoppingassisstantv2.data.retryDelayMillis
import com.example.shoppingassisstantv2.data.shouldNotifyStuck
import com.example.shoppingassisstantv2.notifications.PriceNotifier
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = WatchlistRepository.get(applicationContext)
        val breaker = SiteCircuitBreaker(applicationContext)
        val requestedId = inputData.getLong(KEY_PRODUCT_ID, 0L)
        val manualCheck = inputData.getBoolean(KEY_MANUAL_CHECK, false)
        val ids = if (requestedId > 0) listOf(requestedId) else repository.ids()
        checkMutex.withLock {
            ids.forEach { id ->
                checkProduct(
                    repository = repository,
                    breaker = breaker,
                    id = id,
                    bypassCircuit = manualCheck,
                )
            }
        }
        return Result.success()
    }

    private suspend fun checkProduct(
        repository: WatchlistRepository,
        breaker: SiteCircuitBreaker,
        id: Long,
        bypassCircuit: Boolean,
    ) {
        val product = repository.product(id) ?: return
        val site = ProductSites.forUrl(product.url)
        if (site == null) {
            handleFailure(
                repository = repository,
                breaker = breaker,
                product = product,
                site = null,
                failure = CheckException(
                    "Only ${ProductSites.supportedLabels} product links are supported.",
                    CheckFailureType.Unsupported,
                ),
                now = System.currentTimeMillis(),
            )
            return
        }

        val now = System.currentTimeMillis()
        if (!bypassCircuit && breaker.isOpen(site, now)) return

        try {
            val extraction = site.parse(
                PageFetcher.fetch(product.url, site),
                product.url,
            )
            if (extraction.trustedPrice == null && extraction.trustedStock == null) {
                throw CheckException(
                    "No reliable price or stock data was found on that page.",
                    CheckFailureType.NoData,
                )
            }

            val decision = alertDecision(product, extraction)
            val checkedAt = System.currentTimeMillis()
            repository.recordCheck(
                id = product.id,
                title = extraction.title,
                price = extraction.trustedPrice,
                inStock = extraction.trustedStock,
                priceAlerted = decision.nextPriceAlerted,
                checkedAt = checkedAt,
                strategy = extraction.strategy.takeIf { it.isNotBlank() },
                confidence = extraction.confidence.takeIf { it > 0f },
            )
            breaker.onSuccess(site)
            PriceNotifier.cancelCheckProblem(applicationContext, product.id)
            if (decision.notify) {
                // A notification failure must not throw away a successful price check.
                runCatching {
                    PriceNotifier.notify(applicationContext, product, extraction, decision)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = e as? CheckException
                ?: CheckException("Couldn't check this product.", CheckFailureType.Unknown)
            val failedAt = System.currentTimeMillis()
            when (failure.type) {
                CheckFailureType.Blocked -> breaker.onBlocked(site, failedAt)
                CheckFailureType.RateLimited ->
                    breaker.onRateLimited(site, failure.retryAfterMs, failedAt)
                else -> Unit
            }
            handleFailure(repository, breaker, product, site, failure, failedAt)
        }
    }

    private suspend fun handleFailure(
        repository: WatchlistRepository,
        breaker: SiteCircuitBreaker,
        product: Product,
        site: ProductSite?,
        failure: CheckException,
        now: Long,
    ) {
        val updated = repository.recordFailure(
            id = product.id,
            message = failure.message ?: failure.type.label,
            type = failure.type,
            checkedAt = now,
        ) ?: return

        if (shouldNotifyStuck(
                lastSuccessAt = updated.lastSuccessAt,
                createdAt = updated.createdAt,
                now = now,
                failureCount = updated.failureCount,
                alreadyNotified = updated.failureNotified,
            )
        ) {
            if (PriceNotifier.notifyCheckProblem(applicationContext, updated)) {
                repository.markFailureNotified(updated.id)
            }
        }

        if (site != null &&
            failure.type.retryable &&
            updated.failureCount <= MAX_AUTO_RETRIES
        ) {
            val retryAt = when {
                failure.type == CheckFailureType.RateLimited -> breaker.openUntil(site)
                breaker.isOpen(site, now) -> Long.MAX_VALUE
                else -> 0L
            }
            if (retryAt != Long.MAX_VALUE) {
                val delay = maxOf(
                    retryDelayMillis(
                        failureCount = updated.failureCount,
                        retryAfterMs = failure.retryAfterMs,
                        jitterMs = Random.nextLong(0L, 20_000L),
                    ),
                    (retryAt - now + 1_000L).coerceAtLeast(0L),
                )
                PriceCheckWork.scheduleRetry(applicationContext, product.id, delay)
            }
        }
    }

    companion object {
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_MANUAL_CHECK = "manual_check"
        private val checkMutex = Mutex()
    }
}

object PriceCheckWork {
    private const val PERIODIC_WORK = "price-check-periodic"
    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun checkNow(context: Context, productId: Long) {
        enqueueCheck(context, productId, 0L, manual = true)
    }

    fun scheduleRetry(context: Context, productId: Long, delayMs: Long) {
        enqueueCheck(context, productId, delayMs, manual = false)
    }

    fun cancel(context: Context, productId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("check-$productId")
    }

    private fun enqueueCheck(
        context: Context,
        productId: Long,
        delayMs: Long,
        manual: Boolean,
    ) {
        // One unique name per product keeps a single pending check/retry; replacing a
        // just-finished worker is how a failed check hands off to its retry.
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(network)
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    PriceCheckWorker.KEY_PRODUCT_ID to productId,
                    PriceCheckWorker.KEY_MANUAL_CHECK to manual,
                ),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("check-$productId", ExistingWorkPolicy.REPLACE, request)
    }
}
