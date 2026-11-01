package com.rideauto.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Disabled subscription notification service.
 * All notification mechanisms have been completely removed as requested.
 */
class SubscriptionNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) {
            // Disabled - no notifications
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SubscriptionNotificationService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
