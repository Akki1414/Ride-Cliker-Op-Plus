package moe.shizuku.manager.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object MonkeyAuthManager {

    private const val TAG = "MonkeyAuthManager"
    private const val PREF_NAME = "monkey_auth_session"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * One-time authentication check:
     * Once logged in, returns true permanently.
     * No Firestore calls, no network checks, no expiry verification.
     * Only resets if user uninstalls app or clears app data.
     */
    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getSavedEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun getSavedName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "Monkey User") ?: "Monkey User"
    }

    /**
     * Performs one-time authentication against Ride Clicker Pro+ Firestore ("ride-clicker-proplus").
     * On success, marks is_logged_in = true and never checks again.
     */
    fun login(
        context: Context,
        emailInput: String,
        passwordInput: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val cleanEmail = emailInput.trim().lowercase()
        val cleanPass = passwordInput.trim()

        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
            callback(false, "Please enter both Email Address and Password.")
            return
        }

        val docId = cleanEmail.replace(".", "_")
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("users").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc == null || !doc.exists()) {
                    callback(false, "No account found with this email in Ride Clicker Pro+.")
                    return@addOnSuccessListener
                }

                val dbPassword = doc.getString("password") ?: ""
                if (dbPassword != cleanPass) {
                    callback(false, "Invalid Password. Please check your credentials.")
                    return@addOnSuccessListener
                }

                val isBanned = doc.getBoolean("isBanned") ?: false
                if (isBanned) {
                    callback(false, "🚫 Your account has been banned. Contact Admin to unban.")
                    return@addOnSuccessListener
                }

                val name = doc.getString("name") ?: "User"
                saveSession(context, cleanEmail, name)
                Log.i(TAG, "Monkey one-time authentication successful for $cleanEmail")
                callback(true, "Authentication successful! Welcome to Monkey.")
            }
            .addOnFailureListener { e ->
                callback(false, "Connection error: ${e.localizedMessage ?: "Failed to connect to server."}")
            }
    }

    private fun saveSession(context: Context, email: String, name: String) {
        getPrefs(context).edit()
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    /**
     * As per user requirement:
     * Never observe or check anything after initial authentication.
     * This observer is intentionally a no-op so no kick-outs or background Firestore calls happen.
     */
    fun startRealtimeSessionObserver(
        context: Context,
        onSessionInvalid: (reason: String) -> Unit
    ) {
        // Intentionally NO-OP. One-time authentication only.
    }

    fun stopRealtimeSessionObserver() {
        // Intentionally NO-OP.
    }

    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

