package dev.gianpaj.wakebridge.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.gianpaj.wakebridge.storage.SecureConfigurationStore

class WakeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        if (SecureConfigurationStore(context).load() == null) {
            setWidgetStatus(context, glanceId, WidgetStatus.SETUP_REQUIRED)
            WakeBridgeWidget().update(context, glanceId)
            return
        }
        if (!WidgetWakeGate.tryAcquire(context)) return

        setWidgetStatus(context, glanceId, WidgetStatus.SENDING)
        WakeBridgeWidget().update(context, glanceId)

        val request = OneTimeWorkRequestBuilder<WakeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "wakebridge-widget-wake"
    }
}

private object WidgetWakeGate {
    private const val PREFERENCES = "wakebridge_widget_runtime"
    private const val LAST_TAP = "last_wake_tap_elapsed_time"
    private const val COOLDOWN_MILLIS = 2_000L
    private val lock = Any()

    fun tryAcquire(context: Context): Boolean = synchronized(lock) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val lastTap = preferences.getLong(LAST_TAP, Long.MIN_VALUE)
        val elapsed = now - lastTap
        if (elapsed in 0 until COOLDOWN_MILLIS) return@synchronized false

        preferences.edit(commit = true) { putLong(LAST_TAP, now) }
        true
    }
}

internal suspend fun setWidgetStatus(
    context: Context,
    glanceId: GlanceId,
    status: WidgetStatus,
) {
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences[widgetStatusKey] = status.name
    }
}
