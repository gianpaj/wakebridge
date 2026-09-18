package dev.gianpaj.wakebridge.widget

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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.Row
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.gianpaj.wakebridge.MainActivity
import dev.gianpaj.wakebridge.R
import dev.gianpaj.wakebridge.storage.SecureConfigurationStore

internal val widgetStatusKey = stringPreferencesKey("wake_status")

enum class WidgetStatus(val label: String) {
    READY("Tap to wake"),
    SENDING("Sending…"),
    SENT("Sent · wake again"),
    FAILED("Failed · retry"),
    SETUP_REQUIRED("Tap to set up"),
}

class WakeBridgeWidget : GlanceAppWidget() {
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

class WakeBridgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WakeBridgeWidget()
}

@Composable
private fun WidgetContent(
    configured: Boolean,
    status: WidgetStatus,
    setupAction: Action,
) {
    val action = if (configured) actionRunCallback<WakeAction>() else setupAction
    val isSending = configured && status == WidgetStatus.SENDING
    val accent = if (configured && status == WidgetStatus.FAILED) {
        Color(0xFFFFB4AB)
    } else {
        Color(0xFF9CE8CE)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ColorProvider(Color(0xFF172033)))
            .cornerRadius(24.dp)
            .then(if (isSending) GlanceModifier else GlanceModifier.clickable(action))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_power),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "gianRTX",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = if (configured) status.label else WidgetStatus.SETUP_REQUIRED.label,
                style = TextStyle(color = ColorProvider(accent), fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }
}
