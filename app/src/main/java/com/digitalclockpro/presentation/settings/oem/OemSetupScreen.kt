package com.digitalclockpro.presentation.settings.oem

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.OemPowerSettings
import com.digitalclockpro.presentation.common.ignoreBatteryOptimizationsIntent
import com.digitalclockpro.presentation.common.isIgnoringBatteryOptimizations

/**
 * "Make alarms reliable on <vendor>" guide.
 *
 * None of these vendor restrictions can be read or toggled through a public API, so the screen is
 * a checklist: each step explains what to do, deep-links straight into the vendor Activity when it
 * resolves on this ROM, and lets the user tick it off (persisted in DataStore).
 */
@Composable
fun OemSetupScreen(viewModel: OemSetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val completed by viewModel.completedSteps.collectAsStateWithLifecycle()
    val actions = remember { OemPowerSettings.actionsFor(context) }

    var batteryExempt by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = context.isIgnoringBatteryOptimizations() }

    fun open(intent: Intent?) {
        intent ?: return
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // ROM advertised the component but refuses the launch (some MIUI builds).
            runCatching { launcher.launch(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (OemPowerSettings.requiresManualWhitelisting)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BatteryAlert, contentDescription = null)
                        Text(
                            "  ${OemPowerSettings.vendor.displayName} (${OemPowerSettings.romName})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (OemPowerSettings.requiresManualWhitelisting) {
                            "This ROM runs its own app killer on top of Android. Until the steps " +
                                "below are done, your alarms can be cancelled while the screen " +
                                "is off — even with exact alarms granted."
                        } else {
                            "No vendor-specific restrictions detected. The optional steps below " +
                                "can still make alarms more resilient."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        OemPowerSettings.VERSION_VARIANCE_WARNING,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            StatusRow(
                label = "Android battery optimization (optional)",
                satisfied = batteryExempt,
                actionLabel = "Allow",
                // Only ever launched from this explicit tap – never automatically on screen entry.
                onAction = { open(context.ignoreBatteryOptimizationsIntent()) }
            )
        }

        items(actions, key = { it.id }) { action ->
            OemStepCard(
                action = action,
                checked = action.id in completed,
                onCheckedChange = { viewModel.setStepCompleted(action.id, it) },
                onOpen = { open(action.intent) }
            )
        }

        item {
            Text(
                "Tip: after finishing, set a test alarm 2 minutes ahead, lock the phone and put it " +
                    "aside. If it rings, your device is configured correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    satisfied: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (satisfied) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Text("  $label")
            }
            if (!satisfied) Button(onClick = onAction) { Text(actionLabel) }
            else Text("Granted", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OemStepCard(
    action: OemPowerSettings.OemAction,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (checked) TextDecoration.LineThrough else null
                )
            }
            Text(action.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                action.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (action.available) {
                OutlinedButton(onClick = onOpen) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text("  Open settings")
                }
            } else {
                Text(
                    "This screen is not available on your ROM version — follow the steps manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
