package net.blueknight.downloader

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.InputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Small Android-facing adapter around NewPipeExtractor.
 *
 * NewPipeExtractor deliberately ships without an HTTP implementation. Keeping
 * that adapter here lets it use the same proxy and WebView user-agent as the
 * rest of Blue Knight, and keeps the shared desktop Python free of Android/JVM
 * imports. The result is plain JSON so Chaquopy crosses one stable boundary.
 */
object NewPipeFallback {

    /** Resolve one public stream page into direct media candidates. */
    @Synchronized
    fun resolve(context: Context, url: String, proxyUrl: String): String {
        NewPipe.init(UrlConnectionDownloader(context, proxyUrl))
        val info = StreamInfo.getInfo(url)

        return JSONObject().apply {
            put("title", info.name)
            put("duration", info.duration)
            put("service", info.service.serviceInfo.name)
            put("hls", info.hlsUrl.orEmpty())
            put("dash", info.dashMpdUrl.orEmpty())
            put("audio", JSONArray().apply {
                info.audioStreams.filter(AudioStream::isUrl).forEach { put(audioJson(it)) }
            })
            put("video", JSONArray().apply {
                info.videoStreams.filter(VideoStream::isUrl).forEach { put(videoJson(it)) }
                info.videoOnlyStreams.filter(VideoStream::isUrl).forEach { put(videoJson(it)) }
            })
            put("errors", JSONArray().apply {
                info.errors.forEach { put(it.message ?: it.javaClass.simpleName) }
            })
        }.toString()
    }

    private fun audioJson(stream: AudioStream) = JSONObject().apply {
        put("url", stream.content)
        put("format", stream.format?.suffix.orEmpty())
        put("bitrate", stream.bitrate)
        put("delivery", stream.deliveryMethod.name)
    }

    private fun videoJson(stream: VideoStream) = JSONObject().apply {
        put("url", stream.content)
        put("format", stream.format?.suffix.orEmpty())
        put("resolution", stream.resolution)
        put("height", stream.height)
        put("width", stream.width)
        put("fps", stream.fps)
        put("bitrate", stream.bitrate)
        put("video_only", stream.isVideoOnly)
        put("delivery", stream.deliveryMethod.name)
    }

    /** HttpURLConnection implementation of NewPipe's deliberately abstract downloader. */
    private class UrlConnectionDownloader(
        context: Context,
        proxyUrl: String,
    ) : Downloader() {
        private val userAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
        private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        private val proxySpec = parseProxy(proxyUrl)

        override fun execute(request: Request): Response {
            val target = URL(request.url())
            val connection = (proxySpec?.let { target.openConnection(it.proxy) }
                ?: target.openConnection()) as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.requestMethod = request.httpMethod()
            connection.setRequestProperty("User-Agent", userAgent)

            request.headers().forEach { (name, values) ->
                if (name.equals("Accept-Encoding", ignoreCase = true)) return@forEach
                values.forEachIndexed { index, value ->
                    if (index == 0) connection.setRequestProperty(name, value)
                    else connection.addRequestProperty(name, value)
                }
            }
            // HttpURLConnection does not transparently decode Brotli. Asking
            // for the two encodings handled below keeps Response.responseBody
            // textual on every Android WebView/provider combination.
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            cookies.get(target.toURI(), emptyMap()).forEach { (name, values) ->
                if (values.isNotEmpty()) connection.setRequestProperty(name, values.joinToString("; "))
            }
            proxySpec?.authorization?.let {
                connection.setRequestProperty("Proxy-Authorization", it)
            }

            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            try {
                val code = connection.responseCode
                val headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
                cookies.put(URI(connection.url.toString()), headers)
                val body = if (request.httpMethod().equals("HEAD", ignoreCase = true)) "" else {
                    val source = if (code >= 400) connection.errorStream else connection.inputStream
                    source?.decoded(connection.contentEncoding)
                        ?.bufferedReader(responseCharset(connection.contentType))
                        ?.use { it.readText() }.orEmpty()
                }
                return Response(
                    code,
                    connection.responseMessage.orEmpty(),
                    headers,
                    body,
                    connection.url.toString(),
                )
            } finally {
                connection.disconnect()
            }
        }

        private fun InputStream.decoded(encoding: String?): InputStream =
            when {
                encoding.equals("gzip", ignoreCase = true) -> GZIPInputStream(this)
                encoding.equals("deflate", ignoreCase = true) -> InflaterInputStream(this)
                else -> this
            }

        private fun responseCharset(contentType: String?): Charset {
            val name = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
                .find(contentType.orEmpty())?.groupValues?.get(1)?.trim('"')
            return runCatching { Charset.forName(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        }

        private data class ProxySpec(val proxy: Proxy, val authorization: String?)

        private fun parseProxy(value: String): ProxySpec? {
            if (value.isBlank()) return null
            return runCatching {
                val uri = URI(value)
                val type = if (uri.scheme.orEmpty().startsWith("socks", true)) {
                    Proxy.Type.SOCKS
                } else {
                    Proxy.Type.HTTP
                }
                val port = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
                val authorization = uri.rawUserInfo?.let {
                    "Basic " + Base64.encodeToString(
                        it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
                ProxySpec(Proxy(type, InetSocketAddress(uri.host, port)), authorization)
            }.getOrNull()
        }
    }
}
