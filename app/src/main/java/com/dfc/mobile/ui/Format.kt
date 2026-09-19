package com.dfc.mobile.ui

import com.dfc.mobile.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Labels for the library view. Dates are formatted in the phone's own locale and
 * time zone from the timestamp MediaStore recorded, so nothing here is derived
 * or estimated.
 */

/** Calendar-day difference between two instants, rounded so DST cannot shift it. */
private fun daysApart(fromMillis: Long, toMillis: Long): Long {
    val a = Calendar.getInstance().apply {
        timeInMillis = fromMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val b = Calendar.getInstance().apply {
        timeInMillis = toMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return Math.round((a.timeInMillis - b.timeInMillis) / 86_400_000.0)
}

/**
 * Heading for one day of the timeline. Recent days read as words because that is
 * how people refer to them; everything older carries the date, and the year only
 * appears once it is no longer the current one.
 */
fun dayLabel(epochSeconds: Long, now: Long = System.currentTimeMillis()): String {
    if (epochSeconds <= 0L) return "Undated"
    val millis = epochSeconds * 1000
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val locale = Locale.getDefault()
    val days = daysApart(now, millis)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days in 2L..6L -> SimpleDateFormat("EEEE, d MMMM", locale).format(Date(millis))
        cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) ->
            SimpleDateFormat("d MMMM", locale).format(Date(millis))
        else -> SimpleDateFormat("d MMMM yyyy", locale).format(Date(millis))
    }
}

/** Full date for the viewer's metadata line. */
fun fullDateLabel(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Date not recorded"
    return SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(epochSeconds * 1000))
}

/** Video runtime as a clock, which is what a grid badge has room for. */
fun clockDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val total = durationMs / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun itemCount(n: Int): String = if (n == 1) "1 item" else "$n items"

/** A day of the timeline: its heading, the instant it starts at, and its items. */
data class DaySection(
    val dayStart: Long,
    val label: String,
    val items: List<MediaItem>,
) {
    val videos: Int get() = items.count { it.isVideo }
}

/**
 * Split an already newest-first list into day sections, preserving order. The
 * key is the start of the day, not the label, so two days that format alike can
 * never collide as list keys.
 */
fun groupByDays(items: List<MediaItem>): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val sections = ArrayList<DaySection>()
    var currentStart = Long.MIN_VALUE
    var current = ArrayList<MediaItem>()

    fun flush() {
        if (current.isNotEmpty()) {
            val first = current.first()
            sections.add(
                DaySection(
                    dayStart = if (currentStart == Long.MIN_VALUE) first.dateTaken else currentStart,
                    label = dayLabel(first.dateTaken),
                    items = current,
                )
            )
            current = ArrayList()
        }
    }

    for (item in items) {
        val start = dayStartOf(item.dateTaken)
        if (start != currentStart) {
            flush()
            currentStart = start
        }
        current.add(item)
    }
    flush()
    return sections
}

private fun dayStartOf(epochSeconds: Long): Long {
    if (epochSeconds <= 0L) return 0L
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochSeconds * 1000
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
