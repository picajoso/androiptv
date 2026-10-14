package es.maciptv.android

import java.time.LocalDate
import java.time.ZoneId

object GuideDayFilter {
    fun forDate(programmes: List<Programme>, date: LocalDate, zone: ZoneId): List<Programme> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return programmes.filter { it.startMillis >= start && it.startMillis < endExclusive }
    }
}
