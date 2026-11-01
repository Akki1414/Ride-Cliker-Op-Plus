package com.rideauto.assistant.privileged

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuStarterHelper {

    const val SERVICE_PORT = 55778
    private const val TAG = "ShizukuStarterHelper"

    fun getOwnDaemonAdbCommand(context: Context): String {
        val apkPath = context.packageCodePath
        return "adb shell \"export CLASSPATH=$apkPath && app_process /system/bin --nice-name=screenguard_privileged com.rideauto.assistant.privileged.ServiceStarter\""
    }

    fun isShizukuAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun startService(
        context: Context,
        debugPortStr: String = "",
        onLog: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("Checking privileged service status...")
        if (com.rideauto.assistant.managers.PrivilegedManager.isServiceRunning()) {
            onLog("✅ Privileged daemon is already active on port $SERVICE_PORT")
            return@withContext true
        }

        // 1. Try starting via Shizuku Binder if available
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            onLog("Starting daemon via Shizuku shell...")
            startOwnDaemonViaShizuku(context, onLog)
            delay(1500)
            if (com.rideauto.assistant.managers.PrivilegedManager.isServiceRunning()) {
                onLog("✅ Privileged service started successfully via Shizuku!")
                return@withContext true
            }
        }

        // 2. Try starting via Root (su)
        onLog("Trying direct root (su)...")
        if (tryStartViaRoot(context, onLog)) {
            delay(1500)
            if (com.rideauto.assistant.managers.PrivilegedManager.isServiceRunning()) {
                onLog("✅ Privileged service started successfully via Root!")
                return@withContext true
            }
        }

        // 3. Try starting via Wireless ADB if port provided
        val port = debugPortStr.toIntOrNull() ?: 0
        if (port > 0) {
            onLog("Trying Wireless ADB connection on port $port...")
            if (tryStartViaWirelessAdb(context, port, onLog)) {
                delay(1500)
                if (com.rideauto.assistant.managers.PrivilegedManager.isServiceRunning()) {
                    onLog("✅ Privileged service started via Wireless ADB!")
                    return@withContext true
                }
            }
        }

        onLog("⚠️ Could not start daemon automatically. Run setup command via ADB.")
        false
    }

    private fun startOwnDaemonViaShizuku(context: Context, onLog: (String) -> Unit) {
        val apkPath = context.packageCodePath
        val cmd = "nohup app_process /system/bin --nice-name=screenguard_privileged com.rideauto.assistant.privileged.ServiceStarter > /dev/null 2>&1 &"
        executeViaShizuku("export CLASSPATH=$apkPath\n$cmd")
    }

    private fun executeViaShizuku(command: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()
            proc.waitFor()
            sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "executeViaShizuku failed: ${e.message}")
            ""
        }
    }

    private fun tryStartViaRoot(context: Context, onLog: (String) -> Unit): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec("su")
            val os = proc.outputStream
            val apkPath = context.packageCodePath
            val script = "export CLASSPATH=$apkPath\nnohup app_process /system/bin --nice-name=screenguard_privileged com.rideauto.assistant.privileged.ServiceStarter > /dev/null 2>&1 &\nexit\n"
            os.write(script.toByteArray())
            os.flush()
            proc.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun tryStartViaWirelessAdb(context: Context, port: Int, onLog: (String) -> Unit): Boolean {
        return try {
            AdbClient.executeCommand("127.0.0.1", port, "export CLASSPATH=${context.packageCodePath} && nohup app_process /system/bin --nice-name=screenguard_privileged com.rideauto.assistant.privileged.ServiceStarter > /dev/null 2>&1 &")
            true
        } catch (e: Exception) {
            false
        }
    }
}
