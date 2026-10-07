package com.theblacksheep.appoff.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class HighRateSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    fun start() {
        try {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                Log.d("HighRateSensorManager", "Accelerometer registered at high sampling rate")
            }
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                Log.d("HighRateSensorManager", "Gyroscope registered at high sampling rate")
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                Log.d("HighRateSensorManager", "Magnetometer registered at high sampling rate")
            }
            gravity?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                Log.d("HighRateSensorManager", "Gravity registered at high sampling rate")
            }
        } catch (e: Exception) {
            Log.e("HighRateSensorManager", "Error registering sensor listeners", e)
        }
    }

    fun stop() {
        try {
            sensorManager.unregisterListener(this)
            Log.d("HighRateSensorManager", "Listeners unregistered")
        } catch (e: Exception) {
            Log.e("HighRateSensorManager", "Error unregistering sensor listeners", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // In a real scenario, we would process this data.
            // For now, we just log a throttled message to avoid log spamming
            // while confirming high-rate data is arriving.
            // Log.v("HighRateSensorManager", "Sensor ${it.sensor.name} value: ${it.values[0]}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("HighRateSensorManager", "Accuracy changed for ${sensor?.name}: $accuracy")
    }
}
