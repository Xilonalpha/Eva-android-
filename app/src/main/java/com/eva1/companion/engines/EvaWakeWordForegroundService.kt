package com.eva1.companion.engines

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * User-visible lifecycle anchor for EVA's microphone-based local wake-word detector.
 * The Sherpa AudioRecord/KWS loop remains owned by VoiceAssistantEngine; this service
 * keeps Android informed that the user explicitly enabled hands-free microphone use.
 * Start it only from a visible activity after RECORD_AUDIO has been granted.
 */
class EvaWakeWordForegroundService : Service() {
    companion object {
        private const val TAG = "EvaWakeWordFGS"
        private const val CHANNEL_ID = "eva_wake_word_microphone"
        private const val NOTIFICATION_ID = 2401
        private const val ACTION_START = "com.aistudio.eva1companion.action.START_WAKE_WORD"
        private const val ACTION_STOP = "com.aistudio.eva1companion.action.STOP_WAKE_WORD"

        fun start(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) return false

            val intent = Intent(context, EvaWakeWordForegroundService::class.java).setAction(ACTION_START)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start microphone foreground service", e)
                false
            }
        }

        fun stop(context: Context) {
            // Do not call startService() just to stop a microphone FGS: Android may
            // reject background service starts. Stop the existing service directly.
            try {
                context.stopService(Intent(context, EvaWakeWordForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop wake-word foreground service", e)
            }
        }

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted; stopping wake-word foreground service")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Android rejected microphone foreground service", e)
            stopSelf(startId)
            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote wake-word service", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EVA – ascultare Hey Siri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificare persistentă cât timp detectorul vocal local EVA este activ."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            2402,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("EVA – mod Hands-Free activ")
            .setContentText("Serviciul de microfon este activ; detectarea vocală este gestionată separat de EVA.")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
