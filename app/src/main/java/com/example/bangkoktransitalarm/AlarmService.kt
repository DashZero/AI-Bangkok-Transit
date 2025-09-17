package com.example.bangkoktransitalarm

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.util.Log
import android.net.Uri
import android.media.RingtoneManager
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import android.media.AudioManager


class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val NOTIFICATION_CHANNEL_ID = "AlarmChannel"
    private val NOTIFICATION_ID = 123
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        Log.d("AlarmService", "Service created")

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for alarm notifications"
            }
            val notificationManager: NotificationManager = 
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Prepare media player for alarm sound
        val alarmSoundUriString = sharedPreferences.getString("alarm_sound", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
        val alarmUri: Uri = Uri.parse(alarmSoundUriString)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, alarmUri)
            setLooping(true) // Loop the alarm sound
            prepare()
        }

        // Prepare vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AlarmService", "Service started")

        val geofenceTransitionDetails = intent?.getStringExtra("geofenceTransitionDetails") ?: "Approaching destination!"

        // Get settings from SharedPreferences
        val vibrateAlarm = sharedPreferences.getBoolean("vibrate_alarm", true)
        val alarmVolume = sharedPreferences.getInt("alarm_volume", 100) // Default to 100%

        // Adjust volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val desiredVolume = (maxVolume * (alarmVolume / 100.0)).toInt()

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, desiredVolume, 0)

        // Start alarm sound
        mediaPlayer?.start()

        // Start vibration
        if (vibrateAlarm) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0)) // Vibrate indefinitely
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0) // Vibrate indefinitely
            }
        }


        // Show persistent notification
        val notificationIntent = Intent(this, AlarmActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Destination Alert!")
            .setContentText(geofenceTransitionDetails)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a default icon for now
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY // Service will be restarted if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AlarmService", "Service destroyed")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
        // Restore original volume if needed (optional, depends on UX)
        // audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0) // This line is commented out
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}