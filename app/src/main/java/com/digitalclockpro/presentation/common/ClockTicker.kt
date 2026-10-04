package com.digitalclockpro.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/**
 * Emits the current time once per second (or per minute) – but only while the composable is in
 * the composition, so the dashboard clock stops ticking the moment the app is backgrounded.
 */
@Composable
fun rememberCurrentTime(withSeconds: Boolean = true): State<ZonedDateTime> =
    produceState(initialValue = ZonedDateTime.now(), withSeconds) {
        while (true) {
            value = ZonedDateTime.now()
            val nowMillis = System.currentTimeMillis()
            val step = if (withSeconds) 1_000L else 60_000L
            delay(step - (nowMillis % step))
        }
    }

@Composable
fun rememberStableNow(): ZonedDateTime = remember { mutableStateOf(ZonedDateTime.now()) }.value
