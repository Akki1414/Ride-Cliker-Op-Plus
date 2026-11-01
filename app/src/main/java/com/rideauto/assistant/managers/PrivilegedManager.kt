package com.rideauto.assistant.managers

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.rideauto.assistant.privileged.IPrivilegedService
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PrivilegedManager {

    private const val TAG = "PrivilegedManager"
    private const val HOST = "127.0.0.1"
    private const val SERVICE_PORT = 55778
    private const val CONNECT_TIMEOUT_MS = 250
    private const val SO_TIMEOUT_MS = 30000

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(4) { r ->
        Thread(r, "PrivilegedManager-Worker").apply { isDaemon = true }
    }
    private val requestId = AtomicInteger(0)
    private val connected = AtomicBoolean(false)
    private val lock = Any()

    @Volatile private var socket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var input: DataInputStream? = null
    @Volatile private var serviceProxy: IPrivilegedService? = null
    @Volatile private var cachedServiceRunning: Boolean = false
    @Volatile private var lastServiceCheckTime: Long = 0L

    fun isServiceRunning(): Boolean {
        if (isConnected()) return true
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastServiceCheckTime < 2000L) {
            return cachedServiceRunning
        }
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(HOST, SERVICE_PORT), CONNECT_TIMEOUT_MS)
            s.close()
            cachedServiceRunning = true
            lastServiceCheckTime = now
            true
        } catch (e: Exception) {
            cachedServiceRunning = false
            lastServiceCheckTime = now
            false
        }
    }

    fun isConnected(): Boolean {
        if (!connected.get()) return false
        val s = socket ?: return false
        return s.isConnected && !s.isClosed
    }

    private fun ensureConnectionLocked(): Boolean {
        if (isConnected()) return true
        disconnectInternal()
        return try {
            val s = Socket().apply {
                connect(InetSocketAddress(HOST, SERVICE_PORT), CONNECT_TIMEOUT_MS)
                soTimeout = SO_TIMEOUT_MS
                tcpNoDelay = true
            }
            socket = s
            output = DataOutputStream(s.getOutputStream())
            input = DataInputStream(s.getInputStream())
            connected.set(true)
            serviceProxy = createProxy()
            Log.i(TAG, "Auto-reconnected to privileged service")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto-reconnect failed", e)
            false
        }
    }

    private fun disconnectInternal() {
        connected.set(false)
        serviceProxy = null
        try { output?.close() } catch (ignored: Exception) {}
        try { input?.close() } catch (ignored: Exception) {}
        try { socket?.close() } catch (ignored: Exception) {}
        output = null
        input = null
        socket = null
    }

    private fun sendRaw(method: String, args: JSONArray): JSONObject {
        synchronized(lock) {
            if (!ensureConnectionLocked()) {
                throw IllegalStateException("Privileged service not connected")
            }
            val id = requestId.incrementAndGet()
            val req = JSONObject().apply {
                put("id", id)
                put("method", method)
                put("args", args)
            }
            val bytes = req.toString().toByteArray(StandardCharsets.UTF_8)
            val out = output ?: throw IllegalStateException("Output stream null")
            val inStream = input ?: throw IllegalStateException("Input stream null")

            try {
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()

                val readInt = inStream.readInt()
                if (readInt <= 0 || readInt > 67108864) {
                    disconnectInternal()
                    throw IllegalStateException("Invalid response length: $readInt")
                }
                val respBytes = ByteArray(readInt)
                inStream.readFully(respBytes)
                val respJson = JSONObject(String(respBytes, StandardCharsets.UTF_8))
                if (!respJson.isNull("error")) {
                    throw RemoteException("Service error: " + respJson.optString("error"))
                }
                return respJson
            } catch (e: IOException) {
                Log.w(TAG, "Socket I/O error, disconnecting: ${e.message}")
                disconnectInternal()
                throw e
            }
        }
    }

    private fun createProxy(): IPrivilegedService {
        return object : IPrivilegedService.Stub() {
            override fun getVersion(): Int = sendRaw("getVersion", JSONArray()).optInt("result", 0)
            override fun ping(): Boolean = sendRaw("ping", JSONArray()).optBoolean("result", false)
            override fun grantAccessibilityPermission(packageName: String) {
                sendRaw("grantAccessibilityPermission", JSONArray().put(packageName))
            }
            override fun enableAccessibilityService(componentName: String) {
                sendRaw("enableAccessibilityService", JSONArray().put(componentName))
            }
            override fun getWindowNodes(packageName: String): List<android.view.accessibility.AccessibilityNodeInfo> = ArrayList()
            override fun forceStopApp(packageName: String) {
                sendRaw("forceStopApp", JSONArray().put(packageName))
            }
            override fun startAppActivity(packageName: String, activityName: String) {
                sendRaw("startAppActivity", JSONArray().put(packageName).put(activityName))
            }
            override fun clearAppData(packageName: String) {
                sendRaw("clearAppData", JSONArray().put(packageName))
            }
            override fun getRunningProcesses(): List<android.app.ActivityManager.RunningAppProcessInfo> = ArrayList()
            override fun injectTap(x: Int, y: Int) {
                sendRaw("injectTap", JSONArray().put(x).put(y))
            }
            override fun injectSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) {
                sendRaw("injectSwipe", JSONArray().put(x1).put(y1).put(x2).put(y2).put(duration))
            }
            override fun injectKeyEvent(keyCode: Int) {
                sendRaw("injectKeyEvent", JSONArray().put(keyCode))
            }
            override fun postSystemNotification(title: String, message: String) {
                sendRaw("postSystemNotification", JSONArray().put(title).put(message))
            }
            override fun clearNotification(packageName: String, id: Int) {
                sendRaw("clearNotification", JSONArray().put(packageName).put(id))
            }
            override fun disableBatteryOptimization(packageName: String) {
                sendRaw("disableBatteryOptimization", JSONArray().put(packageName))
            }
            override fun whitelistAppFromDoze(packageName: String) {
                sendRaw("whitelistAppFromDoze", JSONArray().put(packageName))
            }
            override fun keepAwake(enable: Boolean) {
                sendRaw("keepAwake", JSONArray().put(enable))
            }
            override fun putSecureSetting(key: String, value: String) {
                sendRaw("putSecureSetting", JSONArray().put(key).put(value))
            }
            override fun putGlobalSetting(key: String, value: String) {
                sendRaw("putGlobalSetting", JSONArray().put(key).put(value))
            }
            override fun putSystemSetting(key: String, value: String) {
                sendRaw("putSystemSetting", JSONArray().put(key).put(value))
            }
            override fun grantPermission(packageName: String, permission: String) {
                sendRaw("grantPermission", JSONArray().put(packageName).put(permission))
            }
            override fun revokePermission(packageName: String, permission: String) {
                sendRaw("revokePermission", JSONArray().put(packageName).put(permission))
            }
            override fun setComponentEnabled(componentName: String, enabled: Boolean) {
                sendRaw("setComponentEnabled", JSONArray().put(componentName).put(enabled))
            }
            override fun captureScreen(): android.graphics.Bitmap? = null
            override fun capturePackageWindow(packageName: String): android.graphics.Bitmap? = null
            override fun executeShellCommand(command: String): String {
                val r = sendRaw("executeShellCommand", JSONArray().put(command))
                return r.optString("result", "")
            }
            override fun executeShellCommandWithOutput(command: String): String {
                val r = sendRaw("executeShellCommandWithOutput", JSONArray().put(command))
                return r.optString("result", "")
            }
            override fun bringAppToFront(packageName: String) {
                sendRaw("bringAppToFront", JSONArray().put(packageName))
            }
            override fun simulateBackButton() {
                sendRaw("simulateBackButton", JSONArray())
            }
            override fun simulateHomeButton() {
                sendRaw("simulateHomeButton", JSONArray())
            }
            override fun openRecentApps() {
                sendRaw("openRecentApps", JSONArray())
            }
            override fun shutdown() {
                try { sendRaw("shutdown", JSONArray()) } catch (ignored: Exception) {}
                disconnectInternal()
            }
        }
    }

    fun getService(): IPrivilegedService? {
        if ((serviceProxy == null || !isConnected()) && isServiceRunning()) {
            synchronized(lock) {
                if (serviceProxy == null || !isConnected()) {
                    ensureConnectionLocked()
                }
            }
        }
        return serviceProxy
    }

    fun tap(x: Float, y: Float, durationMs: Long = 100L): Boolean {
        if (!isServiceRunning()) return false
        val xi = x.toInt()
        val yi = y.toInt()
        executor.execute {
            try {
                if (durationMs > 0 && durationMs != 100L) {
                    runShellCommand("input swipe $xi $yi $xi $yi $durationMs")
                } else {
                    getService()?.injectTap(xi, yi)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tap injection failed", e)
            }
        }
        return true
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350L): Boolean {
        if (!isServiceRunning()) return false
        executor.execute {
            try {
                getService()?.injectSwipe(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt(), durationMs.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Swipe injection failed", e)
            }
        }
        return true
    }

    fun runShellCommand(command: String): String {
        return try {
            getService()?.executeShellCommand(command) ?: "Privileged service not connected"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun needsAdbTap(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        if (pkg.contains("rapido") || pkg.contains("rr.android")) {
            return false
        }
        return pkg.contains("ola") || pkg.contains("olacabs") || pkg.contains("uber") || pkg.contains("ubercab")
    }

    fun getFullSetupCommand(context: Context): String {
        val apk = context.packageCodePath
        val pkg = context.packageName
        val accessSvc = "$pkg/com.rideauto.assistant.TouchInputService"
        return "adb shell \"set -e\nexport CLASSPATH=$apk\npid=\$(ps -A | grep screenguard_privileged | grep -v grep | awk '{print \$2}')\n[ -n \\\"\$pid\\\" ] && kill -9 \$pid 2>/dev/null || true\nnohup app_process /system/bin --nice-name=screenguard_privileged com.rideauto.assistant.privileged.ServiceStarter > /data/local/tmp/screenguard_priv.log 2>&1 &\nsleep 2\nsettings put secure enabled_accessibility_services $accessSvc\nsettings put secure accessibility_enabled 1\ndumpsys deviceidle whitelist +$pkg || true\nam set-inactive --user 0 $pkg false || true\necho OK\""
    }
}
