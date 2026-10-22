package com.mesut.tvkapat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.mesut.tvkapat.START"
        const val ACTION_CANCEL = "com.mesut.tvkapat.CANCEL"
        const val EXTRA_END = "end_at"
        const val WARN_SEC = 30
        const val SNOOZE_MIN = 15
        private const val NOTIF_ID = 42
        private const val CHANNEL = "timer"
    }

    private val h = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var warning = false
    private var fired = false
    private var overlay: WarningOverlay? = null
    private var wl: PowerManager.WakeLock? = null
    private var lastNotif = 0L

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            h.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelAll()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                endAt = intent.getLongExtra(EXTRA_END, 0L)
                Prefs.setEndAt(this, endAt)
                fired = false
                warning = false
            }
            else -> endAt = Prefs.endAt(this)
        }
        if (endAt <= System.currentTimeMillis() && !fired) {
            cancelAll()
            return START_NOT_STICKY
        }
        startFg(buildNotification())
        if (wl == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvkapat:timer").also { it.acquire() }
        }
        h.removeCallbacks(tick)
        h.post(tick)
        return START_STICKY
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun onTick() {
        if (fired) return
        val rem = endAt - System.currentTimeMillis()
        if (rem <= 0) {
            fire()
            return
        }
        if (rem <= WARN_SEC * 1000L) {
            if (!warning) {
                warning = true
                overlay = WarningOverlay(this, { cancelAll() }, { snooze() })
            }
            val ov = overlay
            if (ov != null) {
                ov.show()
                ov.update((rem + 999) / 1000)
            }
        } else {
            if (warning) {
                warning = false
                overlay?.hide()
                overlay = null
            }
            val now = System.currentTimeMillis()
            if (now - lastNotif > 30_000) {
                lastNotif = now
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIF_ID, buildNotification())
            }
        }
    }

    private fun fire() {
        fired = true
        h.removeCallbacks(tick)
        overlay?.hide()
        overlay = null
        Prefs.clear(this)
        ShutdownHelper.powerOff()
        h.postDelayed({ cancelAll() }, 45_000)
    }

    private fun snooze() {
        endAt = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        Prefs.setEndAt(this, endAt)
        warning = false
        overlay?.hide()
        overlay = null
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun cancelAll() {
        h.removeCallbacksAndMessages(null)
        overlay?.hide()
        overlay = null
        Prefs.clear(this)
        wl?.let { if (it.isHeld) it.release() }
        wl = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Kapatma zamanlayıcı", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val cancelPi = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val at = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(endAt))
        b.setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentTitle("Otomatik kapatma aktif")
            .setContentText("$at'de cihaz kapanacak")
            .setOngoing(true)
            .setContentIntent(openPi)
        @Suppress("DEPRECATION")
        b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal", cancelPi)
        return b.build()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        overlay?.hide()
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
