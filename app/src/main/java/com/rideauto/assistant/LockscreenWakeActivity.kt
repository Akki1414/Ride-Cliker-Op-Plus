package com.rideauto.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager

/**
 * Lightweight transparent activity that wakes up the device screen,
 * dismisses the keyguard/lock screen, and launches the target driver app
 * on top of the lock screen for instant auto-acceptance.
 */
class LockscreenWakeActivity : Activity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"

        fun wakeAndLaunch(context: Context, targetPackage: String? = null) {
            try {
                val intent = Intent(context, LockscreenWakeActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                    if (!targetPackage.isNullOrBlank()) {
                        putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                    }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and bypass lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Acquire a transient bright screen wake lock
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wl = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "RideClickerProPlus:ScreenWake"
            )
            wl?.acquire(3000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Launch target driver app to foreground if provided
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (!targetPkg.isNullOrBlank()) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                    startActivity(launchIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Smooth finish after handover to target app
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                if (!isFinishing) {
                    finish()
                }
            } catch (e: Exception) {}
        }, 300L)
    }
}
