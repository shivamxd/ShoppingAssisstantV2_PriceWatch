package com.example.shoppingassisstantv2.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchlistRepository private constructor(context: Context) {
    private val db = WatchlistDb(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _products = MutableStateFlow<List<Product>?>(null)
    val products: StateFlow<List<Product>?> = _products.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { reload() }

    suspend fun product(id: Long): Product? = withContext(Dispatchers.IO) { db.product(id) }

    suspend fun findByUrl(url: String): Product? = withContext(Dispatchers.IO) { db.findByUrl(url) }

    suspend fun ids(): List<Long> = withContext(Dispatchers.IO) { db.ids() }

    suspend fun history(id: Long): List<PricePoint> = withContext(Dispatchers.IO) { db.history(id) }

    suspend fun add(
        url: String,
        title: String,
        targetPrice: Double?,
        watchStock: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val id = try {
            db.insert(url, title, targetPrice, watchStock, System.currentTimeMillis())
        } catch (e: SQLiteConstraintException) {
            throw DuplicateProductException()
        }
        reload()
        id
    }

    suspend fun update(
        id: Long,
        title: String,
        targetPrice: Double?,
        watchStock: Boolean,
    ) = withContext(Dispatchers.IO) {
        val existing = db.product(id) ?: return@withContext
        db.update(
            id = id,
            title = title,
            targetPrice = targetPrice,
            watchStock = watchStock,
            resetAlert = existing.targetPrice != targetPrice,
        )
        reload()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.delete(id)
        reload()
    }

    suspend fun recordCheck(
        id: Long,
        title: String?,
        price: Double?,
        inStock: Boolean?,
        priceAlerted: Boolean,
        checkedAt: Long,
    ) = withContext(Dispatchers.IO) {
        db.updateAfterCheck(id, title, price, inStock, checkedAt, priceAlerted)
        reload()
    }

    suspend fun recordFailure(id: Long, message: String, checkedAt: Long) =
        withContext(Dispatchers.IO) {
            db.markFailure(id, message, checkedAt)
            reload()
        }

    private fun reload() {
        _products.value = db.products()
    }

    companion object {
        @Volatile
        private var instance: WatchlistRepository? = null

        fun get(context: Context): WatchlistRepository =
            instance ?: synchronized(this) {
                instance ?: WatchlistRepository(context).also { instance = it }
            }
    }
}
