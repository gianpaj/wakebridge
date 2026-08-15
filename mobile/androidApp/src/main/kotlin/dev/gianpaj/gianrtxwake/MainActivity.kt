package dev.gianpaj.gianrtxwake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gianpaj.gianrtxwake.ui.WakeScreen
import dev.gianpaj.gianrtxwake.ui.WakeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WakeViewModel by viewModels {
        val application = application as WakeApplication
        WakeViewModel.Factory(application, application.configurationStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                WakeScreen(
                    state = state,
                    onServerUrlChange = viewModel::updateServerUrl,
                    onCloudflareClientIdChange = viewModel::updateCloudflareClientId,
                    onCloudflareClientSecretChange = viewModel::updateCloudflareClientSecret,
                    onWakeApiTokenChange = viewModel::updateWakeApiToken,
                    onTestConnection = viewModel::testConnection,
                    onWake = viewModel::wake,
                    onOpenSettings = viewModel::openSettings,
                    onCancelSettings = viewModel::cancelSettings,
                )
            }
        }
    }
}
