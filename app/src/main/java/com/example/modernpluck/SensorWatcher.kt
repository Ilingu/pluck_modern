package com.example.modernpluck

import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.properties.Delegates

class SensorWatcher: Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var threshold by Delegates.notNull<Float>()
    private var coolDown = 0L


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        // fetch threshold
        val sharedPref = this.getSharedPreferences(getString(R.string.spreference_key), Context.MODE_PRIVATE)
        threshold = sharedPref.getFloat(getString(R.string.threshold_key), 40.0F)

        // listen to sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION).also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_NORMAL)
        }

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            Actions.START.toString() -> start()
            Actions.STOP.toString() -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start() {
        val notification = NotificationCompat.Builder(this, "background_running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ModernPluck protection is active")
            .setContentText("Currently watching sensor data and ready to lock phone if a thief comes by")
            .build()
        startForeground(1, notification)
    }

    private fun stop() {
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION).also { accelerometer ->
            sensorManager.unregisterListener(this, accelerometer)
        }
        stopSelf()
    }

    enum class Actions {
        START, STOP
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val netacc = event.values.map { abs(it) }.sum()
        Log.d("BG_SENSOR", "curr acceleration: $netacc")
    }

    // main logic of the app: lock if the threshold is exceeded, 1 line -_-
    private fun fakeLockIfThresholdExceeded(curr_acc: Float) {
        if (System.currentTimeMillis() >= coolDown && curr_acc >= threshold) {
            Toast.makeText(this, "False Lock! $threshold", Toast.LENGTH_SHORT).show()
            coolDown = System.currentTimeMillis() + 2000
        }
    }

}