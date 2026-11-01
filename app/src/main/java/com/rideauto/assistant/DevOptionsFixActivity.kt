package com.rideauto.assistant

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rideauto.assistant.ui.theme.*

/**
 * Activity for hiding Developer Options to bypass Ola Driver app's
 * detection. Uses Shizuku shell commands to modify system settings.
 * Styled natively with Ride Clicker Pro+'s dark glassmorphism UI/UX.
 */
class DevOptionsFixActivity : ComponentActivity() {

    private val logs = mutableStateListOf<String>("Ready for action.")
    private var isShizukuInstalled by mutableStateOf(false)
    private var isShizukuRunning by mutableStateOf(false)
    private var isShizukuGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateStatus()

        setContent {
            RideAssistantProTheme {
                DevOptionsFixScreen(
                    installed = isShizukuInstalled,
                    running = isShizukuRunning,
                    granted = isShizukuGranted,
                    logs = logs,
                    onBack = { finish() },
                    onRequestShizuku = { handleRequestShizuku() },
                    onHideDevOptions = { handleHideDevOptions() },
                    onRestoreDevOptions = { handleRestoreDevOptions() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        isShizukuInstalled = ShizukuManager.isShizukuInstalled(this)
        isShizukuRunning = ShizukuManager.isShizukuRunning()
        isShizukuGranted = ShizukuManager.isPermissionGranted()
    }

    private fun appendLog(msg: String) {
        logs.add(msg)
    }

    private fun handleRequestShizuku() {
        if (!ShizukuManager.isShizukuInstalled(this)) {
            appendLog("❌ Monkey / Shizuku is not installed. Please install Monkey or Shizuku.")
            Toast.makeText(this, "Monkey / Shizuku not installed", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ShizukuManager.isShizukuRunning()) {
            appendLog("❌ Monkey / Shizuku app not running. Opening app...")
            ShizukuManager.openShizukuApp(this)
            Toast.makeText(this, "Monkey / Shizuku app not running", Toast.LENGTH_SHORT).show()
            return
        }
        ShizukuManager.requestPermission(this) { msg ->
            appendLog("📋 $msg")
            updateStatus()
        }
        appendLog("📋 Monkey / Shizuku permission requested. Please allow in dialog.")
    }

    private fun handleHideDevOptions() {
        if (!ShizukuManager.isReady()) {
            appendLog("❌ Shizuku not ready. Grant permission first.")
            Toast.makeText(this, "Shizuku not ready", Toast.LENGTH_SHORT).show()
            return
        }

        appendLog("🔧 Executing: Hide Developer Options...")

        // Step 1: Disable developer options flag (Ola app reads this setting)
        val hideDev = ShizukuManager.executeCommand("settings put global development_settings_enabled 0")
        appendLog("  → settings put global development_settings_enabled 0: ${if (hideDev != null) "✅ OK" else "❌ FAIL"}")

        // Step 2: Force stop Ola Driver app so it restarts and re-checks settings
        val forceStop = ShizukuManager.executeCommand("am force-stop com.olacabs.oladriver")
        appendLog("  → am force-stop com.olacabs.oladriver: ${if (forceStop != null) "✅ OK" else "❌ FAIL"}")

        // Verify Shizuku is still alive
        if (ShizukuManager.isReady()) {
            appendLog("⚡ Shizuku is ACTIVE and RUNNING.")
            appendLog("✅ Fix complete! Ola Driver will open without Developer Options warning while Shizuku remains active.")
            Toast.makeText(this, "Dev Options hidden (Shizuku active)", Toast.LENGTH_LONG).show()
        } else {
            appendLog("⚠️ Shizuku service disconnected. Please restart Shizuku.")
        }
        updateStatus()
    }

    private fun handleRestoreDevOptions() {
        if (!ShizukuManager.isReady()) {
            appendLog("❌ Shizuku not ready.")
            Toast.makeText(this, "Shizuku not ready", Toast.LENGTH_SHORT).show()
            return
        }

        appendLog("🔧 Restoring Developer Options...")
        val restore = ShizukuManager.executeCommand("settings put global development_settings_enabled 1")
        appendLog("  → development_settings_enabled = 1: ${if (restore != null) "✅ OK" else "❌ FAIL"}")
        appendLog("✅ Developer Options restored.")
        Toast.makeText(this, "Dev Options restored", Toast.LENGTH_SHORT).show()
        updateStatus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevOptionsFixScreen(
    installed: Boolean,
    running: Boolean,
    granted: Boolean,
    logs: List<String>,
    onBack: () -> Unit,
    onRequestShizuku: () -> Unit,
    onHideDevOptions: () -> Unit,
    onRestoreDevOptions: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Hide Developer Options",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Ola Driver App Bypass",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Application Card
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "TARGET APPLICATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = EarningsGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Ola Driver App",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "com.olacabs.oladriver",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Shizuku Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (granted) GlassBorderActive else GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SHIZUKU SERVICE STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )

                        val (badgeText, badgeColor, badgeBg) = when {
                            granted -> Triple("Active ✓", StatusOnlineGreen, StatusOnlineGreen.copy(alpha = 0.15f))
                            running -> Triple("Permission Needed", WarningOrange, WarningOrange.copy(alpha = 0.15f))
                            installed -> Triple("Not Running", StatusOfflineRed, StatusOfflineRed.copy(alpha = 0.15f))
                            else -> Triple("Not Installed", StatusOfflineRed, StatusOfflineRed.copy(alpha = 0.15f))
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val detailsText = when {
                        granted -> "Monkey / Shizuku is running and permission is granted. Ready for commands."
                        running -> "Monkey / Shizuku is running but permission not granted. Tap below to allow."
                        installed -> "Monkey / Shizuku app not running. Start service in Monkey or Shizuku."
                        else -> "Monkey / Shizuku not installed. Please install Monkey or Shizuku to proceed."
                    }
                    Text(
                        detailsText,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )

                    if (!granted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestShizuku,
                            colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceLighter),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (running) "Allow Monkey / Shizuku" else if (installed) "Open Monkey / Shizuku App" else "Install Monkey / Shizuku",
                                color = EarningsGold,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Developer Options Control Card
            Card(
                colors = CardDefaults.cardColors(containerColor = GlassSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "DEVELOPER OPTIONS CONTROL",
                        style = MaterialTheme.typography.labelSmall,
                        color = EarningsGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Execute Fix Actions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Hides Developer Options setting (development_settings_enabled 0) and stops Ola Driver so it reloads without Developer Options warning while keeping Shizuku active.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onHideDevOptions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Hide Dev Options & Reset App",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onRestoreDevOptions,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(1.dp, GlassBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Restore Developer Options",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Execution Log Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070A12)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "EXECUTION LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        logs.takeLast(20).forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.contains("❌") || line.contains("FAIL")) StatusOfflineRed
                                else if (line.contains("⚠️")) WarningOrange
                                else StatusOnlineGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
