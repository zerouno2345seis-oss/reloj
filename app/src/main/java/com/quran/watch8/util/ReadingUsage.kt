package com.quran.watch8.util

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Minutes spent reading in each display style and font, this week only, kept
 * on the watch. Settings shows the most used pair so the owner can see which
 * one he actually reads with before settling on it.
 *
 * Stored as {"week":"2026-W43","seconds":{"fill|default":1800}}; a new week
 * starts from zero.
 */
object ReadingUsage {
    data class Top(val mode: String, val font: String, val seconds: Long)

    fun weekOf(date: LocalDate): String =
        "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    fun add(json: String, mode: String, font: String, seconds: Long, today: LocalDate): String {
        val week = weekOf(today)
        val old = runCatching { JSONObject(json) }.getOrNull()
        val counts = old?.takeIf { it.optString("week") == week }?.optJSONObject("seconds") ?: JSONObject()
        val key = "$mode|$font"
        counts.put(key, counts.optLong(key) + seconds)
        return JSONObject().put("week", week).put("seconds", counts).toString()
    }

    fun top(json: String, today: LocalDate): Top? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (obj.optString("week") != weekOf(today)) return null
        val counts = obj.optJSONObject("seconds") ?: return null
        var best: Top? = null
        for (key in counts.keys()) {
            val secs = counts.optLong(key)
            val (mode, font) = key.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (best == null || secs > best.seconds) best = Top(mode, font, secs)
        }
        return best?.takeIf { it.seconds >= 60 }
    }
}
