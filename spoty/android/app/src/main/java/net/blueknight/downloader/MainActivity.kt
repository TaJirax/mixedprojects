package net.blueknight.downloader

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The window.
 *
 * On the desktop pywebview owns the window and forwards page calls to the
 * engine's Api object. Here that job is split in two: this Activity owns a
 * WebView, and a small shim injected into the page presents the same
 * `window.pywebview.api` surface the interface already talks to. The HTML is
 * therefore the same file the desktop builds ship, unmodified.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var bridge: PyObject
    private var loginView: WebView? = null
    private var loginKind: String? = null
    private var pendingMediaOptions = "{}"

    /** Where a page call is answered from, and what the page expects back. */
    private val py by lazy { Python.getInstance() }

    private val pickDownloadFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val output = DownloadService.ensureExportFolder(this, uri)
            if (output == null) {
                call("android_import_failed", JSONArray().put(
                    "BlueKnight Downloader could not be created in that folder."))
                return@registerForActivityResult
            }
            getSharedPreferences(DownloadService.PREFS, MODE_PRIVATE).edit()
                .putString(DownloadService.EXPORT_TREE, uri.toString()).apply()
            call("android_set_folder", JSONArray().put(output.uri.toString()))
        }
    }

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importDocument(it, "android_picked_file") }
    }

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selected ->
            importDocument(selected, "android_picked_media", JSONObject(pendingMediaOptions))
        }
    }

    private val pickImageFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selected ->
            Thread {
                val imported = copyImageTree(selected)
                if (imported == null) {
                    call("android_import_failed", JSONArray().put(
                        "No readable images were found in that folder."))
                } else {
                    DownloadService.start(this)
                    call("android_picked_image_folder", JSONArray().put(imported.absolutePath))
                }
            }.start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bridge = py.getModule("android_bridge")
        bridge.callAttr("boot", this)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // The interface is one local document; nothing else may be loaded
            // into the window that holds the bridge.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(0xFF05080D.toInt())
            addJavascriptInterface(Bridge(), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    injectShim()
                }
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: android.webkit.WebResourceRequest?
                ): Boolean {
                    // A link in the interface opens in the browser rather than
                    // replacing the app with a web page it cannot come back from.
                    request?.url?.let {
                        startActivity(Intent(Intent.ACTION_VIEW, it))
                    }
                    return true
                }
            }
        }
        setContentView(web, FrameLayout.LayoutParams(-1, -1))

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        web.loadUrl("file:///android_asset/web/index.html")
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A link shared from another app arrives as the page's pasted URL. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        web.postDelayed({
            web.evaluateJavascript(
                "window.blueknightShared && window.blueknightShared(${quote(shared)})", null)
        }, 800)
    }

    /**
     * Present the desktop bridge's shape to the page.
     *
     * Every Api method is answered by one Kotlin entry point, so the shim is a
     * Proxy rather than a list that would drift out of step with the engine.
     * The calls that return a value are quick; the ones that are slow — a
     * download, a conversion, a folder pick — already report through the poll
     * queue on the desktop, so nothing here needs to block the page.
     */
    private fun injectShim() {
        web.evaluateJavascript(
            """
            (function () {
              if (window.pywebview) return;
              window.pywebview = {
                api: new Proxy({}, {
                  get: function (_, name) {
                    return function () {
                      var args = Array.prototype.slice.call(arguments);
                      try {
                        var raw = AndroidBridge.call(String(name), JSON.stringify(args));
                        return Promise.resolve(raw === "" ? null : JSON.parse(raw));
                      } catch (e) {
                        return Promise.reject(e);
                      }
                    };
                  }
                })
              };
              window.dispatchEvent(new Event("pywebviewready"));
            })();
            """.trimIndent(), null)
    }

    private fun call(method: String, args: JSONArray): String =
        bridge.callAttr("call", method, args.toString()).toString()

    /** The page's only way in. Everything it can ask for arrives here. */
    inner class Bridge {
        @JavascriptInterface
        fun call(method: String, argsJson: String): String {
            // Window controls and anything needing an Activity are handled on
            // this side; everything else is the shared engine's own Api.
            when (method) {
                "minimize" -> { runOnUiThread { moveTaskToBack(true) }; return "" }
                "close" -> { runOnUiThread { finish() }; return "" }
                "toggle_maximize" -> return ""      // a phone window is always full
                "browse" -> { runOnUiThread { pickDownloadFolder.launch(null) }; return "" }
                "convert_media" -> {
                    pendingMediaOptions = JSONArray(argsJson).optJSONObject(0)?.toString() ?: "{}"
                    runOnUiThread { pickMedia.launch(arrayOf("video/*", "audio/*")) }
                    return ""
                }
                "convert_document" -> {
                    runOnUiThread { pickDocument.launch(arrayOf("*/*")) }; return ""
                }
                "convert_image_folder" -> {
                    runOnUiThread { pickImageFolder.launch(null) }; return ""
                }
                "sign_in" -> {
                    val kind = JSONArray(argsJson).optString(0)
                    runOnUiThread { openLogin(kind) }
                    return ""
                }
                "finish_sign_in" -> { runOnUiThread { finishLogin() }; return "" }
                "open_folder" -> {
                    runOnUiThread { openDownloadsFolder() }; return ""
                }
                "open_telegram" -> {
                    runOnUiThread {
                        startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/BlueKnight_Net")))
                    }
                    return ""
                }
                // The clipboard is an Activity service on Android. The engine's
                // desktop implementation shells out to pbpaste/xclip, which do
                // not exist here, so both directions are answered on this side.
                "clipboard" -> return JSONObject.quote(readClipboard())
                "copy_text" -> {
                    writeClipboard(JSONArray(argsJson).optString(0))
                    return ""
                }
            }
            // Anything that begins work has to outlive the Activity being
            // backgrounded, and every such call is named start_*.
            if (method.startsWith("start_")) DownloadService.start(this@MainActivity)
            return bridge.callAttr("call", method, argsJson).toString()
        }
    }

    // ----------------------------------------------------------------------
    // Sign-in.
    //
    // The desktop reads a jar out of an installed browser's profile. Android
    // has no such profile to read, so the app hosts the login itself and takes
    // the session from its own CookieManager — which is the same thing the
    // desktop's "easy way" does, and needs no file export at all.
    // ----------------------------------------------------------------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun openLogin(kind: String) {
        val start = bridge.callAttr("signin_url", kind)?.toString() ?: return
        loginKind = kind
        CookieManager.getInstance().setAcceptCookie(true)

        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // A login is a form post across several hosts, and it needs the
            // same storage a browser would give it.
            settings.databaseEnabled = true
            // Sign-in pages open steps with target="_blank". With multiple
            // windows unsupported the WebView follows them in place; with them
            // supported and no WebChromeClient to service onCreateWindow, the
            // tap does nothing at all and the page looks frozen.
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            // The user agent is deliberately left alone. It used to be
            // overwritten with a desktop Chrome string, which every login page
            // could catch: the client hints this WebView sends alongside it
            // still say Android, and a browser lying about what it is, is the
            // exact thing a sign-in page refuses to trust. The engine reads
            // this same default agent at startup and replays the session under
            // it, so nothing downstream needs the override either.
            setBackgroundColor(0xFF05080D.toInt())

            // Without a WebViewClient a WebView does not navigate — it hands
            // the URL to the system, which opens the YouTube app or a browser
            // and leaves this window sitting blank on top of the app with no
            // page in it and no obvious way back. That is the whole of the
            // "it opens YouTube and then freezes" report: the sign-in never
            // happened here, so no cookie could ever be harvested from it.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: android.webkit.WebResourceRequest
                ): Boolean {
                    val url = request.url
                    // Anything the web can serve stays in this window.
                    if (url.scheme == "http" || url.scheme == "https") return false
                    // intent://, market://, youtube:// and friends are a site
                    // asking to leave. Refuse: leaving is what breaks this.
                    return true
                }

                // A finished login lands on the site's own home page, and from
                // there the window is just a browser someone has to know to
                // close. Every page it settles on is offered to the engine,
                // which answers whether the session now exists — and the first
                // time it does, this closes itself and hands back the app.
                override fun onPageFinished(view: WebView?, url: String?) {
                    val kind = loginKind ?: return
                    val ready = try {
                        bridge.callAttr("signin_ready", kind, url, harvestCookies(kind))
                            .toBoolean()
                    } catch (failure: Throwable) {
                        false      // never let a probe strand the window
                    }
                    if (ready) finishLogin()
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.loadUrl(start)
        loginView = view
        addContentView(view, FrameLayout.LayoutParams(-1, -1))
        view.visibility = View.VISIBLE
    }

    private fun finishLogin() {
        val kind = loginKind ?: return
        bridge.callAttr("save_cookies", kind, harvestCookies(kind))

        loginView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
        loginView = null
        loginKind = null
    }

    /**
     * Read the live session for a source out of the system CookieManager.
     *
     * Called two ways: once when a sign-in finishes, and again — headless, with
     * no window open — whenever the engine needs to refresh a stored session
     * mid-download. A desktop re-reads a browser profile for this; the phone's
     * equivalent is right here, and it is why a rotated YouTube cookie does not
     * have to look like never having signed in.
     */
    fun harvestCookies(kind: String): String {
        val manager = CookieManager.getInstance()
        manager.flush()

        // A session is split across hosts — a YouTube login is partly on
        // google.com — so every domain the engine names for this source is read.
        val domains = bridge.callAttr("signin_domains", kind)
        val jar = JSONArray()
        for (i in 0 until domains.callAttr("__len__").toInt()) {
            val domain = domains.callAttr("__getitem__", i).toString()
            manager.getCookie(domain)?.let { jar.put(JSONArray().put(domain).put(it)) }
        }
        return jar.toString()
    }

    // ----------------------------------------------------------------------
    // Clipboard.
    //
    // ClipboardManager is documented as a main-thread service, and a bridge
    // call arrives on one of the WebView's own threads, so the work is posted
    // and waited for. The guard matters: were this ever called from the main
    // thread, posting and then waiting on it would deadlock the app.
    // ----------------------------------------------------------------------
    private fun onMainThread(work: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { work(); return }
        val done = CountDownLatch(1)
        runOnUiThread { try { work() } finally { done.countDown() } }
        done.await(2, TimeUnit.SECONDS)
    }

    private fun readClipboard(): String {
        var text = ""
        onMainThread {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                // coerceToText, not .text: a link shared as a styled span or a
                // URI item still has to come back as the string the field wants.
                text = clip.getItemAt(0).coerceToText(this).toString().trim()
            }
        }
        return text
    }

    private fun writeClipboard(text: String) {
        onMainThread {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("Blue Knight", text))
        }
    }

    private fun openDownloadsFolder() {
        val saved = getSharedPreferences(DownloadService.PREFS, MODE_PRIVATE)
            .getString(DownloadService.EXPORT_TREE, null)
        if (saved != null) {
            runCatching {
                val treeUri = Uri.parse(saved)
                val outputUri = DownloadService.ensureExportFolder(this, treeUri)?.uri ?: treeUri
                startActivity(Intent(Intent.ACTION_VIEW, outputUri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                })
            }.onSuccess { return }
        }
        pickDownloadFolder.launch(null)
    }

    /** Copy a picked content URI to a real, extension-preserving cache file. */
    private fun importDocument(uri: Uri, method: String, extra: JSONObject? = null) {
        Thread {
            val imported = copyDocument(uri)
            if (imported == null) {
                call("android_import_failed", JSONArray().put("That file could not be opened."))
                return@Thread
            }
            DownloadService.start(this)
            val args = JSONArray().put(imported.absolutePath)
            if (extra != null) args.put(extra)
            call(method, args)
        }.start()
    }

    private fun copyDocument(uri: Uri): File? = runCatching {
        val rawName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "import-${System.currentTimeMillis()}"
        val name = safeName(rawName)
        val folder = File(cacheDir, "imports").apply { mkdirs() }
        var target = File(folder, name)
        if (target.exists()) {
            val suffix = target.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
            target = File(folder,
                "${target.nameWithoutExtension}-${System.currentTimeMillis()}$suffix")
        }
        contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        }
        target
    }.getOrNull()

    /** Materialise a Storage Access Framework tree for the shared Python converter. */
    private fun copyImageTree(uri: Uri): File? = runCatching {
        val root = DocumentFile.fromTreeUri(this, uri) ?: return null
        val target = File(cacheDir, "image-folder-${System.currentTimeMillis()}")
        target.mkdirs()
        var copied = 0

        fun copyFolder(source: DocumentFile, destination: File) {
            source.listFiles().sortedBy { it.name?.lowercase() }.forEach { child ->
                if (copied >= 500) return@forEach
                val name = safeName(child.name ?: "item-$copied")
                if (child.isDirectory) {
                    copyFolder(child, File(destination, name).apply { mkdirs() })
                } else if (child.isFile && name.substringAfterLast('.', "").lowercase() in
                    setOf("jpg", "jpeg", "png", "webp", "avif", "gif", "tif", "tiff", "bmp")) {
                    val input = contentResolver.openInputStream(child.uri) ?: return@forEach
                    input.use {
                        File(destination, name).outputStream().use { output ->
                            it.copyTo(output, 256 * 1024)
                        }
                    }
                    copied++
                }
            }
        }

        copyFolder(root, target)
        if (copied == 0) null else target
    }.getOrNull()

    private fun safeName(value: String): String {
        val cleaned = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ ()-]"), "_").trim('.', ' ').take(140)
        return cleaned.ifBlank { "import-${System.currentTimeMillis()}" }
    }

    override fun onBackPressed() {
        val login = loginView
        when {
            // A sign-in is several pages — account, then password, then any
            // second factor. Back used to end the whole thing from any of them,
            // so one mistyped password cost the entire login. It walks the
            // login's own history first, and only finishes at its start.
            login != null && login.canGoBack() -> login.goBack()
            login != null -> finishLogin()
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    private fun quote(value: String) =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "") + "\""
}
