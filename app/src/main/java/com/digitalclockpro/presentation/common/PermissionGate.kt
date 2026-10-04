package com.digitalclockpro.presentation.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Surfaces the three settings that silently break alarms when missing:
 *  1. `POST_NOTIFICATIONS` (Android 13+) — without it the full-screen intent cannot be posted.
 *  2. `SCHEDULE_EXACT_ALARM` (Android 12+) — without it alarms are downgraded to inexact windows.
 *  3. **Battery optimization exemption** — aggressive OEM power managers (Samsung, Xiaomi, Oppo,
 *     Huawei…) freeze the app and drop its exact alarms and foreground service unless the app is
 *     on the exemption list.
 *
 * All three states are re-read on `ON_RESUME`, because the user grants #2 and #3 in system
 * screens that return no result to us.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.readAlarmReliabilityStatus()) }

    // Re-check whenever the user comes back from a system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = context.readAlarmReliabilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { status = context.readAlarmReliabilityStatus() }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { status = context.readAlarmReliabilityStatus() }

    // System settings screens are not always resolvable under Android 11+ package visibility,
    // and some OEM ROMs ship without them entirely -> never let a missing Activity crash the app.
    val launchSettings: (Intent, Intent?) -> Unit = { primary, fallback ->
        try {
            settingsLauncher.launch(primary)
        } catch (e: ActivityNotFoundException) {
            fallback?.let { runCatching { settingsLauncher.launch(it) } }
        }
    }

    if (status.allGranted) return

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null)
                Text(
                    "  Your alarms may not ring",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.explanation,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.notificationsGranted) {
                    FilledTonalButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            launchSettings(
                                context.appNotificationSettingsIntent(),
                                context.appDetailsSettingsIntent()
                            )
                        }
                    }) { Text("Allow notifications") }
                }
                if (!status.exactAlarmsGranted) {
                    FilledTonalButton(onClick = {
                        launchSettings(
                            context.exactAlarmSettingsIntent(),
                            context.appDetailsSettingsIntent()
                        )
                    }) { Text("Allow exact alarms") }
                }
                if (!status.batteryOptimizationIgnored) {
                    FilledTonalButton(onClick = {
                        launchSettings(
                            context.ignoreBatteryOptimizationsIntent(),
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }) { Text("Ignore battery optimizations") }
                }
            }
        }
    }
}

/** Aggregated reliability state of the alarm pipeline. */
data class AlarmReliabilityStatus(
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val batteryOptimizationIgnored: Boolean
) {
    val allGranted: Boolean
        get() = notificationsGranted && exactAlarmsGranted && batteryOptimizationIgnored

    val explanation: String
        get() = buildList {
            if (!notificationsGranted) add("notifications are blocked")
            if (!exactAlarmsGranted) add("exact alarms are not allowed")
            if (!batteryOptimizationIgnored) add("battery optimization can freeze the app")
        }.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}

fun Context.readAlarmReliabilityStatus() = AlarmReliabilityStatus(
    notificationsGranted = hasNotificationPermission(),
    exactAlarmsGranted = canScheduleExactAlarms(),
    batteryOptimizationIgnored = isIgnoringBatteryOptimizations()
)

fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun Context.canScheduleExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

/** True when the app is on the battery-optimization exemption list (or the API is unavailable). */
fun Context.isIgnoringBatteryOptimizations(): Boolean = runCatching {
    (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(packageName)
}.getOrDefault(true)

/**
 * Direct system dialog ("Allow <app> to run in the background?").
 *
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is Play-policy restricted to apps whose core
 * function genuinely requires it — an alarm clock qualifies (`IMPORTANT_BACKGROUND_FUNCTION`).
 * If the OEM removed the dialog we fall back to the generic settings list so the user is never
 * dropped onto an ActivityNotFoundException.
 */
@SuppressLint("BatteryLife")
fun Context.ignoreBatteryOptimizationsIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:$packageName"))

fun Context.exactAlarmSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
    } else {
        appDetailsSettingsIntent()
    }

fun Context.appNotificationSettingsIntent(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

fun Context.appDetailsSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:$packageName"))
