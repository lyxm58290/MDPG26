package com.example.mdpg26.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mdpg26.R
import com.example.mdpg26.ui.main.MainActivity

/**
 * Holds a persistent notification for as long as a Bluetooth link is up. Without any
 * foreground service, this app is just a regular background process once it's out of view —
 * Android eventually reclaims it to free memory (more aggressively on some OEM skins), which
 * kills the BluetoothSocket along with it. That looks like the connection "timing out" after
 * sitting idle, but it's the process dying, not a protocol or app-level timeout. Running as a
 * foreground service is what tells the OS this process is doing something that matters.
 */
class BluetoothForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        ensureChannel()
        val notification = buildNotification(deviceName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bt_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.bt_service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(deviceName: String?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (deviceName != null) {
            getString(R.string.bt_service_notification_text_fmt, deviceName)
        } else {
            getString(R.string.bt_service_notification_text_generic)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bt_service_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "bluetooth_link"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun start(context: Context, deviceName: String?) {
            val intent = Intent(context, BluetoothForegroundService::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothForegroundService::class.java))
        }
    }
}
