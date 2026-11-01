package com.rideauto.assistant

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

class AuthManager(private val context: Context) {

    private val prefs = PreferencesHelper.getInstance(context)
    private val userRepository = UserRepository.getInstance(context)

    /**
     * Generates a unique hardware device lock fingerprint code
     * formatted as `FP` + 8 uppercase hex characters derived from Android ID.
     */
    fun getDeviceFingerprint(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "UNKNOWN_DEVICE_ID"

            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(androidId.toByteArray())
            val hexString = digest.take(4).joinToString("") { "%02X".format(it) }
            "FP$hexString"
        } catch (e: Exception) {
            "FP328F6970"
        }
    }

    fun register(
        name: String,
        email: String,
        mobile: String,
        pass: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ): Pair<Boolean, String> {
        val deviceId = getDeviceFingerprint()
        return userRepository.registerUser(name, email, mobile, pass, deviceId, onResult)
    }

    fun login(emailInput: String, passwordInput: String): Pair<Boolean, String> {
        val deviceId = getDeviceFingerprint()
        val (success, msg, user) = userRepository.loginUser(emailInput, passwordInput, deviceId)

        if (success && user != null) {
            prefs.isLoggedIn = true
            prefs.loggedInUserId = user.email
            prefs.subscriptionExpiryTimestamp = user.subscriptionExpiryTimestamp
        }

        return Pair(success, msg)
    }

    fun loginAsync(
        emailInput: String,
        passwordInput: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val deviceId = getDeviceFingerprint()
        userRepository.loginUserAsync(emailInput, passwordInput, deviceId) { success, msg, user ->
            if (success && user != null) {
                prefs.isLoggedIn = true
                prefs.loggedInUserId = user.email
                prefs.subscriptionExpiryTimestamp = user.subscriptionExpiryTimestamp
            }
            onResult(success, msg)
        }
    }

    fun loginWithGoogle(email: String, name: String): Pair<Boolean, String> {
        val deviceId = getDeviceFingerprint()
        val (success, msg, user) = userRepository.loginWithGoogle(email, name, deviceId)

        if (success && user != null) {
            prefs.isLoggedIn = true
            prefs.loggedInUserId = user.email
            prefs.subscriptionExpiryTimestamp = user.subscriptionExpiryTimestamp
        }

        return Pair(success, msg)
    }

    fun logout() {
        prefs.isLoggedIn = false
        prefs.loggedInUserId = null
        prefs.subscriptionExpiryTimestamp = 0L
    }

    fun isLoggedIn(): Boolean {
        if (prefs.isTestModeActive) return true
        return prefs.isLoggedIn
    }

    fun getLoggedInUserId(): String {
        if (!prefs.isLoggedIn) return "Not Logged In"
        val email = prefs.loggedInUserId ?: return "User"
        val user = userRepository.getUserByEmail(email)
        return user?.email ?: email
    }

    fun getLoggedInUserName(): String {
        if (!prefs.isLoggedIn) return "Guest"
        val email = prefs.loggedInUserId ?: return "User"
        val user = userRepository.getUserByEmail(email)
        return user?.name ?: email
    }

    /** Check if user is logged in AND has an unexpired subscription and is NOT banned */
    fun isSubscriptionValid(): Boolean {
        if (prefs.isTestModeActive) return true
        if (!prefs.isLoggedIn || prefs.isBanned) return false

        // Fast path: if locally stored expiry is valid against current authoritative network time
        val cachedExpiry = prefs.subscriptionExpiryTimestamp
        val now = NetworkTimeHelper.getCurrentNetworkTime()
        if (cachedExpiry > now) {
            return true
        }

        val email = prefs.loggedInUserId ?: return false
        val user = userRepository.getUserByEmail(email) ?: return false

        if (user.isBanned) {
            prefs.isBanned = true
            return false
        }

        if (user.isCountdownStarted && user.subscriptionExpiryTimestamp > now) {
            prefs.subscriptionExpiryTimestamp = user.subscriptionExpiryTimestamp
            return true
        }

        return false
    }

    fun updateLocalSubscriptionState(expiryTimestamp: Long, isValid: Boolean) {
        prefs.subscriptionExpiryTimestamp = expiryTimestamp
        if (!isValid && prefs.isLoggedIn) {
            prefs.subscriptionExpiryTimestamp = 0L
        }
    }

    fun getPlanExpiryText(): String {
        if (!prefs.isLoggedIn) return "Not Logged In"
        val email = prefs.loggedInUserId ?: return "Not Logged In"
        val user = userRepository.getUserByEmail(email) ?: return "Not Logged In"

        if (user.assignedDays > 0 && !user.isCountdownStarted) {
            return "Pending First Login (${user.assignedDays} Days Plan)"
        }

        val expiry = user.subscriptionExpiryTimestamp
        if (expiry <= 0L) return "Not Activated"

        val remaining = NetworkTimeHelper.formatRemainingTime(expiry)
        if (remaining == "Expired") return "Expired"

        val dateStr = NetworkTimeHelper.formatExpiryDate(expiry)
        return "$remaining (Expires $dateStr)"
    }

    /** Submit a subscription payment with UTR / Transaction ID */
    fun submitPayment(planName: String, amount: String, utr: String): Boolean {
        if (utr.isBlank()) return false
        val email = prefs.loggedInUserId ?: "guest"
        userRepository.submitPaymentUtr(email, planName, amount, utr)
        prefs.pendingPlan = planName
        prefs.pendingAmount = amount
        prefs.pendingUtr = utr
        return true
    }

    fun getPendingPaymentInfo(): Triple<String, String, String>? {
        val email = prefs.loggedInUserId ?: return null
        val user = userRepository.getUserByEmail(email)
        if (user?.pendingUtr != null) {
            return Triple(user.pendingPlan ?: "", user.pendingAmount ?: "", user.pendingUtr!!)
        }
        val plan = prefs.pendingPlan ?: return null
        val amount = prefs.pendingAmount ?: return null
        val utr = prefs.pendingUtr ?: return null
        return Triple(plan, amount, utr)
    }

    fun clearPendingPayment() {
        prefs.pendingPlan = null
        prefs.pendingAmount = null
        prefs.pendingUtr = null
    }
}
