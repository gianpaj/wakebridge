package dev.gianpaj.wakebridge.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WakeConfigurationTest {
    @Test
    fun acceptsHttpsConfiguration() {
        val validation = configuration().validate()

        val valid = assertIs<ConfigurationValidation.Valid>(validation)
        assertEquals("https://wake.example.com", valid.configuration.serverUrl)
    }

    @Test
    fun rejectsPlainHttp() {
        val validation = configuration(serverUrl = "http://wake.example.com").validate()

        val invalid = assertIs<ConfigurationValidation.Invalid>(validation)
        assertEquals(ConfigurationError.HTTPS_REQUIRED, invalid.reason)
    }

    @Test
    fun rejectsIncompleteCloudflareCredentials() {
        val validation = configuration(clientSecret = "").validate()

        val invalid = assertIs<ConfigurationValidation.Invalid>(validation)
        assertEquals(ConfigurationError.INCOMPLETE_CLOUDFLARE_CREDENTIALS, invalid.reason)
    }
}

internal fun configuration(
    serverUrl: String = "https://wake.example.com/",
    clientId: String = "client-id",
    clientSecret: String = "client-secret",
    wakeToken: String = "wake-token",
) = WakeConfiguration(
    serverUrl = serverUrl,
    cloudflareClientId = clientId,
    cloudflareClientSecret = clientSecret,
    wakeApiToken = wakeToken,
)
