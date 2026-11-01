package com.rideauto.assistant

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import com.rideauto.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminPortalScreen(onCloseAdmin: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { PreferencesHelper(context) }
    var isAdminLoggedIn by remember { mutableStateOf(prefs.isAdminLoggedIn) }

    if (isAdminLoggedIn) {
        AdminDashboardView(
            onLogout = {
                prefs.isAdminLoggedIn = false
                prefs.loggedInAdminEmail = null
                prefs.loggedInAdminPassword = null
                isAdminLoggedIn = false
            }
        )
    } else {
        AdminLoginCard(
            onLoginSuccess = {
                prefs.isAdminLoggedIn = true
                isAdminLoggedIn = true
            }
        )
    }
}

@Composable
fun AdminLoginCard(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val userRepository = remember { UserRepository.getInstance(context) }
    val prefs = remember { PreferencesHelper(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.admin_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, AccentOrange, RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("⚙️ Admin Portal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Ride Clicker Pro+ Admin v5.0.6", fontSize = 11.sp, color = AccentOrange)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Authenticate with Admin credentials to manage app subscriptions & settings", fontSize = 12.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(26.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Admin Sign In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Spacer(modifier = Modifier.height(18.dp))

                Text("ADMIN EMAIL ADDRESS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = null },
                    placeholder = { Text("e.g. admin@gmail.com", fontSize = 13.sp, color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("ADMIN PASSWORD", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    placeholder = { Text("Enter admin password", fontSize = 13.sp, color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = AccentOrange
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMsg!!, color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (isLoggingIn) return@Button
                        if (username.isBlank() || password.isBlank()) {
                            errorMsg = "Please enter Admin Email & Password."
                            return@Button
                        }
                        isLoggingIn = true
                        userRepository.loginAdminUserAsync(username, password) { success, adminUser ->
                            isLoggingIn = false
                            if (success && adminUser != null) {
                                prefs.loggedInAdminEmail = adminUser.email
                                prefs.loggedInAdminPassword = password.trim()
                                Toast.makeText(context, "Admin Access Granted", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                errorMsg = "Invalid Admin Credentials or account is not granted Admin access."
                            }
                        }
                    },
                    enabled = !isLoggingIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        disabledContainerColor = AccentOrange.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("LOGIN TO ADMIN PANEL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

data class PlanConfirmPayload(
    val user: UserData,
    val days: Int,
    val planName: String,
    val amount: Double,
    val durationHours: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardView(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val userRepository = remember { UserRepository.getInstance(context) }
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { PreferencesHelper(context) }
    val loggedAdminEmail = prefs.loggedInAdminEmail ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
    val loggedAdminPassword = prefs.loggedInAdminPassword

    fun checkAndEnforceSession(): Boolean {
        val status = userRepository.verifyAdminSession(loggedAdminEmail, loggedAdminPassword)
        if (status != AdminSessionStatus.SUCCESS) {
            prefs.isAdminLoggedIn = false
            prefs.loggedInAdminEmail = null
            prefs.loggedInAdminPassword = null
            Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
            onLogout()
            return false
        }
        return true
    }

    fun findCurrentAdmin(list: List<UserData>): UserData? {
        return if (!loggedAdminEmail.isNullOrBlank()) {
            list.find { it.email.equals(loggedAdminEmail.trim(), ignoreCase = true) }
        } else {
            list.find { it.isAdmin || it.isSuperAdmin }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var usersList by remember { mutableStateOf(userRepository.getAllUsers()) }
    var currentAdminUser by remember { mutableStateOf<UserData?>(findCurrentAdmin(usersList)) }

    // Users Pagination State (Default 10 items limit)
    val userPageSize = 10
    var currentUserPage by remember { mutableStateOf(1) }
    var showAllUsers by remember { mutableStateOf(false) }

    // Sales Pagination State (Default 10 items limit)
    val salesPageSize = 10
    var currentSalesPage by remember { mutableStateOf(1) }
    var showAllSales by remember { mutableStateOf(false) }

    val isSuperAdmin = currentAdminUser?.isSuperAdmin == true
    val isAdminAlpha = (currentAdminUser?.isAdmin == true) && (currentAdminUser?.isAdminAlpha == true) && !isSuperAdmin
    val canViewSales = isSuperAdmin || isAdminAlpha
    val canEditSales = isSuperAdmin

    var selectedUserForCustom by remember { mutableStateOf<UserData?>(null) }
    var customUnitType by remember { mutableStateOf("HOURS") } // "HOURS" or "DAYS"
    var customDurationValueInput by remember { mutableStateOf("") }
    var customAmountInput by remember { mutableStateOf("") }

    var selectedUserForPassReset by remember { mutableStateOf<UserData?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    var isNewPassVisible by remember { mutableStateOf(false) }

    var selectedUserForDelete by remember { mutableStateOf<UserData?>(null) }
    var selectedUserForSubReset by remember { mutableStateOf<UserData?>(null) }
    var subResetReasonInput by remember { mutableStateOf("") }

    // Action confirmation targets (Ban / Unban & Reset FP)
    var selectedUserForBanTarget by remember { mutableStateOf<Pair<UserData, Boolean>?>(null) }
    var selectedUserForResetFpTarget by remember { mutableStateOf<UserData?>(null) }

    var confirmStackTarget by remember { mutableStateOf<PlanConfirmPayload?>(null) }

    var selectedAdminTab by remember { mutableStateOf(0) } // 0 = Users, 1 = Sales Analytics, 2 = Config
    var salesList by remember { mutableStateOf<List<SaleRecord>>(userRepository.getSalesRecords()) }
    var selectedSalesFilter by remember { mutableStateOf("All Time") }

    // Custom Date Range filter state
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var customStartDateStr by remember { mutableStateOf("") }
    var customEndDateStr by remember { mutableStateOf("") }
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }

    // Modify Sale Price Dialog State
    var selectedSaleForModify by remember { mutableStateOf<SaleRecord?>(null) }
    var modifyPriceInput by remember { mutableStateOf("") }
    var modifyReasonInput by remember { mutableStateOf("") }

    // SuperAdmin Role Assignment Dialog State
    var selectedUserForRoleTarget by remember { mutableStateOf<UserData?>(null) }

    // Delete Sale Log Confirmation State
    var selectedSaleForDelete by remember { mutableStateOf<SaleRecord?>(null) }

    // Superadmin Device Account Limit Dialog State
    var showDeviceLimitDialog by remember { mutableStateOf(false) }
    var currentDeviceLimit by remember { mutableStateOf(userRepository.getDeviceAccountLimit()) }
    var selectedLimitUpgrade by remember { mutableStateOf(currentDeviceLimit) }
    var customLimitInput by remember { mutableStateOf("") }
    var showPrivilegesSheet by remember { mutableStateOf(false) }

    // Dynamic App Config States (SuperAdmin)
    var razorpayConfigState by remember { mutableStateOf(RazorpayConfig()) }
    var planPricingState by remember { mutableStateOf(PlanPricingConfig()) }
    var dynamicLinksState by remember { mutableStateOf(DynamicLinksConfig()) }
    var offerBannerState by remember { mutableStateOf(OfferBannerConfig()) }

    var isActionProcessing by remember { mutableStateOf(false) }
    var deletedSalesList by remember { mutableStateOf<List<SaleRecord>>(emptyList()) }
    var showDeletedSalesVaultDialog by remember { mutableStateOf(false) }
    var selectedDeletedSaleForPermanentDelete by remember { mutableStateOf<SaleRecord?>(null) }

    DisposableEffect(Unit) {
        NotificationHelper.cancelAllPaymentNotifications(context)
        val u1 = userRepository.observeAllUsers { newList ->
            usersList = newList
            currentAdminUser = findCurrentAdmin(newList)
            val currentFound = newList.any { it.email.equals(loggedAdminEmail?.trim(), ignoreCase = true) }
            if (!currentFound && !loggedAdminEmail.isNullOrBlank()) {
                prefs.isAdminLoggedIn = false
                prefs.loggedInAdminEmail = null
                prefs.loggedInAdminPassword = null
                Toast.makeText(context, "🚫 Admin account deleted. Logged out.", Toast.LENGTH_LONG).show()
                onLogout()
            } else {
                checkAndEnforceSession()
            }
        }
        val u2 = userRepository.observeSalesRecords { newSales ->
            salesList = newSales
        }
        val u3 = userRepository.observeRazorpayConfig { rzp ->
            razorpayConfigState = rzp
        }
        val u4 = userRepository.observePlanPricing { config ->
            planPricingState = config
        }
        val u5 = userRepository.observeDynamicLinks { links ->
            dynamicLinksState = links
        }
        val u6 = userRepository.observeOfferBanner { banner ->
            offerBannerState = banner
        }
        val u7 = userRepository.observeDeletedSalesRecords { deletedSales ->
            deletedSalesList = deletedSales
        }

        onDispose {
            u1(); u2(); u3(); u4(); u5(); u6(); u7()
        }
    }

    val refreshUsers = {
        if (checkAndEnforceSession()) {
            userRepository.fetchAllUsersFromFirestore { cloudUsers ->
                usersList = cloudUsers
                salesList = userRepository.getSalesRecords()
                currentAdminUser = findCurrentAdmin(cloudUsers)
            }
        }
    }

    fun checkNetworkAndProceed(onConnected: () -> Unit) {
        if (!checkAndEnforceSession()) return
        if (!NetworkTimeHelper.isInternetAvailable(context)) {
            Toast.makeText(
                context,
                "🚫 No Internet Connection! Active internet connection is required to update data.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        onConnected()
    }

    fun handlePlanClick(
        user: UserData,
        days: Int,
        customPlanName: String? = null,
        customAmount: Double? = null,
        durationHours: Int? = null
    ) {
        checkNetworkAndProceed {
            val isHourly = durationHours != null && durationHours > 0
            val dynamicAmt = customAmount ?: when {
                isHourly -> (durationHours / 24.0) * planPricingState.price1Day
                days == 1 -> planPricingState.price1Day
                days == 3 -> planPricingState.price3Days
                days == 7 -> planPricingState.price7Days
                days == 15 -> planPricingState.price15Days
                days == 30 -> planPricingState.price30Days
                days == 365 -> planPricingState.price365Days
                else -> days * (planPricingState.price30Days / 30.0)
            }
            val defaultPlanName = if (isHourly) {
                "Custom Pass (${durationHours} Hour${if (durationHours > 1) "s" else ""})"
            } else {
                "${days} Days Plan"
            }
            val planNameText = customPlanName ?: defaultPlanName
            confirmStackTarget = PlanConfirmPayload(user, days, planNameText, dynamicAmt, durationHours)
        }
    }

    val filteredUsers = remember(usersList, searchQuery) {
        usersList.filter {
            it.email.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.mobileNumber.contains(searchQuery, ignoreCase = true) ||
            (it.boundDeviceId ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    val activeUserPageSize = if (showAllUsers) 100 else userPageSize
    val totalUserPages = if (filteredUsers.isEmpty()) 1 else ((filteredUsers.size + activeUserPageSize - 1) / activeUserPageSize)
    val safeCurrentUserPage = currentUserPage.coerceIn(1, totalUserPages)
    if (safeCurrentUserPage != currentUserPage) {
        currentUserPage = safeCurrentUserPage
    }
    val paginatedUsers = remember(filteredUsers, showAllUsers, safeCurrentUserPage) {
        val startIndex = (safeCurrentUserPage - 1) * activeUserPageSize
        filteredUsers.drop(startIndex).take(activeUserPageSize)
    }

    val now = remember { System.currentTimeMillis() }
    val totalUsers = usersList.size
    val activeSubscribers = remember(usersList) {
        val currentTime = System.currentTimeMillis()
        usersList.count { it.subscriptionExpiryTimestamp > currentTime || (it.assignedDays > 0 && !it.isCountdownStarted) }
    }
    val bannedCount = remember(usersList) { usersList.count { it.isBanned } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── TOP HEADER (TIER 1: APP BRANDING & SYSTEM ACTIONS) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Logo & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.admin_logo),
                    contentDescription = "Admin Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "Admin Portal",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "System & User Management • v5.0.6",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Right: Device Limit Pill, Refresh, Logout
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSuperAdmin) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable {
                                currentDeviceLimit = userRepository.getDeviceAccountLimit()
                                selectedLimitUpgrade = currentDeviceLimit
                                customLimitInput = currentDeviceLimit.toString()
                                showDeviceLimitDialog = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Filled.Smartphone,
                                contentDescription = "Device Limit",
                                tint = AccentOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$currentDeviceLimit/Dev",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = {
                        refreshUsers()
                        Toast.makeText(context, "Data refreshed live!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh Data",
                        tint = AccentOrange,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "LOGOUT",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── TOP HEADER (TIER 2: LOGGED-IN ADMIN PROFILE & PRIVILEGE BAR) ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPrivilegesSheet = true },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardDarkElevated)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = if (isSuperAdmin) AccentOrange else if (isAdminAlpha) AccentGreen else PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = loggedAdminEmail ?: "admin@rideauto.com",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = if (isSuperAdmin) "Super Admin • Full Privileges & Pricing Control"
                                   else if (isAdminAlpha) "Alpha Admin • Sales Analytics & Subscriptions"
                                   else "Standard Admin • Driver Account Operations",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = (if (isSuperAdmin) AccentOrange else if (isAdminAlpha) AccentGreen else PrimaryBlue).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, (if (isSuperAdmin) AccentOrange else if (isAdminAlpha) AccentGreen else PrimaryBlue).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (isSuperAdmin) "👑 SUPER ADMIN" else if (isAdminAlpha) "⭐ ALPHA ADMIN" else "🛡️ ADMIN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSuperAdmin) AccentOrange else if (isAdminAlpha) AccentGreen else PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // TAB SWITCHER ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedAdminTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedAdminTab == 0) AccentOrange else Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "👥 Users (${filteredUsers.size})",
                    color = if (selectedAdminTab == 0) Color.White else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            if (canViewSales) {
                Button(
                    onClick = { selectedAdminTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedAdminTab == 1) AccentOrange else Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "📊 Sales",
                        color = if (selectedAdminTab == 1) Color.White else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            if (isSuperAdmin) {
                Button(
                    onClick = { selectedAdminTab = 2 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedAdminTab == 2) AccentOrange else Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "⚡ Config",
                        color = if (selectedAdminTab == 2) Color.White else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedAdminTab == 2 && isSuperAdmin) {
            // ══════════════════════════════════════════════════════
            // TAB 2: APP DYNAMIC CONFIG MANAGER (SUPERADMIN ONLY)
            // ══════════════════════════════════════════════════════
            AdminDynamicConfigScreen(
                userRepository = userRepository,
                razorpayConfigState = razorpayConfigState,
                planPricingState = planPricingState,
                dynamicLinksState = dynamicLinksState,
                offerBannerState = offerBannerState
            )
        } else if (selectedAdminTab == 1 && canViewSales) {
            // ══════════════════════════════════════════════════════
            // TAB 1: SALES ANALYTICS
            // ══════════════════════════════════════════════════════
            AdminSalesAnalyticsScreen(
                salesList = salesList,
                selectedSalesFilter = selectedSalesFilter,
                onFilterChanged = { filter ->
                    if (filter != "Custom Date" && filter != "Custom Range") {
                        customStartMs = null
                        customEndMs = null
                        customStartDateStr = ""
                        customEndDateStr = ""
                    }
                    selectedSalesFilter = filter
                    currentSalesPage = 1
                },
                onCustomDateClick = { showCustomDateDialog = true },
                customStartMs = customStartMs,
                customEndMs = customEndMs,
                customStartDateStr = customStartDateStr,
                customEndDateStr = customEndDateStr,
                canEditSales = canEditSales,
                currentSalesPage = currentSalesPage,
                onPageChanged = { currentSalesPage = it },
                showAllSales = showAllSales,
                onToggleShowAll = { showAllSales = !showAllSales },
                salesPageSize = salesPageSize,
                onModifySale = { sale ->
                    selectedSaleForModify = sale
                    modifyPriceInput = if (sale.amountPaid > 0) {
                        if (sale.amountPaid % 1.0 == 0.0) sale.amountPaid.toInt().toString() else sale.amountPaid.toString()
                    } else {
                        "0"
                    }
                    modifyReasonInput = sale.modificationReason ?: ""
                },
                onDeleteSale = { selectedSaleForDelete = it },
                deletedSalesCount = deletedSalesList.size,
                onOpenVaultClick = { showDeletedSalesVaultDialog = true }
            )
        } else {
            // ══════════════════════════════════════════════════════
            // TAB 0: REGISTERED USERS & DRIVERS MANAGEMENT
            // ══════════════════════════════════════════════════════

            // Stats Overview Row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("Total Drivers", "$totalUsers", PrimaryBlue, Modifier.weight(1f))
                AdminStatCard("Subscribers", "$activeSubscribers", AccentGreen, Modifier.weight(1f))
                AdminStatCard("Banned", "$bannedCount", DangerRed, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    currentUserPage = 1
                },
                placeholder = { Text("Search by Email, Name, Mobile, Device ID...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AccentOrange) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("REGISTERED DRIVERS (${filteredUsers.size})", style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange))

            Spacer(modifier = Modifier.height(10.dp))

            // TOP PAGINATION BAR
            AdminPaginationBar(
                currentPage = safeCurrentUserPage,
                totalPages = totalUserPages,
                totalItems = filteredUsers.size,
                pageSize = activeUserPageSize,
                showAll = showAllUsers,
                onPageChange = { currentUserPage = it },
                onToggleShowAll = { showAllUsers = !showAllUsers },
                itemLabel = "Drivers"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Users Cards List
            paginatedUsers.forEach { user ->
                AdminUserCard(
                    user = user,
                    now = now,
                    isSuperAdmin = isSuperAdmin,
                    planPricingState = planPricingState,
                    onPlanClick = { days, customName, customAmt, hours ->
                        handlePlanClick(user, days, customName, customAmt, hours)
                    },
                    onCustomClick = {
                        selectedUserForCustom = user
                        customUnitType = "HOURS"
                        customDurationValueInput = ""
                        customAmountInput = ""
                    },
                    onResetFpClick = { selectedUserForResetFpTarget = user },
                    onBanClick = { selectedUserForBanTarget = Pair(user, !user.isBanned) },
                    onPassResetClick = {
                        selectedUserForPassReset = user
                        newPasswordInput = ""
                    },
                    onSubResetClick = {
                        selectedUserForSubReset = user
                        subResetReasonInput = ""
                    },
                    onRoleClick = { selectedUserForRoleTarget = user },
                    onDeleteClick = { selectedUserForDelete = user },
                    onCopyText = { text, label ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // BOTTOM PAGINATION BAR
            if (filteredUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                AdminPaginationBar(
                    currentPage = safeCurrentUserPage,
                    totalPages = totalUserPages,
                    totalItems = filteredUsers.size,
                    pageSize = activeUserPageSize,
                    showAll = showAllUsers,
                    onPageChange = { currentUserPage = it },
                    onToggleShowAll = { showAllUsers = !showAllUsers },
                    itemLabel = "Drivers"
                )
            }
        }
    }

    // ── DIALOGS ──

    // 1. Plan Grant Confirmation Dialog
    confirmStackTarget?.let { payload ->
        Dialog(onDismissRequest = { if (!isActionProcessing) confirmStackTarget = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Confirm Plan Grant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("User: ${payload.user.email}", fontSize = 13.sp, color = TextSecondary)
                    Text("Plan: ${payload.planName}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Text("Amount: ₹${payload.amount.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentGreen)

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { confirmStackTarget = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val target = confirmStackTarget ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.grantPlanToUser(
                                        user = target.user,
                                        days = target.days,
                                        planName = target.planName,
                                        amount = target.amount,
                                        durationHours = target.durationHours
                                    ) { success ->
                                        isActionProcessing = false
                                        confirmStackTarget = null
                                        if (success) {
                                            Toast.makeText(context, "Plan granted successfully!", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                            NotificationHelper.cancelAllPaymentNotifications(context)
                                        }
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GRANTING...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Custom Pass Dialog
    selectedUserForCustom?.let { user ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForCustom = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Assign Custom Pass", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(user.email, fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                customUnitType = "HOURS"
                                if (customDurationValueInput.isBlank()) customDurationValueInput = "1"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (customUnitType == "HOURS") AccentOrange else CardDark
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        ) {
                            Text("Hours", color = Color.White)
                        }
                        Button(
                            onClick = {
                                customUnitType = "DAYS"
                                if (customDurationValueInput.isBlank()) customDurationValueInput = "30"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (customUnitType == "DAYS") AccentOrange else CardDark
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        ) {
                            Text("Days", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customDurationValueInput,
                        onValueChange = { customDurationValueInput = it },
                        label = { Text("Duration ($customUnitType)") },
                        placeholder = { Text(if (customUnitType == "HOURS") "e.g. 1 (for 1 hour)" else "e.g. 30 (for 30 days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customAmountInput,
                        onValueChange = { customAmountInput = it },
                        label = { Text("Amount Paid (₹)") },
                        placeholder = { Text("e.g. 49 (leave empty for ₹0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForCustom = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val value = customDurationValueInput.toIntOrNull() ?: 0
                                    val amount = if (customAmountInput.isBlank()) 0.0 else (customAmountInput.toDoubleOrNull() ?: 0.0)
                                    if (value > 0) {
                                        val u = selectedUserForCustom ?: return@checkNetworkAndProceed
                                        isActionProcessing = true
                                        userRepository.assignCustomDuration(u, value, customUnitType, amount) {
                                            isActionProcessing = false
                                            selectedUserForCustom = null
                                            Toast.makeText(context, "Custom pass assigned!", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                            NotificationHelper.cancelAllPaymentNotifications(context)
                                        }
                                    } else {
                                        val unitName = if (customUnitType == "HOURS") "Hours" else "Days"
                                        Toast.makeText(context, "Please enter valid $unitName (greater than 0)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ASSIGNING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Assign Pass", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Reset Device Lock Confirmation Dialog
    selectedUserForResetFpTarget?.let { user ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForResetFpTarget = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Reset Device Lock?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("User: ${user.email}", fontSize = 13.sp, color = TextSecondary)
                    Text("Current FP: ${user.boundDeviceId ?: "None"}", fontSize = 12.sp, color = AccentGreen)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("This clears the device lock, allowing the user to bind a new hardware device on their next login.", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForResetFpTarget = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val u = selectedUserForResetFpTarget ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.resetDeviceLock(u) {
                                        isActionProcessing = false
                                        selectedUserForResetFpTarget = null
                                        Toast.makeText(context, "Device lock reset!", Toast.LENGTH_SHORT).show()
                                        refreshUsers()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RESETTING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Reset Lock", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Ban / Unban Confirmation Dialog
    selectedUserForBanTarget?.let { pair ->
        val user = pair.first
        val isBanAction = pair.second
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForBanTarget = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(if (isBanAction) "Ban User Account?" else "Unban User Account?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isBanAction) DangerRed else AccentGreen)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("User: ${user.email}", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (isBanAction) "Banning this user will immediately block them from using auto-accept and accessing the app."
                        else "Unbanning this user will restore their access to the app.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForBanTarget = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val currentPair = selectedUserForBanTarget ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.banUser(currentPair.first, currentPair.second) {
                                        isActionProcessing = false
                                        selectedUserForBanTarget = null
                                        Toast.makeText(context, if (currentPair.second) "User Banned!" else "User Unbanned!", Toast.LENGTH_SHORT).show()
                                        refreshUsers()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBanAction) DangerRed else AccentGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("UPDATING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text(if (isBanAction) "Ban" else "Unban", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Change Password Dialog
    selectedUserForPassReset?.let { user ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForPassReset = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Change User Password", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("User: ${user.email}", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (isNewPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isNewPassVisible = !isNewPassVisible }) {
                                Icon(
                                    imageVector = if (isNewPassVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    tint = AccentOrange
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForPassReset = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (newPasswordInput.length >= 6) {
                                    checkNetworkAndProceed {
                                        val u = selectedUserForPassReset ?: return@checkNetworkAndProceed
                                        isActionProcessing = true
                                        userRepository.changeUserPassword(u, newPasswordInput) {
                                            isActionProcessing = false
                                            selectedUserForPassReset = null
                                            Toast.makeText(context, "Password updated!", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Update", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. Delete User Confirmation Dialog
    selectedUserForDelete?.let { user ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Delete User Account?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("User: ${user.email}", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("This action will permanently delete this driver account and all associated subscription records.", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForDelete = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val u = selectedUserForDelete ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.deleteUserAccount(u) {
                                        isActionProcessing = false
                                        selectedUserForDelete = null
                                        Toast.makeText(context, "User deleted!", Toast.LENGTH_SHORT).show()
                                        refreshUsers()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DELETING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. Reset Subscription (0 Days) Dialog
    selectedUserForSubReset?.let { user ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedUserForSubReset = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Reset Subscription (0 Days)?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("User: ${user.email}", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("This will immediately cancel all remaining subscription days, set days to 0, and expire access.", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = subResetReasonInput,
                        onValueChange = { subResetReasonInput = it },
                        label = { Text("Reset Reason (Required)") },
                        placeholder = { Text("e.g. User requested refund / Policy violation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedUserForSubReset = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (subResetReasonInput.trim().isBlank()) {
                                    Toast.makeText(context, "Please specify a reset reason", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                checkNetworkAndProceed {
                                    val u = selectedUserForSubReset ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.resetUserSubscription(
                                        user = u,
                                        reason = subResetReasonInput.trim(),
                                        performingAdminEmail = currentAdminUser?.email
                                    ) { success ->
                                        isActionProcessing = false
                                        selectedUserForSubReset = null
                                        if (success) {
                                            Toast.makeText(context, "Subscription reset and logged in sales!", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                            salesList = userRepository.getSalesRecords()
                                        } else {
                                            Toast.makeText(context, "Failed to reset subscription", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RESETTING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("Reset to 0D", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. SuperAdmin Role Assignment Dialog
    selectedUserForRoleTarget?.let { user ->
        Dialog(onDismissRequest = { selectedUserForRoleTarget = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardDarkElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("🛡️ Assign User Role & Privileges", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Target: ${user.email}", fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(14.dp))

                    val roleOptions = listOf(
                        Triple(
                            "SUPER_ADMIN",
                            "👑 Super Admin (Full Control)",
                            "Unrestricted access: Manage admin roles, edit live pricing, delete sales logs, configure device hardware limits, dynamic links, and banners."
                        ),
                        Triple(
                            "ALPHA_ADMIN",
                            "⭐ Alpha Admin (Sales & Plans)",
                            "Elevated privileges: Grant/reset any subscription pass, view full sales revenue & analytics, unban drivers, reset passwords & device locks."
                        ),
                        Triple(
                            "ADMIN",
                            "🛡️ Standard Admin (User Ops)",
                            "Operational access: View driver list, grant standard passes, reset passwords and hardware device fingerprint bindings."
                        ),
                        Triple(
                            "DRIVER",
                            "🚗 Driver / User (Standard)",
                            "Regular driver account: Auto-accept rides based on active subscription pass."
                        )
                    )

                    roleOptions.forEach { (roleKey, roleTitle, roleDesc) ->
                        val isSelected = when (roleKey) {
                            "SUPER_ADMIN" -> user.isSuperAdmin
                            "ALPHA_ADMIN" -> user.isAdminAlpha && !user.isSuperAdmin
                            "ADMIN" -> user.isAdmin && !user.isAdminAlpha && !user.isSuperAdmin
                            else -> !user.isAdmin && !user.isSuperAdmin
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isActionProcessing) {
                                    val u = selectedUserForRoleTarget ?: return@clickable
                                    checkNetworkAndProceed {
                                        isActionProcessing = true
                                        userRepository.updateUserRole(u, roleKey) {
                                            isActionProcessing = false
                                            selectedUserForRoleTarget = null
                                            Toast.makeText(context, "Role updated to $roleTitle", Toast.LENGTH_SHORT).show()
                                            refreshUsers()
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CardDark else DarkCard
                            ),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) AccentOrange else DividerDark.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = roleTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentOrange else TextPrimary
                                    )
                                    if (isSelected) {
                                        Surface(
                                            color = AccentOrange.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentOrange,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = roleDesc,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { selectedUserForRoleTarget = null },
                        enabled = !isActionProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    }

    // 9. SuperAdmin Device Account Limit Dialog
    if (showDeviceLimitDialog) {
        Dialog(onDismissRequest = { if (!isActionProcessing) showDeviceLimitDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardDarkElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Smartphone,
                            contentDescription = "Device Limit",
                            tint = AccentOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Device Account Limits",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Set the maximum number of driver accounts that can be created or bound to a single hardware phone.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = CardDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Active Limit:", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentDeviceLimit >= 999) "Unlimited" else "$currentDeviceLimit Accounts / Device",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("SELECT LIMIT PRESET:", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 1 Presets: 1, 2, 3, 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1 to "1 Acc",
                            2 to "2 (Default)",
                            3 to "3 Acc",
                            4 to "4 Acc"
                        ).forEach { (limitVal, label) ->
                            val isSel = selectedLimitUpgrade == limitVal
                            Button(
                                onClick = {
                                    if (!isActionProcessing) {
                                        selectedLimitUpgrade = limitVal
                                        customLimitInput = limitVal.toString()
                                    }
                                },
                                enabled = !isActionProcessing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) AccentOrange else CardDark
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2 Presets: 5, 10, Unlimited (999)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            5 to "5 Accounts",
                            10 to "10 Accounts",
                            999 to "♾️ Unlimited"
                        ).forEach { (limitVal, label) ->
                            val isSel = selectedLimitUpgrade == limitVal
                            Button(
                                onClick = {
                                    if (!isActionProcessing) {
                                        selectedLimitUpgrade = limitVal
                                        customLimitInput = limitVal.toString()
                                    }
                                },
                                enabled = !isActionProcessing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) AccentOrange else CardDark
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("OR ENTER CUSTOM LIMIT NUMBER:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = customLimitInput,
                        onValueChange = { input ->
                            if (!isActionProcessing) {
                                customLimitInput = input
                                val num = input.toIntOrNull()
                                if (num != null && num > 0) {
                                    selectedLimitUpgrade = num
                                }
                            }
                        },
                        placeholder = { Text("e.g. 5 or 50", fontSize = 12.sp, color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isActionProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = DividerDark
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showDeviceLimitDialog = false },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    isActionProcessing = true
                                    val targetLim = customLimitInput.toIntOrNull() ?: selectedLimitUpgrade
                                    userRepository.updateDeviceAccountLimit(targetLim) {
                                        isActionProcessing = false
                                        showDeviceLimitDialog = false
                                        currentDeviceLimit = targetLim
                                        Toast.makeText(context, "Device limit updated to $targetLim accounts!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("SAVE LIMIT", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 9B. Modify Sale Record Price Dialog
    selectedSaleForModify?.let { sale ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedSaleForModify = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit Price",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Edit Sale Price",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Driver: ${sale.userEmail}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Plan: ${sale.planName}", fontSize = 12.sp, color = TextSecondary)
                    Text("Txn ID: ${sale.transactionId.ifBlank { sale.docId }}", fontSize = 11.sp, color = TextMuted)
                    Text(
                        "Current Price: ₹${if (sale.amountPaid % 1.0 == 0.0) sale.amountPaid.toInt() else sale.amountPaid}",
                        fontSize = 12.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("New Price (₹)", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = modifyPriceInput,
                        onValueChange = { modifyPriceInput = it.filter { char -> char.isDigit() || char == '.' } },
                        placeholder = { Text("e.g. 199", fontSize = 12.sp, color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isActionProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = DividerDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Modification Reason", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = modifyReasonInput,
                        onValueChange = { modifyReasonInput = it },
                        placeholder = { Text("e.g. Discount adjustment, UPI correction", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        enabled = !isActionProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = DividerDark
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedSaleForModify = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val target = selectedSaleForModify ?: return@checkNetworkAndProceed
                                    val newPrice = modifyPriceInput.toDoubleOrNull()
                                    if (newPrice == null || newPrice < 0.0) {
                                        Toast.makeText(context, "Please enter a valid price", Toast.LENGTH_SHORT).show()
                                        return@checkNetworkAndProceed
                                    }
                                    isActionProcessing = true
                                    val reason = modifyReasonInput.trim().ifBlank { "Price adjusted by Admin" }
                                    val adminEmail = currentAdminUser?.email ?: "SuperAdmin"
                                    userRepository.updateSaleRecordAmount(target, newPrice, reason, adminEmail) { success ->
                                        isActionProcessing = false
                                        if (success) {
                                            salesList = userRepository.getSalesRecords()
                                            selectedSaleForModify = null
                                            Toast.makeText(
                                                context,
                                                "Sale price updated to ₹${if (newPrice % 1.0 == 0.0) newPrice.toInt() else newPrice} successfully!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(context, "Failed to update sale price.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("SAVE PRICE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // 10. Delete Sale Record Confirmation Dialog (Soft Delete to Trash Vault)
    selectedSaleForDelete?.let { sale ->
        Dialog(onDismissRequest = { if (!isActionProcessing) selectedSaleForDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = "Delete Sale Record",
                            tint = DangerRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Move to Trash Vault?",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Driver: ${sale.userEmail}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Plan: ${sale.planName} (₹${sale.amountPaid.toInt()})", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                    Text("Txn ID: ${sale.transactionId}", fontSize = 11.sp, color = TextMuted)
                    Text("Source: ${sale.activationSource}", fontSize = 11.sp, color = PrimaryBlue)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "This moves this transaction to the Super Admin Trash Vault. It can be restored or purged permanently later.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedSaleForDelete = null },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                checkNetworkAndProceed {
                                    val s = selectedSaleForDelete ?: return@checkNetworkAndProceed
                                    isActionProcessing = true
                                    userRepository.softDeleteSaleRecordAsync(s, currentAdminUser?.email) { success, msg ->
                                        isActionProcessing = false
                                        selectedSaleForDelete = null
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isActionProcessing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isActionProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DELETING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Text("MOVE TO TRASH", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // 10B. Super Admin Deleted Sales Vault (Trash & Restore) Dialog
    if (showDeletedSalesVaultDialog && isSuperAdmin) {
        Dialog(onDismissRequest = { showDeletedSalesVaultDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.5.dp, DangerRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🗑️ Trash Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DangerRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = DangerRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "${deletedSalesList.size} DELETED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DangerRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(onClick = { showDeletedSalesVaultDialog = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }
                    Text(
                        "Super Admin exclusive vault. Restore logs back to active revenue or purge them forever.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (deletedSalesList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No deleted sales logs in trash.", fontSize = 13.sp, color = TextMuted)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            deletedSalesList.forEach { sale ->
                                val isRejection = sale.activationSource == "REJECTION_LOG"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardDarkElevated),
                                    border = BorderStroke(1.dp, if (isRejection) DangerRed.copy(alpha = 0.5f) else AccentGreen.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isRejection) DangerRed.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        if (isRejection) "REJECTION LOG" else "ACTIVATION LOG",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isRejection) DangerRed else AccentGreen,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    sale.userEmail,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (!isRejection) {
                                                Text("₹${sale.amountPaid.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        if (isRejection) {
                                            Text("Reason: ${sale.resetReason ?: "Payment Rejected"}", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.SemiBold)
                                            if (sale.planName.isNotBlank()) {
                                                Text("Plan: ${sale.planName}", fontSize = 10.sp, color = TextSecondary)
                                            }
                                        } else {
                                            Text("Plan: ${sale.planName} (${sale.daysAssigned} Days)", fontSize = 11.sp, color = TextSecondary)
                                        }
                                        if (!sale.utr.isNullOrBlank()) {
                                            Text("UTR: ${sale.utr}", fontSize = 10.sp, color = TextMuted)
                                        }
                                        val detailedFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                        Text("Original Date: ${detailedFmt.format(Date(sale.activationTimestamp))}", fontSize = 10.sp, color = TextMuted)
                                        if (sale.deletedTimestamp > 0L) {
                                            Text("Deleted Date: ${detailedFmt.format(Date(sale.deletedTimestamp))}", fontSize = 10.sp, color = DangerRed)
                                        }
                                        if (!sale.deletedByAdmin.isNullOrBlank()) {
                                            Text("Deleted By: ${sale.deletedByAdmin}", fontSize = 10.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    checkNetworkAndProceed {
                                                        isActionProcessing = true
                                                        userRepository.restoreSaleRecordAsync(
                                                            sale = sale,
                                                            performingAdminEmail = currentAdminUser?.email
                                                        ) { success, msg ->
                                                            isActionProcessing = false
                                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isActionProcessing,
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Restore", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { selectedDeletedSaleForPermanentDelete = sale },
                                                enabled = !isActionProcessing,
                                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Purge Forever", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showDeletedSalesVaultDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE VAULT", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // 10C. Permanent Purge Confirmation Dialog (Super Admin Only)
    if (selectedDeletedSaleForPermanentDelete != null && isSuperAdmin) {
        val delSale = selectedDeletedSaleForPermanentDelete!!
        AlertDialog(
            onDismissRequest = { if (!isActionProcessing) selectedDeletedSaleForPermanentDelete = null },
            title = { Text("Permanently Erase Log?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DangerRed) },
            text = {
                Text(
                    "Are you sure you want to permanently erase log ${delSale.transactionId} for ${delSale.userEmail}?\n\nThis will completely remove it from the database and CANNOT be recovered.",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        checkNetworkAndProceed {
                            isActionProcessing = true
                            userRepository.permanentlyDeleteSaleRecordAsync(delSale) { success, msg ->
                                isActionProcessing = false
                                if (success) {
                                    deletedSalesList = deletedSalesList.filterNot { it.transactionId == delSale.transactionId || it.docId == delSale.docId }
                                    Toast.makeText(context, "Log permanently erased forever.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                                selectedDeletedSaleForPermanentDelete = null
                            }
                        }
                    },
                    enabled = !isActionProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    if (isActionProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PURGING...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Text("PURGE FOREVER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedDeletedSaleForPermanentDelete = null },
                    enabled = !isActionProcessing
                ) {
                    Text("Cancel", color = if (isActionProcessing) TextMuted.copy(alpha = 0.5f) else TextMuted)
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary
        )
    }

    // 11. Admin Privilege Breakdown Info Dialog
    if (showPrivilegesSheet) {
        Dialog(onDismissRequest = { showPrivilegesSheet = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardDarkElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = "Privileges",
                            tint = AccentOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Admin Privilege Levels",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Super Admin
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("👑 Super Admin (Full Control)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Assign & revoke Admin / Alpha roles\n• Set Live Pricing & Dynamic Links\n• Delete sales records & payment logs\n• Change max accounts per device limit\n• Grant or reset any driver subscription\n• Unban or ban driver accounts", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
                        }
                    }

                    // Alpha Admin
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⭐ Alpha Admin (Sales & Subscriptions)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Grant standard & custom passes\n• View full sales revenue & analytics\n• Reset driver passwords & device locks\n• Ban or unban driver accounts", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
                        }
                    }

                    // Standard Admin
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🛡️ Standard Admin (User Operations)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• View registered drivers\n• Grant standard subscription passes\n• Reset device locks & passwords", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showPrivilegesSheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 12. CUSTOM DATE RANGE PICKER DIALOG (1:1 V8.0.8 PARITY)
    if (showCustomDateDialog) {
        val dateRangePickerState = rememberDateRangePickerState()

        Dialog(
            onDismissRequest = { showCustomDateDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, AccentOrange.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                color = SurfaceDark,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pinned Top Header with Selection Feedback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCard)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📅 Select Sales Date Range", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            val sUtc = dateRangePickerState.selectedStartDateMillis
                            val eUtc = dateRangePickerState.selectedEndDateMillis
                            val headlineText = when {
                                sUtc != null && eUtc != null && sUtc != eUtc -> {
                                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    "${sdf.format(Date(sUtc))} → ${sdf.format(Date(eUtc))}"
                                }
                                sUtc != null -> {
                                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    "${sdf.format(Date(sUtc))} (1 Day) — Tap OK or pick End Date"
                                }
                                else -> "Tap once for 1 Day, twice for Range"
                            }
                            Text(headlineText, fontSize = 11.sp, color = if (sUtc != null) AccentGreen else TextSecondary, fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { showCustomDateDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    // Middle Calendar Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(
                                containerColor = SurfaceDark,
                                titleContentColor = AccentOrange,
                                headlineContentColor = TextPrimary,
                                weekdayContentColor = TextSecondary,
                                subheadContentColor = TextSecondary,
                                yearContentColor = TextPrimary,
                                currentYearContentColor = AccentOrange,
                                selectedYearContentColor = Color.White,
                                selectedYearContainerColor = AccentOrange,
                                dayContentColor = TextPrimary,
                                selectedDayContentColor = Color.White,
                                selectedDayContainerColor = AccentOrange,
                                todayContentColor = AccentOrange,
                                todayDateBorderColor = AccentOrange,
                                dayInSelectionRangeContentColor = TextPrimary,
                                dayInSelectionRangeContainerColor = AccentOrange.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // Pinned Bottom Action Bar (OK / Cancel)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkCard,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showCustomDateDialog = false },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCEL", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            val hasSelection = dateRangePickerState.selectedStartDateMillis != null
                            Button(
                                onClick = {
                                    val startUtc = dateRangePickerState.selectedStartDateMillis
                                    if (startUtc != null) {
                                        val endUtc: Long = dateRangePickerState.selectedEndDateMillis ?: startUtc
                                        val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startUtc }
                                        val localStart = Calendar.getInstance().apply {
                                            set(startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis

                                        val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = endUtc }
                                        val localEnd = Calendar.getInstance().apply {
                                            set(endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                                            set(Calendar.MILLISECOND, 999)
                                        }.timeInMillis

                                        val actualStart = minOf(localStart, localEnd)
                                        val actualEnd = maxOf(localStart, localEnd)
                                        customStartMs = actualStart
                                        customEndMs = actualEnd

                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                        customStartDateStr = sdf.format(Date(actualStart))
                                        customEndDateStr = sdf.format(Date(actualEnd))
                                        selectedSalesFilter = "Custom Date"
                                        currentSalesPage = 1
                                    }
                                    showCustomDateDialog = false
                                },
                                enabled = hasSelection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentOrange,
                                    disabledContainerColor = SurfaceDark.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.4f)
                            ) {
                                Text("APPLY / OK", color = if (hasSelection) Color.White else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── USER CARD COMPOSABLE ──

@Composable
fun AdminUserCard(
    user: UserData,
    now: Long,
    isSuperAdmin: Boolean,
    planPricingState: PlanPricingConfig,
    onPlanClick: (days: Int, customName: String?, customAmt: Double?, hours: Int?) -> Unit,
    onCustomClick: () -> Unit,
    onResetFpClick: () -> Unit,
    onBanClick: () -> Unit,
    onPassResetClick: () -> Unit,
    onSubResetClick: () -> Unit,
    onRoleClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyText: (String, String) -> Unit
) {
    val isSubActive = user.subscriptionExpiryTimestamp > now
    val expiryText = if (user.subscriptionExpiryTimestamp > 0) {
        val diff = user.subscriptionExpiryTimestamp - now
        if (diff > 0) {
            val d = diff / (24 * 3600 * 1000L)
            val h = (diff % (24 * 3600 * 1000L)) / (3600 * 1000L)
            "Active ($d days, $h hrs left)"
        } else {
            "Expired"
        }
    } else {
        "No Subscription"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (user.isBanned) DangerRed else if (isSubActive) AccentGreen.copy(alpha = 0.5f) else CardDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: User Name & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name.ifBlank { user.email.substringBefore("@") },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        if (user.isSuperAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = AccentOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("SUPERADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        } else if (user.isAdminAlpha) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = AccentGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("ALPHA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        } else if (user.isAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = PrimaryBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(user.email, fontSize = 12.sp, color = TextSecondary)
                    if (user.mobileNumber.isNotBlank()) {
                        Text("Mobile: ${user.mobileNumber}", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Surface(
                    color = (if (user.isBanned) DangerRed else if (isSubActive) AccentGreen else DangerRed).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (user.isBanned) "BANNED" else if (isSubActive) "ACTIVE" else "INACTIVE",
                        color = if (user.isBanned) DangerRed else if (isSubActive) AccentGreen else DangerRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerDark.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Info Details
            Text("Subscription: $expiryText", fontSize = 12.sp, color = if (isSubActive) AccentGreen else TextSecondary, fontWeight = FontWeight.SemiBold)

            // Device Lock FP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device FP: ${user.boundDeviceId ?: "Not Bound"}",
                    fontSize = 11.sp,
                    color = if (user.boundDeviceId != null) AccentGreen else TextMuted
                )
                if (user.boundDeviceId != null) {
                    Row {
                        TextButton(
                            onClick = { onCopyText(user.boundDeviceId!!, "Device ID") },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Copy", fontSize = 11.sp, color = PrimaryBlue)
                        }
                        TextButton(
                            onClick = onResetFpClick,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Reset Lock", fontSize = 11.sp, color = AccentOrange)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Grant Buttons Row
            Text("QUICK GRANT PLANS", style = MaterialTheme.typography.labelSmall, color = AccentOrange)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1, 3, 7, 15, 30, 365).forEach { days ->
                    OutlinedButton(
                        onClick = { onPlanClick(days, null, null, null) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text("${days}D", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCustomClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text("Custom Pass", fontSize = 10.sp, color = Color.White)
                }

                Button(
                    onClick = onPassResetClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text("Password", fontSize = 10.sp, color = Color.White)
                }

                Button(
                    onClick = onBanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = if (user.isBanned) AccentGreen.copy(alpha = 0.3f) else DangerRed.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(if (user.isBanned) "Unban" else "Ban", fontSize = 10.sp, color = if (user.isBanned) AccentGreen else DangerRed, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSubResetClick,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1.2f).height(30.dp)
                ) {
                    Text("Reset Sub", fontSize = 10.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                }

                if (isSuperAdmin) {
                    OutlinedButton(
                        onClick = onRoleClick,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.weight(0.9f).height(30.dp)
                    ) {
                        Text("Role", fontSize = 10.sp, color = AccentOrange)
                    }
                    OutlinedButton(
                        onClick = onDeleteClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.weight(0.9f).height(30.dp)
                    ) {
                        Text("Delete", fontSize = 10.sp, color = DangerRed)
                    }
                }
            }
        }
    }
}

// ── DYNAMIC CONFIG SCREEN ──

@Composable
fun AdminDynamicConfigScreen(
    userRepository: UserRepository,
    razorpayConfigState: RazorpayConfig,
    planPricingState: PlanPricingConfig,
    dynamicLinksState: DynamicLinksConfig,
    offerBannerState: OfferBannerConfig
) {
    val context = LocalContext.current

    // Razorpay Inputs
    var keyIdInput by remember(razorpayConfigState) { mutableStateOf(razorpayConfigState.keyId) }
    var keySecretInput by remember(razorpayConfigState) { mutableStateOf(razorpayConfigState.keySecret) }
    var merchantNameInput by remember(razorpayConfigState) { mutableStateOf(razorpayConfigState.merchantName) }
    var isRazorpayEnabled by remember(razorpayConfigState) { mutableStateOf(razorpayConfigState.isEnabled) }
    var isSecretVisible by remember { mutableStateOf(false) }

    // Pricing Inputs
    var p1Day by remember(planPricingState) { mutableStateOf(planPricingState.price1Day.toInt().toString()) }
    var p3Days by remember(planPricingState) { mutableStateOf(planPricingState.price3Days.toInt().toString()) }
    var p7Days by remember(planPricingState) { mutableStateOf(planPricingState.price7Days.toInt().toString()) }
    var p15Days by remember(planPricingState) { mutableStateOf(planPricingState.price15Days.toInt().toString()) }
    var p30Days by remember(planPricingState) { mutableStateOf(planPricingState.price30Days.toInt().toString()) }
    var p365Days by remember(planPricingState) { mutableStateOf(planPricingState.price365Days.toInt().toString()) }

    // Links Inputs
    var telegramInput by remember(dynamicLinksState) { mutableStateOf(dynamicLinksState.telegramSupport) }
    var whatsappInput by remember(dynamicLinksState) { mutableStateOf(dynamicLinksState.whatsappSupport) }
    var youtubeInput by remember(dynamicLinksState) { mutableStateOf(dynamicLinksState.youtubeChannel) }
    var tutorialInput by remember(dynamicLinksState) { mutableStateOf(dynamicLinksState.tutorialVideo) }

    // Offer Banner Inputs
    var bannerTextInput by remember(offerBannerState) { mutableStateOf(offerBannerState.bannerText) }
    var showBannerToggle by remember(offerBannerState) { mutableStateOf(offerBannerState.showBanner) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── RAZORPAY GATEWAY CONFIG ──
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Payment, contentDescription = null, tint = AccentOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Razorpay Gateway Config", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Razorpay Instant Checkout", fontSize = 13.sp, color = TextPrimary)
                    Switch(
                        checked = isRazorpayEnabled,
                        onCheckedChange = { isRazorpayEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = keyIdInput,
                    onValueChange = { keyIdInput = it },
                    label = { Text("Razorpay Key ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = keySecretInput,
                    onValueChange = { keySecretInput = it },
                    label = { Text("Razorpay Secret Key") },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = AccentOrange
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = merchantNameInput,
                    onValueChange = { merchantNameInput = it },
                    label = { Text("Merchant Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val config = RazorpayConfig(
                            keyId = keyIdInput.trim(),
                            keySecret = keySecretInput.trim(),
                            isEnabled = isRazorpayEnabled,
                            merchantName = merchantNameInput.trim()
                        )
                        userRepository.updateRazorpayConfig(config) { success ->
                            Toast.makeText(context, if (success) "Razorpay Config Saved Live!" else "Failed to save config.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE RAZORPAY CONFIG", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // ── PLAN PRICING MANAGER ──
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CurrencyRupee, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dynamic Subscription Pricing (₹)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = p1Day, onValueChange = { p1Day = it }, label = { Text("1-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = p3Days, onValueChange = { p3Days = it }, label = { Text("3-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = p7Days, onValueChange = { p7Days = it }, label = { Text("7-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = p15Days, onValueChange = { p15Days = it }, label = { Text("15-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = p30Days, onValueChange = { p30Days = it }, label = { Text("30-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = p365Days, onValueChange = { p365Days = it }, label = { Text("365-Day") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val config = PlanPricingConfig(
                            price1Day = p1Day.toDoubleOrNull() ?: 29.0,
                            price3Days = p3Days.toDoubleOrNull() ?: 79.0,
                            price7Days = p7Days.toDoubleOrNull() ?: 179.0,
                            price15Days = p15Days.toDoubleOrNull() ?: 299.0,
                            price30Days = p30Days.toDoubleOrNull() ?: 499.0,
                            price365Days = p365Days.toDoubleOrNull() ?: 3999.0
                        )
                        userRepository.updatePlanPricing(config) { success ->
                            Toast.makeText(context, if (success) "Pricing Saved Live!" else "Failed to save pricing.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE PLAN PRICING", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        // ── DYNAMIC SUPPORT LINKS ──
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Support & Helpdesk Links", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = telegramInput, onValueChange = { telegramInput = it }, label = { Text("Telegram Support URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = whatsappInput, onValueChange = { whatsappInput = it }, label = { Text("WhatsApp Support URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = youtubeInput, onValueChange = { youtubeInput = it }, label = { Text("YouTube / Tutorial URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val config = dynamicLinksState.copy(
                            telegramSupport = telegramInput.trim(),
                            whatsappSupport = whatsappInput.trim(),
                            youtubeChannel = youtubeInput.trim()
                        )
                        userRepository.updateDynamicLinks(config) { success ->
                            Toast.makeText(context, if (success) "Links Saved Live!" else "Failed to save links.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE SUPPORT LINKS", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // ── OFFER BANNER ──
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = AccentOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("In-App Announcement Banner", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Banner to Drivers", fontSize = 13.sp, color = TextPrimary)
                    Switch(checked = showBannerToggle, onCheckedChange = { showBannerToggle = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = bannerTextInput, onValueChange = { bannerTextInput = it }, label = { Text("Banner Text") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val config = OfferBannerConfig(showBanner = showBannerToggle, bannerText = bannerTextInput.trim())
                        userRepository.updateOfferBanner(config) { success ->
                            Toast.makeText(context, if (success) "Banner Saved Live!" else "Failed to save banner.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE BANNER", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ── SALES ANALYTICS SCREEN ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSalesAnalyticsScreen(
    salesList: List<SaleRecord>,
    selectedSalesFilter: String,
    onFilterChanged: (String) -> Unit,
    onCustomDateClick: () -> Unit,
    customStartMs: Long?,
    customEndMs: Long?,
    customStartDateStr: String? = null,
    customEndDateStr: String? = null,
    canEditSales: Boolean,
    currentSalesPage: Int,
    onPageChanged: (Int) -> Unit,
    showAllSales: Boolean,
    onToggleShowAll: () -> Unit,
    salesPageSize: Int,
    onModifySale: (SaleRecord) -> Unit,
    onDeleteSale: (SaleRecord) -> Unit,
    deletedSalesCount: Int = 0,
    onOpenVaultClick: () -> Unit = {}
) {
    val now = remember { System.currentTimeMillis() }
    val cal = Calendar.getInstance()

    val filteredSales = remember(salesList, selectedSalesFilter, customStartMs, customEndMs) {
        when (selectedSalesFilter) {
            "Today" -> {
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                salesList.filter { it.activationTimestamp >= start }
            }
            "Yesterday" -> {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                salesList.filter { it.activationTimestamp in start..end }
            }
            "This Week" -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                salesList.filter { it.activationTimestamp >= start }
            }
            "This Month" -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                salesList.filter { it.activationTimestamp >= start }
            }
            "Custom Date", "Custom Range" -> {
                if (customStartMs != null && customEndMs != null) {
                    salesList.filter { it.activationTimestamp in customStartMs..customEndMs }
                } else {
                    emptyList()
                }
            }
            else -> salesList
        }
    }

    val totalRevenue = remember(filteredSales) { filteredSales.sumOf { it.amountPaid } }
    val totalTxns = filteredSales.size
    val avgTicket = remember(totalRevenue, totalTxns) { if (totalTxns > 0) totalRevenue / totalTxns else 0.0 }

    val activeSalesPageSize = if (showAllSales) 100 else salesPageSize
    val totalPages = remember(filteredSales.size, activeSalesPageSize) {
        if (filteredSales.isEmpty()) 1 else ((filteredSales.size + activeSalesPageSize - 1) / activeSalesPageSize)
    }
    val safePage = currentSalesPage.coerceIn(1, totalPages)
    val paginatedSales = remember(filteredSales, showAllSales, safePage, activeSalesPageSize) {
        val startIndex = (safePage - 1) * activeSalesPageSize
        filteredSales.drop(startIndex).take(activeSalesPageSize)
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Date Filter Header Row with Interactive Custom Date Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FILTER SALES HISTORY", style = MaterialTheme.typography.labelSmall.copy(color = AccentOrange))
            if ((selectedSalesFilter == "Custom Date" || selectedSalesFilter == "Custom Range") && customStartMs != null) {
                val dateDisplay = if (!customStartDateStr.isNullOrBlank() && !customEndDateStr.isNullOrBlank()) {
                    if (customStartDateStr == customEndDateStr) {
                        "📅 $customStartDateStr (1 Day)"
                    } else {
                        "📅 $customStartDateStr to $customEndDateStr"
                    }
                } else {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val sStr = sdf.format(Date(customStartMs))
                    val eStr = sdf.format(Date(customEndMs ?: customStartMs))
                    if (sStr == eStr) "📅 $sStr (1 Day)" else "📅 $sStr to $eStr"
                }
                Text(
                    dateDisplay,
                    fontSize = 11.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCustomDateClick() }
                )
            }
        }

        // Filter Pills Row (All Time, Today, Yesterday, This Week, This Month, Custom Date)
        val filters = listOf("All Time", "Today", "Yesterday", "This Week", "This Month", "Custom Date")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedSalesFilter == filter || (filter == "Custom Date" && selectedSalesFilter == "Custom Range")
                Button(
                    onClick = {
                        if (filter == "Custom Date") {
                            if (customStartMs != null && customEndMs != null && selectedSalesFilter != "Custom Date" && selectedSalesFilter != "Custom Range") {
                                onFilterChanged("Custom Date")
                            } else {
                                onCustomDateClick()
                            }
                        } else {
                            onFilterChanged(filter)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) AccentOrange else SurfaceDark
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(
                        text = if (filter == "Custom Date") "📅 Custom" else filter,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        // Stats Overview Row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminStatCard("Revenue", "₹${totalRevenue.toInt()}", AccentGreen, Modifier.weight(1f))
            AdminStatCard("Transactions", "$totalTxns", PrimaryBlue, Modifier.weight(1f))
            AdminStatCard("Avg Order", "₹${avgTicket.toInt()}", AccentOrange, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Transactions Header with Trash Vault button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ACTIVATED TRANSACTIONS LOG (${filteredSales.size})", style = MaterialTheme.typography.labelSmall.copy(color = AccentGreen))
            if (canEditSales) {
                Surface(
                    modifier = Modifier.clickable { onOpenVaultClick() },
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🗑️ DELETED LOGS ($deletedSalesCount)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                    }
                }
            }
        }

        // Pagination Bar
        AdminPaginationBar(
            currentPage = safePage,
            totalPages = totalPages,
            totalItems = filteredSales.size,
            pageSize = activeSalesPageSize,
            showAll = showAllSales,
            onPageChange = onPageChanged,
            onToggleShowAll = onToggleShowAll,
            itemLabel = "Sales"
        )

        // Sales List
        paginatedSales.forEach { sale ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sale.planName, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                        Text("₹${sale.amountPaid.toInt()}", fontWeight = FontWeight.Bold, color = AccentGreen, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Driver: ${sale.userEmail}", fontSize = 12.sp, color = TextSecondary)
                    Text("Txn ID: ${sale.transactionId}", fontSize = 11.sp, color = TextMuted)
                    Text("Date: ${dateFormat.format(Date(sale.activationTimestamp))}", fontSize = 11.sp, color = TextSecondary)
                    Text("Source: ${sale.activationSource}", fontSize = 10.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    if (!sale.resetReason.isNullOrBlank()) {
                        Text("Reset Reason: ${sale.resetReason}", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.SemiBold)
                    }
                    if (!sale.performedByAdmin.isNullOrBlank()) {
                        Text("👤 Action Performed By: ${sale.performedByAdmin}", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold)
                    }

                    if (canEditSales) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onModifySale(sale) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { onDeleteSale(sale) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (filteredSales.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            AdminPaginationBar(
                currentPage = safePage,
                totalPages = totalPages,
                totalItems = filteredSales.size,
                pageSize = activeSalesPageSize,
                showAll = showAllSales,
                onPageChange = onPageChanged,
                onToggleShowAll = onToggleShowAll,
                itemLabel = "Sales"
            )
        }
    }
}

// ── SHARED COMPONENTS ──

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun AdminPaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    pageSize: Int = 10,
    showAll: Boolean,
    onPageChange: (Int) -> Unit,
    onToggleShowAll: () -> Unit,
    itemLabel: String = "Records",
    modifier: Modifier = Modifier
) {
    if (totalItems == 0) return

    val startItem = ((currentPage - 1) * pageSize + 1).coerceAtMost(totalItems)
    val endItem = (currentPage * pageSize).coerceAtMost(totalItems)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardElevated),
        border = BorderStroke(1.dp, if (showAll) AccentOrange.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (showAll) AccentOrange.copy(alpha = 0.2f) else DarkCard,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$startItem–$endItem / $totalItems",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showAll) AccentOrange else TextPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showAll) "100/page ($itemLabel)" else "10/page ($itemLabel)",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Show All / Limit Toggle Button
                Surface(
                    color = if (showAll) AccentOrange else DarkCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (showAll) AccentOrange else Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleShowAll() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = if (showAll) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                            contentDescription = null,
                            tint = if (showAll) Color.Black else AccentOrange,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (showAll) "⚡ 10/page" else "📂 100/page",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showAll) Color.Black else AccentOrange
                        )
                    }
                }
            }

            if (totalPages > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    Button(
                        onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
                        enabled = currentPage > 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            disabledContainerColor = DarkCard.copy(alpha = 0.35f),
                            contentColor = AccentOrange,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PREVIOUS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Current Page Badge
                    Surface(
                        color = AccentOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Page $currentPage of $totalPages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Next Button
                    Button(
                        onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
                        enabled = currentPage < totalPages,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            disabledContainerColor = DarkCard.copy(alpha = 0.35f),
                            contentColor = AccentOrange,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("NEXT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page", modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}
