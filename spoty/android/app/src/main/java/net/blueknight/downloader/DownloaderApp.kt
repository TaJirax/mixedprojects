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

        // Certificate verification uses certifi; TMPDIR must point somewhere
        // the app owns, because Android has no world-writable /tmp.
        environ.callAttr("__setitem__", "TMPDIR", cacheDir.absolutePath)
        environ.callAttr("__setitem__", "HOME", filesDir.absolutePath)
    }
}
