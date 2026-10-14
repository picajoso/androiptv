package es.maciptv.android

import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest

enum class SourceKind { M3U_URL, M3U_FILE, XTREAM, DEMO }

data class SavedSource(
    val id: String,
    val name: String,
    val config: SourceConfig,
    val channelCount: Int = 0,
    val updatedAt: Long = 0L,
    val lastChannelId: String? = null
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("config", JSONObject(config.toJson()))
        .put("channelCount", channelCount)
        .put("updatedAt", updatedAt)
        .put("lastChannelId", lastChannelId)

    companion object {
        fun fromJson(value: JSONObject) = SavedSource(
            id = value.getString("id"),
            name = value.getString("name"),
            config = SourceConfig.fromJson(value.getJSONObject("config").toString()),
            channelCount = value.optInt("channelCount"),
            updatedAt = value.optLong("updatedAt"),
            lastChannelId = value.optString("lastChannelId").takeUnless { it.isBlank() || it == "null" }
        )
    }
}

data class SourceCollection(val sources: List<SavedSource>, val activeSourceId: String?) {
    val activeSource: SavedSource? get() = sources.firstOrNull { it.id == activeSourceId }

    fun toJson(): String {
        val entries = org.json.JSONArray()
        sources.forEach { entries.put(it.toJson()) }
        return JSONObject().put("sources", entries).put("activeSourceId", activeSourceId).toString()
    }

    companion object {
        fun fromJson(raw: String): SourceCollection {
            val value = JSONObject(raw)
            val entries = value.getJSONArray("sources")
            val sources = List(entries.length()) { index -> SavedSource.fromJson(entries.getJSONObject(index)) }
            val requestedActiveId = value.optString("activeSourceId").takeUnless { it.isBlank() || it == "null" }
            val activeId = requestedActiveId?.takeIf { id -> sources.any { it.id == id } }
                ?: sources.firstOrNull()?.id
            return SourceCollection(sources, activeId)
        }
    }
}

data class SourceConfig(val kind: SourceKind, val address: String = "", val username: String = "",
    val password: String = "", val xmltv: String = "", val allowHttp: Boolean = false,
    val localServer: Boolean = false) {
    fun toJson() = JSONObject().put("kind", kind.name).put("address", address)
        .put("username", username).put("password", password).put("xmltv", xmltv)
        .put("allowHttp", allowHttp).put("localServer", localServer).toString()
    fun playlistUrl(): String = if (kind != SourceKind.XTREAM) address else
        Uri.parse(address.trimEnd('/') + "/get.php").buildUpon()
            .appendQueryParameter("username", username).appendQueryParameter("password", password)
            .appendQueryParameter("type", "m3u_plus").appendQueryParameter("output", "ts").build().toString()
    fun guideUrl(): String = if (kind != SourceKind.XTREAM) xmltv else
        Uri.parse(address.trimEnd('/') + "/xmltv.php").buildUpon()
            .appendQueryParameter("username", username).appendQueryParameter("password", password).build().toString()
    companion object {
        fun fromJson(raw: String): SourceConfig {
            val o = JSONObject(raw)
            return SourceConfig(SourceKind.valueOf(o.getString("kind")), o.optString("address"),
                o.optString("username"), o.optString("password"), o.optString("xmltv"),
                o.optBoolean("allowHttp"), o.optBoolean("localServer"))
        }
    }
}

data class Channel(val id: String, val name: String, val url: String, val group: String,
    val logo: String = "", val tvgId: String = "", val order: Int = 0)
data class Programme(val channelId: String, val title: String, val details: String,
    val startMillis: Long, val endMillis: Long)

fun stableId(url: String, tvgId: String, name: String, group: String): String {
    val uri = android.net.Uri.parse(url)
    val normalized = "${uri.scheme}://${uri.host}:${uri.port}${uri.path}".lowercase()
    val key = "$normalized|${tvgId.lowercase()}|${name.trim().lowercase()}|${group.trim().lowercase()}"
    return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12)
        .joinToString("") { "%02x".format(it) }
}
