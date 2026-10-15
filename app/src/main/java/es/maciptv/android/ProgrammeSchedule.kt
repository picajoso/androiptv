package es.maciptv.android

import java.time.LocalDate
import java.time.ZoneId

/** One day calculation shared by the portrait player and the EPG grid. */
object ProgrammeSchedule {
    fun forDate(entries: List<Programme>, date: LocalDate, zone: ZoneId): List<Programme> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return entries.asSequence().filter { it.startMillis < end && it.endMillis > start }
            .sortedBy { it.startMillis }.toList()
    }

    fun isLive(programme: Programme, nowMillis: Long): Boolean =
        programme.title.trimStart().startsWith("DIRECTO", ignoreCase = true) && programme.endMillis > nowMillis

    fun displayTitle(programme: Programme): String =
        programme.title.trimStart().replaceFirst(Regex("(?i)^DIRECTO\\s*[:·-]?\\s*"), "").ifBlank { programme.title }

    fun reminderKey(sourceId: String, channelId: String, startMillis: Long): String =
        "$sourceId|$channelId|$startMillis"
}
