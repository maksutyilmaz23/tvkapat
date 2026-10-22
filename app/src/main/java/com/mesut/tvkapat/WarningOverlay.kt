package com.mesut.tvkapat

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class WarningOverlay(
    private val ctx: Context,
    private val onCancel: () -> Unit,
    private val onSnooze: () -> Unit
) {
    private var wm: WindowManager? = null
    private var root: View? = null
    private var countTv: TextView? = null

    private class KeyFrame(c: Context, val onBack: () -> Unit) : FrameLayout(c) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun show(): Boolean {
        if (root != null) return true
        val canDraw = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)
        val acc = PowerAccessibilityService.instance
        val manager: WindowManager
        val type: Int
        if (canDraw) {
            manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        } else if (acc != null && Build.VERSION.SDK_INT >= 22) {
            manager = acc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else return false

        val frame = KeyFrame(ctx) { onCancel() }
        frame.setBackgroundColor(Color.parseColor("#E6000000"))

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        val title = TextView(ctx).apply {
            text = "Cihaz kapanıyor"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val count = TextView(ctx).apply {
            text = "30"
            textSize = 96f
            setTextColor(Color.parseColor("#FF5252"))
            gravity = Gravity.CENTER
        }
        val hint = TextView(ctx).apply {
            text = "Kapanmasını istemiyorsan müdahale et"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val cancel = Button(ctx).apply {
            text = "Vazgeç (kapatma)"
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onCancel() }
        }
        val snooze = Button(ctx).apply {
            text = "15 dk ertele"
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onSnooze() }
        }
        box.addView(title)
        box.addView(count)
        box.addView(hint)
        box.addView(cancel, LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
        box.addView(snooze, LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        frame.addView(box, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        return try {
            manager.addView(frame, lp)
            wm = manager
            root = frame
            countTv = count
            cancel.requestFocus()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun update(sec: Long) {
        countTv?.text = sec.toString()
    }

    fun hide() {
        try {
            root?.let { wm?.removeView(it) }
        } catch (_: Exception) {
        }
        root = null
        wm = null
        countTv = null
    }
}
