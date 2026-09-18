package dev.gianpaj.wakebridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Text(
                    "WakeBridge",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.isEditing) {
                        if (state.hasTestedConfiguration) "Connection settings" else "Set up your connection"
                    } else {
                        "gianRTX"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.isEditing) "Connect to your wake server to enable one-tap wake."
                    else "Send a wake command from here or your home screen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
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
                    ReadyContent(state, onWake, onOpenSettings)
                }
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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val testConnection = {
        focusManager.clearFocus()
        keyboard?.hide()
        onTestConnection()
    }
    val nextField = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onServerUrlChange,
        enabled = !state.isBusy,
        label = { Text("Server URL") },
        placeholder = { Text("https://wake.example.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        keyboardActions = nextField,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    SecretField(
        value = state.wakeApiToken,
        onValueChange = onWakeApiTokenChange,
        label = "Wake API token",
        enabled = !state.isBusy,
        keyboardActions = nextField,
    )
    Spacer(Modifier.height(24.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cloudflare Access", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optional. If your server uses Access, enter both credentials. Otherwise, leave both blank.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.cloudflareClientId,
                onValueChange = onCloudflareClientIdChange,
                enabled = !state.isBusy,
                label = { Text("Client ID") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = nextField,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SecretField(
                value = state.cloudflareClientSecret,
                onValueChange = onCloudflareClientSecretChange,
                label = "Client secret",
                enabled = !state.isBusy,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { testConnection() }),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = testConnection,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(if (state.isBusy) "Testing connection…" else "Test & save connection")
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "This checks your server and saves the connection. It won't wake your computer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StatusMessage(state.message)
    if (state.hasTestedConfiguration) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCancelSettings,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(if (visible) "Hide" else "Show")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadyContent(
    state: WakeUiState,
    onWake: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Setup verified", style = MaterialTheme.typography.titleMedium)
            Text(
                state.serverUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onWake,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(if (state.isBusy) "Sending…" else "Wake gianRTX", style = MaterialTheme.typography.titleMedium)
    }
    StatusMessage(state.message)
    Spacer(Modifier.height(24.dp))
    Text(
        "For one-tap access, add the WakeBridge widget from your home screen's widget picker.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedButton(
        onClick = onOpenSettings,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Connection settings")
    }
}

@Composable
private fun StatusMessage(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(16.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
