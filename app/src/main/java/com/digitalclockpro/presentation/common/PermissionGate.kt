package com.digitalclockpro.presentation.common

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Surfaces the three permissions that silently break alarms if missing:
 * POST_NOTIFICATIONS (13+), SCHEDULE_EXACT_ALARM (12+) and battery-optimisation exemption.
 */
@Composable
fun AlarmPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(context.hasNotificationPermission()) }
    var exactGranted by remember { mutableStateOf(context.canScheduleExactAlarms()) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { exactGranted = context.canScheduleExactAlarms() }

    if (notificationsGranted && exactGranted) return

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Your alarms may not ring", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row {
                if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    TextButton(onClick = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("Allow notifications") }
                }
                if (!exactGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(onClick = {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Allow exact alarms") }
                }
            }
        }
    }
}

fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

fun Context.canScheduleExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
