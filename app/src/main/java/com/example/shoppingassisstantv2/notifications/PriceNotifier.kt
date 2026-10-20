package com.example.shoppingassisstantv2.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.shoppingassisstantv2.R
import com.example.shoppingassisstantv2.data.AlertDecision
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.data.Snapshot
import com.example.shoppingassisstantv2.data.asPrice

object PriceNotifier {
    private const val CHANNEL_ID = "price_alerts"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Price & stock alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tells you when a watched product hits your target price or is back in stock."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(context: Context, product: Product, snapshot: Snapshot, decision: AlertDecision) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val priceText = snapshot.price?.asPrice()
        val targetText = product.targetPrice?.asPrice()
        val message = when {
            decision.restocked && decision.priceHit && priceText != null && targetText != null ->
                "Back in stock at $priceText — at or below your $targetText target"
            decision.restocked && priceText != null -> "Back in stock at $priceText — tap to open"
            decision.restocked -> "Back in stock — tap to open"
            decision.priceHit && priceText != null && targetText != null ->
                "Now $priceText — at or below your $targetText target"
            else -> "Tap to open the product"
        }

        val openProduct = PendingIntent.getActivity(
            context,
            notificationId(product.id),
            Intent(Intent.ACTION_VIEW, product.url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle(product.title.ifBlank { snapshot.title ?: "Price alert" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSubText(ProductSites.labelFor(product.url))
            .setContentIntent(openProduct)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(product.id), notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked at any time; the next check will try again.
        }
    }

    fun cancel(context: Context, productId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(productId))
    }

    private fun notificationId(id: Long): Int = (id and 0x7FFFFFFF).toInt()
}
