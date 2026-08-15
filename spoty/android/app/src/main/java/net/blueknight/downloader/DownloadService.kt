package net.blueknight.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import java.io.File

/**
 * Keeps a job alive while the app is not in front.
 *
 * A playlist takes longer than the screen stays on, and Android is free to
 * stop a process whose Activity is no longer visible. A foreground service is
 * the only promise the system honours, and the notification is the price of
 * making it.
 *
 * The service does not run the download — the engine's own thread does. It
 * exists to hold the process open, so it watches the engine's state and stops
 * itself once there is nothing left to protect. That keeps the whole thing to
 * one signal, `is_downloading`, rather than a second lifecycle for the page
 * and the engine to disagree about.
 */
class DownloadService : Service() {

    private var baseline: Map<String, Pair<Long, Long>> = emptyMap()

    private val watcher = Thread {
        try {
            val bridge = Python.getInstance().getModule("android_bridge")
            // A job takes a moment to leave its starting state; polling
            // immediately would see idle and stop the service at once.
            Thread.sleep(1_500)
            while (!Thread.currentThread().isInterrupted) {
                if (!bridge.callAttr("is_working").toBoolean()) break
                Thread.sleep(2_000)
            }
        } catch (_: InterruptedException) {
        } finally {
            exportCompletedFiles()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        baseline = outputSnapshot()
        startForeground(NOTIFICATION_ID, buildNotification())
        watcher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val bridge = Python.getInstance().getModule("android_bridge")
            bridge.callAttr("call", "stop_download", "[]")
            // Media/document conversion isn't interruptible in the shared
            // engine yet. Keep protecting the process if the stop request did
            // not actually make it idle.
            if (!bridge.callAttr("is_working").toBoolean()) stopSelf()
            return START_NOT_STICKY
        }
        // Not sticky: a job killed with the process cannot be resumed from
        // where it stopped, and restarting the service without one would show
        // a notification for work that is not happening.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.channel_downloads),
                    NotificationManager.IMPORTANCE_LOW))
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notice_working))
            .setContentText(getString(R.string.notice_working_detail))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    /** Copy files created by this job into BlueKnight Downloader in the picked tree. */
    private fun exportCompletedFiles() {
        val saved = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(EXPORT_TREE, null) ?: return
        val root = outputRoot()
        if (!root.isDirectory) return
        val destination = ensureExportFolder(this, Uri.parse(saved)) ?: return

        outputSnapshot().forEach { (relative, stamp) ->
            if (baseline[relative] == stamp) return@forEach
            val source = File(root, relative)
            runCatching { copyToTree(source, relative, destination) }
        }
    }

    private fun outputRoot() = File(getExternalFilesDir(null) ?: filesDir,
        "BlueKnightdownloader")

    private fun outputSnapshot(): Map<String, Pair<Long, Long>> {
        val root = outputRoot()
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown().filter { it.isFile }.associate { file ->
            file.relativeTo(root).invariantSeparatorsPath to (file.length() to file.lastModified())
        }
    }

    private fun copyToTree(source: File, relative: String, root: DocumentFile) {
        val parts = relative.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var folder = root
        parts.dropLast(1).forEach { name ->
            folder = folder.findFile(name)?.takeIf { it.isDirectory }
                ?: folder.createDirectory(name) ?: return
        }
        val name = parts.last()
        folder.findFile(name)?.delete()
        val extension = source.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val target = folder.createFile(mime, name) ?: return
        contentResolver.openOutputStream(target.uri, "w")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output, 256 * 1024) }
        }
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "net.blueknight.downloader.STOP"
        const val PREFS = "blueknight_android"
        const val EXPORT_TREE = "export_tree"
        const val EXPORT_FOLDER = "BlueKnight Downloader"

        /** Create or find the public root while preserving the selected tree permission. */
        fun ensureExportFolder(context: Context, treeUri: Uri): DocumentFile? {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (tree.name.equals(EXPORT_FOLDER, ignoreCase = true)) return tree
            return tree.listFiles().firstOrNull {
                it.isDirectory && it.name.equals(EXPORT_FOLDER, ignoreCase = true)
            }
                ?: tree.createDirectory(EXPORT_FOLDER)
        }

        /** Called when a job starts. Harmless if one is already running. */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
