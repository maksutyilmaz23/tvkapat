package com.mesut.tvkapat

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

object ShutdownHelper {

    fun runSu(cmd: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        p.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    fun hasRoot(): Boolean = runSu("id")

    /** Önce root, olmazsa (veya 8 sn içinde sonuç gelmezse) erişilebilirlik yöntemi. */
    fun powerOff() {
        val main = Handler(Looper.getMainLooper())
        val fallbackStarted = AtomicBoolean(false)
        val fallback = Runnable {
            if (fallbackStarted.compareAndSet(false, true)) {
                PowerAccessibilityService.instance?.startPowerOff()
            }
        }
        main.postDelayed(fallback, 8000)
        Thread {
            val cmds = listOf(
                "reboot -p",
                "svc power shutdown",
                "/system/bin/reboot -p",
                "toybox reboot -p",
                "setprop sys.powerctl shutdown"
            )
            for (c in cmds) {
                if (runSu(c)) return@Thread
            }
            main.removeCallbacks(fallback)
            main.post(fallback)
        }.start()
    }
}
