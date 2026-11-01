package com.rideauto.assistant

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkTimeHelper {

    private var initialNetworkTimeMs: Long = 0L
    private var initialElapsedRealtimeMs: Long = 0L
    @Volatile
    private var isInitialized: Boolean = false

    fun isInternetAvailable(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isNotSuspended = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            return hasInternet && (isValidated || isNotSuspended)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Synchronizes authoritative network time from trusted servers via HTTP Date Headers.
     */
    suspend fun syncNetworkTime(): Long = withContext(Dispatchers.IO) {
        val servers = listOf(
            "https://www.google.com",
            "https://www.cloudflare.com",
            "https://time.google.com"
        )

        for (server in servers) {
            try {
                val url = URL(server)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()

                val serverDateHeader = connection.getHeaderField("Date")
                connection.disconnect()

                if (!serverDateHeader.isNullOrBlank()) {
                    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    val date = format.parse(serverDateHeader)
                    if (date != null) {
                        initialNetworkTimeMs = date.time
                        initialElapsedRealtimeMs = SystemClock.elapsedRealtime()
                        isInitialized = true
                        return@withContext initialNetworkTimeMs
                    }
                }
            } catch (e: Exception) {
                // Try next fallback server
            }
        }

        // Fallback if network call fails
        if (!isInitialized) {
            initialNetworkTimeMs = System.currentTimeMillis()
            initialElapsedRealtimeMs = SystemClock.elapsedRealtime()
            isInitialized = true
        }
        return@withContext getCurrentNetworkTime()
    }

    /**
     * Returns current authoritative time in milliseconds.
     * Monotonically tracks time using SystemClock.elapsedRealtime() to prevent device clock manipulation.
     */
    fun getCurrentNetworkTime(): Long {
        if (!isInitialized) {
            return System.currentTimeMillis()
        }
        val elapsedSinceSync = SystemClock.elapsedRealtime() - initialElapsedRealtimeMs
        return initialNetworkTimeMs + elapsedSinceSync
    }

    /**
     * Formats remaining time into a detailed string: "18d 14h 32m left"
     */
    fun formatRemainingTime(expiryTimestamp: Long): String {
        val now = getCurrentNetworkTime()
        val diffMs = expiryTimestamp - now
        if (diffMs <= 0) return "Expired"

        val seconds = diffMs / 1000
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m left")
        }.trim()
    }

    /**
     * Formats expiry timestamp into date and time: "11 Sep 2026, 04:30 PM"
     */
    fun formatExpiryDate(expiryTimestamp: Long): String {
        if (expiryTimestamp <= 0L) return "Not Activated"
        val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return format.format(Date(expiryTimestamp))
    }

    /**
     * Formats submission timestamp into detailed day, date, month, year, time: "Monday, 15 Aug 2026, 02:30 PM"
     */
    fun formatDetailedTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return "N/A"
        val format = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
        return format.format(Date(timestamp))
    }
}
