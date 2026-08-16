package dev.gianpaj.wakebridge.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.gianpaj.wakebridge.shared.ApiResult
import dev.gianpaj.wakebridge.shared.WakeApiFactory
import dev.gianpaj.wakebridge.storage.SecureConfigurationStore

class WakeWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val configuration = SecureConfigurationStore(applicationContext).load()
        if (configuration == null) {
            updateWidgets(WidgetStatus.SETUP_REQUIRED)
            return Result.success()
        }

        val api = WakeApiFactory.create(configuration)
        val result = try {
            api.wake()
        } finally {
            api.close()
        }
        updateWidgets(
            if (result is ApiResult.Success) WidgetStatus.SENT else WidgetStatus.FAILED,
        )

        // A retry could duplicate an accepted wake request if the response was lost.
        return Result.success()
    }

    private suspend fun updateWidgets(status: WidgetStatus) {
        val manager = GlanceAppWidgetManager(applicationContext)
        val widget = WakeBridgeWidget()
        manager.getGlanceIds(WakeBridgeWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(
                applicationContext,
                glanceId,
            ) { preferences ->
                preferences[widgetStatusKey] = status.name
            }
            widget.update(applicationContext, glanceId)
        }
    }
}
