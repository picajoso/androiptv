package es.maciptv.android

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideDayFilterTest {
    @Test fun tomorrowIncludesOnlyProgrammeStartsInNextLocalDay() {
        val zone = ZoneId.of("Europe/Madrid")
        val date = LocalDate.of(2026, 10, 24)
        val start = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val programmes = listOf(
            Programme("c", "before", "", start - 1, start + 60_000),
            Programme("c", "at-start", "", start, start + 60_000),
            Programme("c", "next-day", "", date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(), start + 120_000)
        )

        assertEquals(listOf("at-start"), GuideDayFilter.forDate(programmes, date.plusDays(1), zone).map { it.title })
    }
}
