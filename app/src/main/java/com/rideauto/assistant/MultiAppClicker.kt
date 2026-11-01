package com.rideauto.assistant

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles simultaneous clicking across multiple ride-hailing apps
 * using Shizuku shell tap injection.
 */
class MultiAppClicker(private val context: Context) {

    companion object {
        private const val TAG = "MultiAppClicker"
    }

    private val shizukuManager = ShizukuManager(context)
    private val prefs = PreferencesHelper(context)

    /** Check if multi-app clicking is available (requires Shizuku) */
    fun isAvailable(): Boolean = shizukuManager.isReady()

    /** Launch all supported and enabled ride apps */
    fun launchAllSupportedApps() {
        val pm = context.packageManager
        SupportedApps.all
            .filter { prefs.isAppEnabled(it.packageName) }
            .forEach { app ->
                try {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        Log.d(TAG, "Launched ${app.displayName}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to launch ${app.displayName}", e)
                }
            }
    }

    /**
     * Click accept button coordinates across all apps simultaneously.
     * Uses Shizuku shell to inject taps in parallel.
     */
    fun clickAllAppsSimultaneously(
        knownCoordinates: Map<String, Pair<Int, Int>>
    ) {
        val coords = knownCoordinates.entries
            .filter { prefs.isAppEnabled(it.key) }
            .map { it.value }
            .toList()

        if (coords.isNotEmpty()) {
            shizukuManager.tapAllCoordinates(coords)
        }
    }

    /**
     * Click accept button for a specific app by package name.
     */
    fun clickAppByPackage(packageName: String, x: Int, y: Int): Boolean {
        return shizukuManager.tapScreen(x, y)
    }
}
