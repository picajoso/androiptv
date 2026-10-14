package es.maciptv.android

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class SafeNetwork(private val source: SourceConfig) {
    private val origin = runCatching { URI(source.address) }.getOrNull()
    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val resolved = Dns.SYSTEM.lookup(hostname)
            val allowedLocal = source.localServer && (hostname.equals(origin?.host, true) || source.kind == SourceKind.M3U_FILE)
            if (!allowedLocal && resolved.any { isPrivate(it) }) throw IOException("Dirección de red privada bloqueada")
            return resolved
        }
    }
    val client: OkHttpClient = OkHttpClient.Builder().dns(dns).followRedirects(false)
        .addInterceptor { chain ->
            var request = chain.request().newBuilder().header("Cache-Control", "no-cache").build()
            repeat(6) { hop ->
                validateUrl(request.url.toString())
                val response = chain.proceed(request)
                if (response.code !in 300..399) return@addInterceptor response
                if (hop == 5) { response.close(); throw IOException("Demasiadas redirecciones") }
                val target = response.header("Location") ?: run { response.close(); throw IOException("Redirección inválida") }
                val next = request.url.resolve(target) ?: run { response.close(); throw IOException("Redirección inválida") }
                if (request.url.isHttps && !next.isHttps) { response.close(); throw IOException("Redirección insegura") }
                validateUrl(next.toString())
                response.close()
                request = request.newBuilder().url(next).build()
            }
            throw IOException("Demasiadas redirecciones")
        }
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun isPrivate(ip: InetAddress): Boolean {
        if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress || ip.isSiteLocalAddress || ip.isMulticastAddress) return true
        val b = ip.address.map { it.toInt() and 255 }
        if (b.size == 4) return b[0] == 100 && b[1] in 64..127 || b[0] == 169 && b[1] == 254 || b[0] == 198 && b[1] in 18..19
        return b[0] and 0xfe == 0xfc
    }
    fun validateUrl(url: String) {
        val uri = try { URI(url) } catch (_: Exception) { throw IOException("URL no válida") }
        if (uri.userInfo != null || uri.host.isNullOrBlank()) throw IOException("URL no válida")
        if (uri.scheme.equals("http", true) && !source.allowHttp) throw IOException("Autoriza HTTP para esta fuente")
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) throw IOException("Solo se admiten HTTP y HTTPS")
    }
    fun fetch(url: String, maxBytes: Int): ByteArray {
        var current = url
        repeat(6) { hop ->
            validateUrl(current)
            client.newCall(Request.Builder().url(current).header("User-Agent", "AndroIPTV/1.0").build()).execute().use { response ->
                if (response.code in 300..399) {
                    if (hop == 5) throw IOException("Demasiadas redirecciones")
                    val next = response.header("Location") ?: throw IOException("Redirección inválida")
                    val resolved = URI(current).resolve(next).toString()
                    if (current.startsWith("https:", true) && resolved.startsWith("http:", true)) throw IOException("Redirección insegura")
                    current = resolved
                } else {
                    if (!response.isSuccessful) throw IOException("Servidor HTTP ${response.code}")
                    val input = response.body?.byteStream() ?: throw IOException("Respuesta vacía")
                    val buffer = ByteArray(8192)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > maxBytes) throw IOException("El archivo supera el tamaño permitido")
                        output.write(buffer, 0, read)
                    }
                    return output.toByteArray()
                }
            }
        }
        throw IOException("Demasiadas redirecciones")
    }
}
