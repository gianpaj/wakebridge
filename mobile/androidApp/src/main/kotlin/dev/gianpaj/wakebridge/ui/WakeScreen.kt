package dev.gianpaj.wakebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun WakeScreen(
    state: WakeUiState,
    onServerUrlChange: (String) -> Unit,
    onCloudflareClientIdChange: (String) -> Unit,
    onCloudflareClientSecretChange: (String) -> Unit,
    onWakeApiTokenChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onWake: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancelSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("WakeBridge", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            if (state.isEditing) {
                ConfigurationForm(
                    state = state,
                    onServerUrlChange = onServerUrlChange,
                    onCloudflareClientIdChange = onCloudflareClientIdChange,
                    onCloudflareClientSecretChange = onCloudflareClientSecretChange,
                    onWakeApiTokenChange = onWakeApiTokenChange,
                    onTestConnection = onTestConnection,
                    onCancelSettings = onCancelSettings,
                )
            } else {
                ReadyContent(
                    state = state,
                    onWake = onWake,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ConfigurationForm(
    state: WakeUiState,
    onServerUrlChange: (String) -> Unit,
    onCloudflareClientIdChange: (String) -> Unit,
    onCloudflareClientSecretChange: (String) -> Unit,
    onWakeApiTokenChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onCancelSettings: () -> Unit,
) {
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text("Server URL") },
        placeholder = { Text("https://wake.example.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.cloudflareClientId,
        onValueChange = onCloudflareClientIdChange,
        label = { Text("Cloudflare Client ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.cloudflareClientSecret,
        onValueChange = onCloudflareClientSecretChange,
        label = { Text("Cloudflare Client Secret") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.wakeApiToken,
        onValueChange = onWakeApiTokenChange,
        label = { Text("Wake API Token") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onTestConnection,
            enabled = !state.isBusy,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (state.isBusy) "Testing…" else "Test Connection")
        }
        if (state.hasTestedConfiguration) {
            OutlinedButton(onClick = onCancelSettings, enabled = !state.isBusy) {
                Text("Cancel")
            }
        }
    }
    StatusMessage(state.message)
    if (!state.hasTestedConfiguration) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Widget unavailable until setup succeeds.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReadyContent(
    state: WakeUiState,
    onWake: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text("Server", style = MaterialTheme.typography.labelLarge)
    Text(state.serverUrl, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(16.dp))
    Text("Connection", style = MaterialTheme.typography.labelLarge)
    Text("✓ Connected", color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onWake,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isBusy) "Sending…" else "Wake gianRTX")
    }
    StatusMessage(state.message)
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onOpenSettings, enabled = !state.isBusy) {
        Text("Settings")
    }
}

@Composable
private fun StatusMessage(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(16.dp))
    Text(message, style = MaterialTheme.typography.bodyMedium)
}
