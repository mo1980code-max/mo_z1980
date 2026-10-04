package com.digitalclockpro.alarm.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Counts discrete shakes from the accelerometer. A shake registers when the net acceleration
 * exceeds [THRESHOLD_G] g and the previous shake was at least [DEBOUNCE_MS] ago.
 */
class ShakeDetector(
    context: Context,
    private val onShake: (count: Int) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var count = 0
    private var lastShakeAt = 0L

    val isSupported: Boolean get() = accelerometer != null

    fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sensorManager.unregisterListener(this)

    fun reset() { count = 0 }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce <= THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < DEBOUNCE_MS) return
        lastShakeAt = now
        count++
        onShake(count)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD_G = 2.2f
        const val DEBOUNCE_MS = 220L
    }
}
