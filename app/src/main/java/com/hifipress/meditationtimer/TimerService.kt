package com.hifipress.meditationtimer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps the session alive and owns the bell.
 *
 * This is the piece the PWA could never have. An exact AlarmManager alarm
 * is registered with the OS, so the bell fires on time whether or not this
 * process is scheduled, the screen is on, or the webview has been frozen.
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_STOP  = "stop"
        const val ACTION_RING  = "ring"
        const val EXTRA_END_AT = "endAt"
        const val EXTRA_INTERVAL = "interval"
        private const val CHANNEL_ID = "meditation_session"
        private const val NOTIF_ID = 1
    }

    private var intervalMs: Long = 5 * 60 * 1000
    private var nextBellAt: Long = 0
    private var bells = 0
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                nextBellAt = intent.getLongExtra(EXTRA_END_AT, 0)
                intervalMs = intent.getLongExtra(EXTRA_INTERVAL, intervalMs)
                startForegroundCompat(notification("Session in progress"))
                acquireWakeLock()
                scheduleBell(nextBellAt)
            }
            ACTION_PAUSE -> {
                cancelBell()
                notifyUpdate("Paused")
            }
            ACTION_RING -> {
                bells++
                ringGong()
                // Open-ended by design: reschedule for the next reminder
                // interval and keep going until the session is ended.
                nextBellAt += intervalMs
                scheduleBell(nextBellAt)
                notifyUpdate("Extending · bell $bells")
            }
            ACTION_STOP -> {
                cancelBell()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── Alarm scheduling ──────────────────────────────────────────────

    private fun alarmIntent(): PendingIntent {
        val i = Intent(this, BellReceiver::class.java).setAction(ACTION_RING)
        return PendingIntent.getBroadcast(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleBell(atMs: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setExactAndAllowWhileIdle survives Doze. This is the whole point.
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, alarmIntent())
    }

    private fun cancelBell() {
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent())
    }

    // ── Audio ─────────────────────────────────────────────────────────

    private fun ringGong() {
        player?.release()
        player = MediaPlayer.create(this, R.raw.gong)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_ALARM so the bell is heard through Do Not Disturb,
                    // which is very likely on during a sit.
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setOnCompletionListener { it.release(); player = null }
            start()
        }
    }

    // ── Foreground notification ───────────────────────────────────────

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Meditation session", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the timer running and rings the bell"
            setShowBadge(false)
            setSound(null, null)
        }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyUpdate(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification(text))
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ── Wake lock ─────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "MeditationTimer::session"
        ).apply { acquire(4 * 60 * 60 * 1000L) }  // hard ceiling: 4 hours
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        cancelBell()
        releaseWakeLock()
        player?.release()
        player = null
        super.onDestroy()
    }
}
