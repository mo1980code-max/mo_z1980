package com.digitalclockpro.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * Material You / Glance flavour of the clock widget. Uses the launcher's dynamic colour palette
 * and `SizeMode.Exact` so one composable serves every size without clipping.
 */
class GlanceClockWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun alarmRepository(): AlarmRepository
        fun preferencesRepository(): PreferencesRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val prefs = deps.preferencesRepository().preferences.first()
        val nextAlarm = deps.alarmRepository().getEnabledAlarms()
            .minByOrNull { it.nextTriggerAtMillis() }

        val nextAlarmText = nextAlarm?.let {
            val at = java.time.Instant.ofEpochMilli(it.nextTriggerAtMillis())
                .atZone(java.time.ZoneId.systemDefault())
            TimeFormatters.formatTime(at, prefs.use24Hour, showSeconds = false)
        }

        provideContent {
            GlanceTheme {
                Content(use24h = prefs.use24Hour, nextAlarmText = nextAlarmText)
            }
        }
    }

    @Composable
    private fun Content(use24h: Boolean, nextAlarmText: String?) {
        val now = ZonedDateTime.now()
        val dateOverride = currentState(key = stringPreferencesKey(KEY_DATE_PATTERN)) ?: "EEE, MMM d"
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = TimeFormatters.formatTime(now, use24h, showSeconds = false),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = TimeFormatters.formatDate(now, dateOverride),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
            )
            if (nextAlarmText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⏰ $nextAlarmText",
                        style = TextStyle(
                            color = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF00BFA5),
                                night = androidx.compose.ui.graphics.Color(0xFF64FFDA)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }

    companion object { const val KEY_DATE_PATTERN = "glance_date_pattern" }
}

class GlanceClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceClockWidget()
}
