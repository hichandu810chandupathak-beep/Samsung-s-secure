package com.ssecure.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AppLockService : Service() {

    private lateinit var prefManager: PrefManager
    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 350L

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (prefManager.isServiceEnabled()) {
                checkForegroundApp()
            }
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        startForegroundNotification()
        handler.post(monitorRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "s_secure_protection_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "S Secure Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun checkForegroundApp() {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000L

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var topPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topPackage = event.packageName
            }
        }

        val currentPkg = topPackage ?: return

        if (currentPkg == packageName) {
            return
        }

        val lastUnlocked = prefManager.getLastUnlockedPkg()
        if (lastUnlocked.isNotEmpty() && currentPkg != lastUnlocked) {
            prefManager.setLastUnlockedPkg("")
        }

        if (prefManager.isAppLocked(currentPkg) && prefManager.getLastUnlockedPkg() != currentPkg) {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LockActivity.EXTRA_PACKAGE_NAME, currentPkg)
                putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_LOCK)
            }
            startActivity(lockIntent)
        }
    }
}
