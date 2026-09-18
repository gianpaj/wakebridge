package dev.gianpaj.wakebridge.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.gianpaj.wakebridge.shared.ApiError
import dev.gianpaj.wakebridge.shared.ApiResult
import dev.gianpaj.wakebridge.shared.ConfigurationError
import dev.gianpaj.wakebridge.shared.ConfigurationValidation
import dev.gianpaj.wakebridge.shared.WakeApiFactory
import dev.gianpaj.wakebridge.shared.WakeConfiguration
import dev.gianpaj.wakebridge.shared.validate
import dev.gianpaj.wakebridge.storage.SecureConfigurationStore
import dev.gianpaj.wakebridge.widget.WakeBridgeWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WakeUiState(
    val serverUrl: String = "",
    val cloudflareClientId: String = "",
    val cloudflareClientSecret: String = "",
    val wakeApiToken: String = "",
    val hasTestedConfiguration: Boolean = false,
    val isEditing: Boolean = true,
    val isBusy: Boolean = true,
    val message: String? = null,
    val isError: Boolean = false,
)

class WakeViewModel(
    application: Application,
    private val configurationStore: SecureConfigurationStore,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(WakeUiState())
    val state: StateFlow<WakeUiState> = _state.asStateFlow()

    private var savedConfiguration: WakeConfiguration? = null

    init {
        viewModelScope.launch {
            val configuration = configurationStore.load()
            savedConfiguration = configuration
            _state.value = configuration?.toUiState(
                hasTestedConfiguration = true,
                isEditing = false,
            ) ?: WakeUiState(isBusy = false)
        }
    }

    fun updateServerUrl(value: String) = edit { copy(serverUrl = value) }

    fun updateCloudflareClientId(value: String) = edit { copy(cloudflareClientId = value) }

    fun updateCloudflareClientSecret(value: String) = edit { copy(cloudflareClientSecret = value) }

    fun updateWakeApiToken(value: String) = edit { copy(wakeApiToken = value) }

    fun openSettings() {
        val configuration = savedConfiguration ?: return
        _state.value = configuration.toUiState(
            hasTestedConfiguration = true,
            isEditing = true,
        )
    }

    fun cancelSettings() {
        val configuration = savedConfiguration ?: return
        _state.value = configuration.toUiState(
            hasTestedConfiguration = true,
            isEditing = false,
        )
    }

    fun testConnection() {
        if (_state.value.isBusy) return
        val validation = _state.value.configuration().validate()
        if (validation is ConfigurationValidation.Invalid) {
            _state.update { it.copy(message = validation.reason.message(), isError = true) }
            return
        }
        val configuration = (validation as ConfigurationValidation.Valid).configuration

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, isError = false) }
            val api = WakeApiFactory.create(configuration)
            val result = try {
                api.healthCheck()
            } finally {
                api.close()
            }

            if (result is ApiResult.Success) {
                try {
                    configurationStore.save(configuration)
                    savedConfiguration = configuration
                    _state.value = configuration.toUiState(
                        hasTestedConfiguration = true,
                        isEditing = false,
                        message = "Connected",
                    )
                    WakeBridgeWidget().updateAll(getApplication())
                } catch (_: Exception) {
                    _state.update {
                        it.copy(isBusy = false, message = "Could not save configuration", isError = true)
                    }
                }
            } else {
                val failure = result as ApiResult.Failure
                _state.update { it.copy(isBusy = false, message = failure.error.message(), isError = true) }
            }
        }
    }

    fun wake() {
        val configuration = savedConfiguration ?: return
        if (_state.value.isBusy || _state.value.isEditing) return

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = "Sending…", isError = false) }
            val api = WakeApiFactory.create(configuration)
            val result = try {
                api.wake()
            } finally {
                api.close()
            }
            _state.update {
                it.copy(
                    isBusy = false,
                    isError = result is ApiResult.Failure,
                    message = when (result) {
                        is ApiResult.Success -> "Wake command sent"
                        is ApiResult.Failure -> result.error.message()
                    },
                )
            }
        }
    }

    private fun edit(transform: WakeUiState.() -> WakeUiState) {
        _state.update { it.transform().copy(message = null, isError = false) }
    }

    private fun WakeUiState.configuration() = WakeConfiguration(
        serverUrl = serverUrl,
        cloudflareClientId = cloudflareClientId,
        cloudflareClientSecret = cloudflareClientSecret,
        wakeApiToken = wakeApiToken,
    )

    private fun WakeConfiguration.toUiState(
        hasTestedConfiguration: Boolean,
        isEditing: Boolean,
        message: String? = null,
    ) = WakeUiState(
        serverUrl = serverUrl,
        cloudflareClientId = cloudflareClientId,
        cloudflareClientSecret = cloudflareClientSecret,
        wakeApiToken = wakeApiToken,
        hasTestedConfiguration = hasTestedConfiguration,
        isEditing = isEditing,
        isBusy = false,
        message = message,
    )

    private fun ConfigurationError.message(): String = when (this) {
        ConfigurationError.INVALID_URL -> "Invalid URL"
        ConfigurationError.HTTPS_REQUIRED -> "Server URL must use HTTPS"
        ConfigurationError.WAKE_TOKEN_REQUIRED -> "Wake API token is required"
        ConfigurationError.INCOMPLETE_CLOUDFLARE_CREDENTIALS ->
            "Enter both Cloudflare credentials or leave both blank"
    }

    private fun ApiError.message(): String = when (this) {
        is ApiError.InvalidConfiguration -> reason.message()
        ApiError.CannotReachServer ->
            "Cannot reach server. Check your connection and server URL. " +
                "If you use Tailscale, make sure it is connected on this phone, then try again."
        ApiError.CloudflareAuthenticationFailed -> "Cloudflare authentication failed"
        ApiError.WakeTokenRejected -> "Wake API token was rejected"
        is ApiError.ServerRejected -> "Server rejected request (HTTP $status)"
        ApiError.WakePacketFailed -> "Server could not send the Wake-on-LAN packet"
        ApiError.UnexpectedResponse -> "Unexpected server response"
    }

    class Factory(
        private val application: Application,
        private val configurationStore: SecureConfigurationStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WakeViewModel(application, configurationStore) as T
    }
}
