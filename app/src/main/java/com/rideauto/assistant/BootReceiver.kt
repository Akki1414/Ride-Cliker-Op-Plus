package com.rideauto.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rideauto.assistant.managers.PrivilegedManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Received broadcast: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT" -> {
                handleBoot(context)
            }
        }
    }

    private fun handleBoot(context: Context) {
        Log.i(TAG, "Boot detected — checking privileged service and accessibility")
        try {
            if (PreferencesHelper(context).isAdminLoggedIn) {
                Log.i(TAG, "Admin logged in on boot — starting AdminPaymentNotificationService")
                AdminPaymentNotificationService.start(context)
            }
            if (PrivilegedManager.isServiceRunning()) {
                Log.i(TAG, "Boot: privileged service is active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Boot handler error", e)
        }
    }
}
