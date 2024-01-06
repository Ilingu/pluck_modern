package com.example.modernpluck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.modernpluck.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var sensorManager: SensorManager

    private var threshold by Delegates.notNull<Float>()

    private var coolDown = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // rendering view or whatever
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // permissions handling
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE), 0)
        }

        // fetch threshold
        val sharedPref = this.getSharedPreferences(getString(R.string.spreference_key), Context.MODE_PRIVATE)
        threshold = sharedPref.getFloat(getString(R.string.threshold_key), 40.0F)
        findViewById<EditText>(R.id.threshold_input).setText(threshold.toString())

        // register to sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION).also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }

        // add logics to buttons
        findViewById<Button>(R.id.start_service_btn)
            .setOnClickListener {
                Intent(applicationContext, SensorWatcher::class.java).also {
                    it.action = SensorWatcher.Actions.START.toString()
                    startService(it)
                }
            }
        findViewById<Button>(R.id.stop_service_btn)
            .setOnClickListener {
                Intent(applicationContext, SensorWatcher::class.java).also {
                    it.action = SensorWatcher.Actions.STOP.toString()
                    startService(it)
                }
            }
        findViewById<EditText>(R.id.threshold_input)
            .onSubmit { view ->
                val new_threshold = view.text.toString().toFloatOrNull()
                Log.d("INPUT", "new threshold: $new_threshold")
                if(new_threshold == null || new_threshold < 30.0F || new_threshold > 100.0F){
                    Toast.makeText(this, "Couldn't set threshold ($new_threshold), too low/high", Toast.LENGTH_SHORT).show()
                    return@onSubmit
                }

                with(sharedPref.edit()) {
                    putFloat(getString(R.string.threshold_key), new_threshold)
                    apply()
                }
                threshold=new_threshold
                Toast.makeText(this, "New threshold ($new_threshold) set successfully", Toast.LENGTH_SHORT).show()
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // unregister from sensor
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION).also { accelerometer ->
            sensorManager.unregisterListener(this, accelerometer)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val curr_acc = event.values.map { abs(it) }.sum()
        fakeLockIfThresholdExceeded(curr_acc)
        Log.d("FG_SENSOR", "curr acceleration: $curr_acc")
    }

    // main logic of the app: lock if the threshold is exceeded, 1 line -_-
    private fun fakeLockIfThresholdExceeded(curr_acc: Float) {
        if (System.currentTimeMillis() >= coolDown && curr_acc >= threshold) {
            Toast.makeText(this, "False Lock! $threshold", Toast.LENGTH_SHORT).show()
            coolDown = System.currentTimeMillis() + 2000
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}

fun EditText.onSubmit(func: (view: TextView) -> Unit) {
    setOnEditorActionListener { view, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            func(view)
        }
        true
    }
}
