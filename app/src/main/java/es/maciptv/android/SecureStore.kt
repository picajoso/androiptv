package es.maciptv.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("androiptv", Context.MODE_PRIVATE)
    private val alias = "androiptv.source.v1"

    companion object {
        private const val SOURCE_COLLECTION_KEY = "sources.v2"
        private const val LEGACY_SOURCE_KEY = "source"
        private const val LEGACY_LAST_CHANNEL_KEY = "last"
        private const val SOURCE_LAST_CHANNEL_PREFIX = "last."
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    private fun encrypt(rawText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(rawText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size < 12 + 16) throw IOException("Configuración guardada no válida")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }

    fun loadSources(): SourceCollection {
        val current = prefs.getString(SOURCE_COLLECTION_KEY, null)
        if (current != null) {
            val collection = SourceCollection.fromJson(decrypt(current))
            clearVerifiedLegacySource()
            return withLastChannelOverrides(collection)
        }

        val legacy = prefs.getString(LEGACY_SOURCE_KEY, null) ?: return SourceCollection(emptyList(), null)
        val legacyConfig = SourceConfig.fromJson(decrypt(legacy))
        val imported = SavedSource(
            id = UUID.randomUUID().toString(),
            name = "Mi lista",
            config = legacyConfig,
            updatedAt = System.currentTimeMillis(),
            lastChannelId = prefs.getString(LEGACY_LAST_CHANNEL_KEY, null)
        )
        val migrated = SourceCollection(listOf(imported), imported.id)

        // The legacy keys are removed only after the replacement has been committed and read back.
        saveSources(migrated)
        clearVerifiedLegacySource()
        return migrated
    }

    fun saveSources(collection: SourceCollection) {
        val activeId = collection.activeSourceId?.takeIf { id -> collection.sources.any { it.id == id } }
            ?: collection.sources.firstOrNull()?.id
        val normalized = collection.copy(activeSourceId = activeId)
        val encrypted = encrypt(normalized.toJson())
        if (!prefs.edit().putString(SOURCE_COLLECTION_KEY, encrypted).commit()) {
            throw IOException("No se pudo guardar la lista de fuentes")
        }
        val stored = prefs.getString(SOURCE_COLLECTION_KEY, null)
            ?: throw IOException("No se pudo comprobar la lista de fuentes guardada")
        val decoded = SourceCollection.fromJson(decrypt(stored))
        if (decoded != normalized) throw IOException("No se pudo comprobar la lista de fuentes guardada")
    }

    fun removeSavedSource(sourceId: String): SourceCollection {
        val collection = loadSources()
        if (collection.sources.none { it.id == sourceId }) return collection
        val remaining = collection.sources.filterNot { it.id == sourceId }
        val nextActiveId = if (collection.activeSourceId == sourceId) remaining.firstOrNull()?.id else collection.activeSourceId
        val updated = SourceCollection(remaining, nextActiveId)
        saveSources(updated)
        prefs.edit().remove(sourceLastChannelKey(sourceId)).commit()
        return updated
    }

    fun setLastChannel(sourceId: String, channelId: String?) {
        val editor = prefs.edit()
        // Empty string is a tombstone so an intentionally cleared legacy value cannot reappear.
        editor.putString(sourceLastChannelKey(sourceId), channelId ?: "").apply()
    }

    private fun withLastChannelOverrides(collection: SourceCollection): SourceCollection = collection.copy(
        sources = collection.sources.map { saved ->
            val key = sourceLastChannelKey(saved.id)
            if (prefs.contains(key)) saved.copy(lastChannelId = prefs.getString(key, null)?.takeIf { it.isNotEmpty() })
            else saved
        }
    )

    private fun sourceLastChannelKey(sourceId: String) = "$SOURCE_LAST_CHANNEL_PREFIX$sourceId"

    private fun clearVerifiedLegacySource() {
        if (prefs.contains(LEGACY_SOURCE_KEY)) {
            // The caller has already decrypted, or just wrote and verified, the new collection.
            prefs.edit().remove(LEGACY_SOURCE_KEY).remove(LEGACY_LAST_CHANNEL_KEY).commit()
        }
    }

    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(value) { prefs.edit().putStringSet("favorites", value.toSet()).apply() }
    var recents: List<String>
        get() = prefs.getString("recents", "")!!.split(',').filter { it.isNotEmpty() }
        set(value) { prefs.edit().putString("recents", value.joinToString(",")).apply() }

    var reminderMarks: Set<String>
        get() = prefs.getStringSet("reminderMarks.v1", emptySet())?.toSet() ?: emptySet()
        set(value) { prefs.edit().putStringSet("reminderMarks.v1", value.toSet()).apply() }

    fun pruneEndedReminderMarks(sourceId: String, channels: List<Channel>,
                                guide: Map<String, List<Programme>>, nowMillis: Long) {
        val byId = channels.associateBy { it.id }
        val prefix = "$sourceId|"
        reminderMarks = reminderMarks.filterTo(mutableSetOf()) { mark ->
            if (!mark.startsWith(prefix)) return@filterTo true
            val separator = mark.lastIndexOf('|')
            if (separator <= prefix.length) return@filterTo true
            val channel = byId[mark.substring(prefix.length, separator)] ?: return@filterTo true
            val start = mark.substring(separator + 1).toLongOrNull() ?: return@filterTo true
            guide[channel.tvgId].orEmpty().firstOrNull { it.startMillis == start }
                ?.let { it.endMillis > nowMillis } ?: true
        }
    }

    var resumeLastChannel: Boolean
        get() = prefs.getBoolean("resumeLastChannel", true)
        set(value) { prefs.edit().putBoolean("resumeLastChannel", value).apply() }

    var fillVideo: Boolean
        get() = prefs.getBoolean("fillVideo", true)
        set(value) { prefs.edit().putBoolean("fillVideo", value).apply() }
}
