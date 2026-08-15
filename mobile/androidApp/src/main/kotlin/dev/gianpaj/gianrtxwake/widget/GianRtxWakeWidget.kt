package dev.gianpaj.gianrtxwake.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Button
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.gianpaj.gianrtxwake.MainActivity
import dev.gianpaj.gianrtxwake.storage.SecureConfigurationStore

internal val widgetStatusKey = stringPreferencesKey("wake_status")

enum class WidgetStatus(val label: String) {
    READY("⚡ WAKE"),
    SENDING("Sending…"),
    SENT("Sent ✓"),
    FAILED("Failed"),
    SETUP_REQUIRED("Setup required"),
}

class GianRtxWakeWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val configured = SecureConfigurationStore(context).load() != null
        val setupAction = actionStartActivity(Intent(context, MainActivity::class.java))
        provideContent {
            val preferences = currentState<Preferences>()
            val status = preferences[widgetStatusKey]
                ?.let { value -> WidgetStatus.entries.firstOrNull { it.name == value } }
                ?: WidgetStatus.READY
            WidgetContent(configured = configured, status = status, setupAction = setupAction)
        }
    }
}

class GianRtxWakeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GianRtxWakeWidget()
}

@Composable
private fun WidgetContent(
    configured: Boolean,
    status: WidgetStatus,
    setupAction: Action,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF172033)))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "gianRTX",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        Button(
            text = if (configured) status.label else WidgetStatus.SETUP_REQUIRED.label,
            onClick = if (configured) {
                actionRunCallback<WakeAction>()
            } else {
                setupAction
            },
        )
    }
}
