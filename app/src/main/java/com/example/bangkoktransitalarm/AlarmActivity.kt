package com.example.bangkoktransitalarm

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm) // We will create this layout next

        val stopAlarmButton: Button = findViewById(R.id.stopAlarmButton)
        stopAlarmButton.setOnClickListener {
            stopAlarm()
        }

        val snoozeAlarmButton: Button = findViewById(R.id.snoozeAlarmButton)
        snoozeAlarmButton.setOnClickListener {
            snoozeAlarm()
        }
    }

    private fun stopAlarm() {
        val stopIntent = Intent(this, AlarmService::class.java)
        stopService(stopIntent)
        finish() // Close the alarm activity
    }

    private fun snoozeAlarm() {
        // Implement snooze logic here (e.g., set a new alarm for a few minutes later)
        // For now, it will just stop the alarm and close the activity
        stopAlarm()
    }
}