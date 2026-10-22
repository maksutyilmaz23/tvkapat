package com.mesut.tvkapat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PowerAccessibilityService? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var running = false
    private var clickedOnce = false
    private val seen = LinkedHashSet<String>()

    private val primary = listOf("kapat", "power off", "shut down", "shutdown", "switch off", "turn off", "poweroff")
    private val confirm = listOf("tamam", "ok", "evet", "yes", "onayla", "confirm")
    private val exclude = listOf("ekran", "screen", "restart", "yeniden", "reboot", "iptal", "cancel")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun startPowerOff() {
        if (running) return
        running = true
        attempts = 0
        clickedOnce = false
        seen.clear()
        val opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        seen.add("Güç menüsü komutu: ${if (opened) "gönderildi" else "REDDEDİLDİ"}")
        saveLog()
        h.postDelayed(step, 800)
    }

    private val step = object : Runnable {
        override fun run() {
            attempts++
            if (attempts > 25) {
                running = false
                // Güç menüsü bulunamadı: en azından ekranı kilitle
                if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                return
            }
            val roots = mutableListOf<AccessibilityNodeInfo>()
            try {
                windows?.forEach { w -> w.root?.let { roots.add(it) } }
            } catch (_: Exception) {
            }
            rootInActiveWindow?.let { roots.add(it) }

            seen.add("pencere sayısı: ${roots.size}")
            for (r in roots) collect(r, 0)
            saveLog()
            var clicked = false
            for (r in roots) {
                if (r.packageName?.toString() == packageName) continue
                if (clickMatch(r)) {
                    clicked = true
                    clickedOnce = true
                    break
                }
            }
            h.postDelayed(this, if (clicked) 1200L else 600L)
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > 25 || seen.size > 80) return
        val t = node.text?.toString() ?: node.contentDescription?.toString()
        if (!t.isNullOrBlank()) seen.add("${node.packageName}: $t [${if (node.isClickable) "tık" else "-"}]")
        for (i in 0 until node.childCount) collect(node.getChild(i), depth + 1)
    }

    private fun saveLog() {
        Prefs.setLog(this, "Deneme $attempts\n" + seen.joinToString("\n"))
    }

    private fun variants(s: CharSequence?): List<String> {
        if (s == null) return emptyList()
        val t = s.toString()
        return listOf(t.lowercase(Locale.ROOT), t.lowercase(Locale("tr")))
    }

    private fun matches(node: AccessibilityNodeInfo): Boolean {
        val vs = variants(node.text) + variants(node.contentDescription)
        if (vs.isEmpty()) return false
        if (vs.any { v -> exclude.any { v.contains(it) } }) return false
        if (vs.any { v -> primary.any { v.contains(it) } }) return true
        if (clickedOnce && vs.any { v -> confirm.any { v == it } }) return true
        return false
    }

    private fun clickMatch(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (matches(node)) {
            var n: AccessibilityNodeInfo? = node
            var depth = 0
            while (n != null && depth < 6) {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                n = n.parent
                depth++
            }
            if (Build.VERSION.SDK_INT >= 24) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (!r.isEmpty) {
                    val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
                    val g = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
                    dispatchGesture(g, null, null)
                    seen.add("dokunuş: ${node.text ?: node.contentDescription}")
                    return true
                }
            }
        }
        for (i in 0 until node.childCount) {
            if (clickMatch(node.getChild(i))) return true
        }
        return false
    }
}
