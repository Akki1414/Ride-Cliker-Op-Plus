package com.rideauto.assistant.security

import android.content.Context

/**
 * Neutralized Security Guard for Ride Clicker Pro+.
 * Anti-tamper and force-termination checks removed to prevent crashes on Shizuku,
 * custom ROMs, developer options, and re-signed builds.
 */
object AppSecurityGuard {

    @Volatile
    var isSecurityVerified: Boolean = true
        private set

    fun checkIntegrity(context: Context): Boolean {
        isSecurityVerified = true
        return true
    }

    fun verifyOrTerminate(context: Context) {
        // No-op: Anti-tamper process termination disabled
    }

    fun getAppSignatureSHA256(context: Context): String = "VERIFIED"
}
