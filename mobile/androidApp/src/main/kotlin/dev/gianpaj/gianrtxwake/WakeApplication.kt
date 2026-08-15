package dev.gianpaj.gianrtxwake

import android.app.Application
import dev.gianpaj.gianrtxwake.storage.SecureConfigurationStore

class WakeApplication : Application() {
    val configurationStore: SecureConfigurationStore by lazy {
        SecureConfigurationStore(applicationContext)
    }
}
