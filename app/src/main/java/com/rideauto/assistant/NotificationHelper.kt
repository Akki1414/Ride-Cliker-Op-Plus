package com.rideauto.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ride_assistant_service_channel"
        const val CHANNEL_NAME = "Ride Clicker Pro+ Background Service"
        const val NOTIFICATION_ID = 2001

        private const val SUB_CHANNEL_ID = "subscription_notifications"
        private const val SUB_CHANNEL_NAME = "Subscription & Payment Updates"

        fun triggerSubscriptionActivatedNotification(context: Context, days: Int) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications for active subscription plans and payments"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 500, 200, 500)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                try {
                    val ringtone = RingtoneManager.getRingtone(context, defaultSoundUri)
                    ringtone?.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_logo)
                    .setContentTitle("⚡ Subscription Activated!")
                    .setContentText("Your $days-Day Plan countdown has started. All pro features are now unlocked!")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setSound(defaultSoundUri)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(1001, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun triggerSubscriptionActivatedExtendedNotification(context: Context, planName: String, addedDays: Int, totalDays: Int) {
            // Notifications completely disabled
        }

        fun triggerAccountBannedNotification(context: Context) {
            // Notifications completely disabled
        }

        fun triggerDeviceResetNotification(context: Context) {
            // Notifications completely disabled
        }

        fun triggerNewPaymentNotification(context: Context, userName: String, plan: String, utr: String) {
            // Notifications completely disabled
        }

        fun cancelPaymentNotification(context: Context, utr: String) {
            try {
                val clean = utr.trim()
                if (clean.isBlank()) return
                val notifId = (clean.hashCode() and 0x7FFFFFFF) % 10000 + 4000
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notifId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelAllPaymentNotifications(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001)
                notificationManager.cancel(1002)
                notificationManager.cancel(1003)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val active = notificationManager.activeNotifications ?: emptyArray()
                    for (an in active) {
                        if (an.id != NOTIFICATION_ID) {
                            notificationManager.cancel(an.id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private const val PAYMENT_PREFS_NAME = "admin_payment_service_prefs"
        private const val KEY_NOTIFIED_UTRS = "notified_utrs"
        private const val KEY_NOTIFIED_UTRS_CSV = "notified_utrs_csv"
        private val inMemoryNotifiedUtrs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun isPaymentUtrNotified(context: Context, utr: String): Boolean {
            val clean = utr.trim()
            if (clean.isBlank()) return true
            if (inMemoryNotifiedUtrs.contains(clean)) return true
            return try {
                val prefs = context.getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
                val csv = prefs.getString(KEY_NOTIFIED_UTRS_CSV, "") ?: ""
                if (csv.contains(clean)) {
                    inMemoryNotifiedUtrs.add(clean)
                    return true
                }
                val saved = prefs.getStringSet(KEY_NOTIFIED_UTRS, emptySet()) ?: emptySet()
                if (saved.contains(clean)) {
                    inMemoryNotifiedUtrs.add(clean)
                    return true
                }
                false
            } catch (e: Exception) {
                false
            }
        }

        fun markPaymentUtrNotified(context: Context, utr: String) {
            val clean = utr.trim()
            if (clean.isBlank()) return
            inMemoryNotifiedUtrs.add(clean)
            try {
                val prefs = context.getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
                val saved = prefs.getStringSet(KEY_NOTIFIED_UTRS, emptySet())?.toMutableSet() ?: mutableSetOf()
                saved.add(clean)
                val trimmed = if (saved.size > 500) saved.toList().takeLast(500).toSet() else saved

                val currentCsv = prefs.getString(KEY_NOTIFIED_UTRS_CSV, "") ?: ""
                val csvList = if (currentCsv.isNotBlank()) currentCsv.split(",").map { it.trim() }.toMutableList() else mutableListOf()
                if (!csvList.contains(clean)) {
                    csvList.add(clean)
                }
                val trimmedCsvList = if (csvList.size > 500) csvList.takeLast(500) else csvList
                val newCsv = trimmedCsvList.joinToString(",")

                prefs.edit()
                    .putStringSet(KEY_NOTIFIED_UTRS, trimmed)
                    .putString(KEY_NOTIFIED_UTRS_CSV, newCsv)
                    .commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun clearNotifiedPaymentUtr(context: Context, utr: String) {
            val clean = utr.trim()
            if (clean.isBlank()) return
            inMemoryNotifiedUtrs.remove(clean)
            try {
                val prefs = context.getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
                val saved = prefs.getStringSet(KEY_NOTIFIED_UTRS, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (saved.remove(clean)) {
                    val currentCsv = prefs.getString(KEY_NOTIFIED_UTRS_CSV, "") ?: ""
                    val csvList = if (currentCsv.isNotBlank()) currentCsv.split(",").map { it.trim() }.toMutableList() else mutableListOf()
                    csvList.remove(clean)
                    val newCsv = csvList.joinToString(",")

                    prefs.edit()
                        .putStringSet(KEY_NOTIFIED_UTRS, saved)
                        .putString(KEY_NOTIFIED_UTRS_CSV, newCsv)
                        .commit()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        createNotificationChannel()
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.ENGLISH
                    isTtsInitialized = true
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Ride Clicker Pro+ engine running in the background."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(statusText: String = "Monitoring ride offers…"): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Ride Clicker Pro+ Active")
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun playAcceptedAlert(appName: String, fare: Int?) {
        val prefs = PreferencesHelper(context)

        // Vibration
        if (prefs.isVibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(200)
                }
            } catch (ignored: Exception) {}
        }

        // Audio Tone & Speech
        if (prefs.isAudioFeedbackEnabled) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)

                val speechText = if (fare != null && fare > 0) {
                    "$appName ride accepted for $fare rupees"
                } else {
                    "$appName ride accepted"
                }

                if (isTtsInitialized) {
                    tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "ride_accept")
                }
            } catch (ignored: Exception) {}
        }
    }
}
