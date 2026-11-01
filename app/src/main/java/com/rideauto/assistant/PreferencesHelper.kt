package com.rideauto.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.Locale

class PreferencesHelper(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // In-memory volatile cache for 0ns hot-path lookups
    @Volatile private var _isEnabled: Boolean = false
    @Volatile private var _isBanned: Boolean = false
    @Volatile private var _subExpiry: Long = 0L
    @Volatile private var _isOlaEnabled: Boolean = true
    @Volatile private var _isRapidoEnabled: Boolean = true
    @Volatile private var _isUberEnabled: Boolean = true
    @Volatile private var _isNammaYatriEnabled: Boolean = true
    @Volatile private var _isRedTaxiEnabled: Boolean = true
    @Volatile private var _isBharatTaxiEnabled: Boolean = true
    @Volatile private var _isPorterEnabled: Boolean = true
    @Volatile private var _autoWakeLockscreen: Boolean = true
    @Volatile private var _acceptWhenMinimized: Boolean = true
    @Volatile private var _cooldownMs: Long = 1200L

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_SERVICE_ENABLED, "master_enabled" -> _isEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, prefs.getBoolean("master_enabled", false))
            KEY_IS_BANNED -> _isBanned = prefs.getBoolean(KEY_IS_BANNED, false)
            KEY_SUBSCRIPTION_EXPIRY, "sub_expiry_ts" -> _subExpiry = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, prefs.getLong("sub_expiry_ts", 0L))
            KEY_OLA_ENABLED -> _isOlaEnabled = prefs.getBoolean(KEY_OLA_ENABLED, true)
            KEY_RAPIDO_ENABLED -> _isRapidoEnabled = prefs.getBoolean(KEY_RAPIDO_ENABLED, true)
            KEY_UBER_ENABLED -> _isUberEnabled = prefs.getBoolean(KEY_UBER_ENABLED, true)
            KEY_NAMMA_YATRI_ENABLED -> _isNammaYatriEnabled = prefs.getBoolean(KEY_NAMMA_YATRI_ENABLED, true)
            KEY_RED_TAXI_ENABLED -> _isRedTaxiEnabled = prefs.getBoolean(KEY_RED_TAXI_ENABLED, true)
            KEY_BHARAT_TAXI_ENABLED -> _isBharatTaxiEnabled = prefs.getBoolean(KEY_BHARAT_TAXI_ENABLED, true)
            KEY_PORTER_ENABLED -> _isPorterEnabled = prefs.getBoolean(KEY_PORTER_ENABLED, true)
            "auto_wake_lockscreen", "lockscreen_wake_enabled" -> _autoWakeLockscreen = prefs.getBoolean("auto_wake_lockscreen", prefs.getBoolean("lockscreen_wake_enabled", true))
            "accept_when_minimized" -> _acceptWhenMinimized = prefs.getBoolean("accept_when_minimized", true)
            KEY_COOLDOWN_MS -> _cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, 1200L)
        }
    }

    init {
        _isEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, prefs.getBoolean("master_enabled", false))
        _isBanned = prefs.getBoolean(KEY_IS_BANNED, false)
        _subExpiry = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, prefs.getLong("sub_expiry_ts", 0L))
        _isOlaEnabled = prefs.getBoolean(KEY_OLA_ENABLED, true)
        _isRapidoEnabled = prefs.getBoolean(KEY_RAPIDO_ENABLED, true)
        _isUberEnabled = prefs.getBoolean(KEY_UBER_ENABLED, true)
        _isNammaYatriEnabled = prefs.getBoolean(KEY_NAMMA_YATRI_ENABLED, true)
        _isRedTaxiEnabled = prefs.getBoolean(KEY_RED_TAXI_ENABLED, true)
        _isBharatTaxiEnabled = prefs.getBoolean(KEY_BHARAT_TAXI_ENABLED, true)
        _isPorterEnabled = prefs.getBoolean(KEY_PORTER_ENABLED, true)
        _autoWakeLockscreen = prefs.getBoolean("auto_wake_lockscreen", prefs.getBoolean("lockscreen_wake_enabled", true))
        _acceptWhenMinimized = prefs.getBoolean("accept_when_minimized", true)
        _cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, 1200L)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    companion object {
        @Volatile private var instance: PreferencesHelper? = null

        fun getInstance(context: Context): PreferencesHelper {
            return instance ?: synchronized(this) {
                instance ?: PreferencesHelper(context.applicationContext).also { instance = it }
            }
        }

        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_MIN_FARE = "min_fare"
        private const val KEY_MAX_FARE = "max_fare"
        private const val KEY_DISTANCE_FILTER_ENABLED = "distance_filter_enabled"
        private const val KEY_MIN_PICKUP_KM = "min_pickup_km"
        private const val KEY_MAX_PICKUP_KM = "max_pickup_km"
        private const val KEY_MIN_DROP_KM = "min_drop_km"
        private const val KEY_MAX_DROP_KM = "max_drop_km"

        private const val KEY_OLA_ENABLED = "ola_enabled"
        private const val KEY_RAPIDO_ENABLED = "rapido_enabled"
        private const val KEY_UBER_ENABLED = "uber_enabled"
        private const val KEY_NAMMA_YATRI_ENABLED = "namma_yatri_enabled"
        private const val KEY_RED_TAXI_ENABLED = "red_taxi_enabled"
        private const val KEY_BHARAT_TAXI_ENABLED = "bharat_taxi_enabled"
        private const val KEY_PORTER_ENABLED = "porter_enabled"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"

        // Auth & Subscription
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry_timestamp"
        private const val KEY_IS_BANNED = "is_banned"

        // Overlay Coordinates
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"

        // Admin Session
        private const val KEY_IS_ADMIN_LOGGED_IN = "is_admin_logged_in"
        private const val KEY_LOGGED_ADMIN_EMAIL = "logged_admin_email"
        private const val KEY_LOGGED_ADMIN_PASS = "logged_admin_password"
    }

    var isServiceEnabled: Boolean
        get() = _isEnabled
        set(value) {
            _isEnabled = value
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).putBoolean("master_enabled", value).apply()
        }

    var isEnabled: Boolean
        get() = isServiceEnabled
        set(value) { isServiceEnabled = value }

    private fun getSafeInt(key: String, defValue: Int): Int {
        return try {
            prefs.getInt(key, defValue)
        } catch (_: ClassCastException) {
            try {
                prefs.getString(key, null)?.toIntOrNull() ?: defValue
            } catch (_: Exception) {
                defValue
            }
        }
    }

    private fun getSafeFloat(key: String, defValue: Float): Float {
        return try {
            prefs.getFloat(key, defValue)
        } catch (_: ClassCastException) {
            try {
                prefs.getString(key, null)?.toFloatOrNull() ?: defValue
            } catch (_: Exception) {
                defValue
            }
        }
    }

    var minFare: Int
        get() = getSafeInt(KEY_MIN_FARE, 0)
        set(value) {
            prefs.edit().putInt(KEY_MIN_FARE, value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("min_fare", value).apply()
            } catch (_: Exception) {}
        }

    var maxFare: Int
        get() = getSafeInt(KEY_MAX_FARE, 500)
        set(value) {
            prefs.edit().putInt(KEY_MAX_FARE, value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("max_fare", value).apply()
            } catch (_: Exception) {}
        }

    var isDistanceFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISTANCE_FILTER_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DISTANCE_FILTER_ENABLED, value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("dist_filter_enabled", value).apply()
            } catch (_: Exception) {}
        }

    var minPickupKm: Float
        get() = getSafeFloat(KEY_MIN_PICKUP_KM, 0.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MIN_PICKUP_KM, value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putFloat("min_dist", value).apply()
            } catch (_: Exception) {}
        }

    var maxPickupKm: Float
        get() = getSafeFloat(KEY_MAX_PICKUP_KM, 10.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MAX_PICKUP_KM, value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putFloat("max_dist", value).apply()
            } catch (_: Exception) {}
        }

    var minDropKm: Float
        get() = getSafeFloat(KEY_MIN_DROP_KM, 0.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MIN_DROP_KM, value).apply()
        }

    var maxDropKm: Float
        get() = getSafeFloat(KEY_MAX_DROP_KM, 50.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MAX_DROP_KM, value).apply()
        }

    var cooldownMs: Long
        get() = _cooldownMs
        set(value) {
            _cooldownMs = value
            prefs.edit().putLong(KEY_COOLDOWN_MS, value).apply()
        }

    // ── Auth & Subscription ──
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var loggedInUserId: String?
        get() = prefs.getString(KEY_LOGGED_IN_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_LOGGED_IN_USER_ID, value).apply()

    var subscriptionExpiryTimestamp: Long
        get() = _subExpiry
        set(value) {
            _subExpiry = value
            prefs.edit().putLong(KEY_SUBSCRIPTION_EXPIRY, value).putLong("sub_expiry_ts", value).apply()
        }

    var isBanned: Boolean
        get() = _isBanned
        set(value) {
            _isBanned = value
            prefs.edit().putBoolean(KEY_IS_BANNED, value).apply()
        }

    fun isSubscriptionActive(): Boolean {
        return subscriptionExpiryTimestamp > System.currentTimeMillis()
    }

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, 0)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, 200)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    // ── Pending Payment Fields ──
    var pendingPlan: String?
        get() = prefs.getString("pending_plan", null)
        set(value) = prefs.edit().putString("pending_plan", value).apply()

    var pendingAmount: String?
        get() = prefs.getString("pending_amount", null)
        set(value) = prefs.edit().putString("pending_amount", value).apply()

    var pendingUtr: String?
        get() = prefs.getString("pending_utr", null)
        set(value) = prefs.edit().putString("pending_utr", value).apply()

    // ── Additional App Settings & Helpers ──
    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", true)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()

    var isFareFilterEnabled: Boolean
        get() = prefs.getBoolean("fare_filter_enabled", false)
        set(value) {
            prefs.edit().putBoolean("fare_filter_enabled", value).apply()
            try {
                appContext.getSharedPreferences("fare_filter_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("filter_enabled", value).apply()
            } catch (_: Exception) {}
        }

    var isAudioFeedbackEnabled: Boolean
        get() = prefs.getBoolean("audio_feedback_enabled", true)
        set(value) = prefs.edit().putBoolean("audio_feedback_enabled", value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    var isTtsEnabled: Boolean
        get() = isAudioFeedbackEnabled
        set(value) { isAudioFeedbackEnabled = value }

    // ── Per-App Toggles ──
    var isOlaEnabled: Boolean
        get() = _isOlaEnabled
        set(value) {
            _isOlaEnabled = value
            prefs.edit().putBoolean(KEY_OLA_ENABLED, value).apply()
        }

    var isRapidoEnabled: Boolean
        get() = _isRapidoEnabled
        set(value) {
            _isRapidoEnabled = value
            prefs.edit().putBoolean(KEY_RAPIDO_ENABLED, value).apply()
        }

    var isUberEnabled: Boolean
        get() = _isUberEnabled
        set(value) {
            _isUberEnabled = value
            prefs.edit().putBoolean(KEY_UBER_ENABLED, value).apply()
        }

    var isNammaYatriEnabled: Boolean
        get() = _isNammaYatriEnabled
        set(value) {
            _isNammaYatriEnabled = value
            prefs.edit().putBoolean(KEY_NAMMA_YATRI_ENABLED, value).apply()
        }

    var isRedTaxiEnabled: Boolean
        get() = _isRedTaxiEnabled
        set(value) {
            _isRedTaxiEnabled = value
            prefs.edit().putBoolean(KEY_RED_TAXI_ENABLED, value).apply()
        }

    var isBharatTaxiEnabled: Boolean
        get() = _isBharatTaxiEnabled
        set(value) {
            _isBharatTaxiEnabled = value
            prefs.edit().putBoolean(KEY_BHARAT_TAXI_ENABLED, value).apply()
        }

    var isPorterEnabled: Boolean
        get() = _isPorterEnabled
        set(value) {
            _isPorterEnabled = value
            prefs.edit().putBoolean(KEY_PORTER_ENABLED, value).apply()
        }

    var isLockscreenWakeEnabled: Boolean
        get() = prefs.getBoolean("lockscreen_wake_enabled", true)
        set(value) = prefs.edit().putBoolean("lockscreen_wake_enabled", value).apply()

    var autoWakeLockscreen: Boolean
        get() = _autoWakeLockscreen
        set(value) {
            _autoWakeLockscreen = value
            isLockscreenWakeEnabled = value
            prefs.edit().putBoolean("auto_wake_lockscreen", value).apply()
        }

    var acceptWhenMinimized: Boolean
        get() = _acceptWhenMinimized
        set(value) {
            _acceptWhenMinimized = value
            prefs.edit().putBoolean("accept_when_minimized", value).apply()
        }

    var skipPickupForAdvanceRide: Boolean
        get() = prefs.getBoolean("skip_pickup_for_advance_ride", true)
        set(value) = prefs.edit().putBoolean("skip_pickup_for_advance_ride", value).apply()

    var lastNotifiedSubscriptionExpiry: Long
        get() = prefs.getLong("last_notified_expiry_ts", 0L)
        set(value) = prefs.edit().putLong("last_notified_expiry_ts", value).apply()

    var lastNotifiedBanState: Boolean
        get() = prefs.getBoolean("last_notified_ban_state", false)
        set(value) = prefs.edit().putBoolean("last_notified_ban_state", value).apply()

    var isShizukuEnabled: Boolean
        get() = prefs.getBoolean("shizuku_enabled", true)
        set(value) = prefs.edit().putBoolean("shizuku_enabled", value).apply()

    var isTestModeActive: Boolean
        get() = prefs.getBoolean("test_mode_active", false)
        set(value) = prefs.edit().putBoolean("test_mode_active", value).apply()

    fun isAppEnabled(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return when {
            lower.contains("ola") -> isOlaEnabled
            lower.contains("rapido") || lower.contains("captain") || lower.contains("rider") || lower.contains("rr.android") -> isRapidoEnabled
            lower.contains("uber") -> isUberEnabled
            lower.contains("redtaxi") || lower.contains("red_taxi") -> isRedTaxiEnabled
            lower.contains("bharattaxi") || lower.contains("bharat_taxi") || lower.contains("mobility") -> isBharatTaxiEnabled
            lower.contains("porter") || lower.contains("theporter") -> isPorterEnabled
            lower.contains("namma") || lower.contains("yatri") || lower.contains("juspay") -> isNammaYatriEnabled
            lower.contains("swiggy") || lower.contains("zomato") -> true
            else -> prefs.getBoolean("app_enabled_$packageName", true)
        }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val lower = packageName.lowercase(Locale.ROOT)
        when {
            lower.contains("ola") -> isOlaEnabled = enabled
            lower.contains("rapido") || lower.contains("captain") || lower.contains("rider") || lower.contains("rr.android") -> isRapidoEnabled = enabled
            lower.contains("uber") -> isUberEnabled = enabled
            lower.contains("redtaxi") || lower.contains("red_taxi") -> isRedTaxiEnabled = enabled
            lower.contains("bharattaxi") || lower.contains("bharat_taxi") || lower.contains("mobility") -> isBharatTaxiEnabled = enabled
            lower.contains("porter") || lower.contains("theporter") -> isPorterEnabled = enabled
            lower.contains("namma") || lower.contains("yatri") || lower.contains("juspay") -> isNammaYatriEnabled = enabled
            else -> prefs.edit().putBoolean("app_enabled_$packageName", enabled).apply()
        }
        prefs.edit().putBoolean("app_enabled_$packageName", enabled).apply()
    }

    // ── Zone Filters ──
    var isFavZoneEnabled: Boolean
        get() = prefs.getBoolean("fav_zones_enabled", false)
        set(value) = prefs.edit().putBoolean("fav_zones_enabled", value).apply()

    var isNonFavZoneEnabled: Boolean
        get() = prefs.getBoolean("non_fav_zones_enabled", false)
        set(value) = prefs.edit().putBoolean("non_fav_zones_enabled", value).apply()

    var favZones: String?
        get() = prefs.getString("fav_zones", null)
        set(value) = prefs.edit().putString("fav_zones", value).apply()

    var nonFavZones: String?
        get() = prefs.getString("non_fav_zones", null)
        set(value) = prefs.edit().putString("non_fav_zones", value).apply()

    /** Parse fav zones into a list of trimmed, lowercase keywords */
    fun getFavZonesList(): List<String> {
        if (!isFavZoneEnabled) return emptyList()
        return favZones?.split(Regex("[,;\\n\\r]+"))
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    /** Parse non-fav zones into a list of trimmed, lowercase keywords */
    fun getNonFavZonesList(): List<String> {
        if (!isNonFavZoneEnabled) return emptyList()
        return nonFavZones?.split(Regex("[,;\\n\\r]+"))
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    // ── Admin Session ──
    var isAdminLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN_LOGGED_IN, value).apply()

    var loggedInAdminEmail: String?
        get() = prefs.getString(KEY_LOGGED_ADMIN_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_LOGGED_ADMIN_EMAIL, value).apply()

    var loggedInAdminPassword: String?
        get() = prefs.getString(KEY_LOGGED_ADMIN_PASS, null)
        set(value) = prefs.edit().putString(KEY_LOGGED_ADMIN_PASS, value).apply()

    var loggedAdminEmail: String?
        get() = loggedInAdminEmail
        set(value) { loggedInAdminEmail = value }

    var loggedAdminPassword: String?
        get() = loggedInAdminPassword
        set(value) { loggedInAdminPassword = value }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
