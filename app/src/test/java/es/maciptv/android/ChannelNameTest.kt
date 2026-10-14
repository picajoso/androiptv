package es.maciptv.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNameTest {
    @Test fun cleansCountryBrandAndResolutionSuffix() {
        val original = "|ES| M+ DEPORTES FHD"
        val parsed = ChannelName.parse(original)

        assertEquals("M+ Deportes", parsed.displayName)
        assertEquals("ES", parsed.country)
        assertEquals("FHD", parsed.quality)
    }

    @Test fun usesCodecAsQualityWhenPresent() {
        val parsed = ChannelName.parse("|ES| DAZN LALIGA FHD (HEVC)")

        assertEquals("DAZN LaLiga", parsed.displayName)
        assertEquals("ES", parsed.country)
        assertEquals("HEVC", parsed.quality)
        val us = ChannelName.parse("US - ESPN HD")
        assertEquals("ESPN", us.displayName)
        assertEquals("US", us.country)
        assertEquals("HD", us.quality)
    }

    @Test fun keepsOriginalNameSearchable() {
        val original = "|ES| DAZN LALIGA FHD (HEVC)"
        val channel = Channel("id", original, "https://example.test/a", "Deportes")
        val parsed = ChannelName.parse(original)

        assertTrue(ChannelName.matches(channel, parsed.displayName, "HEVC"))
    }
}
