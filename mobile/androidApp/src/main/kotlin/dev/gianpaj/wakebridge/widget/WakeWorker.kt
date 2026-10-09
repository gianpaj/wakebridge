package dev.gianpaj.wakebridge.widget

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.gianpaj.wakebridge.shared.ApiError
import dev.gianpaj.wakebridge.shared.OnlineResult
import dev.gianpaj.wakebridge.shared.awaitOnline
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
        try {
            val checkOnly = inputData.getBoolean(CHECK_ONLY, false)
            if (!checkOnly) {
                if (api.wake() is ApiResult.Failure) {
                    updateWidgets(WidgetStatus.FAILED)
                    return Result.success()
                }
            }
            updateWidgets(if (checkOnly) WidgetStatus.CHECKING else WidgetStatus.WAKING)
            val status = when (val result = api.awaitOnline()) {
                OnlineResult.Online -> WidgetStatus.ONLINE
                OnlineResult.TimedOut -> WidgetStatus.TIMED_OUT
                is OnlineResult.Failed -> if (result.error == ApiError.StatusNotConfigured) {
                    WidgetStatus.NOT_CONFIGURED
                } else {
                    WidgetStatus.CHECK_FAILED
                }
            }
            updateWidgets(status)
        } catch (cancelled: CancellationException) {
            // Leave a check-only action if Android stops the worker mid-poll.
            withContext(NonCancellable) { updateWidgets(WidgetStatus.CHECK_FAILED) }
            throw cancelled
        } finally {
            api.close()
        }

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
