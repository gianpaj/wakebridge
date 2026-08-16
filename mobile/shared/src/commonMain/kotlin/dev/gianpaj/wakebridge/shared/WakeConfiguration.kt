package dev.gianpaj.wakebridge.shared

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data class WakeConfiguration(
    val serverUrl: String,
    val cloudflareClientId: String,
    val cloudflareClientSecret: String,
    val wakeApiToken: String,
)

sealed interface ConfigurationValidation {
    data class Valid(val configuration: WakeConfiguration) : ConfigurationValidation
    data class Invalid(val reason: ConfigurationError) : ConfigurationValidation
}

enum class ConfigurationError {
    INVALID_URL,
    HTTPS_REQUIRED,
    WAKE_TOKEN_REQUIRED,
    INCOMPLETE_CLOUDFLARE_CREDENTIALS,
}

fun WakeConfiguration.validate(): ConfigurationValidation {
    val normalizedUrl = serverUrl.trim().trimEnd('/')
    val url = try {
        Url(normalizedUrl)
    } catch (_: Exception) {
        return ConfigurationValidation.Invalid(ConfigurationError.INVALID_URL)
    }

    if (url.protocol != URLProtocol.HTTPS) {
        return ConfigurationValidation.Invalid(ConfigurationError.HTTPS_REQUIRED)
    }
    if (
        url.host.isBlank() ||
        url.fragment.isNotEmpty() ||
        !url.parameters.isEmpty() ||
        url.user != null ||
        url.password != null
    ) {
        return ConfigurationValidation.Invalid(ConfigurationError.INVALID_URL)
    }
    if (wakeApiToken.isBlank()) {
        return ConfigurationValidation.Invalid(ConfigurationError.WAKE_TOKEN_REQUIRED)
    }

    val clientId = cloudflareClientId.trim()
    val clientSecret = cloudflareClientSecret.trim()
    if (clientId.isBlank() != clientSecret.isBlank()) {
        return ConfigurationValidation.Invalid(ConfigurationError.INCOMPLETE_CLOUDFLARE_CREDENTIALS)
    }

    return ConfigurationValidation.Valid(
        copy(
            serverUrl = normalizedUrl,
            cloudflareClientId = clientId,
            cloudflareClientSecret = clientSecret,
            wakeApiToken = wakeApiToken.trim(),
        ),
    )
}
