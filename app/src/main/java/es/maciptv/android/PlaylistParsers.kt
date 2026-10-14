package es.maciptv.android

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

object PlaylistParsers {
    private val attr = Regex("([a-zA-Z0-9-]+)=(?:\"([^\"]*)\"|([^\\s,]+))")
    fun m3u(data: ByteArray, base: String? = null): List<Channel> {
        if (data.size > 8_000_000) throw IOException("Lista demasiado grande")
        val result = ArrayList<Channel>()
        val emitted = HashSet<String>()
        var meta: Map<String, String> = emptyMap()
        var name = ""
        var group = "Sin grupo"
        for (raw in data.toString(Charsets.UTF_8).lineSequence()) {
            if (raw.length > 16_384) throw IOException("Línea M3U demasiado larga")
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                meta = attr.findAll(line.substringBefore(',')).associate { it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
                name = line.substringAfter(',', "").trim()
                group = meta["group-title"].orEmpty().ifBlank { "Sin grupo" }
            } else if (line.startsWith("#EXTGRP:", true)) {
                group = line.substringAfter(':').trim().ifBlank { group }
            } else if (line.isNotEmpty() && !line.startsWith('#')) {
                val url = runCatching { if (base != null) java.net.URI(base).resolve(line).toString() else line }.getOrDefault(line)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val index = result.size
                    val channelName = name.ifBlank { android.net.Uri.parse(url).lastPathSegment?.substringBeforeLast('.') ?: "Canal ${index + 1}" }
                    val tvgId = meta["tvg-id"].orEmpty()
                    val id = stableId(url, tvgId, channelName, group)
                    if (emitted.add(id)) {
                        if (result.size >= 50_000) throw IOException("Demasiados canales")
                        result.add(Channel(id, channelName, url, group, meta["tvg-logo"].orEmpty(), tvgId, index))
                    }
                }
                meta = emptyMap(); name = ""; group = "Sin grupo"
            }
        }
        if (result.isEmpty()) throw IOException("No se encontraron canales HTTP válidos")
        return result
    }

    fun xmltv(data: ByteArray): Map<String, List<Programme>> {
        if (data.size > 40_000_000) throw IOException("Guía demasiado grande")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        val result = HashMap<String, MutableList<Programme>>()
        var event = parser.eventType
        var channel = ""; var start = 0L; var end = 0L; var title = ""; var details = ""; var count = 0
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (parser.name) {
                "programme" -> { channel = parser.getAttributeValue(null, "channel").orEmpty(); start = date(parser.getAttributeValue(null, "start")); end = date(parser.getAttributeValue(null, "stop")); title = ""; details = "" }
                "title" -> if (channel.isNotEmpty()) title = parser.nextText()
                "desc" -> if (channel.isNotEmpty()) details = parser.nextText()
            }
            if (event == XmlPullParser.END_TAG && parser.name == "programme" && channel.isNotEmpty() && title.isNotBlank()) {
                result.getOrPut(channel) { ArrayList() }.add(Programme(channel, title, details, start, end))
                count++
                if (count > 200_000) throw IOException("Guía demasiado grande")
                channel = ""
            }
            event = parser.next()
        }
        return result
    }
    private fun date(raw: String?): Long {
        if (raw == null) return 0
        return runCatching { SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(raw)?.time ?: 0L }.getOrDefault(0L)
    }
}
