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
import com.example.shoppingassisstantv2.data.PageFetcher
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.data.WatchlistRepository
import com.example.shoppingassisstantv2.data.alertDecision
import com.example.shoppingassisstantv2.notifications.PriceNotifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = WatchlistRepository.get(applicationContext)
        val requestedId = inputData.getLong(KEY_PRODUCT_ID, 0L)
        val ids = if (requestedId > 0) listOf(requestedId) else repository.ids()
        checkMutex.withLock {
            ids.forEach { id -> checkProduct(repository, id) }
        }
        return Result.success()
    }

    private suspend fun checkProduct(repository: WatchlistRepository, id: Long) {
        val product = repository.product(id) ?: return
        try {
            val site = ProductSites.forUrl(product.url)
                ?: throw CheckException("Only ${ProductSites.supportedLabels} product links are supported.")
            val snapshot = site.parse(PageFetcher.fetch(product.url), product.url)
            val decision = alertDecision(product, snapshot)
            val checkedAt = System.currentTimeMillis()
            repository.recordCheck(
                id = product.id,
                title = snapshot.title,
                price = snapshot.price,
                inStock = snapshot.inStock,
                priceAlerted = decision.nextPriceAlerted,
                checkedAt = checkedAt,
            )
            if (decision.notify) {
                // A notification failure must not throw away a successful price check.
                runCatching { PriceNotifier.notify(applicationContext, product, snapshot, decision) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = (e as? CheckException)?.message ?: "Couldn't check this product."
            repository.recordFailure(product.id, message, System.currentTimeMillis())
        }
    }

    companion object {
        const val KEY_PRODUCT_ID = "product_id"
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
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(PriceCheckWorker.KEY_PRODUCT_ID to productId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("check-$productId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, productId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("check-$productId")
    }
}
