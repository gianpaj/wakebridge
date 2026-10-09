package dev.gianpaj.wakebridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gianpaj.wakebridge.ui.WakeScreen
import dev.gianpaj.wakebridge.ui.WakeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WakeViewModel by viewModels {
        val application = application as WakeBridgeApplication
        WakeViewModel.Factory(application, application.configurationStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    darkColorScheme(
                        primary = Color(0xFF9CE8CE),
                        onPrimary = Color(0xFF00382B),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF006B53),
                        onPrimary = Color.White,
                    )
                },
            ) {
                WakeScreen(
                    state = state,
                    onServerUrlChange = viewModel::updateServerUrl,
                    onCloudflareClientIdChange = viewModel::updateCloudflareClientId,
                    onCloudflareClientSecretChange = viewModel::updateCloudflareClientSecret,
                    onWakeApiTokenChange = viewModel::updateWakeApiToken,
                    onTestConnection = viewModel::testConnection,
                    onWake = viewModel::wake,
                    onCheckAgain = viewModel::checkAgain,
                    onOpenSettings = viewModel::openSettings,
                    onCancelSettings = viewModel::cancelSettings,
                )
            }
        }
    }
}
