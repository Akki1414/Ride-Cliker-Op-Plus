package com.rideauto.assistant.privileged

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class PrivilegedService(private val context: Context?) : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedService"
    }

    override fun getVersion(): Int = 1

    override fun ping(): Boolean = true

    override fun grantAccessibilityPermission(packageName: String) {
        try {
            val componentName = "$packageName/com.rideauto.assistant.TouchInputService"
            enableAccessibilityService(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant accessibility permission", e)
        }
    }

    override fun enableAccessibilityService(componentName: String) {
        try {
            if (context != null) {
                val resolver = context.contentResolver
                Settings.Secure.putString(resolver, "enabled_accessibility_services", componentName)
                Settings.Secure.putString(resolver, "accessibility_enabled", "1")
            } else {
                executeShellCommand("settings put secure enabled_accessibility_services '$componentName'")
                executeShellCommand("settings put secure accessibility_enabled 1")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service", e)
        }
    }

    override fun getWindowNodes(packageName: String): List<AccessibilityNodeInfo> = ArrayList()

    override fun forceStopApp(packageName: String) {
        executeShellCommand("am force-stop $packageName")
    }

    override fun startAppActivity(packageName: String, activityName: String) {
        executeShellCommand("am start -n $packageName/$activityName")
    }

    override fun clearAppData(packageName: String) {
        executeShellCommand("pm clear $packageName")
    }

    override fun getRunningProcesses(): List<ActivityManager.RunningAppProcessInfo> {
        val list = ArrayList<ActivityManager.RunningAppProcessInfo>()
        try {
            if (context != null) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.runningAppProcesses?.let { list.addAll(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running processes", e)
        }
        return list
    }

    override fun injectTap(x: Int, y: Int) {
        executeShellCommand("input tap $x $y")
    }

    override fun injectSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) {
        executeShellCommand("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    override fun injectKeyEvent(keyCode: Int) {
        executeShellCommand("input keyevent $keyCode")
    }

    override fun postSystemNotification(title: String, message: String) {
        Log.i(TAG, "Notification: $title - $message")
    }

    override fun clearNotification(packageName: String, id: Int) {
        Log.i(TAG, "Clear notification: $packageName, id=$id")
    }

    override fun disableBatteryOptimization(packageName: String) {
        executeShellCommand("dumpsys deviceidle whitelist +$packageName")
    }

    override fun whitelistAppFromDoze(packageName: String) {
        disableBatteryOptimization(packageName)
    }

    override fun keepAwake(enable: Boolean) {
        if (enable) {
            executeShellCommand("svc power stayon true")
        } else {
            executeShellCommand("svc power stayon false")
        }
    }

    override fun putSecureSetting(key: String, value: String) {
        executeShellCommand("settings put secure $key $value")
    }

    override fun putGlobalSetting(key: String, value: String) {
        executeShellCommand("settings put global $key $value")
    }

    override fun putSystemSetting(key: String, value: String) {
        executeShellCommand("settings put system $key $value")
    }

    override fun grantPermission(packageName: String, permission: String) {
        executeShellCommand("pm grant $packageName $permission")
    }

    override fun revokePermission(packageName: String, permission: String) {
        executeShellCommand("pm revoke $packageName $permission")
    }

    override fun setComponentEnabled(componentName: String, enabled: Boolean) {
        val state = if (enabled) "enable" else "disable"
        executeShellCommand("pm $state $componentName")
    }

    override fun captureScreen(): Bitmap? {
        return try {
            val capturePath = "/data/local/tmp/screenguard_screen_${System.currentTimeMillis()}.png"
            executeShellCommandWithOutput("screencap -p $capturePath")
            val file = File(capturePath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(capturePath)
                file.delete()
                bitmap
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen", e)
            null
        }
    }

    override fun capturePackageWindow(packageName: String): Bitmap? = captureScreen()

    override fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
            ""
        }
    }

    override fun executeShellCommandWithOutput(command: String): String = executeShellCommand(command)

    override fun bringAppToFront(packageName: String) {
        executeShellCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    override fun simulateBackButton() {
        injectKeyEvent(4)
    }

    override fun simulateHomeButton() {
        injectKeyEvent(3)
    }

    override fun openRecentApps() {
        injectKeyEvent(187)
    }

    override fun shutdown() {
        executeShellCommand("reboot -p")
    }
}
