package net.blueknight.downloader

import android.app.Application
import android.os.Environment
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/**
 * Starts the embedded interpreter once, before anything asks for it.
 *
 * The engine is the same Python the desktop builds run. It discovers where it
 * lives through two environment variables rather than an Android API, which is
 * what lets one copy of the code serve four platforms — so they are set here,
 * before the module is ever imported.
 */
class DownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        val python = Python.getInstance()
        val os = python.getModule("os")
        val environ = os.get("environ")!!

        // Downloads land on shared external storage so a file manager can see
        // them, and survive the app being updated. This path needs no runtime
        // permission on any API level, unlike the public Downloads folder.
        val home = getExternalFilesDir(null) ?: filesDir
        environ.callAttr("__setitem__", "BLUEKNIGHT_HOME", home.absolutePath)
        environ.callAttr("__setitem__", "BLUEKNIGHT_DATA", File(filesDir, "data").absolutePath)

        // FFmpeg and FFprobe ship as jniLibs, which is the only place Android
        // will unpack a file and leave it executable. bundled_tool() looks for
        // them by plain name, so the directory is handed over as-is.
        environ.callAttr("__setitem__", "BLUEKNIGHT_TOOLS", applicationInfo.nativeLibraryDir)

        // The identity every request goes out under, taken from the WebView
        // rather than invented. The sign-in window is a WebView, so this is
        // what the login page really sees; claiming a desktop browser from an
        // Android WebView contradicts the client hints the same WebView sends,
        // and Google reads that contradiction as an unsafe embedded browser.
        // The engine replays the harvested cookies under this same string, so
        // the session is used by the identity that created it.
        // Reading it loads the WebView provider, which is one of the few things
        // that can fail this early on a device with a broken or updating WebView
        // package. The engine has an Android-shaped default for exactly that, so
        // a failure here costs accuracy, never startup.
        try {
            environ.callAttr("__setitem__", "BLUEKNIGHT_UA",
                             android.webkit.WebSettings.getDefaultUserAgent(this))
        } catch (failure: Throwable) {
            android.util.Log.w("BlueKnight", "WebView agent unavailable", failure)
        }

        // Certificate verification uses certifi; TMPDIR must point somewhere
        // the app owns, because Android has no world-writable /tmp.
        environ.callAttr("__setitem__", "TMPDIR", cacheDir.absolutePath)
        environ.callAttr("__setitem__", "HOME", filesDir.absolutePath)
    }
}
