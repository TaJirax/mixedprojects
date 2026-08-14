package com.whitebooster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * white launcher VPN + polished status notification.
 */
class BoostVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBoost()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIF -> {
                pushNotification(applicationContext)
                return START_STICKY
            }
            else -> startBoost(intent)
        }
        return START_STICKY
    }

    private fun startBoost(intent: Intent?) {
        if (BoostState.connected && XrayCore.isRunning()) {
            pushNotification(this)
            return
        }
        val dns = intent?.getStringExtra(EXTRA_DNS) ?: BoostState.activeDns.ifBlank { "8.8.8.8" }
        val session = intent?.getStringExtra(EXTRA_SESSION) ?: "white launcher"
        val rawConfig = intent?.getStringExtra(EXTRA_CONFIG) ?: BoostState.activeConfigRaw

        try {
            BoostState.phase = "connecting"
            BoostState.status = "Connecting…"
            BoostState.coreRunning = false
            BoostState.livePing = null
            BoostState.liveLoss = null
            BoostState.liveJitter = null
            BoostState.liveOk = null
            startForeground(NOTIF_ID, buildNotification(this))

            val splitMode = intent?.getStringExtra(EXTRA_SPLIT_MODE)
                ?: BoostState.splitMode.ifBlank { "full" }
            val pkgList = (intent?.getStringExtra(EXTRA_PACKAGES) ?: BoostState.splitPackages)
                .split(',', ' ', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            BoostState.splitMode = splitMode
            BoostState.splitPackages = pkgList.joinToString(",")

            val builder = Builder()
                .setSession(session)
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns)
                .setBlocking(true)
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

            // Split tunneling:
            // - full: entire device (disallow only this app)
            // - games: only listed game packages enter the tunnel
            if (splitMode == "games" && pkgList.isNotEmpty()) {
                var allowed = 0
                for (pkg in pkgList) {
                    try {
                        builder.addAllowedApplication(pkg)
                        allowed++
                    } catch (_: Exception) {
                    }
                }
                // Always keep white launcher out of its own tunnel path issues
                try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
                BoostState.splitAppCount = allowed
                BoostState.status = "Split · $allowed game app(s)"
            } else {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {
                }
                BoostState.splitMode = "full"
                BoostState.splitAppCount = 0
            }

            val pfd = builder.establish()
            if (pfd == null) {
                BoostState.connected = false
                BoostState.phase = "error"
                BoostState.status = "VPN establish failed"
                pushNotification(this)
                stopSelf()
                return
            }
            tun = pfd
            BoostState.activeDns = dns
            BoostState.connected = true
            BoostState.phase = "binding"
            BoostState.status = "TUN up · starting core…"
            pushNotification(this)

            if (rawConfig.isNotBlank()) {
                val ok = V2RayCoreManager.start(applicationContext, rawConfig, dns, pfd.fd)
                if (ok) {
                    BoostState.status = "Xray ON"
                    BoostState.coreRunning = true
                    BoostState.phase = "live"
                } else {
                    BoostState.status = "DNS/TUN only · ${V2RayCoreManager.lastError.ifBlank { "core fail" }}"
                    BoostState.coreRunning = false
                    BoostState.phase = "live"
                }
            } else {
                BoostState.status = "TUN only · no config"
                BoostState.coreRunning = false
                BoostState.phase = "live"
            }
            pushNotification(this)
        } catch (e: Exception) {
            Log.e(TAG, "startBoost", e)
            BoostState.connected = false
            BoostState.coreRunning = false
            BoostState.phase = "error"
            BoostState.status = "Error: ${e.message}"
            pushNotification(this)
            stopBoost()
            stopSelf()
        }
    }

    private fun stopBoost() {
        try {
            V2RayCoreManager.stop()
        } catch (_: Exception) {
        }
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        BoostState.connected = false
        BoostState.coreRunning = false
        BoostState.phase = "off"
        BoostState.livePing = null
        BoostState.liveLoss = null
        BoostState.liveJitter = null
        BoostState.liveOk = null
        BoostState.status = "Off"
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopBoost()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WGB-VPN"
        const val NOTIF_ID = 42
        const val CHANNEL_ID = "wgb_boost"
        const val ACTION_STOP = "com.whitebooster.app.BOOST_STOP"
        const val ACTION_UPDATE_NOTIF = "com.whitebooster.app.BOOST_UPDATE_NOTIF"
        const val EXTRA_DNS = "dns"
        const val EXTRA_SESSION = "session"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_SPLIT_MODE = "split_mode"
        const val EXTRA_PACKAGES = "packages"

        fun buildNotification(ctx: Context): Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "white launcher",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Connection & path status"
                    setShowBadge(false)
                    lightColor = 0xFF0D9B9B.toInt()
                    if (Build.VERSION.SDK_INT >= 30) setAllowBubbles(false)
                }
                nm.createNotificationChannel(ch)
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stop = PendingIntent.getService(
                ctx, 1,
                Intent(ctx, BoostVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val phase = BoostState.phase
            val connecting = phase == "connecting" || phase == "binding"
            val coreLabel = when {
                connecting -> "…"
                BoostState.coreRunning -> "Xray"
                BoostState.connected -> "TUN"
                phase == "error" -> "Error"
                else -> "Off"
            }
            val title = "white launcher · $coreLabel"
            val node = BoostState.activeConfig.ifBlank { "No node" }
            val dns = BoostState.activeDns.ifBlank { "—" }
            val ping = BoostState.livePing
            val loss = BoostState.liveLoss
            val jit = BoostState.liveJitter

            val line = when {
                connecting -> when (phase) {
                    "binding" -> "TUN ready · starting engine…"
                    else -> "Connecting tunnel…"
                }
                phase == "error" -> BoostState.status.ifBlank { "Connection failed" }
                ping != null -> "Ping ${ping}ms   Loss ${loss ?: 0}%   ±${jit ?: 0}ms"
                BoostState.connected -> "Connected · measuring path…"
                else -> BoostState.status.ifBlank { "Idle" }
            }
            val state = when {
                connecting -> "Connecting"
                phase == "error" -> "Error"
                BoostState.liveOk == true -> "Stable"
                BoostState.liveOk == false -> "Unstable"
                BoostState.connected -> "Live"
                else -> "Off"
            }
            val splitInfo = when {
                BoostState.splitMode == "games" && BoostState.splitAppCount > 0 ->
                    "Split · ${BoostState.splitAppCount} game(s)"
                BoostState.splitMode == "games" -> "Split · games (none matched)"
                else -> "Full device"
            }
            val big = buildString {
                appendLine(line)
                appendLine("Status   $state")
                appendLine("Tunnel   $splitInfo")
                appendLine("Node     $node")
                appendLine("DNS      $dns")
                appendLine("Core     ${if (BoostState.coreRunning) "ON" else if (connecting) "…" else "OFF"}")
                appendLine("Note     Front path · verify in-game")
                if (BoostState.status.isNotBlank() && BoostState.status != line) {
                    appendLine(BoostState.status)
                }
            }

            val b = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(ctx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            val icon = try {
                ctx.resources.getIdentifier("ic_stat_gwd", "drawable", ctx.packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.ic_lock_lock
            } catch (_: Exception) {
                android.R.drawable.ic_lock_lock
            }

            b.setContentTitle(title)
                .setContentText(line)
                .setSubText(state)
                .setStyle(Notification.BigTextStyle().bigText(big).setSummaryText(node.take(40)))
                .setSmallIcon(icon)
                .setColor(0xFF0D9B9B.toInt())
                .setContentIntent(open)
                .setOngoing(BoostState.connected || connecting)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setUsesChronometer(connecting)
                .setWhen(if (connecting) System.currentTimeMillis() else System.currentTimeMillis())

            // System progress — closest thing to notification "animation"
            if (connecting) {
                b.setProgress(0, 0, true)
            } else {
                b.setProgress(0, 0, false)
            }

            if (BoostState.connected || connecting) {
                b.addAction(0, "Disconnect", stop)
            }
            return b.build()
        }

        fun pushNotification(ctx: Context) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!BoostState.connected && BoostState.phase != "connecting" && BoostState.phase != "binding" && BoostState.phase != "error") {
                    nm.cancel(NOTIF_ID)
                    return
                }
                nm.notify(NOTIF_ID, buildNotification(ctx))
            } catch (e: Exception) {
                Log.w(TAG, "notif", e)
            }
        }
    }
}

object BoostState {
    @Volatile var connected: Boolean = false
    @Volatile var coreRunning: Boolean = false
    /** off | connecting | binding | live | error */
    @Volatile var phase: String = "off"
    @Volatile var status: String = "Off"
    @Volatile var coreStatus: String = "Xray OFF"
    @Volatile var activeDns: String = ""
    @Volatile var activeConfig: String = ""
    @Volatile var activeConfigRaw: String = ""
    @Volatile var activeGamePkg: String = ""
    @Volatile var splitMode: String = "full"
    @Volatile var splitPackages: String = ""
    @Volatile var splitAppCount: Int = 0
    @Volatile var lastCoreJson: String = ""
    @Volatile var livePing: Long? = null
    @Volatile var liveLoss: Int? = null
    @Volatile var liveJitter: Long? = null
    @Volatile var liveOk: Boolean? = null
    @Volatile var liveProfile: String = "Casual"
}
