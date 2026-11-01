package com.rideauto.assistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Immutable
data class RejectionRecord(
    val id: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val planName: String = "",
    val amount: String = "",
    val utr: String? = null,
    val screenshotUrl: String? = null,
    val rejectionReason: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedByAdmin: String? = null,
    val deletedTimestamp: Long = 0L
)

@Immutable
data class UsedUtrRecord(
    val utr: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val planName: String = "",
    val amountPaid: Double = 0.0,
    val activatedTimestamp: Long = System.currentTimeMillis(),
    val performedByAdmin: String? = null,
    val status: String = "ACTIVATED"
)

enum class AdminSessionStatus(val message: String) {
    SUCCESS("Admin session valid"),
    ACCOUNT_DELETED("Your Admin account was deleted by SuperAdmin. Logged out."),
    PASSWORD_CHANGED("Your Admin password was changed by SuperAdmin. Logged out."),
    ROLE_REVOKED("Your Admin access role was revoked by SuperAdmin. Logged out."),
    ACCOUNT_BANNED("Your account has been banned. Logged out."),
    NOT_LOGGED_IN("No active admin session.")
}

@Immutable
data class UserData(
    val email: String = "",
    var name: String = "",
    var mobileNumber: String = "",
    var password: String = "",
    var boundDeviceId: String? = null,
    var createdDeviceId: String? = null,
    var assignedDays: Int = 0,
    var isCountdownStarted: Boolean = false,
    var subscriptionExpiryTimestamp: Long = 0L,
    var pendingPlan: String? = null,
    var pendingAmount: String? = null,
    var pendingPlanPriceSnapshot: Double? = null,
    var pendingUtr: String? = null,
    var pendingScreenshotUrl: String? = null,
    var paymentSubmissionTimestamp: Long = 0L,
    var paymentStatus: String? = null,
    var rejectionReason: String? = null,
    var wasRejectedPaymentActivatedLater: Boolean = false,
    var isAdmin: Boolean = false,
    var isAdminAlpha: Boolean = false,
    var isSuperAdmin: Boolean = false,
    var isBanned: Boolean = false,
    var rejectionHistoryJson: String = "[]",
    var hasUsedTrial: Boolean = false,
    var isTrialActive: Boolean = false
)

@Immutable
data class SaleRecord(
    val transactionId: String = "",
    val docId: String = transactionId,
    val userEmail: String = "",
    val userName: String = "",
    val planName: String = "",
    val daysAssigned: Int = 0,
    val amountPaid: Double = 0.0,
    val originalAmount: Double = amountPaid,
    val isTrial: Boolean = false,
    val utr: String? = null,
    val screenshotUrl: String? = null,
    val activationTimestamp: Long = System.currentTimeMillis(),
    val activationSource: String = "AUTO_RAZORPAY",
    val modificationReason: String? = null,
    val resetReason: String? = null,
    val performedByAdmin: String? = null,
    val isDeleted: Boolean = false,
    val deletedByAdmin: String? = null,
    val deletedTimestamp: Long = 0L
)

@Immutable
data class PlanPricingConfig(
    val price1Day: Double = 29.0,
    val price3Days: Double = 79.0,
    val price7Days: Double = 179.0,
    val price15Days: Double = 299.0,
    val price30Days: Double = 499.0,
    val price365Days: Double = 3999.0
)

@Immutable
data class RazorpayConfig(
    val keyId: String = "rzp_test_TYKenXfKX7ntr1",
    val keySecret: String = "8N30wnnM8CDn2fp0CROG2M4t",
    val isEnabled: Boolean = true,
    val merchantName: String = "Ride Clicker Pro+"
)

@Immutable
data class DynamicLinksConfig(
    val telegramSupport: String = "https://t.me/RideAssistant_Support",
    val whatsappSupport: String = "https://t.me/RideAssistant_Support",
    val youtubeChannel: String = "https://t.me/RideAssistant_Support",
    val getApp: String = "https://t.me/RideAssistant_Support",
    val shizukuApk: String = "https://t.me/RideAssistant_Support",
    val tutorialVideo: String = "https://t.me/RideAssistant_Support"
)

@Immutable
data class OfferBannerConfig(
    val showBanner: Boolean = false,
    val bannerText: String = "🔥 Special Offer: Get Instant Pro Pass now!"
)

@Immutable
data class PaymentQrConfig(
    val qrUrls: List<String> = emptyList()
)

class UserRepository(private val context: Context) {

    companion object {
        private const val TAG = "UserRepository"
        @Volatile private var instance: UserRepository? = null
        @Volatile private var inMemoryUsersCache: MutableList<UserData>? = null
        private val inMemoryUsersMap = java.util.concurrent.ConcurrentHashMap<String, UserData>()
        private val cacheLock = Any()

        fun getInstance(context: Context): UserRepository {
            return instance ?: synchronized(this) {
                instance ?: UserRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("ride_assistant_users_db", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var allUsersListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null
    private var salesRecordsListener: ListenerRegistration? = null
    private var deletedSalesListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null
    private var razorpayListener: ListenerRegistration? = null

    private val currentUserCallbacks = mutableSetOf<(UserData?) -> Unit>()
    private var currentlyObservedEmail: String? = null

    private val activeUserLocks = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val cachedUsedUtrs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // 1. App Device limit settings listener
        try {
            configListener = firestore.collection("app_config").document("device_settings")
                .addSnapshotListener { doc, error ->
                    if (error == null && doc != null && doc.exists()) {
                        val limit = doc.getLong("maxAccountsPerDevice")?.toInt() ?: 2
                        prefs.edit().putInt("device_account_limit", limit).apply()
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeListeners() {
        allUsersListener?.remove()
        currentUserListener?.remove()
        salesRecordsListener?.remove()
        deletedSalesListener?.remove()
        configListener?.remove()
        razorpayListener?.remove()
    }

    fun parseUserFromDoc(doc: DocumentSnapshot): UserData {
        val email = (doc.getString("email")
            ?: doc.getString("userEmail")
            ?: doc.getString("mail")
            ?: doc.id.replace("_", ".")).trim()

        val name = (doc.getString("name")
            ?: doc.getString("userName")
            ?: doc.getString("fullName")
            ?: "").trim()

        val mobile = (doc.getString("mobileNumber")
            ?: doc.getString("mobile")
            ?: doc.getString("phone")
            ?: doc.getString("phoneNumber")
            ?: "").trim()

        val pass = (doc.getString("password")
            ?: doc.getString("pass")
            ?: doc.getString("userPassword")
            ?: doc.get("password")?.toString()
            ?: doc.get("pass")?.toString()
            ?: "").trim()

        val rawBound = doc.getString("boundDeviceId")
            ?: doc.getString("deviceId")
            ?: doc.getString("hardwareId")
        val boundDeviceId = if (rawBound.isNullOrBlank() || rawBound == "null" || rawBound == "NONE" || rawBound.equals("unbound", ignoreCase = true)) null else rawBound.trim()

        val rawCreated = doc.getString("createdDeviceId")
        val createdDeviceId = if (rawCreated.isNullOrBlank() || rawCreated == "null") null else rawCreated.trim()

        val assignedDays = doc.getLong("assignedDays")?.toInt()
            ?: doc.get("assignedDays")?.toString()?.toIntOrNull()
            ?: 0

        val isCountdownStarted = doc.getBoolean("isCountdownStarted")
            ?: doc.getString("isCountdownStarted")?.toBooleanStrictOrNull()
            ?: false

        val subscriptionExpiryTimestamp = doc.getLong("subscriptionExpiryTimestamp")
            ?: (doc.getDouble("subscriptionExpiryTimestamp")?.toLong())
            ?: (doc.getTimestamp("subscriptionExpiryTimestamp")?.toDate()?.time)
            ?: (doc.get("subscriptionExpiryTimestamp")?.toString()?.toLongOrNull())
            ?: 0L

        val pendingPlan = doc.getString("pendingPlan")
        val pendingAmount = doc.getString("pendingAmount") ?: doc.get("pendingAmount")?.toString()
        val pendingPlanPriceSnapshot = doc.getDouble("pendingPlanPriceSnapshot")
        val pendingUtr = doc.getString("pendingUtr") ?: doc.getString("utr")
        val pendingScreenshotUrl = doc.getString("pendingScreenshotUrl")
        val paymentSubmissionTimestamp = doc.getLong("paymentSubmissionTimestamp") ?: 0L
        val paymentStatus = doc.getString("paymentStatus") ?: doc.getString("status")
        val rejectionReason = doc.getString("rejectionReason")
        val wasRejectedPaymentActivatedLater = doc.getBoolean("wasRejectedPaymentActivatedLater") ?: false

        val roleStr = doc.getString("role") ?: ""
        val isAdmin = doc.getBoolean("isAdmin")
            ?: doc.getBoolean("admin")
            ?: doc.getString("isAdmin")?.toBooleanStrictOrNull()
            ?: (roleStr.contains("admin", ignoreCase = true))
            ?: false

        val isAdminAlpha = doc.getBoolean("isAdminAlpha")
            ?: doc.getString("isAdminAlpha")?.toBooleanStrictOrNull()
            ?: false

        val isSuperAdmin = doc.getBoolean("isSuperAdmin")
            ?: doc.getString("isSuperAdmin")?.toBooleanStrictOrNull()
            ?: (roleStr.contains("superadmin", ignoreCase = true))
            ?: (roleStr.contains("super_admin", ignoreCase = true))
            ?: false

        val isBanned = doc.getBoolean("isBanned")
            ?: doc.getBoolean("banned")
            ?: doc.getString("isBanned")?.toBooleanStrictOrNull()
            ?: false

        val rejectionHistoryJson = doc.getString("rejectionHistoryJson") ?: "[]"
        val hasUsedTrial = doc.getBoolean("hasUsedTrial") ?: false
        val isTrialActive = doc.getBoolean("isTrialActive") ?: false

        return UserData(
            email = email,
            name = name,
            mobileNumber = mobile,
            password = pass,
            boundDeviceId = boundDeviceId,
            createdDeviceId = createdDeviceId,
            assignedDays = assignedDays,
            isCountdownStarted = isCountdownStarted,
            subscriptionExpiryTimestamp = subscriptionExpiryTimestamp,
            pendingPlan = pendingPlan,
            pendingAmount = pendingAmount,
            pendingPlanPriceSnapshot = pendingPlanPriceSnapshot,
            pendingUtr = pendingUtr,
            pendingScreenshotUrl = pendingScreenshotUrl,
            paymentSubmissionTimestamp = paymentSubmissionTimestamp,
            paymentStatus = paymentStatus,
            rejectionReason = rejectionReason,
            wasRejectedPaymentActivatedLater = wasRejectedPaymentActivatedLater,
            isAdmin = isAdmin,
            isAdminAlpha = isAdminAlpha,
            isSuperAdmin = isSuperAdmin,
            isBanned = isBanned,
            rejectionHistoryJson = rejectionHistoryJson,
            hasUsedTrial = hasUsedTrial,
            isTrialActive = isTrialActive
        )
    }

    fun userToFirestoreMap(user: UserData): HashMap<String, Any?> {
        return hashMapOf(
            "email" to user.email,
            "name" to user.name,
            "mobileNumber" to user.mobileNumber,
            "password" to user.password,
            "boundDeviceId" to user.boundDeviceId,
            "createdDeviceId" to user.createdDeviceId,
            "assignedDays" to user.assignedDays,
            "isCountdownStarted" to user.isCountdownStarted,
            "subscriptionExpiryTimestamp" to user.subscriptionExpiryTimestamp,
            "pendingPlan" to user.pendingPlan,
            "pendingAmount" to user.pendingAmount,
            "pendingPlanPriceSnapshot" to user.pendingPlanPriceSnapshot,
            "pendingUtr" to user.pendingUtr,
            "pendingScreenshotUrl" to user.pendingScreenshotUrl,
            "paymentSubmissionTimestamp" to user.paymentSubmissionTimestamp,
            "paymentStatus" to user.paymentStatus,
            "rejectionReason" to user.rejectionReason,
            "wasRejectedPaymentActivatedLater" to user.wasRejectedPaymentActivatedLater,
            "isAdmin" to user.isAdmin,
            "isAdminAlpha" to user.isAdminAlpha,
            "isSuperAdmin" to user.isSuperAdmin,
            "role" to (if (user.isSuperAdmin) "superadmin" else if (user.isAdmin || user.isAdminAlpha) "admin" else "user"),
            "isBanned" to user.isBanned,
            "rejectionHistoryJson" to user.rejectionHistoryJson,
            "hasUsedTrial" to user.hasUsedTrial,
            "isTrialActive" to user.isTrialActive
        )
    }

    fun syncUserToFirestore(user: UserData) {
        try {
            val cleanEmail = user.email.trim().lowercase()
            val docIdUnderscore = cleanEmail.replace(".", "_")
            val map = userToFirestoreMap(user)
            // Strictly write ONLY to canonical docIdUnderscore to prevent double account creation
            firestore.collection("users").document(docIdUnderscore).set(map)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun serializeSingleUser(u: UserData): String {
        return JSONObject().apply {
            put("email", u.email)
            put("name", u.name)
            put("mobileNumber", u.mobileNumber)
            put("password", u.password)
            put("boundDeviceId", u.boundDeviceId ?: JSONObject.NULL)
            put("createdDeviceId", u.createdDeviceId ?: JSONObject.NULL)
            put("assignedDays", u.assignedDays)
            put("isCountdownStarted", u.isCountdownStarted)
            put("subscriptionExpiryTimestamp", u.subscriptionExpiryTimestamp)
            put("pendingPlan", u.pendingPlan ?: JSONObject.NULL)
            put("pendingAmount", u.pendingAmount ?: JSONObject.NULL)
            put("pendingPlanPriceSnapshot", u.pendingPlanPriceSnapshot ?: JSONObject.NULL)
            put("pendingUtr", u.pendingUtr ?: JSONObject.NULL)
            put("pendingScreenshotUrl", u.pendingScreenshotUrl ?: JSONObject.NULL)
            put("paymentSubmissionTimestamp", u.paymentSubmissionTimestamp)
            put("paymentStatus", u.paymentStatus ?: JSONObject.NULL)
            put("rejectionReason", u.rejectionReason ?: JSONObject.NULL)
            put("wasRejectedPaymentActivatedLater", u.wasRejectedPaymentActivatedLater)
            put("isAdmin", u.isAdmin)
            put("isAdminAlpha", u.isAdminAlpha)
            put("isSuperAdmin", u.isSuperAdmin)
            put("isBanned", u.isBanned)
            put("rejectionHistoryJson", u.rejectionHistoryJson)
            put("hasUsedTrial", u.hasUsedTrial)
            put("isTrialActive", u.isTrialActive)
        }.toString()
    }

    fun parseSingleUser(json: String): UserData? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            UserData(
                email = obj.optString("email"),
                name = obj.optString("name"),
                mobileNumber = obj.optString("mobileNumber"),
                password = obj.optString("password"),
                boundDeviceId = if (obj.has("boundDeviceId") && !obj.isNull("boundDeviceId")) obj.getString("boundDeviceId") else null,
                createdDeviceId = if (obj.has("createdDeviceId") && !obj.isNull("createdDeviceId")) obj.getString("createdDeviceId") else null,
                assignedDays = obj.optInt("assignedDays", 0),
                isCountdownStarted = obj.optBoolean("isCountdownStarted", false),
                subscriptionExpiryTimestamp = obj.optLong("subscriptionExpiryTimestamp", 0L),
                pendingPlan = if (obj.has("pendingPlan") && !obj.isNull("pendingPlan")) obj.getString("pendingPlan") else null,
                pendingAmount = if (obj.has("pendingAmount") && !obj.isNull("pendingAmount")) obj.getString("pendingAmount") else null,
                pendingPlanPriceSnapshot = if (obj.has("pendingPlanPriceSnapshot") && !obj.isNull("pendingPlanPriceSnapshot")) obj.getDouble("pendingPlanPriceSnapshot") else null,
                pendingUtr = if (obj.has("pendingUtr") && !obj.isNull("pendingUtr")) obj.getString("pendingUtr") else null,
                pendingScreenshotUrl = if (obj.has("pendingScreenshotUrl") && !obj.isNull("pendingScreenshotUrl")) obj.getString("pendingScreenshotUrl") else null,
                paymentSubmissionTimestamp = obj.optLong("paymentSubmissionTimestamp", 0L),
                paymentStatus = if (obj.has("paymentStatus") && !obj.isNull("paymentStatus")) obj.getString("paymentStatus") else null,
                rejectionReason = if (obj.has("rejectionReason") && !obj.isNull("rejectionReason")) obj.getString("rejectionReason") else null,
                wasRejectedPaymentActivatedLater = obj.optBoolean("wasRejectedPaymentActivatedLater", false),
                isAdmin = obj.optBoolean("isAdmin", false),
                isAdminAlpha = obj.optBoolean("isAdminAlpha", false),
                isSuperAdmin = obj.optBoolean("isSuperAdmin", false),
                isBanned = obj.optBoolean("isBanned", false),
                rejectionHistoryJson = obj.optString("rejectionHistoryJson", "[]"),
                hasUsedTrial = obj.optBoolean("hasUsedTrial", false),
                isTrialActive = obj.optBoolean("isTrialActive", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveUserLocallyOnly(user: UserData) {
        val cleanEmail = user.email.trim().lowercase()
        if (cleanEmail.isNotBlank()) {
            inMemoryUsersMap[cleanEmail] = user
            val docKey = cleanEmail.replace(".", "_")
            try {
                val json = serializeSingleUser(user)
                prefs.edit().putString("user_profile_$docKey", json).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Always update full users_list with deduplication so client and admin apps stay synchronized
        val users = getUsers().toMutableList()
        val index = users.indexOfFirst { it.email.equals(user.email, ignoreCase = true) }
        if (index >= 0) {
            users[index] = user
        } else {
            users.add(user)
        }
        val distinct = users.distinctBy { it.email.trim().lowercase() }
        saveLocalUsers(distinct)
    }

    fun saveUser(user: UserData) {
        saveUserLocallyOnly(user)
        syncUserToFirestore(user)
    }

    fun getUserByEmail(email: String): UserData? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        inMemoryUsersMap[cleanEmail]?.let { return it }
        val docKey = cleanEmail.replace(".", "_")
        val singleJson = prefs.getString("user_profile_$docKey", null)
        if (!singleJson.isNullOrBlank()) {
            val u = parseSingleUser(singleJson)
            if (u != null) {
                inMemoryUsersMap[cleanEmail] = u
                return u
            }
        }
        // Fallback for admin portal or when user list is loaded
        val user = getUsers().find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (user != null) {
            inMemoryUsersMap[cleanEmail] = user
        }
        return user
    }

    fun deleteLocalUser(cleanEmail: String) {
        val clean = cleanEmail.trim().lowercase()
        inMemoryUsersMap.remove(clean)
        val docKey = clean.replace(".", "_")
        prefs.edit().remove("user_profile_$docKey").apply()
        if (inMemoryUsersCache != null) {
            val users = getUsers().toMutableList()
            val removed = users.removeAll { it.email.equals(clean, ignoreCase = true) }
            if (removed) {
                saveLocalUsers(users)
            }
        }
    }

    /**
     * Resilient multi-tier query for retrieving a user document from Firestore:
     * 1. Check doc ID with underscores (e.g. `user@gmail_com`)
     * 2. Check doc ID with raw email dots (e.g. `user@gmail.com`)
     * 3. Query `whereEqualTo("email", cleanEmail)`
     * 4. Query `whereEqualTo("email", emailInput.trim())`
     * 5. Scan all documents in the `users` collection for case-insensitive email match
     * If document is not found, local cache is purged and null is returned (no auto-resurrection).
     */
    fun findUserInFirestoreFlexible(emailInput: String, onComplete: (UserData?) -> Unit) {
        val cleanEmail = emailInput.trim().lowercase()
        val docIdUnderscore = cleanEmail.replace(".", "_")
        val docIdDot = cleanEmail

        // Path 1: Check document with underscores
        firestore.collection("users").document(docIdUnderscore).get()
            .addOnSuccessListener { doc1 ->
                if (doc1 != null && doc1.exists()) {
                    val u = parseUserFromDoc(doc1)
                    saveUserLocallyOnly(u)
                    onComplete(u)
                } else {
                    // Path 2: Check document with dots intact
                    firestore.collection("users").document(docIdDot).get()
                        .addOnSuccessListener { doc2 ->
                            if (doc2 != null && doc2.exists()) {
                                val u = parseUserFromDoc(doc2)
                                saveUserLocallyOnly(u)
                                onComplete(u)
                            } else {
                                // Path 3: Query by email field
                                firestore.collection("users").whereEqualTo("email", cleanEmail).limit(1).get()
                                    .addOnSuccessListener { q1 ->
                                        if (q1 != null && !q1.isEmpty) {
                                            val u = parseUserFromDoc(q1.documents[0])
                                            saveUserLocallyOnly(u)
                                            onComplete(u)
                                        } else {
                                            // Path 4: Scan all documents in collection
                                            firestore.collection("users").get()
                                                .addOnSuccessListener { allDocs ->
                                                    val match = allDocs.documents
                                                        .map { parseUserFromDoc(it) }
                                                        .find { it.email.equals(cleanEmail, ignoreCase = true) }
                                                    if (match != null) {
                                                        saveUserLocallyOnly(match)
                                                        onComplete(match)
                                                    } else {
                                                        deleteLocalUser(cleanEmail)
                                                        onComplete(null)
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    deleteLocalUser(cleanEmail)
                                                    onComplete(null)
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        deleteLocalUser(cleanEmail)
                                        onComplete(null)
                                    }
                            }
                        }
                        .addOnFailureListener {
                            deleteLocalUser(cleanEmail)
                            onComplete(null)
                        }
                }
            }
            .addOnFailureListener {
                deleteLocalUser(cleanEmail)
                onComplete(null)
            }
    }

    // ── Live Firestore Observers ──

    fun observeAllUsers(onUsersChanged: (List<UserData>) -> Unit): () -> Unit {
        allUsersListener?.remove()
        try {
            allUsersListener = firestore.collection("users")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        onUsersChanged(getUsers())
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val userList = snapshots.documents
                            .map { parseUserFromDoc(it) }
                            .filter { it.email.isNotBlank() }
                            .distinctBy { it.email.trim().lowercase() }
                        saveLocalUsers(userList)
                        onUsersChanged(userList)

                        // Automatically purge legacy duplicate dot documents from Firestore
                        for (doc in snapshots.documents) {
                            if (doc.id.contains(".")) {
                                try {
                                    firestore.collection("users").document(doc.id).delete()
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        onUsersChanged(getUsers())
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onUsersChanged(getUsers())
        }
        return {
            allUsersListener?.remove()
            allUsersListener = null
        }
    }

    fun observeCurrentUser(email: String, onUserChanged: (UserData?) -> Unit): () -> Unit {
        if (email.isBlank()) return {}
        val cleanEmail = email.trim().lowercase()

        synchronized(currentUserCallbacks) {
            currentUserCallbacks.add(onUserChanged)
        }

        // 1. Immediately emit local cached user so UI updates without network lag
        val localUser = getUserByEmail(cleanEmail)
        if (localUser != null) {
            onUserChanged(localUser)
        }

        // 2. Attach or reuse shared real-time Firestore listener
        if (currentlyObservedEmail != cleanEmail || currentUserListener == null) {
            currentUserListener?.remove()
            currentlyObservedEmail = cleanEmail
            val docId = cleanEmail.replace(".", "_")
            try {
                currentUserListener = firestore.collection("users").document(docId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            val currentLocal = getUserByEmail(cleanEmail)
                            if (currentLocal != null) {
                                val callbacks = synchronized(currentUserCallbacks) { currentUserCallbacks.toList() }
                                callbacks.forEach { it.invoke(currentLocal) }
                            }
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val user = parseUserFromDoc(snapshot)
                            saveUserLocallyOnly(user)
                            val users = getUsers().toMutableList()
                            val index = users.indexOfFirst { it.email.equals(user.email, ignoreCase = true) }
                            if (index >= 0) {
                                users[index] = user
                            } else {
                                users.add(user)
                            }
                            saveLocalUsers(users.distinctBy { it.email.trim().lowercase() })
                            val callbacks = synchronized(currentUserCallbacks) { currentUserCallbacks.toList() }
                            callbacks.forEach { it.invoke(user) }
                        } else if (snapshot != null && !snapshot.exists() && !snapshot.metadata.hasPendingWrites()) {
                            // Check legacy dot ID once if migrating
                            firestore.collection("users").document(cleanEmail).get()
                                .addOnSuccessListener { doc2 ->
                                    if (doc2 != null && doc2.exists()) {
                                        val user = parseUserFromDoc(doc2)
                                        // Migrate to canonical underscore docId and remove dot duplicate
                                        syncUserToFirestore(user)
                                        try { firestore.collection("users").document(cleanEmail).delete() } catch (_: Exception) {}
                                        saveUserLocallyOnly(user)
                                        val callbacks = synchronized(currentUserCallbacks) { currentUserCallbacks.toList() }
                                        callbacks.forEach { it.invoke(user) }
                                    } else {
                                        deleteLocalUser(cleanEmail)
                                        val callbacks = synchronized(currentUserCallbacks) { currentUserCallbacks.toList() }
                                        callbacks.forEach { it.invoke(null) }
                                    }
                                }
                                .addOnFailureListener {
                                    deleteLocalUser(cleanEmail)
                                    val callbacks = synchronized(currentUserCallbacks) { currentUserCallbacks.toList() }
                                    callbacks.forEach { it.invoke(null) }
                                }
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return {
            synchronized(currentUserCallbacks) {
                currentUserCallbacks.remove(onUserChanged)
            }
        }
    }

    fun observeSalesRecords(onSalesChanged: (List<SaleRecord>) -> Unit): () -> Unit {
        salesRecordsListener?.remove()
        try {
            salesRecordsListener = firestore.collection("sales_records")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        onSalesChanged(getSalesRecords())
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val list = mutableListOf<SaleRecord>()
                        for (doc in snapshots.documents) {
                            val isDeleted = doc.getBoolean("isDeleted") ?: false
                            if (isDeleted) continue
                            val amountPaid = doc.getDouble("amountPaid") ?: 0.0
                            val originalAmt = doc.getDouble("originalAmount") ?: amountPaid
                            val isTrial = doc.getBoolean("isTrial") ?: false
                            val rawTs = doc.get("activationTimestamp")
                                ?: doc.get("activatedTimestamp")
                                ?: doc.get("timestamp")
                            val activationTimestamp: Long = when (rawTs) {
                                is Number -> rawTs.toLong()
                                is com.google.firebase.Timestamp -> rawTs.toDate().time
                                is String -> rawTs.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            list.add(
                                SaleRecord(
                                    transactionId = doc.getString("transactionId") ?: doc.id,
                                    docId = doc.id,
                                    userEmail = doc.getString("userEmail") ?: "",
                                    userName = doc.getString("userName") ?: "",
                                    planName = doc.getString("planName") ?: "",
                                    daysAssigned = doc.getLong("daysAssigned")?.toInt() ?: 0,
                                    amountPaid = amountPaid,
                                    originalAmount = originalAmt,
                                    isTrial = isTrial,
                                    utr = doc.getString("utr"),
                                    screenshotUrl = doc.getString("screenshotUrl"),
                                    activationTimestamp = if (activationTimestamp > 0L) activationTimestamp else System.currentTimeMillis(),
                                    activationSource = doc.getString("activationSource") ?: "AUTO_RAZORPAY",
                                    modificationReason = doc.getString("modificationReason"),
                                    resetReason = doc.getString("resetReason"),
                                    performedByAdmin = doc.getString("performedByAdmin"),
                                    isDeleted = false
                                )
                            )
                        }
                        val sorted = list.sortedByDescending { it.activationTimestamp }
                        saveLocalSales(sorted)
                        onSalesChanged(sorted)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onSalesChanged(getSalesRecords())
        }
        return {
            salesRecordsListener?.remove()
            salesRecordsListener = null
        }
    }

    fun observeDeletedSalesRecords(onRecordsChanged: (List<SaleRecord>) -> Unit): () -> Unit {
        deletedSalesListener?.remove()
        try {
            deletedSalesListener = firestore.collection("sales_records")
                .addSnapshotListener { snapshots, error ->
                    if (error == null && snapshots != null) {
                        val list = mutableListOf<SaleRecord>()
                        for (doc in snapshots.documents) {
                            val isDeleted = doc.getBoolean("isDeleted") ?: false
                            if (!isDeleted) continue
                            val amountPaid = doc.getDouble("amountPaid") ?: 0.0
                            val originalAmount = doc.getDouble("originalAmount") ?: amountPaid
                            val isTrial = doc.getBoolean("isTrial") ?: (doc.getString("planName")?.contains("Trial", ignoreCase = true) == true)
                            val rawTs = doc.get("activationTimestamp")
                                ?: doc.get("activatedTimestamp")
                                ?: doc.get("timestamp")
                                ?: doc.get("date")
                            val activationTimestamp: Long = when (rawTs) {
                                is Number -> rawTs.toLong()
                                is com.google.firebase.Timestamp -> rawTs.toDate().time
                                is String -> rawTs.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            val delTs = doc.get("deletedTimestamp")
                            val deletedTimestamp: Long = when (delTs) {
                                is Number -> delTs.toLong()
                                is com.google.firebase.Timestamp -> delTs.toDate().time
                                is String -> delTs.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                            list.add(
                                SaleRecord(
                                    transactionId = doc.getString("transactionId") ?: doc.id,
                                    docId = doc.id,
                                    userEmail = doc.getString("userEmail") ?: "",
                                    userName = doc.getString("userName") ?: "",
                                    planName = doc.getString("planName") ?: "",
                                    daysAssigned = doc.getLong("daysAssigned")?.toInt() ?: 0,
                                    amountPaid = amountPaid,
                                    originalAmount = originalAmount,
                                    isTrial = isTrial,
                                    utr = doc.getString("utr"),
                                    screenshotUrl = doc.getString("screenshotUrl"),
                                    activationTimestamp = activationTimestamp,
                                    activationSource = doc.getString("activationSource") ?: "DIRECT_ADMIN",
                                    modificationReason = doc.getString("modificationReason"),
                                    resetReason = doc.getString("resetReason"),
                                    performedByAdmin = doc.getString("performedByAdmin"),
                                    isDeleted = true,
                                    deletedByAdmin = doc.getString("deletedByAdmin"),
                                    deletedTimestamp = deletedTimestamp
                                )
                            )
                        }
                        val sorted = list.sortedByDescending { if (it.deletedTimestamp > 0L) it.deletedTimestamp else it.activationTimestamp }
                        onRecordsChanged(sorted)
                    } else {
                        onRecordsChanged(emptyList())
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onRecordsChanged(emptyList())
        }
        return {
            deletedSalesListener?.remove()
            deletedSalesListener = null
        }
    }

    fun observePlanPricing(onConfigChanged: (PlanPricingConfig) -> Unit): () -> Unit {
        val registration = firestore.collection("config").document("plan_pricing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val config = PlanPricingConfig(
                        price1Day = snapshot.getDouble("price1Day") ?: 29.0,
                        price3Days = snapshot.getDouble("price3Days") ?: 79.0,
                        price7Days = snapshot.getDouble("price7Days") ?: 179.0,
                        price15Days = snapshot.getDouble("price15Days") ?: 299.0,
                        price30Days = snapshot.getDouble("price30Days") ?: 499.0,
                        price365Days = snapshot.getDouble("price365Days") ?: 3999.0
                    )
                    onConfigChanged(config)
                } else {
                    onConfigChanged(PlanPricingConfig())
                }
            }
        return { registration.remove() }
    }

    fun observeRazorpayConfig(onConfigChanged: (RazorpayConfig) -> Unit): () -> Unit {
        razorpayListener?.remove()
        razorpayListener = firestore.collection("config").document("razorpay")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val config = RazorpayConfig(
                        keyId = snapshot.getString("keyId") ?: "rzp_test_TYKenXfKX7ntr1",
                        keySecret = snapshot.getString("keySecret") ?: "8N30wnnM8CDn2fp0CROG2M4t",
                        isEnabled = snapshot.getBoolean("isEnabled") ?: true,
                        merchantName = snapshot.getString("merchantName") ?: "Ride Clicker Pro+"
                    )
                    onConfigChanged(config)
                } else {
                    onConfigChanged(RazorpayConfig())
                }
            }
        return { razorpayListener?.remove() }
    }

    fun observeDynamicLinks(onConfigChanged: (DynamicLinksConfig) -> Unit): () -> Unit {
        val registration = firestore.collection("config").document("dynamic_links")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val config = DynamicLinksConfig(
                        telegramSupport = snapshot.getString("telegramSupport") ?: "https://t.me/RideAssistant_Support",
                        whatsappSupport = snapshot.getString("whatsappSupport") ?: "https://t.me/RideAssistant_Support",
                        youtubeChannel = snapshot.getString("youtubeChannel") ?: "https://t.me/RideAssistant_Support",
                        getApp = snapshot.getString("getApp") ?: "https://t.me/RideAssistant_Support",
                        shizukuApk = snapshot.getString("shizukuApk") ?: "https://t.me/RideAssistant_Support",
                        tutorialVideo = snapshot.getString("tutorialVideo") ?: "https://t.me/RideAssistant_Support"
                    )
                    onConfigChanged(config)
                } else {
                    onConfigChanged(DynamicLinksConfig())
                }
            }
        return { registration.remove() }
    }

    fun observeOfferBanner(onConfigChanged: (OfferBannerConfig) -> Unit): () -> Unit {
        val registration = firestore.collection("config").document("offer_banner")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val config = OfferBannerConfig(
                        showBanner = snapshot.getBoolean("showBanner") ?: false,
                        bannerText = snapshot.getString("bannerText") ?: "🔥 Special Offer: Upgrade your plan today!"
                    )
                    onConfigChanged(config)
                } else {
                    onConfigChanged(OfferBannerConfig())
                }
            }
        return { registration.remove() }
    }

    // ── Local User CRUD ──

    fun getUsers(): List<UserData> {
        synchronized(cacheLock) {
            inMemoryUsersCache?.let { return ArrayList(it) }
            val json = prefs.getString("users_list", "[]") ?: "[]"
            val list = mutableListOf<UserData>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        UserData(
                            email = obj.optString("email"),
                            name = obj.optString("name"),
                            mobileNumber = obj.optString("mobileNumber"),
                            password = obj.optString("password"),
                            boundDeviceId = if (obj.has("boundDeviceId") && !obj.isNull("boundDeviceId")) obj.getString("boundDeviceId") else null,
                            createdDeviceId = if (obj.has("createdDeviceId") && !obj.isNull("createdDeviceId")) obj.getString("createdDeviceId") else null,
                            assignedDays = obj.optInt("assignedDays", 0),
                            isCountdownStarted = obj.optBoolean("isCountdownStarted", false),
                            subscriptionExpiryTimestamp = obj.optLong("subscriptionExpiryTimestamp", 0L),
                            pendingPlan = if (obj.has("pendingPlan") && !obj.isNull("pendingPlan")) obj.getString("pendingPlan") else null,
                            pendingAmount = if (obj.has("pendingAmount") && !obj.isNull("pendingAmount")) obj.getString("pendingAmount") else null,
                            pendingPlanPriceSnapshot = if (obj.has("pendingPlanPriceSnapshot") && !obj.isNull("pendingPlanPriceSnapshot")) obj.getDouble("pendingPlanPriceSnapshot") else null,
                            pendingUtr = if (obj.has("pendingUtr") && !obj.isNull("pendingUtr")) obj.getString("pendingUtr") else null,
                            pendingScreenshotUrl = if (obj.has("pendingScreenshotUrl") && !obj.isNull("pendingScreenshotUrl")) obj.getString("pendingScreenshotUrl") else null,
                            paymentSubmissionTimestamp = obj.optLong("paymentSubmissionTimestamp", 0L),
                            paymentStatus = if (obj.has("paymentStatus") && !obj.isNull("paymentStatus")) obj.getString("paymentStatus") else null,
                            rejectionReason = if (obj.has("rejectionReason") && !obj.isNull("rejectionReason")) obj.getString("rejectionReason") else null,
                            wasRejectedPaymentActivatedLater = obj.optBoolean("wasRejectedPaymentActivatedLater", false),
                            isAdmin = obj.optBoolean("isAdmin", false),
                            isAdminAlpha = obj.optBoolean("isAdminAlpha", false),
                            isSuperAdmin = obj.optBoolean("isSuperAdmin", false),
                            isBanned = obj.optBoolean("isBanned", false),
                            rejectionHistoryJson = obj.optString("rejectionHistoryJson", "[]"),
                            hasUsedTrial = obj.optBoolean("hasUsedTrial", false),
                            isTrialActive = obj.optBoolean("isTrialActive", false)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            inMemoryUsersCache = ArrayList(list)
            return list
        }
    }

    fun getAllUsers(): List<UserData> = getUsers()

    fun saveLocalUsers(users: List<UserData>) {
        val distinct = users.distinctBy { it.email.trim().lowercase() }
        val snapshotList: List<UserData>
        synchronized(cacheLock) {
            inMemoryUsersCache = ArrayList(distinct)
            distinct.forEach { u ->
                if (u.email.isNotBlank()) {
                    inMemoryUsersMap[u.email.trim().lowercase()] = u
                }
            }
            snapshotList = ArrayList(distinct)
        }
        ioScope.launch {
            try {
                val array = JSONArray()
                snapshotList.forEach { u ->
                    val obj = JSONObject().apply {
                        put("email", u.email)
                        put("name", u.name)
                        put("mobileNumber", u.mobileNumber)
                        put("password", u.password)
                        put("boundDeviceId", u.boundDeviceId ?: JSONObject.NULL)
                        put("createdDeviceId", u.createdDeviceId ?: JSONObject.NULL)
                        put("assignedDays", u.assignedDays)
                        put("isCountdownStarted", u.isCountdownStarted)
                        put("subscriptionExpiryTimestamp", u.subscriptionExpiryTimestamp)
                        put("pendingPlan", u.pendingPlan ?: JSONObject.NULL)
                        put("pendingAmount", u.pendingAmount ?: JSONObject.NULL)
                        put("pendingPlanPriceSnapshot", u.pendingPlanPriceSnapshot ?: JSONObject.NULL)
                        put("pendingUtr", u.pendingUtr ?: JSONObject.NULL)
                        put("pendingScreenshotUrl", u.pendingScreenshotUrl ?: JSONObject.NULL)
                        put("paymentSubmissionTimestamp", u.paymentSubmissionTimestamp)
                        put("paymentStatus", u.paymentStatus ?: JSONObject.NULL)
                        put("rejectionReason", u.rejectionReason ?: JSONObject.NULL)
                        put("wasRejectedPaymentActivatedLater", u.wasRejectedPaymentActivatedLater)
                        put("isAdmin", u.isAdmin)
                        put("isAdminAlpha", u.isAdminAlpha)
                        put("isSuperAdmin", u.isSuperAdmin)
                        put("isBanned", u.isBanned)
                        put("rejectionHistoryJson", u.rejectionHistoryJson)
                        put("hasUsedTrial", u.hasUsedTrial)
                        put("isTrialActive", u.isTrialActive)
                    }
                    array.put(obj)
                }
                prefs.edit().putString("users_list", array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchAllUsersFromFirestore(onComplete: (List<UserData>) -> Unit) {
        firestore.collection("users").get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents
                    .map { parseUserFromDoc(it) }
                    .filter { it.email.isNotBlank() }
                    .distinctBy { it.email.trim().lowercase() }
                saveLocalUsers(list)
                onComplete(list)

                // Clean up legacy dot duplicates from Firestore
                for (doc in snapshots.documents) {
                    if (doc.id.contains(".")) {
                        try {
                            firestore.collection("users").document(doc.id).delete()
                        } catch (_: Exception) {}
                    }
                }
            }
            .addOnFailureListener {
                onComplete(getUsers())
            }
    }

    // ── Local Sales CRUD ──

    fun getSalesRecords(): List<SaleRecord> {
        val json = prefs.getString("sales_records_list", "[]") ?: "[]"
        val list = mutableListOf<SaleRecord>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val isDeleted = obj.optBoolean("isDeleted", false)
                if (isDeleted) continue
                val amountPaid = obj.optDouble("amountPaid", 0.0)
                val originalAmt = if (obj.has("originalAmount")) obj.getDouble("originalAmount") else amountPaid
                list.add(
                    SaleRecord(
                        transactionId = obj.optString("transactionId"),
                        docId = obj.optString("docId", obj.optString("transactionId")),
                        userEmail = obj.optString("userEmail"),
                        userName = obj.optString("userName"),
                        planName = obj.optString("planName"),
                        daysAssigned = obj.optInt("daysAssigned", 0),
                        amountPaid = amountPaid,
                        originalAmount = originalAmt,
                        isTrial = obj.optBoolean("isTrial", false),
                        utr = if (obj.has("utr") && !obj.isNull("utr")) obj.getString("utr") else null,
                        screenshotUrl = if (obj.has("screenshotUrl") && !obj.isNull("screenshotUrl")) obj.getString("screenshotUrl") else null,
                        activationTimestamp = obj.optLong("activationTimestamp", System.currentTimeMillis()),
                        activationSource = obj.optString("activationSource", "AUTO_RAZORPAY"),
                        modificationReason = if (obj.has("modificationReason") && !obj.isNull("modificationReason")) obj.getString("modificationReason") else null,
                        resetReason = if (obj.has("resetReason") && !obj.isNull("resetReason")) obj.getString("resetReason") else null,
                        performedByAdmin = if (obj.has("performedByAdmin") && !obj.isNull("performedByAdmin")) obj.getString("performedByAdmin") else null,
                        isDeleted = false,
                        deletedByAdmin = if (obj.has("deletedByAdmin") && !obj.isNull("deletedByAdmin")) obj.getString("deletedByAdmin") else null,
                        deletedTimestamp = obj.optLong("deletedTimestamp", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.activationTimestamp }
    }

    private fun saveLocalSales(sales: List<SaleRecord>) {
        try {
            val array = JSONArray()
            sales.forEach { s ->
                val obj = JSONObject().apply {
                    put("transactionId", s.transactionId)
                    put("docId", s.docId)
                    put("userEmail", s.userEmail)
                    put("userName", s.userName)
                    put("planName", s.planName)
                    put("daysAssigned", s.daysAssigned)
                    put("amountPaid", s.amountPaid)
                    put("originalAmount", s.originalAmount)
                    put("isTrial", s.isTrial)
                    put("utr", s.utr ?: JSONObject.NULL)
                    put("screenshotUrl", s.screenshotUrl ?: JSONObject.NULL)
                    put("activationTimestamp", s.activationTimestamp)
                    put("activationSource", s.activationSource)
                    put("modificationReason", s.modificationReason ?: JSONObject.NULL)
                    put("resetReason", s.resetReason ?: JSONObject.NULL)
                    put("performedByAdmin", s.performedByAdmin ?: JSONObject.NULL)
                    put("isDeleted", s.isDeleted)
                    put("deletedByAdmin", s.deletedByAdmin ?: JSONObject.NULL)
                    put("deletedTimestamp", s.deletedTimestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString("sales_records_list", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun submitPaymentUtr(email: String, planName: String, amount: String, utr: String) {
        val cleanEmail = email.trim().lowercase()
        val cleanUtr = utr.trim()
        val user = getUserByEmail(cleanEmail) ?: UserData(email = cleanEmail)
        user.pendingPlan = planName
        user.pendingAmount = amount
        user.pendingUtr = cleanUtr
        user.paymentSubmissionTimestamp = System.currentTimeMillis()
        user.paymentStatus = "PENDING_ACTIVATION"
        saveUser(user)
    }

    // ── Driver Authentication & Registration ──

    fun registerUser(
        name: String,
        email: String,
        mobile: String,
        pass: String,
        currentDeviceId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ): Pair<Boolean, String> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim()
        val cleanMobile = mobile.trim()
        val cleanPass = pass.trim()

        if (cleanName.isEmpty() || cleanEmail.isEmpty() || cleanMobile.isEmpty() || cleanPass.isEmpty()) {
            val msg = "All fields (Full Name, Email Address, Mobile Number, Password) are compulsory."
            onResult(false, msg)
            return Pair(false, msg)
        }
        if (!cleanEmail.contains("@")) {
            val msg = "Please enter a valid email address."
            onResult(false, msg)
            return Pair(false, msg)
        }
        if (cleanPass.length < 4) {
            val msg = "Password must be at least 4 characters."
            onResult(false, msg)
            return Pair(false, msg)
        }

        val limit = getDeviceAccountLimit()
        val users = getUsers().toMutableList()
        val createdOnDeviceCount = users.count { it.createdDeviceId == currentDeviceId }
        if (createdOnDeviceCount >= limit) {
            val msg = "Using this device you have already created $limit accounts."
            onResult(false, msg)
            return Pair(false, msg)
        }

        if (users.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            val msg = "An account with this email already exists."
            onResult(false, msg)
            return Pair(false, msg)
        }

        val newUser = UserData(
            email = cleanEmail,
            name = cleanName,
            mobileNumber = cleanMobile,
            password = cleanPass,
            boundDeviceId = currentDeviceId,
            createdDeviceId = currentDeviceId
        )
        users.add(newUser)
        saveLocalUsers(users)
        syncUserToFirestore(newUser)

        // Handle Firebase Auth creation & leftover account from previously deleted users
        try {
            firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass)
                .addOnFailureListener {
                    firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                        .addOnSuccessListener { authRes ->
                            authRes.user?.updatePassword(cleanPass)
                        }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val msg = "Account created successfully!"
        onResult(true, msg)
        return Pair(true, msg)
    }

    fun loginUser(emailInput: String, passInput: String, currentDeviceId: String): Triple<Boolean, String, UserData?> {
        val cleanEmail = emailInput.trim().lowercase()
        val cleanPass = passInput.trim()

        val users = getUsers()
        val user = users.find { it.email.equals(cleanEmail, ignoreCase = true) }

        if (user == null) {
            return Triple(false, "No account found with this email. Please register first.", null)
        }

        if (user.password.isNotBlank() && user.password.trim() != cleanPass) {
            return Triple(false, "Invalid Email Address or Password.", null)
        }

        if (user.isBanned) {
            return Triple(false, "🚫 Your account has been banned. Contact Admin to unban.", user)
        }

        val isUnbound = user.boundDeviceId.isNullOrBlank() || user.boundDeviceId == "null" || user.boundDeviceId == "NONE" || user.boundDeviceId.equals("unbound", ignoreCase = true)
        if (!isUnbound && user.boundDeviceId != currentDeviceId) {
            return Triple(
                false,
                "Account bound to device (${user.boundDeviceId}). Contact Admin to reset device binding.",
                user
            )
        }

        if (isUnbound) {
            user.boundDeviceId = currentDeviceId
        }

        saveUser(user)
        return Triple(true, "Login successful!", user)
    }

    fun loginUserAsync(
        emailInput: String,
        passInput: String,
        currentDeviceId: String,
        onResult: (Boolean, String, UserData?) -> Unit
    ) {
        val cleanEmail = emailInput.trim().lowercase()
        val cleanPass = passInput.trim()

        findUserInFirestoreFlexible(cleanEmail) { user ->
            if (user == null) {
                // If user is deleted or does not exist, reject login and do NOT auto-create
                onResult(false, "No account found with this email. Please register first.", null)
                return@findUserInFirestoreFlexible
            }

            // Compare password
            val passMatches = user.password.isNotBlank() && user.password.trim() == cleanPass
            if (!passMatches) {
                // Try Firebase Auth verification in case password was changed in Auth
                try {
                    firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                        .addOnSuccessListener {
                            user.password = cleanPass
                            performLoginFinalize(user, currentDeviceId, onResult)
                        }
                        .addOnFailureListener {
                            onResult(false, "Invalid Email Address or Password.", null)
                        }
                } catch (e: Exception) {
                    onResult(false, "Invalid Email Address or Password.", null)
                }
                return@findUserInFirestoreFlexible
            }

            performLoginFinalize(user, currentDeviceId, onResult)
        }
    }

    private fun performLoginFinalize(
        user: UserData,
        currentDeviceId: String,
        onResult: (Boolean, String, UserData?) -> Unit
    ) {
        if (user.isBanned) {
            onResult(false, "🚫 Your account has been banned. Contact Admin to unban.", user)
            return
        }

        val isUnbound = user.boundDeviceId.isNullOrBlank() || user.boundDeviceId == "null" || user.boundDeviceId == "NONE" || user.boundDeviceId.equals("unbound", ignoreCase = true)
        if (!isUnbound && user.boundDeviceId != currentDeviceId) {
            onResult(
                false,
                "Account bound to device (${user.boundDeviceId}). Contact Admin to reset device binding.",
                user
            )
            return
        }

        if (isUnbound) {
            user.boundDeviceId = currentDeviceId
        }

        val now = System.currentTimeMillis()
        if (user.assignedDays > 0 && !user.isCountdownStarted) {
            user.subscriptionExpiryTimestamp = now + (user.assignedDays * 24 * 60 * 60 * 1000L)
            user.isCountdownStarted = true
        }

        saveUser(user)

        onResult(true, "Login successful!", user)
    }

    fun loginWithGoogle(email: String, name: String, currentDeviceId: String): Triple<Boolean, String, UserData?> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim().ifBlank { cleanEmail.substringBefore("@") }
        val users = getUsers().toMutableList()
        var user = users.find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (user == null) {
            val limit = getDeviceAccountLimit()
            val createdOnDeviceCount = users.count { it.createdDeviceId == currentDeviceId }
            if (createdOnDeviceCount >= limit) {
                return Triple(false, "Using this device you have already created $limit accounts.", null)
            }
            user = UserData(
                email = cleanEmail,
                name = cleanName,
                boundDeviceId = currentDeviceId,
                createdDeviceId = currentDeviceId
            )
            users.add(user)
        } else {
            if (user.isBanned) {
                return Triple(false, "🚫 Your account has been banned. Contact Admin to unban.", user)
            }
            val isUnbound = user.boundDeviceId.isNullOrBlank() || user.boundDeviceId == "null" || user.boundDeviceId == "NONE" || user.boundDeviceId.equals("unbound", ignoreCase = true)
            if (!isUnbound && user.boundDeviceId != currentDeviceId) {
                return Triple(false, "Account bound to device (${user.boundDeviceId}). Contact Admin to reset device binding.", user)
            }
            if (isUnbound) {
                user.boundDeviceId = currentDeviceId
            }
        }
        saveUser(user)
        return Triple(true, "Google Login Successful!", user)
    }

    // ── Admin Session & Authentication ──

    fun verifyAdminSession(email: String?, pass: String?): AdminSessionStatus {
        if (email.isNullOrBlank()) return AdminSessionStatus.NOT_LOGGED_IN
        val cleanEmail = email.trim().lowercase()
        val users = getUsers()

        // If local cache is not populated yet, do not falsely claim ACCOUNT_DELETED
        if (users.isEmpty()) {
            return AdminSessionStatus.SUCCESS
        }

        val user = users.find { it.email.equals(cleanEmail, ignoreCase = true) }
            ?: return AdminSessionStatus.ACCOUNT_DELETED

        if (user.isBanned) return AdminSessionStatus.ACCOUNT_BANNED
        if (!user.isAdmin && !user.isSuperAdmin && !user.isAdminAlpha && !user.email.contains("admin", ignoreCase = true)) {
            return AdminSessionStatus.ROLE_REVOKED
        }
        if (!pass.isNullOrBlank() && user.password.isNotBlank() && user.password.trim() != pass.trim()) {
            return AdminSessionStatus.PASSWORD_CHANGED
        }

        return AdminSessionStatus.SUCCESS
    }

    fun loginAdminUserAsync(email: String, pass: String, onComplete: (Boolean, UserData?) -> Unit) {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        findUserInFirestoreFlexible(cleanEmail) { user ->
            if (user != null) {
                val passMatch = user.password.isNotBlank() && user.password.trim() == cleanPass
                val isAdminRole = user.isAdmin || user.isSuperAdmin || user.isAdminAlpha || user.email.contains("admin", ignoreCase = true)

                if (passMatch && isAdminRole && !user.isBanned) {
                    if (!user.isAdmin && !user.isSuperAdmin && !user.isAdminAlpha) {
                        user.isAdmin = true
                    }
                    saveUser(user)
                    try {
                        firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                            .addOnSuccessListener {
                                onComplete(true, user)
                            }
                            .addOnFailureListener {
                                firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass)
                                    .addOnCompleteListener {
                                        onComplete(true, user)
                                    }
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onComplete(true, user)
                    }
                    return@findUserInFirestoreFlexible
                } else if (!passMatch) {
                    // Try Firebase Auth verification
                    try {
                        firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                            .addOnSuccessListener {
                                user.password = cleanPass
                                if (isAdminRole && !user.isBanned) {
                                    if (!user.isAdmin && !user.isSuperAdmin && !user.isAdminAlpha) {
                                        user.isAdmin = true
                                    }
                                    saveUser(user)
                                    onComplete(true, user)
                                } else {
                                    onComplete(false, null)
                                }
                            }
                            .addOnFailureListener {
                                onComplete(false, null)
                            }
                    } catch (e: Exception) {
                        onComplete(false, null)
                    }
                    return@findUserInFirestoreFlexible
                } else {
                    onComplete(false, null)
                    return@findUserInFirestoreFlexible
                }
            }

            // If admin is not found in database, reject login and do NOT auto-create
            onComplete(false, null)
        }
    }

    // ── Razorpay Auto-Activation ──

    fun activateAutoRazorpaySubscription(
        email: String,
        planName: String,
        amount: Double,
        days: Int,
        razorpayPaymentId: String,
        durationHours: Int? = null
    ) {
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        val users = getUsers().toMutableList()
        val user = users.find { it.email.equals(cleanEmail, ignoreCase = true) } ?: UserData(email = cleanEmail)

        val durationMs = if (durationHours != null && durationHours > 0) {
            durationHours * 3600 * 1000L
        } else {
            days * 24 * 3600 * 1000L
        }

        val isCurrentlyActive = user.isCountdownStarted && (user.subscriptionExpiryTimestamp > now) && !user.isBanned
        val newExpiry = if (isCurrentlyActive) {
            user.subscriptionExpiryTimestamp + durationMs
        } else {
            now + durationMs
        }

        val totalRemainingDays = Math.ceil((newExpiry - now).toDouble() / (24 * 3600 * 1000.0)).toInt().coerceAtLeast(0)
        user.assignedDays = totalRemainingDays
        user.subscriptionExpiryTimestamp = newExpiry
        user.isCountdownStarted = true
        user.paymentStatus = "ACTIVE"
        user.pendingPlan = null
        user.pendingAmount = null
        user.pendingUtr = null

        val isTrial = (durationHours != null && durationHours <= 2) || planName.contains("Trial", ignoreCase = true) || amount <= 1.0
        if (isTrial) {
            user.hasUsedTrial = true
            user.isTrialActive = true
        }

        val idx = users.indexOfFirst { it.email.equals(cleanEmail, ignoreCase = true) }
        if (idx != -1) users[idx] = user else users.add(user)
        saveLocalUsers(users)

        val docId = cleanEmail.replace(".", "_")
        val map = userToFirestoreMap(user)
        firestore.collection("users").document(docId).set(map)

        // Create Sale Record
        val sale = SaleRecord(
            transactionId = razorpayPaymentId,
            userEmail = cleanEmail,
            userName = user.name.ifBlank { cleanEmail },
            planName = planName,
            daysAssigned = days,
            amountPaid = amount,
            originalAmount = amount,
            isTrial = isTrial,
            activationTimestamp = now,
            activationSource = "AUTO_RAZORPAY",
            utr = razorpayPaymentId
        )
        val sales = getSalesRecords().toMutableList()
        sales.removeAll { it.transactionId == razorpayPaymentId }
        sales.add(0, sale)
        saveLocalSales(sales)

        firestore.collection("sales_records").document(razorpayPaymentId).set(sale)
    }

    // ── Admin User Management Operations ──

    fun grantPlanToUser(
        user: UserData,
        days: Int,
        planName: String = "$days Days Plan",
        amount: Double = 0.0,
        isTrial: Boolean = false,
        durationHours: Int? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanEmail = user.email.trim().lowercase()
        val now = System.currentTimeMillis()
        val durationMs = if (durationHours != null && durationHours > 0) {
            durationHours * 3600 * 1000L
        } else {
            days * 24 * 3600 * 1000L
        }

        val newExpiry = if (user.subscriptionExpiryTimestamp > now) {
            user.subscriptionExpiryTimestamp + durationMs
        } else {
            now + durationMs
        }

        val totalRemainingDays = Math.ceil((newExpiry - now).toDouble() / (24 * 3600 * 1000.0)).toInt().coerceAtLeast(0)
        user.assignedDays = totalRemainingDays
        user.subscriptionExpiryTimestamp = newExpiry
        user.isCountdownStarted = true
        user.paymentStatus = "ACTIVE"
        val pendingUtrToClear = user.pendingUtr ?: ""
        user.pendingPlan = null
        user.pendingAmount = null
        user.pendingUtr = null
        if (isTrial) user.hasUsedTrial = true

        saveUser(user)

        // Clear and mark notified for any pending UTR
        if (pendingUtrToClear.isNotBlank()) {
            NotificationHelper.markPaymentUtrNotified(context, pendingUtrToClear)
            NotificationHelper.cancelPaymentNotification(context, pendingUtrToClear)
        }
        NotificationHelper.cancelAllPaymentNotifications(context)

        // Direct single canonical Firestore update
        try {
            val docIdUnderscore = cleanEmail.replace(".", "_")
            val map = userToFirestoreMap(user)
            firestore.collection("users").document(docIdUnderscore).set(map)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val txnId = "ADMIN_${System.currentTimeMillis()}"
        val sale = SaleRecord(
            transactionId = txnId,
            userEmail = cleanEmail,
            userName = user.name.ifBlank { cleanEmail },
            planName = planName,
            daysAssigned = days,
            amountPaid = amount,
            originalAmount = amount,
            isTrial = isTrial,
            activationTimestamp = now,
            activationSource = "DIRECT_ADMIN",
            performedByAdmin = prefs.getString("logged_admin_email", "Admin")
        )
        val sales = getSalesRecords().toMutableList()
        sales.add(0, sale)
        saveLocalSales(sales)

        firestore.collection("sales_records").document(txnId).set(sale)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun assignCustomDuration(
        user: UserData,
        value: Int,
        unitType: String,
        amount: Double,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (unitType == "HOURS") {
            grantPlanToUser(
                user = user,
                days = (value / 24).coerceAtLeast(1),
                planName = "Custom Pass ($value Hour${if (value > 1) "s" else ""})",
                amount = amount,
                durationHours = value,
                onComplete = onComplete
            )
        } else {
            grantPlanToUser(
                user = user,
                days = value,
                planName = "Custom Pass ($value Day${if (value > 1) "s" else ""})",
                amount = amount,
                onComplete = onComplete
            )
        }
    }

    fun resetDeviceLock(user: UserData, onComplete: (Boolean) -> Unit) {
        user.boundDeviceId = null
        saveUser(user)
        onComplete(true)
    }

    fun banUser(user: UserData, isBanned: Boolean, onComplete: (Boolean) -> Unit) {
        user.isBanned = isBanned
        saveUser(user)
        val docIdUnderscore = user.email.trim().lowercase().replace(".", "_")
        firestore.collection("users").document(docIdUnderscore)
            .update("isBanned", isBanned)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun changeUserPassword(user: UserData, newPass: String, onComplete: (Boolean) -> Unit) {
        user.password = newPass.trim()
        saveUser(user)
        onComplete(true)
    }

    fun deleteUserAccount(user: UserData, onComplete: (Boolean) -> Unit = {}) {
        val cleanEmail = user.email.trim().lowercase()
        val users = getUsers().toMutableList()
        users.removeAll { it.email.equals(cleanEmail, ignoreCase = true) }
        saveLocalUsers(users)

        val docIdUnderscore = cleanEmail.replace(".", "_")
        firestore.collection("users").document(docIdUnderscore).delete()
        firestore.collection("users").document(cleanEmail).delete()

        try {
            firestore.collection("deleted_users").document(docIdUnderscore)
                .set(mapOf("email" to cleanEmail, "deletedAt" to System.currentTimeMillis()))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onComplete(true)
    }

    fun reGrantTrial(user: UserData, onComplete: (Boolean) -> Unit = {}) {
        user.hasUsedTrial = false
        saveUser(user)
        onComplete(true)
    }

    fun resetUserSubscription(
        user: UserData,
        reason: String = "",
        performingAdminEmail: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanEmail = user.email.trim().lowercase()
        val now = System.currentTimeMillis()

        user.assignedDays = 0
        user.subscriptionExpiryTimestamp = 0L
        user.isCountdownStarted = false
        user.paymentStatus = "EXPIRED"
        user.isTrialActive = false
        user.pendingPlan = null
        user.pendingAmount = null
        user.pendingUtr = null
        saveUser(user)

        val docIdUnderscore = cleanEmail.replace(".", "_")
        val userMap = userToFirestoreMap(user)
        firestore.collection("users").document(docIdUnderscore).set(userMap)

        val txnId = "RESET_${now}_${(1000..9999).random()}"
        val adminEmail = performingAdminEmail ?: prefs.getString("logged_admin_email", "Admin")
        val resetRecord = SaleRecord(
            transactionId = txnId,
            docId = txnId,
            userEmail = cleanEmail,
            userName = user.name.ifBlank { cleanEmail },
            planName = "Subscription Reset",
            daysAssigned = 0,
            amountPaid = 0.0,
            originalAmount = 0.0,
            isTrial = false,
            utr = "RESET",
            screenshotUrl = null,
            activationTimestamp = now,
            activationSource = "ADMIN_RESET",
            resetReason = reason.ifBlank { "Reset by Admin" },
            performedByAdmin = adminEmail
        )

        val sales = getSalesRecords().toMutableList()
        sales.add(0, resetRecord)
        saveLocalSales(sales)

        firestore.collection("sales_records").document(txnId).set(resetRecord)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun getProfileSubscriptionStatus(user: UserData?): String {
        if (user == null) return "EXPIRED"
        if (user.isBanned) return "BANNED"
        val now = System.currentTimeMillis()
        return if (user.isCountdownStarted && user.subscriptionExpiryTimestamp > now) {
            "ACTIVE"
        } else if (user.assignedDays > 0 && !user.isCountdownStarted) {
            "NOT STARTED"
        } else {
            "EXPIRED"
        }
    }

    fun getActivePlanDetails(user: UserData?): String {
        if (user == null) return "No active plan. Please choose a plan below."
        if (user.isBanned) return "Account Suspended by Admin. Contact support."
        val now = System.currentTimeMillis()
        if (user.isCountdownStarted && user.subscriptionExpiryTimestamp > now) {
            val diffMs = user.subscriptionExpiryTimestamp - now
            val hours = diffMs / (1000 * 60 * 60)
            val days = hours / 24
            val remHours = hours % 24
            return if (days > 0) {
                "$days Days, $remHours Hours remaining"
            } else {
                "$hours Hours remaining"
            }
        } else if (user.assignedDays > 0 && !user.isCountdownStarted) {
            return "${user.assignedDays} Days Plan (Starts on first ride accept)"
        }
        return "Expired. Please renew to access features."
    }

    fun updateUserRole(user: UserData, role: String, onComplete: (Boolean) -> Unit) {
        val isAdmin = role == "ADMIN" || role == "ALPHA_ADMIN" || role == "SUPER_ADMIN"
        val isAdminAlpha = role == "ALPHA_ADMIN"
        val isSuperAdmin = role == "SUPER_ADMIN"

        user.isAdmin = isAdmin
        user.isAdminAlpha = isAdminAlpha
        user.isSuperAdmin = isSuperAdmin
        saveUser(user)
        onComplete(true)
    }

    // ── Dynamic Config Updates ──

    fun updateRazorpayConfig(config: RazorpayConfig, onComplete: (Boolean) -> Unit) {
        val data = hashMapOf(
            "keyId" to config.keyId.trim(),
            "keySecret" to config.keySecret.trim(),
            "isEnabled" to config.isEnabled,
            "merchantName" to config.merchantName.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("config").document("razorpay")
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updatePlanPricing(config: PlanPricingConfig, onComplete: (Boolean) -> Unit) {
        val data = hashMapOf(
            "price1Day" to config.price1Day,
            "price3Days" to config.price3Days,
            "price7Days" to config.price7Days,
            "price15Days" to config.price15Days,
            "price30Days" to config.price30Days,
            "price365Days" to config.price365Days,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("config").document("plan_pricing")
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateDynamicLinks(config: DynamicLinksConfig, onComplete: (Boolean) -> Unit) {
        val data = hashMapOf(
            "telegramSupport" to config.telegramSupport.trim(),
            "whatsappSupport" to config.whatsappSupport.trim(),
            "youtubeChannel" to config.youtubeChannel.trim(),
            "getApp" to config.getApp.trim(),
            "shizukuApk" to config.shizukuApk.trim(),
            "tutorialVideo" to config.tutorialVideo.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("config").document("dynamic_links")
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateOfferBanner(config: OfferBannerConfig, onComplete: (Boolean) -> Unit) {
        val data = hashMapOf(
            "showBanner" to config.showBanner,
            "bannerText" to config.bannerText.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("config").document("offer_banner")
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun getDeviceAccountLimit(): Int = prefs.getInt("device_account_limit", 2)

    fun updateDeviceAccountLimit(limit: Int, onComplete: (Boolean) -> Unit) {
        prefs.edit().putInt("device_account_limit", limit).apply()
        val data = hashMapOf("maxAccountsPerDevice" to limit, "updatedAt" to System.currentTimeMillis())
        firestore.collection("app_config").document("device_settings").set(data)
        firestore.collection("config").document("device_settings").set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(true) }
    }

    fun updateSaleRecordAmount(sale: SaleRecord, newAmount: Double, reason: String, adminEmail: String, onComplete: (Boolean) -> Unit) {
        val targetId = sale.docId.ifBlank { sale.transactionId }
        if (targetId.isBlank()) {
            onComplete(false)
            return
        }

        // Authenticate admin if credentials available
        val loggedEmail = prefs.getString("logged_admin_email", null) ?: ""
        val loggedPass = prefs.getString("logged_admin_password", null) ?: ""
        if (loggedEmail.isNotBlank() && loggedPass.isNotBlank()) {
            try {
                firebaseAuth.signInWithEmailAndPassword(loggedEmail.trim().lowercase(), loggedPass)
            } catch (_: Exception) {}
        }

        // Update local memory cache immediately
        val sales = getSalesRecords().toMutableList()
        val index = sales.indexOfFirst { (it.docId.isNotBlank() && it.docId == targetId) || (it.transactionId.isNotBlank() && it.transactionId == targetId) }
        if (index >= 0) {
            val old = sales[index]
            sales[index] = old.copy(
                amountPaid = newAmount,
                modificationReason = reason,
                performedByAdmin = adminEmail
            )
            saveLocalSales(sales)
        }

        val updateMap = hashMapOf<String, Any?>(
            "amountPaid" to newAmount,
            "modificationReason" to reason,
            "performedByAdmin" to adminEmail
        )

        val docRef = firestore.collection("sales_records").document(targetId)
        docRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (sale.transactionId.isNotBlank() && sale.transactionId != targetId) {
                    firestore.collection("sales_records").document(sale.transactionId)
                        .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                }
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun softDeleteSaleRecordAsync(
        sale: SaleRecord,
        deletedByAdmin: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val targetDocId = sale.docId.ifBlank { sale.transactionId }
        val adminEmail = prefs.getString("logged_admin_email", null) ?: ""
        val adminPass = prefs.getString("logged_admin_password", null) ?: ""
        if (adminEmail.isNotBlank() && adminPass.isNotBlank()) {
            try {
                firebaseAuth.signInWithEmailAndPassword(adminEmail.trim().lowercase(), adminPass)
            } catch (e: Exception) {
                // Ignore auth error
            }
        }

        val deleteTs = NetworkTimeHelper.getCurrentNetworkTime()
        val deletedBy = deletedByAdmin ?: adminEmail.ifBlank { "Admin" }
        val updateMap = hashMapOf<String, Any?>(
            "isDeleted" to true,
            "deletedByAdmin" to deletedBy,
            "deletedTimestamp" to deleteTs
        )

        try {
            firestore.collection("sales_records").document(targetDocId)
                .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (sale.transactionId.isNotBlank() && sale.transactionId != targetDocId) {
                        firestore.collection("sales_records").document(sale.transactionId)
                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                    }

                    // Remove from local active sales cache
                    val currentSales = getSalesRecords().toMutableList()
                    currentSales.removeAll {
                        (sale.transactionId.isNotBlank() && it.transactionId == sale.transactionId) ||
                        (targetDocId.isNotBlank() && it.docId == targetDocId) ||
                        (!sale.utr.isNullOrBlank() && it.utr == sale.utr)
                    }
                    saveLocalSales(currentSales)

                    onComplete(true, "Log moved to trash! (Super Admin can restore)")
                }
                .addOnFailureListener { e ->
                    onComplete(false, "Failed to delete log: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            onComplete(false, "Error: ${e.localizedMessage}")
        }
    }

    fun deleteSaleRecordAsync(
        transactionId: String,
        docId: String = "",
        utr: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val sales = getSalesRecords()
        val record = sales.find {
            (transactionId.isNotBlank() && it.transactionId == transactionId) ||
            (docId.isNotBlank() && it.docId == docId) ||
            (utr.isNotBlank() && it.utr == utr)
        }
        val targetSale = record ?: SaleRecord(
            transactionId = transactionId,
            docId = if (docId.isNotBlank()) docId else transactionId,
            utr = utr.ifBlank { null }
        )
        softDeleteSaleRecordAsync(targetSale, prefs.getString("logged_admin_email", null), onComplete)
    }

    fun restoreSaleRecordAsync(
        sale: SaleRecord,
        performingAdminEmail: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val targetDocId = sale.docId.ifBlank { sale.transactionId }
        val adminEmail = performingAdminEmail ?: prefs.getString("logged_admin_email", null) ?: "SuperAdmin"
        val adminPass = prefs.getString("logged_admin_password", null) ?: ""
        if (adminEmail.isNotBlank() && adminPass.isNotBlank()) {
            try {
                firebaseAuth.signInWithEmailAndPassword(adminEmail.trim().lowercase(), adminPass)
            } catch (e: Exception) {
                // Ignore auth error
            }
        }

        if (sale.activationSource == "REJECTION_LOG") {
            try {
                val cleanEmail = sale.userEmail.trim().lowercase()
                val user = getUserByEmail(cleanEmail)
                if (user != null) {
                    val historyList = parseRejectionHistory(user.rejectionHistoryJson).toMutableList()
                    val rejId = sale.transactionId.removePrefix("REJ_")
                    if (historyList.none { it.id == rejId }) {
                        val restoredRej = RejectionRecord(
                            id = rejId,
                            userEmail = cleanEmail,
                            userName = sale.userName,
                            planName = sale.planName.removePrefix("Rejected: ").trim(),
                            amount = if (sale.amountPaid > 0) "₹${sale.amountPaid.toInt()}" else "₹0",
                            utr = sale.utr,
                            screenshotUrl = sale.screenshotUrl,
                            rejectionReason = sale.resetReason ?: "Payment Rejected",
                            timestamp = sale.activationTimestamp
                        )
                        historyList.add(0, restoredRej)
                        user.rejectionHistoryJson = serializeRejectionHistory(historyList)
                        val userDocId = cleanEmail.replace(".", "_")
                        firestore.collection("users").document(userDocId).set(userToFirestoreMap(user))
                            .addOnSuccessListener {
                                saveUser(user)
                            }
                    }
                }
                // Delete the tombstone from sales_records
                firestore.collection("sales_records").document(targetDocId).delete()
                if (sale.transactionId.isNotBlank() && sale.transactionId != targetDocId) {
                    firestore.collection("sales_records").document(sale.transactionId).delete()
                }
                onComplete(true, "Rejection record restored to user profile!")
            } catch (e: Exception) {
                onComplete(false, "Failed to restore rejection: ${e.localizedMessage}")
            }
            return
        }

        val restoreMap = hashMapOf<String, Any?>(
            "isDeleted" to false,
            "deletedByAdmin" to null,
            "deletedTimestamp" to 0L
        )

        try {
            firestore.collection("sales_records").document(targetDocId)
                .set(restoreMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (sale.transactionId.isNotBlank() && sale.transactionId != targetDocId) {
                        firestore.collection("sales_records").document(sale.transactionId)
                            .set(restoreMap, com.google.firebase.firestore.SetOptions.merge())
                    }

                    // Add to local cache
                    val restoredRecord = sale.copy(isDeleted = false, deletedByAdmin = null, deletedTimestamp = 0L)
                    val currentSales = getSalesRecords().toMutableList()
                    currentSales.removeAll { it.transactionId == sale.transactionId || it.docId == targetDocId }
                    currentSales.add(0, restoredRecord)
                    saveLocalSales(currentSales)

                    onComplete(true, "Sales log restored successfully!")
                }
                .addOnFailureListener { e ->
                    onComplete(false, "Failed to restore log: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            onComplete(false, "Error: ${e.localizedMessage}")
        }
    }

    fun permanentlyDeleteSaleRecordAsync(
        sale: SaleRecord,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val targetDocId = sale.docId.ifBlank { sale.transactionId }
        val adminEmail = prefs.getString("logged_admin_email", null) ?: ""
        val adminPass = prefs.getString("logged_admin_password", null) ?: ""
        if (adminEmail.isNotBlank() && adminPass.isNotBlank()) {
            try {
                firebaseAuth.signInWithEmailAndPassword(adminEmail.trim().lowercase(), adminPass)
            } catch (e: Exception) {
                // Ignore auth error
            }
        }

        try {
            if (targetDocId.isNotBlank()) {
                firestore.collection("sales_records").document(targetDocId).delete()
            }
            if (sale.transactionId.isNotBlank() && sale.transactionId != targetDocId) {
                firestore.collection("sales_records").document(sale.transactionId).delete()
            }
            if (sale.transactionId.isNotBlank()) {
                firestore.collection("sales_records").whereEqualTo("transactionId", sale.transactionId).get()
                    .addOnSuccessListener { qs ->
                        for (d in qs.documents) {
                            d.reference.delete()
                        }
                    }
            }
            onComplete(true, "Log permanently deleted.")
        } catch (e: Exception) {
            onComplete(false, "Error: ${e.localizedMessage}")
        }
    }

    fun deleteSaleRecord(sale: SaleRecord, onComplete: (Boolean) -> Unit) {
        softDeleteSaleRecordAsync(sale, prefs.getString("logged_admin_email", null)) { success, _ ->
            onComplete(success)
        }
    }

    fun parseRejectionHistory(jsonStr: String): List<RejectionRecord> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<RejectionRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RejectionRecord(
                        id = obj.optString("id", ""),
                        userEmail = obj.optString("userEmail", ""),
                        userName = obj.optString("userName", ""),
                        planName = obj.optString("planName", ""),
                        amount = obj.optString("amount", ""),
                        utr = if (obj.has("utr") && !obj.isNull("utr")) obj.getString("utr") else null,
                        screenshotUrl = if (obj.has("screenshotUrl") && !obj.isNull("screenshotUrl")) obj.getString("screenshotUrl") else null,
                        rejectionReason = obj.optString("rejectionReason", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        deletedByAdmin = if (obj.has("deletedByAdmin") && !obj.isNull("deletedByAdmin")) obj.getString("deletedByAdmin") else null,
                        deletedTimestamp = obj.optLong("deletedTimestamp", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeRejectionHistory(list: List<RejectionRecord>): String {
        val array = JSONArray()
        for (r in list) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("userEmail", r.userEmail)
                put("userName", r.userName)
                put("planName", r.planName)
                put("amount", r.amount)
                put("utr", r.utr ?: JSONObject.NULL)
                put("screenshotUrl", r.screenshotUrl ?: JSONObject.NULL)
                put("rejectionReason", r.rejectionReason)
                put("timestamp", r.timestamp)
                put("isDeleted", r.isDeleted)
                put("deletedByAdmin", r.deletedByAdmin ?: JSONObject.NULL)
                put("deletedTimestamp", r.deletedTimestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun adminDeleteRejectionRecordAsync(
        userEmail: String,
        rejectionId: String,
        performingAdminEmail: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanEmail = userEmail.trim().lowercase()
        val docId = cleanEmail.replace(".", "_")
        val users = getUsers()
        val user = users.find { it.email.equals(cleanEmail, ignoreCase = true) }
        if (user == null) {
            onComplete(false, "User not found")
            return
        }

        val historyList = parseRejectionHistory(user.rejectionHistoryJson).toMutableList()
        val targetRej = historyList.find { it.id == rejectionId }
        if (targetRej == null) {
            onComplete(false, "Rejection record not found")
            return
        }
        historyList.remove(targetRej)

        user.rejectionHistoryJson = serializeRejectionHistory(historyList)

        val adminEmail = performingAdminEmail ?: prefs.getString("logged_admin_email", null) ?: ""
        val adminPass = prefs.getString("logged_admin_password", null) ?: ""
        if (adminEmail.isNotBlank() && adminPass.isNotBlank()) {
            try {
                firebaseAuth.signInWithEmailAndPassword(adminEmail.trim().lowercase(), adminPass)
            } catch (e: Exception) {
                // Ignore auth error
            }
        }

        val map = userToFirestoreMap(user)
        firestore.collection("users").document(docId).set(map)
            .addOnSuccessListener {
                saveUser(user)

                // Route deleted rejection to Trash Vault in sales_records
                val now = NetworkTimeHelper.getCurrentNetworkTime()
                val tombstoneDocId = "REJ_$rejectionId"
                val rejMap = hashMapOf<String, Any?>(
                    "transactionId" to tombstoneDocId,
                    "docId" to tombstoneDocId,
                    "userEmail" to cleanEmail,
                    "userName" to user.name,
                    "planName" to "Rejected: ${targetRej.planName}",
                    "daysAssigned" to 0,
                    "amountPaid" to 0.0,
                    "originalAmount" to 0.0,
                    "isTrial" to false,
                    "utr" to targetRej.utr,
                    "screenshotUrl" to targetRej.screenshotUrl,
                    "activationTimestamp" to targetRej.timestamp,
                    "activatedTimestamp" to targetRej.timestamp,
                    "activationSource" to "REJECTION_LOG",
                    "resetReason" to targetRej.rejectionReason,
                    "performedByAdmin" to adminEmail,
                    "isDeleted" to true,
                    "deletedByAdmin" to adminEmail,
                    "deletedTimestamp" to now
                )
                firestore.collection("sales_records").document(tombstoneDocId).set(rejMap)

                onComplete(true, "Rejection record moved to Trash Vault! (Super Admin can restore)")
            }
            .addOnFailureListener { e ->
                onComplete(false, "Cloud delete failed: ${e.localizedMessage}")
            }
    }
}
