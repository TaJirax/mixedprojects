package net.blueknight.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone's third YouTube engine: YouTube.js, run in a WebView.
 *
 * The desktop runs the same script under Deno. Android has no Deno and no
 * Node, but it has something better suited than either: a real browser, with a
 * real fetch, a real URL parser and a real event loop. The libraries that do
 * this on other platforms go to considerable trouble to imitate that
 * environment; here it is the environment.
 *
 * Nothing is loaded from the network. The page is served from memory and the
 * two scripts come out of the APK's assets, so the engine has no opportunity
 * to fetch its own code and no way to be interfered with over the wire.
 */
object YouTubeJsFallback {

    /** Long enough for five clients to be tried, short enough to give up. */
    private const val TIMEOUT_SECONDS = 90L

    /**
     * Resolve a video and return the same JSON the desktop host returns.
     *
     * Called from the shared Python engine on a download thread, and the
     * WebView must live on the main thread — so the work is posted there and
     * this waits for the answer, which is the same shape the clipboard and
     * cookie bridges already use.
     */
    fun mintToken(context: Context, proxyUrl: String): String =
        run(context, proxyUrl, tokenPage())

    fun resolve(context: Context, url: String, proxyUrl: String): String {
        val videoId = videoIdOf(url)
            ?: return error("that is not a YouTube video link")
        return run(context, proxyUrl, page(videoId))
    }

    private fun run(context: Context, proxyUrl: String, document: String): String {
        val answer = arrayOf<String?>(null)
        val done = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                start(context, document) { result ->
                    if (answer[0] == null) {
                        answer[0] = result
                        done.countDown()
                    }
                }
            } catch (failure: Throwable) {
                answer[0] = error(failure.message ?: "the engine could not start")
                done.countDown()
            }
        }

        if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return error("the engine did not answer in ${TIMEOUT_SECONDS}s")
        }
        return answer[0] ?: error("the engine returned nothing")
    }

    private fun start(
        context: Context, document: String, onResult: (String) -> Unit
    ) {
        val library = context.assets.open("engines/youtubejs/youtubei.bundle.mjs")
            .bufferedReader().use { it.readText() }
        val engine = context.assets.open("engines/youtubejs/engine.mjs")
            .bufferedReader().use { it.readText() }
        fun asset(name: String) = context.assets
            .open("engines/youtubejs/$name").bufferedReader().use { it.readText() }
        val potoken = asset("potoken.mjs")
        val webpo = asset("bgutils-webpo.mjs")
        val botguard = asset("bgutils-botguard.mjs")

        @Suppress("SetJavaScriptEnabled")
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // The engine's own code never comes from the network, so nothing
            // here may load a file or a content URI either.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }

        var finished = false
        fun finish(result: String) {
            if (finished) return
            finished = true
            view.destroy()
            onResult(result)
        }

        view.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun deliver(payload: String) {
                Handler(Looper.getMainLooper()).post { finish(payload) }
            }
        }, "BlueKnightYouTubeJs")

        // The page must appear to come from YouTube's own origin, or every
        // request the library makes is a cross-origin one the browser refuses
        // before YouTube ever sees it.
        val origin = "https://www.youtube.com/"
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                // Three files, all answered from memory. They are served as
                // real modules rather than inlined into one page because
                // separate module scripts do not share a scope, and the engine
                // has to be able to import the library by name.
                val served: Pair<String, String>? = when (request.url.toString()) {
                    origin + PAGE -> "text/html" to document
                    origin + LIB -> "text/javascript" to library
                    origin + ENGINE -> "text/javascript" to engine
                    origin + POTOKEN -> "text/javascript" to potoken
                    origin + WEBPO -> "text/javascript" to webpo
                    origin + BOTGUARD -> "text/javascript" to botguard
                    else -> null
                }
                return served?.let { (mime, body) ->
                    WebResourceResponse(mime, "utf-8",
                        ByteArrayInputStream(body.toByteArray()))
                }
            }
        }

        view.loadUrl(origin + PAGE)

        // A page that never calls back must not hold the download for ever.
        Handler(Looper.getMainLooper()).postDelayed({
            finish(error("the engine timed out inside the browser"))
        }, (TIMEOUT_SECONDS - 5) * 1000)
    }

    private const val PAGE = "blueknight-engine"
    private const val LIB = "blueknight-lib.mjs"
    private const val ENGINE = "blueknight-engine.mjs"
    private const val POTOKEN = "blueknight-potoken.mjs"
    private const val WEBPO = "blueknight-webpo.mjs"
    private const val BOTGUARD = "blueknight-botguard.mjs"

    /**
     * The page that mints a token.
     *
     * BotGuard is passed here and not on the desktop, because this host has
     * the browser it needs; when its challenge fails the minter falls back to
     * the cold-start path on its own.
     */
    private fun tokenPage(): String = """
        <!doctype html><meta charset="utf-8"><body><script type="module">
        import { Innertube } from "./$LIB";
        import * as webpo from "./$WEBPO";
        import * as botguard from "./$BOTGUARD";
        import { mint } from "./$POTOKEN";
        try {
          BlueKnightYouTubeJs.deliver(
            JSON.stringify(await mint(Innertube, webpo, botguard)));
        } catch (e) {
          BlueKnightYouTubeJs.deliver(JSON.stringify({
            error: String((e && e.message) || e).slice(0, 200),
          }));
        }
        </script></body>
    """.trimIndent()

    /**
     * The document the engine runs in.
     *
     * The engine takes the library as arguments rather than importing it
     * itself, which is what lets the identical file run here and under Deno
     * with only this six-line host differing between them.
     */
    private fun page(videoId: String): String = """
        <!doctype html><meta charset="utf-8"><body><script type="module">
        import { Innertube, ClientType } from "./$LIB";
        import { resolve } from "./$ENGINE";
        try {
          const out = await resolve(Innertube, ClientType, ${'"'}$videoId${'"'});
          BlueKnightYouTubeJs.deliver(JSON.stringify(out));
        } catch (e) {
          BlueKnightYouTubeJs.deliver(JSON.stringify({
            ok: false, streams: [], attempts: [],
            error: String((e && e.message) || e).slice(0, 300),
          }));
        }
        </script></body>
    """.trimIndent()

    private fun videoIdOf(url: String): String? =
        Regex("(?:v=|/shorts/|youtu\\.be/|/embed/|/v/)([A-Za-z0-9_-]{11})")
            .find(url)?.groupValues?.get(1)

    private fun error(message: String): String = JSONObject()
        .put("ok", false)
        .put("streams", org.json.JSONArray())
        .put("attempts", org.json.JSONArray())
        .put("error", message)
        .toString()
}
