package com.rideauto.assistant

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.rideauto.assistant.data.RideHistoryDatabase
import com.rideauto.assistant.data.RideHistoryEntity
import com.rideauto.assistant.security.AppSecurityGuard
import com.rideauto.assistant.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab(val label: String, val icon: ImageVector, val requiresSubscription: Boolean = false) {
    PLANS("Plans", Icons.Filled.ShoppingBag, false),
    LOGIN("Login", Icons.AutoMirrored.Filled.Login, false),
    DASHBOARD("Home", Icons.Filled.Dashboard, true),
    FILTERS("Filter", Icons.Filled.Tune, true),
    APPS("Apps", Icons.Filled.Apps, true),
    HISTORY("History", Icons.Filled.History, true),
    PROFILE("Profile", Icons.Filled.Person, false)
}

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private lateinit var prefs: PreferencesHelper
    private lateinit var authManager: AuthManager
    private lateinit var userRepository: UserRepository
    private val selectedTabState = mutableStateOf(AppTab.LOGIN)

    // Current checkout tracking
    private var pendingCheckoutPlanName: String? = null
    private var pendingCheckoutAmount: Double = 0.0
    private var pendingCheckoutDays: Int = 0
    private var pendingCheckoutDurationHours: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesHelper.getInstance(this)
        authManager = AuthManager(this)
        userRepository = UserRepository.getInstance(this)
        ShizukuManager.initialize(this)

        // Preload Razorpay Checkout
        try {
            Checkout.preload(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SubscriptionNotificationService.stop(this)

        val hasIntentTab = handleTabIntent(intent)
        if (!hasIntentTab) {
            selectedTabState.value = when {
                !authManager.isLoggedIn() -> AppTab.LOGIN
                authManager.isSubscriptionValid() -> AppTab.DASHBOARD
                else -> AppTab.PLANS
            }
        }

        setContent {
            RideAssistantProTheme {
                MainAppScreen(
                    prefs = prefs,
                    selectedTabState = selectedTabState,
                    onStartRazorpayPayment = { planName, amount, days, razorpayKey, durationHours ->
                        startRazorpayCheckout(planName, amount, days, razorpayKey, durationHours)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTabIntent(intent)
    }

    private fun handleTabIntent(intent: Intent?): Boolean {
        val tabName = intent?.getStringExtra(OverlayService.EXTRA_OPEN_TAB)
        if (!tabName.isNullOrEmpty()) {
            try {
                selectedTabState.value = AppTab.valueOf(tabName)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    private fun startRazorpayCheckout(planName: String, amount: Double, days: Int, keyId: String, durationHours: Int? = null) {
        pendingCheckoutPlanName = planName
        pendingCheckoutAmount = amount
        pendingCheckoutDays = days
        pendingCheckoutDurationHours = durationHours

        val checkout = Checkout()
        val keyToUse = if (keyId.isNotBlank()) keyId else "rzp_test_TYKenXfKX7ntr1"
        checkout.setKeyID(keyToUse)

        try {
            val userEmail = authManager.getLoggedInUserId()
            val userName = authManager.getLoggedInUserName()

            val desc = if (durationHours != null && durationHours > 0) {
                "$planName ($durationHours Hours Access)"
            } else {
                "$planName ($days Days Subscription)"
            }

            val options = JSONObject().apply {
                put("name", "Ride Clicker Pro+")
                put("description", desc)
                put("currency", "INR")
                put("amount", (amount * 100).toLong()) // Amount in paise
                put("send_sms_hash", true)

                val prefill = JSONObject().apply {
                    put("email", if (userEmail != "Not Logged In") userEmail else "")
                    put("name", if (userName != "Guest") userName else "")
                }
                put("prefill", prefill)

                val theme = JSONObject().apply {
                    put("color", "#1E88E5")
                }
                put("theme", theme)
            }

            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Error initiating Razorpay checkout: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val email = authManager.getLoggedInUserId()
        val planName = pendingCheckoutPlanName ?: "Pro Pass"
        val amount = pendingCheckoutAmount
        val days = pendingCheckoutDays.coerceAtLeast(0)
        val durationHours = pendingCheckoutDurationHours
        val paymentId = razorpayPaymentId ?: paymentData?.paymentId ?: "TXN_${System.currentTimeMillis()}"

        userRepository.activateAutoRazorpaySubscription(
            email = email,
            planName = planName,
            amount = amount,
            days = days,
            razorpayPaymentId = paymentId,
            durationHours = durationHours
        )

        val updatedUser = userRepository.getUserByEmail(email)
        val expiry = updatedUser?.subscriptionExpiryTimestamp ?: (System.currentTimeMillis() + (if (durationHours != null && durationHours > 0) durationHours * 3600 * 1000L else days * 24 * 3600 * 1000L))
        authManager.updateLocalSubscriptionState(expiry, true)

        val label = if (durationHours != null && durationHours > 0) "$planName ($durationHours Hours)" else "$planName ($days Days)"
        Toast.makeText(
            this,
            "🎉 Payment Successful! $label Activated Successfully!",
            Toast.LENGTH_LONG
        ).show()

        selectedTabState.value = AppTab.DASHBOARD
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Payment Failed/Cancelled: $response", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            userRepository.removeListeners()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    prefs: PreferencesHelper,
    selectedTabState: MutableState<AppTab>,
    onStartRazorpayPayment: (String, Double, Int, String, Int?) -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val userRepository = remember { UserRepository.getInstance(context) }
    var selectedTab by selectedTabState

    var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn()) }
    var isSubscribed by remember { mutableStateOf(authManager.isSubscriptionValid()) }
    var currentUserData by remember { mutableStateOf<UserData?>(null) }
    var lastSeenExpiry by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var planPricing by remember { mutableStateOf(PlanPricingConfig()) }
    var razorpayConfig by remember { mutableStateOf(RazorpayConfig()) }
    var dynamicLinks by remember { mutableStateOf(DynamicLinksConfig()) }
    var offerBanner by remember { mutableStateOf(OfferBannerConfig()) }

    val loggedInEmail = if (isLoggedIn) authManager.getLoggedInUserId() else ""

    // Real-time synchronization
    DisposableEffect(isLoggedIn, loggedInEmail) {
        val u1 = userRepository.observePlanPricing { planPricing = it }
        val u2 = userRepository.observeRazorpayConfig { razorpayConfig = it }
        val u3 = userRepository.observeDynamicLinks { dynamicLinks = it }
        val u4 = userRepository.observeOfferBanner { offerBanner = it }

        var userUnsub: (() -> Unit)? = null
        if (isLoggedIn && loggedInEmail.isNotBlank() && loggedInEmail != "Not Logged In") {

            userUnsub = userRepository.observeCurrentUser(loggedInEmail) { user ->
                if (user == null) {
                    userRepository.deleteLocalUser(loggedInEmail)
                    OverlayService.stop(context)
                    prefs.isServiceEnabled = false
                    authManager.logout()
                    isLoggedIn = false
                    isSubscribed = false
                    currentUserData = null
                    selectedTab = AppTab.LOGIN
                    Toast.makeText(context, "🚫 Account deleted by Admin. Logged out immediately.", Toast.LENGTH_LONG).show()
                } else {
                    currentUserData = user
                    val currentFp = authManager.getDeviceFingerprint()
                    val isUnbound = user.boundDeviceId.isNullOrBlank() || user.boundDeviceId == "null" || user.boundDeviceId == "NONE" || user.boundDeviceId.equals("unbound", ignoreCase = true)
                    if (isUnbound) {
                        user.boundDeviceId = currentFp
                        userRepository.saveUser(user)
                    } else if (user.boundDeviceId != currentFp) {
                        authManager.logout()
                        isLoggedIn = false
                        isSubscribed = false
                        selectedTab = AppTab.LOGIN
                        Toast.makeText(context, "Account bound to another device (${user.boundDeviceId}). Logged out.", Toast.LENGTH_LONG).show()
                        return@observeCurrentUser
                    }

                    if (user.isBanned) {
                        prefs.isBanned = true
                        prefs.isServiceEnabled = false
                        isSubscribed = false
                        OverlayService.stop(context)
                    } else {
                        prefs.isBanned = false
                    }

                    val now = System.currentTimeMillis()
                    var soundNeeded = false
                    if (user.assignedDays > 0 && !user.isCountdownStarted && !user.isBanned) {
                        user.subscriptionExpiryTimestamp = now + (user.assignedDays * 24 * 60 * 60 * 1000L)
                        user.isCountdownStarted = true
                        soundNeeded = true
                        userRepository.syncUserToFirestore(user)
                    } else if (user.isCountdownStarted && user.subscriptionExpiryTimestamp > lastSeenExpiry && lastSeenExpiry > 0L && !user.isBanned) {
                        soundNeeded = true
                    }
                    lastSeenExpiry = user.subscriptionExpiryTimestamp

                    val hasActiveSub = user.isCountdownStarted && (user.subscriptionExpiryTimestamp > now) && !user.isBanned
                    val wasSubscribed = isSubscribed
                    isSubscribed = hasActiveSub
                    authManager.updateLocalSubscriptionState(user.subscriptionExpiryTimestamp, hasActiveSub)

                    if (soundNeeded) {
                        NotificationHelper.triggerSubscriptionActivatedNotification(context, user.assignedDays)
                    }

                    if (hasActiveSub && (!wasSubscribed && (selectedTab == AppTab.PLANS || selectedTab == AppTab.PROFILE || selectedTab == AppTab.LOGIN))) {
                        selectedTab = AppTab.DASHBOARD
                        Toast.makeText(context, "⚡ Subscription Activated! Access Granted.", Toast.LENGTH_SHORT).show()
                    } else if (!hasActiveSub && selectedTab.requiresSubscription) {
                        selectedTab = AppTab.PROFILE
                        if (user.isBanned) {
                            Toast.makeText(context, "🚫 Account Banned by Admin. Access Restricted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Subscription expired or reset by Admin. Feature locked.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        onDispose {
            u1(); u2(); u3(); u4()
            userUnsub?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            NetworkTimeHelper.syncNetworkTime()
        }
        if (!authManager.isLoggedIn()) {
            selectedTab = AppTab.LOGIN
        } else if (!authManager.isSubscriptionValid() && selectedTab.requiresSubscription) {
            selectedTab = AppTab.PLANS
        }
    }

    LaunchedEffect(isLoggedIn, isSubscribed) {
        while (isLoggedIn && isSubscribed) {
            kotlinx.coroutines.delay(5000L)
            if (!authManager.isSubscriptionValid()) {
                isSubscribed = false
                if (selectedTab.requiresSubscription) {
                    selectedTab = AppTab.PROFILE
                    Toast.makeText(context, "Subscription expired. Feature locked.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        isLoggedIn = authManager.isLoggedIn()
        isSubscribed = authManager.isSubscriptionValid() && currentUserData?.isBanned != true
    }

    LaunchedEffect(isSubscribed) {
        if (isLoggedIn && isSubscribed && selectedTab == AppTab.PLANS) {
            selectedTab = AppTab.DASHBOARD
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedTab != AppTab.LOGIN) {
                TopAppBar(
                    title = {
                        if (selectedTab == AppTab.DASHBOARD) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.user_app_inside),
                                    contentDescription = "Ride Clicker Pro+",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Ride Clicker Pro+",
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 17.sp
                                        )
                                        if (prefs.isTestModeActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = AccentOrange.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "TEST",
                                                    color = AccentOrange,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "v5.0.6 • Ultra Fast Clicker",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NavActiveYellow
                                    )
                                }
                            }
                        } else {
                            val pageTitle = when (selectedTab) {
                                AppTab.FILTERS -> "Filters"
                                AppTab.APPS -> "Supported Apps"
                                AppTab.HISTORY -> "Ride History"
                                AppTab.PLANS -> "Subscription Plans"
                                AppTab.PROFILE -> "My Profile"
                                else -> ""
                            }
                            Text(
                                text = pageTitle,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 18.sp
                            )
                        }
                    },
                    actions = {
                        if (isLoggedIn && selectedTab != AppTab.PROFILE) {
                            IconButton(onClick = { selectedTab = AppTab.PROFILE }) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = NavActiveYellow)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GlassSurface)
                )
            }
        },
        bottomBar = {
            val tabs = if (isLoggedIn) {
                if (isSubscribed || prefs.isTestModeActive) {
                    listOf(AppTab.DASHBOARD, AppTab.FILTERS, AppTab.APPS, AppTab.HISTORY, AppTab.PROFILE)
                } else {
                    listOf(AppTab.PLANS, AppTab.DASHBOARD, AppTab.FILTERS, AppTab.APPS, AppTab.HISTORY, AppTab.PROFILE)
                }
            } else {
                listOf(AppTab.LOGIN, AppTab.PLANS)
            }

            NavigationBar(
                containerColor = GlassSurface,
                modifier = Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                tabs.forEach { tab ->
                    val isLocked = tab.requiresSubscription && !isSubscribed && !prefs.isTestModeActive
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (isLocked) {
                                        Badge(
                                            containerColor = Color.Transparent,
                                            contentColor = Color(0xFFFF3B30)
                                        ) {
                                            Text("🔒", fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isLocked) ActionGreen else NavActiveYellow,
                            selectedTextColor = if (isLocked) ActionGreen else NavActiveYellow,
                            indicatorColor = if (isLocked) ActionGreen.copy(alpha = 0.18f) else NavActiveYellow.copy(alpha = 0.18f),
                            unselectedIconColor = if (isLocked) TextSecondary.copy(alpha = 0.6f) else TextSecondary,
                            unselectedTextColor = if (isLocked) TextSecondary.copy(alpha = 0.6f) else TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            label = "TabContentAnimation",
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { currentTab ->
            when (currentTab) {
                AppTab.LOGIN -> LoginTab(
                    context = context,
                    authManager = authManager,
                    onLoginSuccess = {
                        isLoggedIn = true
                        isSubscribed = authManager.isSubscriptionValid()
                        selectedTab = if (isSubscribed) AppTab.DASHBOARD else AppTab.PLANS
                        scope.launch {
                            snackbarHostState.showSnackbar("Logged in successfully!")
                        }
                    },
                    onNavigateToPlans = {
                        selectedTab = AppTab.PLANS
                    }
                )
                AppTab.DASHBOARD -> {
                    if (!isSubscribed && !prefs.isTestModeActive) {
                        LockedFeatureScreen(
                            featureName = "Dashboard",
                            description = "An active subscription is required to access Dashboard, auto-accept controls, fare filters, and multi-app monitoring.",
                            onUpgradeClick = { selectedTab = AppTab.PLANS }
                        )
                    } else {
                        DashboardTab(context, prefs, dynamicLinks)
                    }
                }
                AppTab.FILTERS -> {
                    if (!isSubscribed && !prefs.isTestModeActive) {
                        LockedFeatureScreen(
                            featureName = "Filter",
                            description = "An active subscription is required to configure fare range filters, pickup distance limits, and preferred zones.",
                            onUpgradeClick = { selectedTab = AppTab.PLANS }
                        )
                    } else {
                        FiltersTab(context, prefs)
                    }
                }
                AppTab.APPS -> {
                    if (!isSubscribed && !prefs.isTestModeActive) {
                        LockedFeatureScreen(
                            featureName = "Apps",
                            description = "An active subscription is required to configure multi-app auto accept for Ola, Rapido, and Uber.",
                            onUpgradeClick = { selectedTab = AppTab.PLANS }
                        )
                    } else {
                        AppsTab(prefs)
                    }
                }
                AppTab.HISTORY -> {
                    if (!isSubscribed && !prefs.isTestModeActive) {
                        LockedFeatureScreen(
                            featureName = "History",
                            description = "An active subscription is required to view accepted ride logs, daily earnings, and trip metrics.",
                            onUpgradeClick = { selectedTab = AppTab.PLANS }
                        )
                    } else {
                        HistoryTab(context)
                    }
                }
                AppTab.PLANS -> PlansTab(
                    planPricing = planPricing,
                    offerBanner = offerBanner,
                    razorpayConfig = razorpayConfig,
                    dynamicLinks = dynamicLinks,
                    currentUser = currentUserData,
                    isLoggedIn = isLoggedIn,
                    onBuyPlan = { planName, amount, days, durationHours ->
                        if (!authManager.isLoggedIn() || !isLoggedIn) {
                            Toast.makeText(context, "⚠️ Please sign in or register first to purchase a subscription.", Toast.LENGTH_LONG).show()
                            selectedTab = AppTab.LOGIN
                            return@PlansTab
                        }
                        onStartRazorpayPayment(planName, amount, days, razorpayConfig.keyId, durationHours)
                    },
                    onContactAdminTelegram = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dynamicLinks.telegramSupport))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                AppTab.PROFILE -> ProfileTab(
                    context = context,
                    authManager = authManager,
                    user = currentUserData,
                    isSubscribed = isSubscribed,
                    dynamicLinks = dynamicLinks,
                    onLogout = {
                        authManager.logout()
                        isLoggedIn = false
                        isSubscribed = false
                        selectedTab = AppTab.LOGIN
                    },
                    onNavigateToPlans = {
                        selectedTab = AppTab.PLANS
                    }
                )
            }
        }
    }
}

@Composable
fun LockedFeatureScreen(
    featureName: String,
    description: String,
    onUpgradeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Red circular background with lock icon matching user's reference screenshot
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color(0xFF381418))
                .border(1.5.dp, Color(0xFFD32F2F).copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "$featureName Locked",
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Title: "$featureName Locked 🔒"
        Text(
            text = "$featureName Locked 🔒",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Subtitle / Description text
        Text(
            text = description,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(34.dp))

        // UPGRADE PLAN TO UNLOCK Button
        Button(
            onClick = onUpgradeClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = ActionGreen,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "UPGRADE PLAN TO UNLOCK",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (TouchInputService.isServiceRunning) return true
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        enabledServices.contains(context.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isOverlayPermissionGranted(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (e: Exception) {
        false
    }
}

@Composable
fun DashboardTab(
    context: Context,
    prefs: PreferencesHelper,
    dynamicLinks: DynamicLinksConfig = DynamicLinksConfig()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var isServiceOn by remember { mutableStateOf(prefs.isServiceEnabled) }
    var isOverlayRunning by remember { mutableStateOf(prefs.isOverlayEnabled) }

    // Real-time Permission States (initial false to avoid blocking main thread during Compose composition)
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isBatteryOptGranted by remember { mutableStateOf(false) }
    var isShizukuInstalled by remember { mutableStateOf(false) }
    var isShizukuRunning by remember { mutableStateOf(false) }
    var isShizukuGranted by remember { mutableStateOf(false) }

    fun refreshAllPermissions() {
        coroutineScope.launch(Dispatchers.IO) {
            val acc = isAccessibilityServiceEnabled(context)
            val ovr = isOverlayPermissionGranted(context)
            val bat = isBatteryOptimizationIgnored(context)
            val shizInstalled = try { ShizukuManager.isShizukuInstalled(context) } catch (_: Exception) { false }
            val shizRunning = try { ShizukuManager.isShizukuRunning() } catch (_: Exception) { false }
            val shizGranted = try { ShizukuManager.isPermissionGranted() } catch (_: Exception) { false }
            withContext(Dispatchers.Main) {
                isAccessibilityGranted = acc
                isOverlayGranted = ovr
                isBatteryOptGranted = bat
                isShizukuInstalled = shizInstalled
                isShizukuRunning = shizRunning
                isShizukuGranted = shizGranted
            }
        }
    }

    // Refresh permissions immediately when entering and returning to the app (onResume) without blocking UI thread
    DisposableEffect(lifecycleOwner) {
        refreshAllPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllPermissions()
                isServiceOn = prefs.isServiceEnabled
                isOverlayRunning = prefs.isOverlayEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Real-time listeners for Shizuku binder and permission authorization results
    DisposableEffect(Unit) {
        val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            isShizukuGranted = granted
            if (granted) {
                isShizukuRunning = true
            }
        }
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            coroutineScope.launch(Dispatchers.IO) {
                val shizInstalled = try { ShizukuManager.isShizukuInstalled(context) } catch (_: Exception) { false }
                val shizRunning = try { ShizukuManager.isShizukuRunning() } catch (_: Exception) { false }
                val shizGranted = try { ShizukuManager.isPermissionGranted() } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    isShizukuInstalled = shizInstalled
                    isShizukuRunning = shizRunning
                    isShizukuGranted = shizGranted
                }
            }
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            isShizukuRunning = false
            isShizukuGranted = false
        }

        ShizukuManager.addRequestPermissionResultListener(permListener)
        ShizukuManager.addBinderReceivedListener(binderReceivedListener)
        ShizukuManager.addBinderDeadListener(binderDeadListener)

        onDispose {
            ShizukuManager.removeRequestPermissionResultListener(permListener)
            ShizukuManager.removeBinderReceivedListener(binderReceivedListener)
            ShizukuManager.removeBinderDeadListener(binderDeadListener)
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                isServiceOn = prefs.isServiceEnabled
                isOverlayRunning = prefs.isOverlayEnabled
            }
        }
        val filter = IntentFilter(OverlayService.ACTION_FILTER_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {


        // Master Auto-Accepter Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, if (isServiceOn) AccentGreen else CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceOn) "Auto-Accepter: ACTIVE" else "Auto-Accepter: INACTIVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isServiceOn) AccentGreen else DangerRed
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isServiceOn) "Actively scanning and accepting orders" else "Tap toggle to activate auto-acceptance",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isServiceOn,
                        onCheckedChange = {
                            isServiceOn = it
                            prefs.isServiceEnabled = it
                            if (it && prefs.isOverlayEnabled && Settings.canDrawOverlays(context)) {
                                try {
                                    OverlayService.start(context)
                                    isOverlayRunning = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            try {
                                val intent = Intent(OverlayService.ACTION_FILTER_UPDATED).apply {
                                    setPackage(context.packageName)
                                }
                                context.sendBroadcast(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = DangerRed.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }



        // Floating HUD Overlay Control
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, if (isOverlayRunning) GlassBorderActive else GlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (isOverlayRunning) StatusOnlineGreen else TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Floating HUD Overlay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isOverlayRunning) "HUD widget is active on screen" else "HUD widget is hidden",
                                fontSize = 12.sp,
                                color = if (isOverlayRunning) StatusOnlineGreen else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                if (isOverlayRunning) {
                                    OverlayService.stop(context)
                                    prefs.isOverlayEnabled = false
                                    isOverlayRunning = false
                                    Toast.makeText(context, "Floating HUD Stopped", Toast.LENGTH_SHORT).show()
                                } else {
                                    OverlayService.start(context)
                                    prefs.isOverlayEnabled = true
                                    isOverlayRunning = true
                                    Toast.makeText(context, "Floating HUD Started", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) StatusOfflineRed else ActionGreen,
                            contentColor = if (isOverlayRunning) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isOverlayRunning) "Stop HUD" else "Launch HUD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Permissions Checklist
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("System Permissions & Status", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
        }

        item {
            PermissionStatusRow(
                title = "Accessibility Engine",
                subtitle = "Required to scan and click ride buttons",
                isGranted = isAccessibilityGranted,
                actionLabel = "Enable",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        item {
            PermissionStatusRow(
                title = "Floating HUD Overlay",
                subtitle = "Display draggable status badge over apps",
                isGranted = isOverlayGranted,
                actionLabel = "Grant",
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )
        }

        item {
            PermissionStatusRow(
                title = "Battery Optimization",
                subtitle = "Prevent background termination",
                isGranted = isBatteryOptGranted,
                actionLabel = "Disable",
                onAction = {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        item {
            val (subText, btnLabel) = when {
                isShizukuGranted -> "Active (Sub-millisecond touch)" to ""
                isShizukuRunning -> "Service running - Tap Allow to grant permission" to "Allow"
                isShizukuInstalled -> "Monkey / Shizuku app not running" to "Open App"
                else -> "Monkey / Shizuku not installed" to "Install"
            }

            PermissionStatusRow(
                title = "Monkey / Shizuku",
                subtitle = subText,
                isGranted = isShizukuGranted,
                actionLabel = btnLabel,
                onAction = {
                    when {
                        isShizukuRunning -> {
                            try {
                                Shizuku.requestPermission(ShizukuManager.SHIZUKU_REQUEST_CODE)
                                Toast.makeText(context, "Requesting Monkey / Shizuku permission...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        isShizukuInstalled -> {
                            val opened = ShizukuManager.openShizukuApp(context)
                            if (opened) {
                                Toast.makeText(context, "Monkey / Shizuku app not running. Please start service in app.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Monkey / Shizuku app not running.", Toast.LENGTH_LONG).show()
                            }
                        }
                        else -> {
                            val url = dynamicLinks.shizukuApk.ifBlank { "https://shizuku.rikka.app" }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                Toast.makeText(context, "Monkey / Shizuku not installed. Opening download page...", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        // ── Hide Developer Options from Ola (Ola Bypass) ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, GlassBorderGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarningOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = "Security",
                                tint = WarningOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Hide Developer from Ola",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Bypasses Ola Driver app developer mode check while keeping Shizuku active",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            context.startActivity(Intent(context, DevOptionsFixActivity::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Open Dev Options Fix",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginTab(
    context: Context,
    authManager: AuthManager,
    onLoginSuccess: () -> Unit,
    onNavigateToPlans: (() -> Unit)? = null
) {
    var isRegisterMode by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val deviceFp = remember { authManager.getDeviceFingerprint() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Inside Branding Logo
        Image(
            painter = painterResource(id = R.drawable.user_app_inside),
            contentDescription = "Ride Clicker Pro+",
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, GlassBorderGold, RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ride Clicker Pro+",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "v5.0.6 • Ultra Fast Clicker",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NavActiveYellow
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isRegisterMode) "CREATE DRIVER ACCOUNT" else "MEMBER LOGIN PORTAL",
            fontSize = 11.sp,
            color = AccentGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(18.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavActiveYellow.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isRegisterMode) "Create Account" else "Driver Sign In",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text("Device: $deviceFp", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMsg = null },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it; errorMsg = null },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = { Text("Email Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(errorMsg!!, color = DangerRed, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (isLoading) return@Button
                        if (email.isBlank() || password.isBlank()) {
                            errorMsg = "Please enter email and password."
                            return@Button
                        }
                        isLoading = true

                        if (isRegisterMode) {
                            if (name.isBlank() || mobile.isBlank()) {
                                errorMsg = "Please enter your name and mobile number."
                                isLoading = false
                                return@Button
                            }
                            authManager.register(name, email, mobile, password) { success, msg ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    isRegisterMode = false
                                } else {
                                    errorMsg = msg
                                }
                            }
                        } else {
                            authManager.loginAsync(email, password) { success, msg ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    errorMsg = msg
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                    } else {
                        Text(if (isRegisterMode) "REGISTER ACCOUNT" else "SIGN IN", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = { isRegisterMode = !isRegisterMode; errorMsg = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isRegisterMode) "Already have an account? Sign In" else "Don't have an account? Register Now",
                        color = AccentOrange,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlans?.invoke() },
                    colors = CardDefaults.cardColors(containerColor = GlassSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NavActiveYellow.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = "Plans", tint = NavActiveYellow, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Explore VIP Subscription Plans", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Text("View high-speed auto accept packages & benefits", color = TextSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Go", tint = NavActiveYellow, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTab(
    context: Context,
    authManager: AuthManager,
    user: UserData?,
    isSubscribed: Boolean,
    dynamicLinks: DynamicLinksConfig,
    onLogout: () -> Unit,
    onNavigateToPlans: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val deviceFp = remember { authManager.getDeviceFingerprint() }
    val email = authManager.getLoggedInUserId()
    val name = authManager.getLoggedInUserName()

    val userRepository = remember { UserRepository.getInstance(context) }
    val now = System.currentTimeMillis()
    val isBanned = user?.isBanned == true
    val isSubValid = !isBanned && ((user != null && user.isCountdownStarted && user.subscriptionExpiryTimestamp > now) || isSubscribed)

    val statusText = if (isBanned) {
        "BANNED"
    } else if (isSubValid) {
        "ACTIVE"
    } else if (user != null && user.assignedDays > 0 && !user.isCountdownStarted) {
        "NOT STARTED"
    } else {
        "EXPIRED"
    }

    val expiryText = if (isBanned) {
        "🚫 Account suspended by Admin. Contact support."
    } else if (user != null) {
        userRepository.getActivePlanDetails(user)
    } else {
        authManager.getPlanExpiryText()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isBanned) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, DangerRed)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚫", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Account Suspended", fontWeight = FontWeight.Bold, color = DangerRed, fontSize = 16.sp)
                        Text("Your account has been banned by Admin. All services are restricted.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Driver Identity Card
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(SupportCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        Text(email, color = TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DividerDark)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Device Lock Fingerprint", fontSize = 11.sp, color = TextSecondary)
                        Text(deviceFp, fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 15.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(deviceFp))
                            Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }

        // Subscription Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, if (isBanned) GlassBorderRed else if (isSubValid) GlassBorderActive else GlassBorderRed)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subscription Status", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    val badgeColor = when (statusText) {
                        "ACTIVE" -> AccentGreen
                        "NOT STARTED" -> AccentYellow
                        else -> DangerRed
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(expiryText, fontSize = 14.sp, color = if (isBanned) DangerRed else TextSecondary)

                Spacer(modifier = Modifier.height(16.dp))

                if (!isBanned) {
                    Button(
                        onClick = onNavigateToPlans,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ShoppingBag, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Renew / Upgrade Subscription", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Support Links
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Support & Helpdesk", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dynamicLinks.telegramSupport))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SupportCyan.copy(alpha = 0.5f))
                ) {
                    Text("💬 Telegram Official Support", color = SupportCyan)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dynamicLinks.whatsappSupport))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📱 WhatsApp Support", color = AccentGreen)
                }
            }
        }

        // Logout Button
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = DangerRed)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Logout Account", color = DangerRed, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.user_app_inside),
                contentDescription = "Ride Clicker Pro+",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ride Clicker Pro+ v5.0.6 • Ultra Fast Clicker",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PlansTab(
    planPricing: PlanPricingConfig,
    offerBanner: OfferBannerConfig,
    razorpayConfig: RazorpayConfig,
    dynamicLinks: DynamicLinksConfig,
    currentUser: UserData?,
    isLoggedIn: Boolean,
    onBuyPlan: (String, Double, Int, Int?) -> Unit,
    onContactAdminTelegram: () -> Unit
) {
    val plans = listOf(
        Triple("1-Day Pass", planPricing.price1Day, 1),
        Triple("3-Day Pass", planPricing.price3Days, 3),
        Triple("7-Day Pro Pass", planPricing.price7Days, 7),
        Triple("15-Day Ultra Pass", planPricing.price15Days, 15),
        Triple("30-Day Monthly Pass", planPricing.price30Days, 30),
        Triple("365-Day Annual Pass", planPricing.price365Days, 365)
    )

    val hasClaimedTrial = currentUser?.hasUsedTrial == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (offerBanner.showBanner) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentOrange)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(offerBanner.bannerText, color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Online Payments Disabled Fallback Banner
        if (!razorpayConfig.isEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GlassSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GlassBorderCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💬", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Online Gateway Paused", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                                Text("Manual Activation via Admin Telegram", fontSize = 12.sp, color = SupportCyan)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Online checkout is temporarily paused. Contact our Admin directly on Telegram to activate or renew any subscription plan instantly!",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onContactAdminTelegram,
                            colors = ButtonDefaults.buttonColors(containerColor = SupportCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💬", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONTACT ADMIN ON TELEGRAM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ── 2-HOUR TRIAL PROMOTIONAL OFFER (₹1) ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, if (hasClaimedTrial) GlassBorder else GlassBorderGold)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "2-Hour Express Trial",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                        }
                        Surface(
                            color = if (hasClaimedTrial) CardDark else AccentOrange.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (hasClaimedTrial) "ALREADY CLAIMED" else "🔥 1-TIME OFFER",
                                color = if (hasClaimedTrial) TextMuted else AccentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Test real high-speed ride acceptance for 2 full hours across all supported driver apps.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("₹1", fontWeight = FontWeight.ExtraBold, color = if (hasClaimedTrial) TextMuted else AccentGreen, fontSize = 24.sp)
                            Text("2 Hours Access", fontSize = 11.sp, color = TextMuted)
                        }

                        if (hasClaimedTrial) {
                            OutlinedButton(
                                onClick = onContactAdminTelegram,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SupportCyan.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SupportCyan)
                            ) {
                                Text("💬 Contact Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            if (razorpayConfig.isEnabled) {
                                Button(
                                    onClick = { onBuyPlan("2-Hour Express Trial", 1.0, 0, 2) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Claim for ₹1", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onContactAdminTelegram,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SupportCyan.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SupportCyan)
                                ) {
                                    Text("💬 Contact Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Instant Subscription Plans", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Text("Secure payment with Instant Activation or Contact Admin for Manual UPI", color = TextSecondary, fontSize = 12.sp)
        }

        items(plans, key = { it.first }) { plan ->
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plan.first, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Surface(
                            color = NavActiveYellow.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${plan.third} DAYS",
                                color = NavActiveYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${plan.third} Days Unlimited high-speed auto-acceptance across all driver apps",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("₹${plan.second.toInt()}", fontWeight = FontWeight.ExtraBold, color = AccentGreen, fontSize = 22.sp)
                            Text("Instant Activation", fontSize = 11.sp, color = TextMuted)
                        }

                        if (razorpayConfig.isEnabled) {
                            Button(
                                onClick = { onBuyPlan(plan.first, plan.second, plan.third, null) },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Pay ₹${plan.second.toInt()}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onContactAdminTelegram,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SupportCyan.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SupportCyan)
                            ) {
                                Text("💬 Contact Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Bottom Telegram Support Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorderCyan)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💬", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Need Custom Plan or Manual UPI?", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("Admin is available 24/7 on Telegram for instant activation", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onContactAdminTelegram,
                        colors = ButtonDefaults.buttonColors(containerColor = SupportCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CHAT WITH ADMIN ON TELEGRAM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private data class AppBrandStyle(
    val subtitle: String,
    val border: BorderStroke,
    val badgeColor: Color,
    val badgeText: String
)

@Composable
fun AppsTab(prefs: PreferencesHelper) {
    val platforms = SupportedApps.unifiedPlatforms
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Supported Driver Platforms", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Text("Enable or disable auto-acceptance for your driver platforms", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(platforms, key = { it.id }) { platform ->
            var enabled by remember { mutableStateOf(prefs.isAppEnabled(platform.primaryPackage)) }
            val (subtitle, brandBorder, brandBadgeColor, brandBadgeText) = when (platform.id) {
                "ola" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, OlaYellow.copy(alpha = 0.6f)), OlaYellow, platform.badgeText)
                "rapido" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, RapidoAmber.copy(alpha = 0.6f)), RapidoAmber, platform.badgeText)
                "uber" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, UberSilver.copy(alpha = 0.4f)), UberSilver, platform.badgeText)
                "namma_yatri" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, ActionGreen.copy(alpha = 0.5f)), ActionGreen, platform.badgeText)
                "red_taxi" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, Color(0xFFE53935).copy(alpha = 0.5f)), Color(0xFFE53935), platform.badgeText)
                "bharat_taxi" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, Color(0xFFFF9800).copy(alpha = 0.5f)), Color(0xFFFF9800), platform.badgeText)
                "porter" -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, Color(0xFF2979FF).copy(alpha = 0.5f)), Color(0xFF2979FF), platform.badgeText)
                else -> AppBrandStyle(platform.subtitle, BorderStroke(1.2.dp, Color(0xFFFC8019).copy(alpha = 0.5f)), Color(0xFFFC8019), platform.badgeText)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = brandBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(platform.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = brandBadgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = brandBadgeText,
                                    color = brandBadgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ActionGreen
                        ),
                        onCheckedChange = { isChecked ->
                            enabled = isChecked
                            prefs.setAppEnabled(platform.primaryPackage, isChecked)
                            // Synchronize all subpackages for this platform
                            platform.subPackages.forEach { subPkg ->
                                prefs.setAppEnabled(subPkg, isChecked)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FiltersTab(context: Context, prefs: PreferencesHelper) {
    var fareFilterOn by remember { mutableStateOf(prefs.isFareFilterEnabled) }
    var minFareText by remember { mutableStateOf(prefs.minFare.toString()) }
    var maxFareText by remember { mutableStateOf(prefs.maxFare.toString()) }

    var distFilterOn by remember { mutableStateOf(prefs.isDistanceFilterEnabled) }
    var maxPickupText by remember { mutableStateOf(prefs.maxPickupKm.toString()) }

    var favZoneOn by remember { mutableStateOf(prefs.isFavZoneEnabled) }
    var favZonesText by remember { mutableStateOf(prefs.favZones ?: "") }

    var nonFavZoneOn by remember { mutableStateOf(prefs.isNonFavZoneEnabled) }
    var nonFavZonesText by remember { mutableStateOf(prefs.nonFavZones ?: "") }

    var audioOn by remember { mutableStateOf(prefs.isAudioFeedbackEnabled) }
    var vibrationOn by remember { mutableStateOf(prefs.isVibrationEnabled) }

    fun notifyOverlayUpdated() {
        val intent = Intent(OverlayService.ACTION_FILTER_UPDATED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Fare Filters
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (fareFilterOn) GlassBorderActive else GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fare Range Filter", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Accept only within price range", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = fareFilterOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionGreen),
                            onCheckedChange = {
                                fareFilterOn = it
                                prefs.isFareFilterEnabled = it
                                notifyOverlayUpdated()
                            }
                        )
                    }

                    AnimatedVisibility(visible = fareFilterOn) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = minFareText,
                                    onValueChange = { minFareText = it },
                                    label = { Text("Min Fare (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = maxFareText,
                                    onValueChange = { maxFareText = it },
                                    label = { Text("Max Fare (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val min = minFareText.toIntOrNull() ?: 0
                                    val max = maxFareText.toIntOrNull() ?: 0
                                    prefs.minFare = min
                                    prefs.maxFare = max
                                    notifyOverlayUpdated()
                                    val toastMsg = if (max <= 0) "Fare filter saved: ₹$min+ (Unlimited)" else "Fare filter saved: ₹$min – ₹$max"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Fare Filter", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Pickup Distance Filter
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (distFilterOn) GlassBorderActive else GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pickup Distance Filter", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Accept rides with pickup within 0 km to Max km", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = distFilterOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionGreen),
                            onCheckedChange = {
                                distFilterOn = it
                                prefs.isDistanceFilterEnabled = it
                                notifyOverlayUpdated()
                            }
                        )
                    }

                    AnimatedVisibility(visible = distFilterOn) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            OutlinedTextField(
                                value = maxPickupText,
                                onValueChange = { maxPickupText = it },
                                label = { Text("Max Pickup Distance (km)") },
                                placeholder = { Text("e.g. 2.0") },
                                supportingText = {
                                    Text(
                                        text = if (maxPickupText.toFloatOrNull() != null)
                                            "Rides from 0 km to ${maxPickupText} km will be accepted"
                                        else
                                            "Enter maximum pickup distance (km)",
                                        fontSize = 11.sp,
                                        color = ActionGreen
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val max = maxPickupText.toFloatOrNull() ?: 50f
                                    prefs.maxPickupKm = max
                                    notifyOverlayUpdated()
                                    Toast.makeText(context, "Max pickup distance saved: ${max} km", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Pickup Distance Filter", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Favourite Zones (Whitelist)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, if (favZoneOn) GlassBorderGold else GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Favourite Zones (Whitelist)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Accept only rides in these preferred locations", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = favZoneOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NavActiveYellow),
                            onCheckedChange = {
                                favZoneOn = it
                                prefs.isFavZoneEnabled = it
                                notifyOverlayUpdated()
                            }
                        )
                    }

                    AnimatedVisibility(visible = favZoneOn) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = favZonesText,
                                onValueChange = { favZonesText = it },
                                label = { Text("Preferred Locations (comma separated)") },
                                placeholder = { Text("e.g. Airport, Station, Tech Park") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    prefs.isFavZoneEnabled = true
                                    prefs.favZones = favZonesText.ifEmpty { null }
                                    notifyOverlayUpdated()
                                    Toast.makeText(context, "Fav zones saved", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Whitelist", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Non-Favourite Zones (Blacklist)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, if (nonFavZoneOn) GlassBorderRed else GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Non-Favourite Zones (Blacklist)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("Automatically reject rides matching these locations", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = nonFavZoneOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StatusOfflineRed),
                            onCheckedChange = {
                                nonFavZoneOn = it
                                prefs.isNonFavZoneEnabled = it
                                notifyOverlayUpdated()
                            }
                        )
                    }

                    AnimatedVisibility(visible = nonFavZoneOn) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = nonFavZonesText,
                                onValueChange = { nonFavZonesText = it },
                                label = { Text("Blocked Locations (comma separated)") },
                                placeholder = { Text("e.g. Toll Plaza, Chakan, Bypass") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    prefs.isNonFavZoneEnabled = true
                                    prefs.nonFavZones = nonFavZonesText.ifEmpty { null }
                                    notifyOverlayUpdated()
                                    Toast.makeText(context, "Blacklist saved", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Blacklist", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Sound, Alerts & Fast Touch
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Alerts & Feedback", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice & Audio Announcement", color = TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = audioOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionGreen),
                            onCheckedChange = { audioOn = it; prefs.isAudioFeedbackEnabled = it }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibrate on Accept", color = TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = vibrationOn,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionGreen),
                            onCheckedChange = { vibrationOn = it; prefs.isVibrationEnabled = it }
                        )
                    }
                }
            }
        }
    }
}

private val OLA_ICON_PREFIX_REGEX = Regex("""(?i)^(?:category\s+icon|icon)\s*,\s*""")
private val DIST_ETA_PREFIX_REGEX = Regex("""(?i)^\d+(?:\.\d+)?\s*(?:km|kms|k\.m|m|meters)\s*[•·,]\s*\d+\s*(?:mins?|min|minutes?)\s*,\s*""")
private val DIST_PREFIX_REGEX = Regex("""(?i)^\d+(?:\.\d+)?\s*(?:km|kms|k\.m|m|meters)\s*,\s*""")
private val ETA_PREFIX_REGEX = Regex("""(?i)^\d+\s*(?:mins?|min|minutes?)\s*,\s*""")

private val LOCATION_PREFIXES = listOf(
    "pickup:", "drop:", "pickup at:", "drop at:", "to:", "from:", "pick up:",
    "drop off:", "destination:", "origin:", "pickup location:", "drop location:",
    "pickup", "drop", "pick up", "drop off", "pick-up:", "drop-off:", "pick-up", "drop-off",
    "pickup point:", "drop point:", "pickup point", "drop point",
    "पिकअप:", "ड्रॉप:", "पिकअप", "ड्रॉप"
)

private val LOCATION_TRAILING_JUNK = listOf(
    ", reject", ", accept", ", pass", ", decline", ", confirm", ", cancel", ", 1 ride", ", 1 order",
    " reject", " accept", " pass", " decline", " confirm", " cancel", " skip", " dismiss",
    ", अस्वीकार", ", स्वीकार", ", रिजेक्ट", ", एक्सेप्ट", ", रद्द"
)

private fun cleanDisplayLocation(loc: String?): String {
    if (loc.isNullOrBlank()) return ""
    var s = loc.trim()
    s = s.removePrefix("📍").removePrefix("🎯").removePrefix("🟢").removePrefix("🔴").trim()

    // Strip Ola composite icon metadata prefixes like "Category Icon, ", "Icon, "
    s = s.replace(OLA_ICON_PREFIX_REGEX, "").trim()

    // Strip distance & ETA prefixes embedded in accessibility nodes
    s = s.replace(DIST_ETA_PREFIX_REGEX, "").trim()
    s = s.replace(DIST_PREFIX_REGEX, "").trim()
    s = s.replace(ETA_PREFIX_REGEX, "").trim()

    for (p in LOCATION_PREFIXES) {
        if (s.startsWith(p, ignoreCase = true) && s.length > p.length + 2) {
            s = s.substring(p.length).trim().removePrefix(":").removePrefix("-").removePrefix("•").trim()
        }
    }
    var changed = true
    while (changed) {
        changed = false
        for (junk in LOCATION_TRAILING_JUNK) {
            if (s.endsWith(junk, ignoreCase = true)) {
                s = s.substring(0, s.length - junk.length).trim()
                changed = true
            }
        }
    }
    s = s.trim().trimEnd(',', '-', '•', ':').trim()

    val isLabelOnly = s.equals("Pickup Location", ignoreCase = true) ||
        s.equals("Drop Location", ignoreCase = true) ||
        s.equals("Pickup", ignoreCase = true) ||
        s.equals("Pick up", ignoreCase = true) ||
        s.equals("Pick-up", ignoreCase = true) ||
        s.equals("Pickup Point", ignoreCase = true) ||
        s.equals("Drop", ignoreCase = true) ||
        s.equals("Drop off", ignoreCase = true) ||
        s.equals("Dropoff", ignoreCase = true) ||
        s.equals("Drop-off", ignoreCase = true) ||
        s.equals("Drop Point", ignoreCase = true) ||
        s.equals("Destination", ignoreCase = true) ||
        s.equals("Origin", ignoreCase = true) ||
        s.equals("Location", ignoreCase = true) ||
        s.equals("Category Icon", ignoreCase = true) ||
        s.equals("Icon", ignoreCase = true) ||
        s.equals("पिकअप", ignoreCase = true) ||
        s.equals("ड्रॉप", ignoreCase = true) ||
        s.length < 2

    if (isLabelOnly) {
        return ""
    }
    return s
}

@Composable
fun HistoryTab(context: Context) {
    val dao = remember { RideHistoryDatabase.getDatabase(context).rideHistoryDao() }
    val rides by dao.getRecentRides(limit = 50).collectAsState(initial = emptyList())
    val earnings by dao.getTotalEarnings().collectAsState(initial = 0)
    val acceptedCount by dao.getAcceptedCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        dao.deleteDirtySystemUiEntries()
        dao.deleteCorruptedHistoryEntries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stats Cards Row: Accepted & Total Earnings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GlassBorderActive)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎯 Accepted", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$acceptedCount Rides", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GlassBorderGold)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💰 Total Earnings", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${earnings ?: 0}", color = EarningsGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (rides.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ride History", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                TextButton(
                    onClick = { scope.launch { dao.clearHistory() } },
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (rides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No rides accepted yet", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rides, key = { it.id }) { ride ->
                    val cleanPickupLoc = remember(ride.pickupLocation) {
                        cleanDisplayLocation(ride.pickupLocation)
                    }
                    val cleanDropLoc = remember(ride.pickupLocation, ride.dropLocation) {
                        val p = cleanDisplayLocation(ride.pickupLocation)
                        val d = cleanDisplayLocation(ride.dropLocation)
                        if (d.equals(p, ignoreCase = true)) "" else d
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ride.appName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                                Text(sdf.format(Date(ride.timestamp)), color = TextSecondary, fontSize = 11.sp)
                                val hasPickupDist = ride.distance != null && ride.distance > 0f
                                val hasTripDist = ride.tripDistance != null && ride.tripDistance > 0f
                                val hasPickupLoc = cleanPickupLoc.isNotBlank()
                                val hasDropLoc = cleanDropLoc.isNotBlank()

                                val pickupKmText = if (hasPickupDist) "${ride.distance} km" else ""
                                val pickupText = when {
                                    hasPickupDist && hasPickupLoc -> "📍 Pickup: $pickupKmText • $cleanPickupLoc"
                                    hasPickupDist -> "📍 Pickup: $pickupKmText"
                                    hasPickupLoc -> "📍 $cleanPickupLoc"
                                    else -> ""
                                }
                                if (pickupText.isNotEmpty()) {
                                    Text(pickupText, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                val tripKmText = if (hasTripDist) "${ride.tripDistance} km" else ""
                                val dropText = when {
                                    hasTripDist && hasDropLoc -> "🎯 Drop: $tripKmText • $cleanDropLoc"
                                    hasTripDist -> "🎯 Drop: $tripKmText"
                                    hasDropLoc -> "🎯 $cleanDropLoc"
                                    else -> ""
                                }
                                if (dropText.isNotEmpty()) {
                                    Text(dropText, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (ride.fare != null && ride.fare > 0) {
                                    Text("₹${ride.fare}", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 18.sp)
                                } else {
                                    Text("Accepted", fontWeight = FontWeight.SemiBold, color = AccentGreen, fontSize = 14.sp)
                                }
                                Text(ride.method, color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isGranted) GlassBorderActive else GlassBorderRed)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) AccentGreen else DangerRed)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (!isGranted) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            } else {
                Surface(
                    color = AccentGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "READY",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
