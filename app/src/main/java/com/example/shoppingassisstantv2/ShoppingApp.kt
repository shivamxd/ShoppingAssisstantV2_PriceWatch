package com.example.shoppingassisstantv2

import android.app.Application
import com.example.shoppingassisstantv2.notifications.PriceNotifier
import com.example.shoppingassisstantv2.work.PriceCheckWork

class ShoppingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PriceNotifier.ensureChannel(this)
        PriceCheckWork.schedulePeriodic(this)
    }
}
