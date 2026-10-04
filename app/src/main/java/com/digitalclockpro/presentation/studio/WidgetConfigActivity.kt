package com.digitalclockpro.presentation.studio

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitalclockpro.core.ui.theme.DigitalClockProTheme
import com.digitalclockpro.presentation.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Widget configuration activity (declared via `android:configure`).
 *
 * Contract: it must return RESULT_CANCELED unless the user saves, otherwise the launcher
 * would place an unconfigured widget.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val prefs by appViewModel.preferences.collectAsStateWithLifecycle()
            DigitalClockProTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                amoledBlack = prefs.amoledBlack
            ) {
                WidgetStudioScreen(
                    appWidgetId = appWidgetId,
                    onSaved = {
                        setResult(Activity.RESULT_OK, resultIntent())
                        finish()
                    }
                )
            }
        }
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}
