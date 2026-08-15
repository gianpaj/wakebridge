package dev.gianpaj.gianrtxwake.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import dev.gianpaj.gianrtxwake.shared.ConfigurationValidation
import dev.gianpaj.gianrtxwake.shared.WakeConfiguration
import dev.gianpaj.gianrtxwake.shared.validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun load(): WakeConfiguration? = withContext(Dispatchers.IO) {
        val encoded = preferences.getString(CONFIGURATION_KEY, null) ?: return@withContext null
        try {
            val payload = json.decodeFromString<EncryptedPayload>(encoded)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(payload.iv, Base64.NO_WRAP)),
                )
            }
            val plaintext = cipher.doFinal(Base64.decode(payload.ciphertext, Base64.NO_WRAP))
            val configuration = json.decodeFromString<WakeConfiguration>(plaintext.decodeToString())
            when (val validation = configuration.validate()) {
                is ConfigurationValidation.Valid -> validation.configuration
                is ConfigurationValidation.Invalid -> null
            }
        } catch (_: Exception) {
            Log.e(TAG, "Stored configuration could not be decrypted")
            null
        }
    }

    suspend fun save(configuration: WakeConfiguration) = withContext(Dispatchers.IO) {
        val valid = when (val validation = configuration.validate()) {
            is ConfigurationValidation.Valid -> validation.configuration
            is ConfigurationValidation.Invalid -> error("Refusing to store invalid configuration")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(json.encodeToString(valid).encodeToByteArray())
        val payload = EncryptedPayload(
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        )
        preferences.edit(commit = true) {
            putString(CONFIGURATION_KEY, json.encodeToString(payload))
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    @Serializable
    private data class EncryptedPayload(
        val iv: String,
        val ciphertext: String,
    )

    private companion object {
        const val TAG = "SecureConfigStore"
        const val PREFERENCES_NAME = "gianrtx_secure_configuration"
        const val CONFIGURATION_KEY = "encrypted_configuration"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gianrtx_wake_configuration_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        val json = Json { ignoreUnknownKeys = false }
    }
}
