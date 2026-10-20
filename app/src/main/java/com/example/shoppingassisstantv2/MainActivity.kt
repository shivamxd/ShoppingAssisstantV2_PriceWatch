package com.example.shoppingassisstantv2

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.shoppingassisstantv2.data.SharedLink
import com.example.shoppingassisstantv2.data.parseSharedText
import com.example.shoppingassisstantv2.ui.PriceWatchApp
import com.example.shoppingassisstantv2.ui.theme.PriceWatchTheme

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf<SharedLink?>(null)
    private var notificationsEnabled by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        // Only on a fresh launch: a rotation would otherwise replay the original share intent.
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            PriceWatchTheme {
                PriceWatchApp(
                    sharedLink = sharedLink,
                    onSharedLinkHandled = { sharedLink = null },
                    notificationsEnabled = notificationsEnabled,
                    onOpenNotificationSettings = ::openNotificationSettings,
                )
            }
        }
        askForNotificationsOnce()
    }

    override fun onResume() {
        super.onResume()
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
        sharedLink = parseSharedText(text)
    }

    private fun askForNotificationsOnce() {
        if (notificationsEnabled) return
        val prefs = getSharedPreferences("pricewatch", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit { putBoolean("asked_notifications", true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }
}
