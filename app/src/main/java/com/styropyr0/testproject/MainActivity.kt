package com.styropyr0.testproject

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.styropyr0.kairos.KairosTimeSlot
import java.util.Calendar
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("testProject", MODE_PRIVATE)
        editor = sharedPreferences.edit()

        askIgnoreOptimization()

        findViewById<Button>(R.id.button).setOnClickListener {
            if (!sharedPreferences.getBoolean("isScheduled", false)) {
                editor.putBoolean("isScheduled", true)
                editor.apply()

                // Schedule event after 1 minute
                scheduleEventAt(Calendar.getInstance().apply { add(Calendar.MINUTE, 1) })
            } else Toast.makeText(this, "Event already scheduled.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun scheduleEventAt(time: Calendar) {
        val event = SampleEvent("SAMPLE_EVENT", KairosTimeSlot(time))
        SampleEventScheduler(this).addToSchedule(event)
    }

    private fun askIgnoreOptimization() {
        if (!isIgnoringBatteryOptimizations()) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:$packageName".toUri()
                startActivity(this)
            }
        }
    }

    private fun isIgnoringBatteryOptimizations() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
}