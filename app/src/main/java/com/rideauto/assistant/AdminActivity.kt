package com.rideauto.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rideauto.assistant.ui.theme.BackgroundDark
import com.rideauto.assistant.ui.theme.RideAssistantProTheme
import kotlinx.coroutines.launch

class AdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure any lingering payment notification service is stopped
        AdminPaymentNotificationService.stop(this)

        // Auto-sync authoritative network time
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                NetworkTimeHelper.syncNetworkTime()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            RideAssistantProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    AdminPortalScreen(
                        onCloseAdmin = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
