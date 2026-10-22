package com.mesut.tvkapat

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("tvkapat", Context.MODE_PRIVATE)
    fun endAt(c: Context): Long = p(c).getLong("end_at", 0L)
    fun setEndAt(c: Context, v: Long) = p(c).edit().putLong("end_at", v).apply()
    fun clear(c: Context) = p(c).edit().remove("end_at").apply()
}
