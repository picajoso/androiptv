package es.maciptv.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("androiptv", Context.MODE_PRIVATE)
    private val alias = "androiptv.source.v1"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    fun saveSource(source: SourceConfig) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("source", Base64.encodeToString(cipher.iv + cipher.doFinal(source.toJson().toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun loadSource(): SourceConfig? {
        val raw = Base64.decode(prefs.getString("source", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return SourceConfig.fromJson(String(cipher.doFinal(raw.copyOfRange(12, raw.size))))
    }
    fun removeSource() { prefs.edit().remove("source").remove("last").apply() }
    var lastChannel: String?
        get() = prefs.getString("last", null)
        set(value) { prefs.edit().putString("last", value).apply() }
    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(value) { prefs.edit().putStringSet("favorites", value.toSet()).apply() }
    var recents: List<String>
        get() = prefs.getString("recents", "")!!.split(',').filter { it.isNotEmpty() }
        set(value) { prefs.edit().putString("recents", value.joinToString(",")).apply() }
}
