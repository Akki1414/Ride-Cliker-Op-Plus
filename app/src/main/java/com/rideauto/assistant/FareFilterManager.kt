package com.rideauto.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Manages fare and distance filter settings.
 * Handles range validation for incoming ride requests.
 * Synchronizes with both fare_filter_prefs and default SharedPreferences.
 */
class FareFilterManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)

    private val defaultPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val KEY_FILTER_ENABLED = "filter_enabled"
        private const val KEY_MIN_FARE = "min_fare"
        private const val KEY_MAX_FARE = "max_fare"
        private const val KEY_DIST_FILTER_ENABLED = "dist_filter_enabled"
        private const val KEY_MIN_DIST = "min_dist"
        private const val KEY_MAX_DIST = "max_dist"

        fun shouldAcceptFare(fare: Int?, minFare: Int, maxFare: Int, isFilterEnabled: Boolean): Boolean {
            if (!isFilterEnabled) return true
            if (fare == null || fare <= 0) return true
            val max = if (maxFare > 0) maxFare else Int.MAX_VALUE
            return fare in minFare..max
        }

        fun shouldAcceptDistance(
            pickupDistance: Float?,
            maxPickupKm: Float,
            dropDistance: Float? = null,
            maxDropKm: Float = 0f,
            isDistanceFilterEnabled: Boolean = true
        ): Boolean {
            if (!isDistanceFilterEnabled) return true
            if (pickupDistance != null && maxPickupKm > 0f) {
                if (pickupDistance > maxPickupKm) return false
            }
            return true
        }
    }

    // ── Fare Filter ──
    var isFilterEnabled: Boolean
        get() {
            return if (prefs.contains(KEY_FILTER_ENABLED)) {
                prefs.getBoolean(KEY_FILTER_ENABLED, false)
            } else {
                defaultPrefs.getBoolean("fare_filter_enabled", false)
            }
        }
        set(value) {
            prefs.edit().putBoolean(KEY_FILTER_ENABLED, value).apply()
            defaultPrefs.edit().putBoolean("fare_filter_enabled", value).apply()
        }

    var minFare: Int
        get() {
            return if (prefs.contains(KEY_MIN_FARE)) {
                try { prefs.getInt(KEY_MIN_FARE, 0) } catch (_: Exception) { 0 }
            } else {
                try {
                    defaultPrefs.getInt("min_fare", 0)
                } catch (_: ClassCastException) {
                    try { defaultPrefs.getString("min_fare", "0")?.toIntOrNull() ?: 0 } catch (_: Exception) { 0 }
                }
            }
        }
        set(value) {
            prefs.edit().putInt(KEY_MIN_FARE, value).apply()
            defaultPrefs.edit().putInt("min_fare", value).apply()
        }

    var maxFare: Int
        get() {
            return if (prefs.contains(KEY_MAX_FARE)) {
                try { prefs.getInt(KEY_MAX_FARE, 10000) } catch (_: Exception) { 10000 }
            } else {
                val def = try {
                    defaultPrefs.getInt("max_fare", 500)
                } catch (_: ClassCastException) {
                    try { defaultPrefs.getString("max_fare", "500")?.toIntOrNull() ?: 500 } catch (_: Exception) { 500 }
                }
                if (def > 0) def else 10000
            }
        }
        set(value) {
            prefs.edit().putInt(KEY_MAX_FARE, value).apply()
            defaultPrefs.edit().putInt("max_fare", value).apply()
        }

    // ── Distance Filter ──
    var isDistanceFilterEnabled: Boolean
        get() {
            return if (prefs.contains(KEY_DIST_FILTER_ENABLED)) {
                try { prefs.getBoolean(KEY_DIST_FILTER_ENABLED, false) } catch (_: Exception) { false }
            } else {
                try { defaultPrefs.getBoolean("distance_filter_enabled", false) } catch (_: Exception) { false }
            }
        }
        set(value) {
            prefs.edit().putBoolean(KEY_DIST_FILTER_ENABLED, value).apply()
            defaultPrefs.edit().putBoolean("distance_filter_enabled", value).apply()
        }

    var minDistance: Float
        get() {
            return if (prefs.contains(KEY_MIN_DIST)) {
                try { prefs.getFloat(KEY_MIN_DIST, 0f) } catch (_: Exception) { 0f }
            } else {
                try {
                    defaultPrefs.getFloat("min_pickup_km", 0f)
                } catch (_: ClassCastException) {
                    try { defaultPrefs.getString("min_pickup_km", "0")?.toFloatOrNull() ?: 0f } catch (_: Exception) { 0f }
                }
            }
        }
        set(value) {
            prefs.edit().putFloat(KEY_MIN_DIST, value).apply()
            defaultPrefs.edit().putFloat("min_pickup_km", value).apply()
        }

    var maxDistance: Float
        get() {
            return if (prefs.contains(KEY_MAX_DIST)) {
                try { prefs.getFloat(KEY_MAX_DIST, 50f) } catch (_: Exception) { 50f }
            } else {
                try {
                    defaultPrefs.getFloat("max_pickup_km", 10f)
                } catch (_: ClassCastException) {
                    try { defaultPrefs.getString("max_pickup_km", "10")?.toFloatOrNull() ?: 50f } catch (_: Exception) { 50f }
                }
            }
        }
        set(value) {
            prefs.edit().putFloat(KEY_MAX_DIST, value).apply()
            defaultPrefs.edit().putFloat("max_pickup_km", value).apply()
        }

    /**
     * Check if given fare is within the configured range.
     * Validates totalFare >= minFare (ensuring driver gets minimum earnings)
     * and baseFare <= maxFare (so surge doesn't reject high value rides).
     * If baseFare is not provided, defaults to totalFare.
     * Returns true if filter is disabled (accept all) or fare is in range.
     */
    fun isFareInRange(totalFare: Int, baseFare: Int = totalFare): Boolean {
        if (!isFilterEnabled) return true
        val max = if (maxFare > 0) maxFare else Int.MAX_VALUE
        return totalFare >= minFare && baseFare <= max
    }

    /**
     * Check if given pickup distance is <= maxDistance threshold.
     * Returns true if filter is disabled (accept all) or distance <= maxDistance.
     * Null distance passes by default.
     */
    fun isDistanceInRange(distance: Float?): Boolean {
        if (!isDistanceFilterEnabled) return true
        if (distance == null) return true // Can't parse = accept by default
        return distance <= maxDistance
    }

    /** Save fare filter range */
    fun saveFilter(min: Int, max: Int) {
        minFare = min
        maxFare = max
    }

    /** Save max pickup distance filter */
    fun saveMaxDistanceFilter(max: Float) {
        maxDistance = max
    }

    /** Save distance filter range */
    fun saveDistanceFilter(min: Float, max: Float) {
        minDistance = min
        maxDistance = max
    }
}
