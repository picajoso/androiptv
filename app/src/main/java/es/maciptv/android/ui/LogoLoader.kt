package es.maciptv.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.util.LruCache
import es.maciptv.android.SafeNetwork
import java.io.IOException
import java.util.concurrent.Executors

object LogoLoader {
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private val executor = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16 / 1024).toInt().coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun load(
        url: String,
        network: SafeNetwork,
        target: ImageView,
        fallback: Drawable,
        onHttpBlocked: (() -> Unit)? = null
    ) {
        val token = Any()
        target.tag = token
        target.setImageDrawable(fallback)
        if (url.isBlank()) return
        val width = target.width.takeIf { it > 0 }
            ?: target.layoutParams?.width?.takeIf { it > 0 }
            ?: AndroTheme.dp(target.context, 52)
        val height = target.height.takeIf { it > 0 }
            ?: target.layoutParams?.height?.takeIf { it > 0 }
            ?: width
        val key = "${System.identityHashCode(network)}|$url|${width}x$height"
        cache.get(key)?.let { bitmap ->
            target.setImageDrawable(BitmapDrawable(target.resources, bitmap))
            return
        }
        executor.execute {
            try {
                val bytes = network.fetch(url, MAX_RESPONSE_BYTES)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Imagen no válida")
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("Imagen no válida")
                val scale = minOf(1f, width.toFloat() / decoded.width, height.toFloat() / decoded.height)
                val bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== decoded) decoded.recycle() }
                } else decoded
                cache.put(key, bitmap)
                target.post {
                    if (target.tag === token) target.setImageDrawable(BitmapDrawable(target.resources, bitmap))
                }
            } catch (_: Exception) {
                val blocked = url.startsWith("http://", true) && runCatching { network.validateUrl(url) }.isFailure
                if (blocked) target.post { onHttpBlocked?.invoke() }
                target.post { if (target.tag === token) target.setImageDrawable(fallback) }
            }
        }
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    fun clear() { cache.evictAll() }
}

class InitialsDrawable(private val name: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        paint.color = AndroTheme.NEUTRAL_200
        canvas.drawRect(bounds, paint)
        paint.color = AndroTheme.NEUTRAL_800
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = bounds.height() * 0.34f
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        val baseline = bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(initials.ifEmpty { "TV" }, bounds.exactCenterX(), baseline, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
