package es.maciptv.android

object ChannelName {
    data class Result(val displayName: String, val country: String?, val quality: String?)

    private val countryPrefix = Regex("^\\s*(?:\\|([A-Za-z]{2})\\|\\s*|([A-Za-z]{2})\\s*:\\s*|([A-Za-z]{2})\\s+-\\s*)")
    private val suffixToken = Regex("(?i)(?:\\s*\\((UHD|4K|FHD|HD|SD|HEVC|H265)\\)\\s*|\\s+(UHD|4K|FHD|HD|SD|HEVC|H265))$")
    private val acronymBrands = mapOf(
        "M+" to "M+", "DAZN" to "DAZN", "ESPN" to "ESPN", "TV" to "TV", "F1" to "F1",
        "MLB" to "MLB", "NBA" to "NBA", "CNN" to "CNN", "BBC" to "BBC", "HBO" to "HBO",
        "TNT" to "TNT", "LALIGA" to "LaLiga"
    )
    private val codecs = setOf("HEVC", "H265")

    fun parse(original: String): Result {
        var name = original.trim()
        val countryMatch = countryPrefix.find(name)
        val country = countryMatch?.groupValues?.drop(1)?.firstOrNull(String::isNotEmpty)?.uppercase()
        if (countryMatch != null) name = name.removeRange(countryMatch.range).trim()

        var quality: String? = null
        while (true) {
            val match = suffixToken.find(name) ?: break
            val token = match.groupValues.drop(1).firstOrNull(String::isNotEmpty)?.uppercase() ?: break
            if (quality == null || token in codecs) quality = token
            name = name.removeRange(match.range).trim()
        }
        if (country == null && quality == null) return Result(original, null, null)
        val displayName = name.takeIf { it.isNotBlank() }?.let(::titleCase) ?: original
        return Result(displayName, country, quality)
    }

    private fun titleCase(value: String): String = value.split(Regex("\\s+")).joinToString(" ") { word ->
        acronymBrands[word.uppercase()] ?: word.lowercase().replaceFirstChar { it.titlecase() }
    }

    fun matches(channel: Channel, displayName: String, query: String): Boolean {
        val needle = query.trim()
        return needle.isEmpty() || channel.name.contains(needle, true) ||
            displayName.contains(needle, true) || channel.group.contains(needle, true)
    }
}
