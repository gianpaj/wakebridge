package dev.gianpaj.wakebridge

import android.app.Application
import dev.gianpaj.wakebridge.storage.SecureConfigurationStore

class WakeBridgeApplication : Application() {
    val configurationStore: SecureConfigurationStore by lazy {
        SecureConfigurationStore(applicationContext)
    }
}
