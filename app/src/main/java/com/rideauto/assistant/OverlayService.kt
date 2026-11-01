package com.rideauto.assistant

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.content.pm.ServiceInfo
import android.util.Log
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.rideauto.assistant.data.RideHistoryDatabase
import com.rideauto.assistant.security.AppSecurityGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var prefs: PreferencesHelper
    private val authManager by lazy { AuthManager(this) }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "ride_assistant_overlay_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.rideclicker.proplus.action.START_OVERLAY"
        const val ACTION_STOP = "com.rideclicker.proplus.action.STOP_OVERLAY"
        const val ACTION_TOGGLE = "com.rideclicker.proplus.action.TOGGLE_SERVICE"
        const val ACTION_FILTER_UPDATED = "com.rideclicker.proplus.FILTER_UPDATED"
        const val EXTRA_OPEN_TAB = "open_tab"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OverlayService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                try {
                    context.stopService(Intent(context, OverlayService::class.java))
                } catch (ignored: Exception) {}
            }
        }
    }

    private val filterUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("from_overlay", false) == true) return
            updateOverlayState()
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "service_enabled" || key == "fare_filter_enabled" || key == "min_fare" || key == "max_fare" || key == "is_banned" ||
            key == "distance_filter_enabled" || key == "max_pickup_km" || key == "fav_zones_enabled" || key == "non_fav_zones_enabled" ||
            key == "fav_zones" || key == "non_fav_zones") {
            updateOverlayState()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)
        createNotificationChannel()
                try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start foreground service", t)
        }

        try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val filter = IntentFilter(ACTION_FILTER_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(filterUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(filterUpdateReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.isOverlayEnabled = false
                stopOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                toggleServiceState()
            }
            else -> {
                if (Settings.canDrawOverlays(this)) {
                    showOverlay()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showOverlay() {
        if (overlayView != null) {
            updateOverlayState()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }
        overlayParams = params

        val container = try {
            LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        } catch (e: Exception) {
            createProgrammaticOverlay()
        }
        overlayView = container

        val btnToggle = container.findViewById<ImageButton?>(R.id.btn_overlay_toggle)
        val btnSettings = container.findViewById<ImageButton?>(R.id.btn_overlay_settings)
        val tvInfo = container.findViewById<TextView?>(R.id.tv_overlay_info)

        btnToggle?.setOnClickListener {
            toggleServiceState()
        }

        btnSettings?.setOnClickListener {
            openFiltersInApp()
        }

        // Add view to WindowManager first before updating state
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateOverlayState()

        // Dragging & Click Handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var lastLayoutUpdateTime = 0L

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isClick = false
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastLayoutUpdateTime >= 16L) {
                            lastLayoutUpdateTime = now
                            if (overlayView?.isAttachedToWindow == true) {
                                try {
                                    windowManager?.updateViewLayout(overlayView, params)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isClick) {
                        prefs.overlayX = params.x
                        prefs.overlayY = params.y
                        if (overlayView?.isAttachedToWindow == true) {
                            try {
                                windowManager?.updateViewLayout(overlayView, params)
                            } catch (_: Exception) {}
                        }
                    } else {
                        val touchX = event.x.toInt()
                        val touchY = event.y.toInt()

                        val settingsRect = Rect()
                        btnSettings?.getHitRect(settingsRect)
                        settingsRect.inset(-24, -24)

                        val toggleRect = Rect()
                        btnToggle?.getHitRect(toggleRect)
                        toggleRect.inset(-32, -32)

                        val tvInfoRect = Rect()
                        tvInfo?.getHitRect(tvInfoRect)
                        tvInfoRect.inset(-16, -16)

                        if (settingsRect.contains(touchX, touchY)) {
                            openFiltersInApp()
                        } else if (toggleRect.contains(touchX, touchY) || tvInfoRect.contains(touchX, touchY)) {
                            toggleServiceState()
                        } else {
                            // Any tap on the widget not on the settings gear triggers the toggle
                            toggleServiceState()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleServiceState() {
        if (!authManager.isSubscriptionValid() && !prefs.isTestModeActive) {
            prefs.isServiceEnabled = false
            updateOverlayState()
            return
        }

        prefs.isServiceEnabled = !prefs.isServiceEnabled
        updateOverlayState()

        // Broadcast to MainActivity (mark as from_overlay to prevent loopback)
        val intent = Intent(ACTION_FILTER_UPDATED).apply {
            putExtra("from_overlay", true)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun openFiltersInApp() {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, "FILTERS")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(appIntent)
    }

    private fun updateOverlayState() {
        try {
            val container = overlayView ?: return
            val btnToggle = container.findViewById<ImageButton?>(R.id.btn_overlay_toggle)
            val tvInfo = container.findViewById<TextView?>(R.id.tv_overlay_info)

            val isEnabled = prefs.isServiceEnabled
            val isSubscribed = authManager.isSubscriptionValid() || prefs.isTestModeActive

            if (!isSubscribed) {
                btnToggle?.setImageResource(android.R.drawable.presence_busy)
                btnToggle?.setColorFilter(Color.parseColor("#FF3B30"))
                tvInfo?.text = "🔴 Sub Expired"
                tvInfo?.setTextColor(Color.parseColor("#FF5252"))
                container.invalidate()
                return
            }

            // 1. Configure ON/OFF colors: Green = ON, Red = OFF
            val strokeColorHex = if (isEnabled) "#30D158" else "#FF3B30"
            if (isEnabled) {
                btnToggle?.setImageResource(android.R.drawable.presence_online)
                btnToggle?.setColorFilter(Color.parseColor("#30D158")) // Green = ON
                tvInfo?.setTextColor(Color.parseColor("#00E676"))
            } else {
                btnToggle?.setImageResource(android.R.drawable.presence_busy)
                btnToggle?.setColorFilter(Color.parseColor("#FF3B30")) // Red = OFF
                tvInfo?.setTextColor(Color.parseColor("#FF5252"))
            }

            // Apply dynamic glowing border stroke to the HUD container
            try {
                val bg = container.background as? android.graphics.drawable.GradientDrawable
                bg?.mutate()
                bg?.setStroke(4, Color.parseColor(strokeColorHex))
            } catch (_: Exception) {}

            // 2. Build persistent filter display (always shows values even when turned off)
            val filterParts = mutableListOf<String>()

            // Fare Filter Value
            if (prefs.isFareFilterEnabled) {
                val minF = prefs.minFare
                val maxF = prefs.maxFare
                if (maxF > 0) {
                    filterParts.add("₹$minF-$maxF")
                } else {
                    filterParts.add("₹$minF+")
                }
            }

            // Pickup Distance Value
            if (prefs.isDistanceFilterEnabled) {
                val maxDist = prefs.maxPickupKm
                val distStr = if (maxDist % 1.0f == 0.0f) {
                    "${maxDist.toInt()}km"
                } else {
                    "${maxDist}km"
                }
                filterParts.add(distStr)
            }

            // Favourite Zone Filter (show badge whenever enabled)
            if (prefs.isFavZoneEnabled) {
                filterParts.add("Fev")
            }

            // Non-Favourite Zone Filter (show badge whenever enabled)
            if (prefs.isNonFavZoneEnabled) {
                filterParts.add("Non Fev")
            }

            val dot = if (isEnabled) "🟢" else "🔴"
            val statusText = if (filterParts.isNotEmpty()) {
                "$dot ${filterParts.joinToString(" • ")}"
            } else {
                if (isEnabled) "🟢 ACTIVE" else "🔴 INACTIVE"
            }

            val previousText = tvInfo?.text?.toString()
            tvInfo?.text = statusText

            btnToggle?.invalidate()
            tvInfo?.invalidate()
            container.invalidate()

            if (previousText != statusText && overlayView?.isAttachedToWindow == true) {
                container.requestLayout()
                overlayParams?.let { p ->
                    try {
                        windowManager?.updateViewLayout(overlayView, p)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createProgrammaticOverlay(): View {
        val layout = android.widget.LinearLayout(this).apply {
            id = R.id.overlay_container
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(24, 12, 24, 12)
            setBackgroundResource(R.drawable.overlay_background)
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnToggle = ImageButton(this).apply {
            id = R.id.btn_overlay_toggle
            setImageResource(android.R.drawable.presence_online)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 16, 0)
        }

        val tvInfo = TextView(this).apply {
            id = R.id.tv_overlay_info
            text = "🟢 ACTIVE"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 16, 0)
            isClickable = false
            isFocusable = false
        }

        val btnSettings = ImageButton(this).apply {
            id = R.id.btn_overlay_settings
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 12, 0)
        }

        layout.addView(btnToggle)
        layout.addView(tvInfo)
        layout.addView(btnSettings)

        return layout
    }

    private fun stopOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (ignored: Exception) {}
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride Clicker Pro+ Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running floating overlay status HUD"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ride Clicker Pro+")
            .setContentText("Floating HUD active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(filterUpdateReceiver)
        } catch (e: Exception) {}
        stopOverlay()
        super.onDestroy()
    }
}
