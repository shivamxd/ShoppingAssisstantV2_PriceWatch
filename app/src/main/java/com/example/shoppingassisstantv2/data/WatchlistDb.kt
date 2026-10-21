package com.example.shoppingassisstantv2.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

class WatchlistDb(context: Context) : SQLiteOpenHelper(context, "pricewatch.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL DEFAULT '',
                target_price REAL,
                watch_stock INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                last_price REAL,
                last_in_stock INTEGER,
                last_checked INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                price_alerted INTEGER NOT NULL DEFAULT 0,
                failure_count INTEGER NOT NULL DEFAULT 0,
                last_success INTEGER NOT NULL DEFAULT 0,
                last_error_type TEXT,
                last_strategy TEXT,
                last_confidence REAL,
                failure_notified INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                price REAL,
                in_stock INTEGER,
                checked_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX history_product_time ON history(product_id, checked_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE products ADD COLUMN failure_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN last_success INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN last_error_type TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN last_strategy TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN last_confidence REAL")
            db.execSQL(
                "ALTER TABLE products ADD COLUMN failure_notified INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE products SET last_success = " +
                    "CASE WHEN last_error IS NULL THEN last_checked ELSE 0 END",
            )
        }
    }

    fun products(): List<Product> =
        readableDatabase.query(
            "products", null, null, null, null, null, "created_at DESC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toProduct()) }
        }

    fun product(id: Long): Product? =
        readableDatabase.query(
            "products", null, "id = ?", arrayOf(id.toString()), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toProduct() else null }

    fun findByUrl(url: String): Product? =
        readableDatabase.query(
            "products", null, "url = ?", arrayOf(url), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toProduct() else null }

    fun ids(): List<Long> =
        readableDatabase.query(
            "products", arrayOf("id"), null, null, null, null, "created_at DESC",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    fun history(productId: Long): List<PricePoint> =
        readableDatabase.query(
            "history",
            arrayOf("checked_at", "price", "in_stock"),
            "product_id = ?",
            arrayOf(productId.toString()),
            null,
            null,
            "checked_at ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PricePoint(
                            at = cursor.getLong(0),
                            price = if (cursor.isNull(1)) null else cursor.getDouble(1),
                            inStock = if (cursor.isNull(2)) null else cursor.getInt(2) != 0,
                        ),
                    )
                }
            }
        }

    fun insert(
        url: String,
        title: String,
        targetPrice: Double?,
        watchStock: Boolean,
        createdAt: Long,
    ): Long {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            if (targetPrice == null) putNull("target_price") else put("target_price", targetPrice)
            put("watch_stock", if (watchStock) 1 else 0)
            put("created_at", createdAt)
        }
        return writableDatabase.insertOrThrow("products", null, values)
    }

    fun update(
        id: Long,
        title: String,
        targetPrice: Double?,
        watchStock: Boolean,
        resetAlert: Boolean,
    ): Int {
        val values = ContentValues().apply {
            put("title", title)
            if (targetPrice == null) putNull("target_price") else put("target_price", targetPrice)
            put("watch_stock", if (watchStock) 1 else 0)
            if (resetAlert) put("price_alerted", 0)
        }
        return writableDatabase.update("products", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateAfterCheck(
        productId: Long,
        title: String?,
        price: Double?,
        inStock: Boolean?,
        checkedAt: Long,
        priceAlerted: Boolean,
        strategy: String?,
        confidence: Float?,
    ) {
        writableDatabase.transaction {
            if (price != null || inStock != null) {
                val point = ContentValues().apply {
                    put("product_id", productId)
                    if (price == null) putNull("price") else put("price", price)
                    if (inStock == null) putNull("in_stock") else put("in_stock", if (inStock) 1 else 0)
                    put("checked_at", checkedAt)
                }
                insertOrThrow("history", null, point)
            }
            val values = ContentValues().apply {
                put("last_checked", checkedAt)
                putNull("last_error")
                putNull("last_error_type")
                put("price_alerted", if (priceAlerted) 1 else 0)
                put("failure_count", 0)
                put("failure_notified", 0)
                put("last_success", checkedAt)
                if (price != null) put("last_price", price)
                if (inStock != null) put("last_in_stock", if (inStock) 1 else 0)
                if (strategy != null) put("last_strategy", strategy)
                if (confidence != null) put("last_confidence", confidence)
            }
            update("products", values, "id = ?", arrayOf(productId.toString()))
            if (!title.isNullOrBlank()) {
                execSQL(
                    "UPDATE products SET title = ? WHERE id = ? AND TRIM(title) = ''",
                    arrayOf<Any>(title, productId),
                )
            }
        }
    }

    fun markFailure(
        productId: Long,
        message: String,
        type: CheckFailureType,
        checkedAt: Long,
    ) {
        writableDatabase.execSQL(
            "UPDATE products SET last_error = ?, last_error_type = ?, last_checked = ?, " +
                "failure_count = failure_count + 1 WHERE id = ?",
            arrayOf<Any?>(message.take(300), type.name, checkedAt, productId),
        )
    }

    fun markFailureNotified(productId: Long) {
        val values = ContentValues().apply { put("failure_notified", 1) }
        writableDatabase.update("products", values, "id = ?", arrayOf(productId.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("products", "id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toProduct(): Product {
        val targetIndex = getColumnIndexOrThrow("target_price")
        val priceIndex = getColumnIndexOrThrow("last_price")
        val stockIndex = getColumnIndexOrThrow("last_in_stock")
        val confidenceIndex = getColumnIndexOrThrow("last_confidence")
        return Product(
            id = getLong(getColumnIndexOrThrow("id")),
            url = getString(getColumnIndexOrThrow("url")),
            title = getString(getColumnIndexOrThrow("title")),
            targetPrice = if (isNull(targetIndex)) null else getDouble(targetIndex),
            watchStock = getInt(getColumnIndexOrThrow("watch_stock")) != 0,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            lastPrice = if (isNull(priceIndex)) null else getDouble(priceIndex),
            lastInStock = if (isNull(stockIndex)) null else getInt(stockIndex) != 0,
            lastCheckedAt = getLong(getColumnIndexOrThrow("last_checked")),
            lastError = getString(getColumnIndexOrThrow("last_error")),
            priceAlerted = getInt(getColumnIndexOrThrow("price_alerted")) != 0,
            failureCount = getInt(getColumnIndexOrThrow("failure_count")),
            lastSuccessAt = getLong(getColumnIndexOrThrow("last_success")),
            lastErrorType = getString(getColumnIndexOrThrow("last_error_type")),
            lastStrategy = getString(getColumnIndexOrThrow("last_strategy")),
            lastConfidence = if (isNull(confidenceIndex)) null else getFloat(confidenceIndex),
            failureNotified = getInt(getColumnIndexOrThrow("failure_notified")) != 0,
        )
    }
}
