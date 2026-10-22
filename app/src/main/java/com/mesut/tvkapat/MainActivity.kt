package com.mesut.tvkapat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var permTv: TextView
    private lateinit var activeTv: TextView
    private lateinit var cancelBtn: Button
    private lateinit var minutesTv: TextView
    private lateinit var durationBox: LinearLayout
    private lateinit var timePicker: TimePicker
    private lateinit var modeDurBtn: Button
    private lateinit var modeClockBtn: Button
    private var mode = 0
    private var minutes = 60
    @Volatile private var rootOk = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
        setPadding(0, dp(8), 0, dp(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        col.addView(text("TV Kapat", 28f))

        permTv = text("", 16f)
        col.addView(permTv)
        col.addView(row(
            btn("Üstte gösterme izni") { openOverlaySettings() },
            btn("Erişilebilirlik ayarları") { openAccessibilitySettings() }
        ))

        col.addView(text("Kapanış zamanı", 20f))
        modeDurBtn = btn("Süre ile") { setMode(0) }
        modeClockBtn = btn("Saat ile") { setMode(1) }
        col.addView(row(modeDurBtn, modeClockBtn))

        durationBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        minutesTv = text("", 32f).apply { textAlignment = View.TEXT_ALIGNMENT_CENTER }
        durationBox.addView(minutesTv)
        durationBox.addView(row(
            btn("− 5 dk") { minutes = (minutes - 5).coerceAtLeast(1); showMinutes() },
            btn("+ 5 dk") { minutes = (minutes + 5).coerceAtMost(720); showMinutes() }
        ))
        durationBox.addView(row(
            btn("1 dk") { minutes = 1; showMinutes() },
            btn("15 dk") { minutes = 15; showMinutes() },
            btn("30 dk") { minutes = 30; showMinutes() }
        ))
        durationBox.addView(row(
            btn("60 dk") { minutes = 60; showMinutes() },
            btn("90 dk") { minutes = 90; showMinutes() },
            btn("120 dk") { minutes = 120; showMinutes() }
        ))
        col.addView(durationBox)

        timePicker = TimePicker(this).apply {
            setIs24HourView(true)
            visibility = View.GONE
        }
        col.addView(timePicker)

        col.addView(btn("BAŞLAT") { start() }.apply { textSize = 22f })

        activeTv = text("", 18f)
        col.addView(activeTv)
        cancelBtn = btn("Zamanlayıcıyı iptal et") {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_CANCEL))
            Prefs.clear(this)
            refresh()
        }
        col.addView(cancelBtn)

        setContentView(ScrollView(this).apply { addView(col) })

        showMinutes()
        setMode(0)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Thread { rootOk = ShutdownHelper.hasRoot() }.start()
    }

    override fun onResume() {
        super.onResume()
        h.post(object : Runnable {
            override fun run() {
                refresh()
                h.postDelayed(this, 1000)
            }
        })
    }

    override fun onPause() {
        h.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun showMinutes() {
        val hh = minutes / 60
        val mm = minutes % 60
        minutesTv.text = if (hh > 0) "$minutes dk  ($hh sa ${mm} dk)" else "$minutes dk"
    }

    private fun setMode(m: Int) {
        mode = m
        durationBox.visibility = if (m == 0) View.VISIBLE else View.GONE
        timePicker.visibility = if (m == 1) View.VISIBLE else View.GONE
        modeDurBtn.alpha = if (m == 0) 1f else 0.5f
        modeClockBtn.alpha = if (m == 1) 1f else 0.5f
    }

    private fun overlayOk() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun accEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains("$packageName/")
    }

    private fun mark(b: Boolean) = if (b) "✓" else "✗"

    private fun refresh() {
        permTv.text = "Üstte gösterme izni: ${mark(overlayOk())}\n" +
            "Erişilebilirlik servisi: ${mark(accEnabled())}\n" +
            "Root: ${mark(rootOk)}\n" +
            "(Uyarı için izin veya erişilebilirlik; kapatma için root veya erişilebilirlik gerekir)"
        val end = Prefs.endAt(this)
        val now = System.currentTimeMillis()
        if (end > now) {
            val rem = (end - now) / 1000
            val at = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(end))
            activeTv.text = "AKTİF: $at'de kapanacak (kalan %d:%02d:%02d)".format(rem / 3600, (rem % 3600) / 60, rem % 60)
            cancelBtn.visibility = View.VISIBLE
        } else {
            activeTv.text = "Zamanlayıcı kapalı"
            cancelBtn.visibility = View.GONE
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Toast.makeText(this, "Bu cihazda izin ekranı yok. README'deki ADB komutunu kullan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Ayar ekranı açılamadı. README'deki ADB komutunu kullan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun start() {
        val acc = accEnabled()
        if (!overlayOk() && !acc) {
            Toast.makeText(this, "Uyarı ekranı için 'üstte gösterme' izni veya erişilebilirlik gerekli", Toast.LENGTH_LONG).show()
            return
        }
        if (!rootOk && !acc) {
            Toast.makeText(this, "Kapatma için root veya erişilebilirlik servisi gerekli", Toast.LENGTH_LONG).show()
            return
        }
        val endAt: Long = if (mode == 0) {
            System.currentTimeMillis() + minutes * 60_000L
        } else {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, timePicker.currentHour)
                set(Calendar.MINUTE, timePicker.currentMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= System.currentTimeMillis() + 35_000) c.add(Calendar.DAY_OF_MONTH, 1)
            c.timeInMillis
        }
        val i = Intent(this, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_END, endAt)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endAt))
        Toast.makeText(this, "Başladı: $at'de kapanacak", Toast.LENGTH_LONG).show()
        finish()
    }
}
