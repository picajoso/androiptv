package es.maciptv.android

import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class ChannelPresentation(
    val channel: Channel,
    val displayName: String,
    val country: String?,
    val quality: String?,
    val currentProgramme: Programme?,
    val progressPercent: Int?,
    val programmeEndLabel: String?
)

fun buildChannelPresentations(
    channels: List<Channel>, guide: Map<String, List<Programme>>, nowMillis: Long
): List<ChannelPresentation> {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return channels.map { channel ->
        val parsed = ChannelName.parse(channel.name)
        val programme = guide[channel.tvgId].orEmpty().firstOrNull {
            nowMillis >= it.startMillis && nowMillis < it.endMillis
        }
        val duration = programme?.let { it.endMillis - it.startMillis }?.takeIf { it > 0L }
        val progress = programme?.let {
            (((nowMillis - it.startMillis) * 100) / duration!!).toInt().coerceIn(0, 100)
        }
        ChannelPresentation(
            channel = channel,
            displayName = parsed.displayName,
            country = parsed.country,
            quality = parsed.quality,
            currentProgramme = programme,
            progressPercent = progress,
            programmeEndLabel = programme?.let { timeFormat.format(Date(it.endMillis)) }
        )
    }
}
