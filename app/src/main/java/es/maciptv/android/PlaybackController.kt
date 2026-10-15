package es.maciptv.android

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.DeviceInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView

@UnstableApi
class PlaybackController(context: Context, val view: PlayerView) {
    private val localPlayer = ExoPlayer.Builder(context).setMediaSourceFactory(
        DefaultMediaSourceFactory(DataSource.Factory {
            val active = network ?: error("No hay una fuente activa")
            OkHttpDataSource.Factory(active.client).setUserAgent("VLC/3.0.21 LibVLC/3.0.21").createDataSource()
        })
    ).build()
    private val player = CastPlayer.Builder(context).setLocalPlayer(localPlayer).build()
    private var source: SourceConfig? = null
    private var network: SafeNetwork? = null
    private var current: Channel? = null
    private var retries = 0
    private var generation = 0
    private var convertedToHls = false
    private var playingOriginal = false
    var onError: ((String) -> Unit)? = null
    var onPlaybackStateChanged: (() -> Unit)? = null
    var onRemoteChanged: ((Boolean) -> Unit)? = null
    val isPlaying: Boolean get() = player.playWhenReady
    val currentChannelId: String? get() = current?.id
    val isCasting: Boolean get() = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    fun togglePlayPause() {
        if (player.playWhenReady) player.pause() else if (current != null) player.play()
    }
    fun pause() { player.pause() }

    init {
        view.player = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { onPlaybackStateChanged?.invoke() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { onPlaybackStateChanged?.invoke() }
            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                onRemoteChanged?.invoke(deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val code = generateSequence(error as Throwable?) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.responseCode
                if (convertedToHls && !playingOriginal && code in 400..499 && current != null && source != null && network != null) {
                    playingOriginal = true
                    play(current!!, source!!, network!!, false, true)
                } else if (code in 400..499) {
                    onError?.invoke("El servidor rechazó el canal (HTTP $code). Comprueba que el proveedor permite reproducirlo en otro dispositivo.")
                } else if (retries < 2 && current != null && source != null && network != null) {
                    retries++
                    val expectedGeneration = generation
                    player.stop()
                    view.postDelayed({
                        if (generation == expectedGeneration) current?.let { play(it, source!!, network!!, false, playingOriginal) }
                    }, retries * 1500L)
                } else onError?.invoke("No se pudo reproducir el canal: ${error.errorCodeName}${code?.let { " (HTTP $it)" } ?: ""}")
            }
        })
    }

    fun play(channel: Channel, config: SourceConfig, network: SafeNetwork,
        resetRetries: Boolean = true, forceOriginal: Boolean = false) {
        try {
            var url = channel.url
            if (resetRetries) { retries = 0; playingOriginal = false; generation++ }
            convertedToHls = false
            if (!forceOriginal && config.kind == SourceKind.XTREAM && url.substringBefore('?').endsWith(".ts", true)) {
                val uri = Uri.parse(url)
                url = uri.buildUpon().path(uri.path!!.dropLast(3) + ".m3u8").build().toString()
                convertedToHls = true
            }
            network.validateUrl(url)
            source = config; this.network = network; current = channel
            val isHls = url.substringBefore('?').endsWith(".m3u8", true)
            val item = MediaItem.Builder().setUri(Uri.parse(url))
                .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build()).build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) { onError?.invoke(e.message ?: "Error de reproducción") }
    }
    fun stop() { generation++; player.stop(); current = null; source = null; network = null }
    fun release() { player.release(); localPlayer.release() }
}
