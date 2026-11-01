package com.rideauto.assistant

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Disabled background payment notification service.
 * All notification mechanisms have been completely removed as requested.
 */
class AdminPaymentNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdminPaymentService"

        fun start(context: Context) {
            // Disabled - no background notifications
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AdminPaymentNotificationService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
