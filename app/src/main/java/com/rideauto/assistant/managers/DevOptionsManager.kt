package com.rideauto.assistant.managers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DevOptionsManager {

    private const val TAG = "DevOptionsManager"

    fun isDevOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "development_settings_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun isWirelessDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "stay_on_while_plugged_in", 0) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun getDevOptionsState(context: Context): DevOptionsState {
        val windowAnim = try {
            Settings.Global.getFloat(context.contentResolver, "window_animation_scale", 1.0f)
        } catch (e: Exception) {
            1.0f
        }
        val transAnim = try {
            Settings.Global.getFloat(context.contentResolver, "transition_animation_scale", 1.0f)
        } catch (e: Exception) {
            1.0f
        }
        val animDuration = try {
            Settings.Global.getFloat(context.contentResolver, "animator_duration_scale", 1.0f)
        } catch (e: Exception) {
            1.0f
        }
        return DevOptionsState(
            isDevOptionsEnabled = isDevOptionsEnabled(context),
            isUsbDebuggingEnabled = isUsbDebuggingEnabled(context),
            isWirelessDebuggingEnabled = isWirelessDebuggingEnabled(context),
            isKeepScreenOnEnabled = isKeepScreenOnEnabled(context),
            windowAnimationScale = windowAnim,
            transitionAnimationScale = transAnim,
            animatorDurationScale = animDuration
        )
    }

    suspend fun hideDeveloperOptions(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            PrivilegedManager.runShellCommand("settings put global development_settings_enabled 0")
            Pair(true, "Developer options cloaked successfully")
        } catch (e: Exception) {
            Pair(false, "Failed to cloak developer options: ${e.message}")
        }
    }

    suspend fun restoreDeveloperOptions(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            PrivilegedManager.runShellCommand("settings put global development_settings_enabled 1")
            Pair(true, "Developer options restored")
        } catch (e: Exception) {
            Pair(false, "Failed to restore developer options: ${e.message}")
        }
    }

    suspend fun setUsbDebuggingEnabled(enabled: Boolean): String = withContext(Dispatchers.IO) {
        val value = if (enabled) 1 else 0
        PrivilegedManager.runShellCommand("settings put global adb_enabled $value")
    }

    suspend fun setKeepScreenAwake(enabled: Boolean): String = withContext(Dispatchers.IO) {
        val value = if (enabled) 7 else 0
        PrivilegedManager.runShellCommand("settings put global stay_on_while_plugged_in $value")
    }

    suspend fun setFastAnimationScales(scale: Float): String = withContext(Dispatchers.IO) {
        PrivilegedManager.runShellCommand("settings put global window_animation_scale $scale")
        PrivilegedManager.runShellCommand("settings put global transition_animation_scale $scale")
        PrivilegedManager.runShellCommand("settings put global animator_duration_scale $scale")
    }

    fun openDeveloperOptions(context: Context) {
        try {
            val intent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("android.settings.SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ignored: Exception) {}
        }
    }

    fun openWirelessDebugging(context: Context) {
        try {
            val intent = Intent("android.settings.WIFI_DEBUGGING_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openDeveloperOptions(context)
        }
    }

    fun openAboutPhone(context: Context) {
        try {
            val intent = Intent("android.settings.DEVICE_INFO_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("android.settings.SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ignored: Exception) {}
        }
    }
}
