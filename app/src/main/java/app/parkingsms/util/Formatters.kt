package app.parkingsms.util

import java.util.Locale

object Formatters {
    /** 3725000 ms -> "01:02:05". */
    fun clock(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
    }
}
