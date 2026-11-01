package com.rideauto.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Manages Shizuku (rootless ADB) integration for ultra-fast
 * touch injection (<1ms) via persistent shell `input tap` stream.
 * Supports both instance-based execution and static helper calls.
 */
class ShizukuManager(private val context: Context) {

    init {
        initialize(context)
    }

    companion object {
        private const val TAG = "ShizukuManager"
        const val SHIZUKU_REQUEST_CODE = 10001

        const val MONKEY_PACKAGE = "com.monkey.privileged.api"
        const val SPIDER_PACKAGE = "com.spider.privileged.api"
        const val PARI_PACKAGE = "com.pari.privileged.api"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        @Volatile private var persistentProcess: Process? = null
        @Volatile private var persistentOutput: DataOutputStream? = null
        private val streamLock = Any()
        private val tapExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        @Volatile private var appContext: Context? = null

        fun initialize(context: Context) {
            appContext = context.applicationContext
        }

        /** Check if Monkey, Spider, Pari or Shizuku Manager app is installed */
        fun isShizukuInstalled(context: Context? = null): Boolean {
            val ctx = context ?: appContext
            return if (ctx != null) {
                isPackageInstalled(ctx, MONKEY_PACKAGE) || isPackageInstalled(ctx, SPIDER_PACKAGE) || isPackageInstalled(ctx, PARI_PACKAGE) || isPackageInstalled(ctx, SHIZUKU_PACKAGE)
            } else {
                false
            }
        }

        fun isMonkeyInstalled(context: Context? = null): Boolean {
            val ctx = context ?: appContext
            return if (ctx != null) isPackageInstalled(ctx, MONKEY_PACKAGE) else false
        }

        fun isSpiderInstalled(context: Context? = null): Boolean {
            val ctx = context ?: appContext
            return if (ctx != null) isPackageInstalled(ctx, SPIDER_PACKAGE) else false
        }

        fun isPariInstalled(context: Context? = null): Boolean = isShizukuInstalled(context)

        private fun isPackageInstalled(ctx: Context, packageName: String): Boolean {
            return try {
                ctx.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        /** Check if Pari / Shizuku binder service is alive */
        fun isShizukuRunning(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                false
            }
        }

        fun isPariRunning(): Boolean = isShizukuRunning()

        /** Check if this app has Pari / Shizuku permission granted */
        fun isPermissionGranted(): Boolean {
            return try {
                if (!isShizukuRunning()) return false
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }

        /** Full readiness check — Shizuku must be installed, running, and permission granted */
        fun isReady(): Boolean = isShizukuRunning() && isPermissionGranted()
        fun isShizukuAvailable(): Boolean = isReady()

        /** Open installed Monkey / Spider / Pari / Shizuku app */
        fun openShizukuApp(context: Context? = null): Boolean {
            val ctx = context ?: appContext ?: return false
            return try {
                val launchIntent = ctx.packageManager.getLaunchIntentForPackage(MONKEY_PACKAGE)
                    ?: ctx.packageManager.getLaunchIntentForPackage(SPIDER_PACKAGE)
                    ?: ctx.packageManager.getLaunchIntentForPackage(PARI_PACKAGE)
                    ?: ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Shizuku/Spider app", e)
                false
            }
        }

        /** Request Pari / Shizuku permission from user */
        fun requestPermission(
            context: Context? = null,
            fallbackDownloadUrl: String? = null,
            onResult: ((String) -> Unit)? = null
        ) {
            val ctx = context ?: appContext
            try {
                if (isShizukuRunning()) {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    onResult?.invoke("Permission requested")
                } else if (isShizukuInstalled(ctx)) {
                    val opened = openShizukuApp(ctx)
                    if (opened) {
                        onResult?.invoke("Shizuku / Spider app not running. Opening app...")
                    } else {
                        onResult?.invoke("Shizuku / Spider app not running")
                    }
                } else {
                    if (!fallbackDownloadUrl.isNullOrBlank() && ctx != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackDownloadUrl)).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                        onResult?.invoke("Shizuku / Spider not installed. Opening download page...")
                    } else {
                        onResult?.invoke("Shizuku / Spider not installed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Permission request failed", e)
                onResult?.invoke("Failed to request permission: ${e.message}")
            }
        }

        fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
            try { Shizuku.addBinderReceivedListener(listener) } catch (e: Exception) { Log.w(TAG, "addBinderReceivedListener failed", e) }
        }

        fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
            try { Shizuku.removeBinderReceivedListener(listener) } catch (e: Exception) { Log.w(TAG, "removeBinderReceivedListener failed", e) }
        }

        fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
            try { Shizuku.addBinderDeadListener(listener) } catch (e: Exception) { Log.w(TAG, "addBinderDeadListener failed", e) }
        }

        fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
            try { Shizuku.removeBinderDeadListener(listener) } catch (e: Exception) { Log.w(TAG, "removeBinderDeadListener failed", e) }
        }

        fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
            try { Shizuku.addRequestPermissionResultListener(listener) } catch (e: Exception) { Log.w(TAG, "addRequestPermissionResultListener failed", e) }
        }

        fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
            try { Shizuku.removeRequestPermissionResultListener(listener) } catch (e: Exception) { Log.w(TAG, "removeRequestPermissionResultListener failed", e) }
        }

        fun executeCommand(command: String): String? {
            val ctx = appContext
            return if (ctx != null) {
                ShizukuManager(ctx).executeCommand(command)
            } else {
                try {
                    if (!isPermissionGranted()) return null
                    val newProcessMethod = findNewProcessMethodStatic() ?: return null
                    val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.appendLine(line)
                    }
                    process.waitFor()
                    reader.close()
                    output.toString().trim()
                } catch (e: Exception) {
                    null
                }
            }
        }

        fun tap(x: Int, y: Int): Boolean {
            val ctx = appContext
            return if (ctx != null) {
                ShizukuManager(ctx).tapScreen(x, y)
            } else {
                tapScreenStatic(x, y)
            }
        }

        fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 300): Boolean {
            val ctx = appContext
            return if (ctx != null) {
                ShizukuManager(ctx).swipeScreen(startX, startY, endX, endY, durationMs)
            } else {
                swipeScreenStatic(startX, startY, endX, endY, durationMs)
            }
        }

        fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean =
            swipe(startX, startY, endX, endY, durationMs.toInt())

        private fun findNewProcessMethodStatic(): java.lang.reflect.Method? {
            return try {
                Shizuku::class.java.methods.firstOrNull {
                    it.name == "newProcess" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == Array<String>::class.java
                }?.apply { isAccessible = true }
                    ?: Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    ).apply { isAccessible = true }
            } catch (e: Exception) {
                null
            }
        }

        private fun tapScreenStatic(x: Int, y: Int): Boolean {
            if (x <= 0 || y <= 0) return false
            tapExecutor.execute {
                synchronized(streamLock) {
                    try {
                        if (persistentOutput == null || persistentProcess == null) {
                            initPersistentShellStatic()
                        }
                        persistentOutput?.apply {
                            writeBytes("input tap $x $y\n")
                            flush()
                        }
                    } catch (e: Exception) {
                        resetShellStatic()
                    }
                }
            }
            return true
        }

        private fun swipeScreenStatic(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean {
            if (startX <= 0 || startY <= 0 || endX <= 0 || endY <= 0) return false
            tapExecutor.execute {
                synchronized(streamLock) {
                    try {
                        if (persistentOutput == null || persistentProcess == null) {
                            initPersistentShellStatic()
                        }
                        persistentOutput?.apply {
                            writeBytes("input swipe $startX $startY $endX $endY $durationMs\n")
                            flush()
                        }
                    } catch (e: Exception) {
                        resetShellStatic()
                    }
                }
            }
            return true
        }

        private fun initPersistentShellStatic() {
            try {
                if (!isPermissionGranted()) return
                val newProcessMethod = findNewProcessMethodStatic() ?: return
                val proc = newProcessMethod.invoke(null, arrayOf("sh"), null, null) as Process
                persistentProcess = proc
                persistentOutput = DataOutputStream(proc.outputStream)
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.inputStream))
                        while (reader.readLine() != null) {}
                    } catch (e: Exception) {}
                }.apply { isDaemon = true; start() }
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.errorStream))
                        while (reader.readLine() != null) {}
                    } catch (e: Exception) {}
                }.apply { isDaemon = true; start() }
            } catch (e: Exception) {}
        }

        private fun resetShellStatic() {
            try { persistentOutput?.close() } catch (e: Exception) {}
            try { persistentProcess?.destroy() } catch (e: Exception) {}
            persistentOutput = null
            persistentProcess = null
        }
    }

    /** Check if Pari or Shizuku Manager app is installed */
    fun isShizukuInstalled(): Boolean = isShizukuInstalled(context)

    fun isPariInstalled(): Boolean = isShizukuInstalled(context)

    /** Helper to launch Spider, Pari or Shizuku Manager app */
    fun openShizukuApp(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SPIDER_PACKAGE)
            ?: context.packageManager.getLaunchIntentForPackage(PARI_PACKAGE)
            ?: context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }

    fun openSpiderApp(): Boolean = openShizukuApp()
    fun openPariApp(): Boolean = openShizukuApp()

    /** Check if Pari / Shizuku binder service is alive */
    fun isShizukuRunning(): Boolean = ShizukuManager.isShizukuRunning()

    fun isPariRunning(): Boolean = isShizukuRunning()

    /** Check if this app has Pari / Shizuku permission granted */
    fun isPermissionGranted(): Boolean = ShizukuManager.isPermissionGranted()

    /** Request Pari / Shizuku permission from user */
    fun requestPermission() {
        requestPermission(context)
    }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        ShizukuManager.addBinderReceivedListener(listener)
    }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        ShizukuManager.removeBinderReceivedListener(listener)
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        ShizukuManager.addBinderDeadListener(listener)
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        ShizukuManager.removeBinderDeadListener(listener)
    }

    fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        ShizukuManager.addRequestPermissionResultListener(listener)
    }

    fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        ShizukuManager.removeRequestPermissionResultListener(listener)
    }

    private fun findNewProcessMethod(): java.lang.reflect.Method? = findNewProcessMethodStatic()

    private fun initPersistentShell() {
        initPersistentShellStatic()
    }

    private fun resetShell() {
        resetShellStatic()
    }

    /**
     * Execute a shell command via Shizuku.
     * @return Command output string, or null on failure.
     */
    fun executeCommand(command: String): String? {
        if (!isPermissionGranted()) {
            Log.w(TAG, "Cannot execute command: Shizuku permission not granted")
            return null
        }

        return try {
            val newProcessMethod = findNewProcessMethod() ?: return null
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            // Drain stdout to prevent deadlock
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            // Drain stderr
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine() != null) { /* discard */ }

            process.waitFor()
            reader.close()
            errReader.close()

            val result = output.toString().trim()
            Log.d(TAG, "Command executed: $command -> $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            null
        }
    }

    /**
     * Perform a non-blocking ultra-fast screen tap via persistent Shizuku shell injection (<1ms).
     */
    fun tapScreen(x: Int, y: Int): Boolean = tapScreenStatic(x, y)

    /**
     * Perform a non-blocking horizontal/vertical swipe gesture via persistent Shizuku shell injection.
     */
    fun swipeScreen(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int = 300): Boolean =
        swipeScreenStatic(startX, startY, endX, endY, durationMs)

    fun swipeScreen(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean =
        swipeScreen(startX, startY, endX, endY, durationMs.toInt())

    /**
     * Tap multiple coordinates across different apps simultaneously.
     */
    fun tapAllCoordinates(coordinates: List<Pair<Int, Int>>): Boolean {
        if (coordinates.isEmpty()) return false
        tapExecutor.execute {
            synchronized(streamLock) {
                try {
                    if (persistentOutput == null || persistentProcess == null) {
                        initPersistentShell()
                    }
                    val out = persistentOutput
                    if (out != null) {
                        for ((x, y) in coordinates) {
                            if (x > 0 && y > 0) {
                                out.writeBytes("input tap $x $y\n")
                            }
                        }
                        out.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "tapAllCoordinates failed", e)
                    resetShell()
                }
            }
        }
        return true
    }

    /** Get a human-readable status summary */
    fun getStatusSummary(): String {
        return buildString {
            append("Installed: ${isShizukuInstalled()}")
            append("\nRunning: ${isShizukuRunning()}")
            append("\nPermission: ${if (isPermissionGranted()) "Granted" else "Denied"}")
        }
    }

    /** Full readiness check — Shizuku must be installed, running, and permission granted */
    fun isReady(): Boolean = ShizukuManager.isReady()
}
