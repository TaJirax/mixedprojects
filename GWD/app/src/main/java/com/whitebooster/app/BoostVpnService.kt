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
 * GWD Boost VPN + gaming monitor notification.
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
        if (BoostState.connected && XrayCore.isRunning()) return
        val dns = intent?.getStringExtra(EXTRA_DNS) ?: BoostState.activeDns.ifBlank { "8.8.8.8" }
        val session = intent?.getStringExtra(EXTRA_SESSION) ?: "GWD Boost"
        val rawConfig = intent?.getStringExtra(EXTRA_CONFIG) ?: BoostState.activeConfigRaw

        try {
            startForeground(NOTIF_ID, buildNotification(this))

            val builder = Builder()
                .setSession(session)
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns)
                .setBlocking(true)
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            val pfd = builder.establish()
            if (pfd == null) {
                BoostState.connected = false
                BoostState.status = "VPN establish failed"
                stopSelf()
                return
            }
            tun = pfd
            BoostState.activeDns = dns
            BoostState.connected = true

            if (rawConfig.isNotBlank()) {
                val ok = V2RayCoreManager.start(applicationContext, rawConfig, dns, pfd.fd)
                if (ok) {
                    BoostState.status = "Xray ON"
                    BoostState.coreRunning = true
                } else {
                    BoostState.status = "DNS only · ${V2RayCoreManager.lastError.ifBlank { "core fail" }}"
                    BoostState.coreRunning = false
                }
            } else {
                BoostState.status = "DNS only · no config"
                BoostState.coreRunning = false
            }
            pushNotification(this)
        } catch (e: Exception) {
            Log.e(TAG, "startBoost", e)
            BoostState.connected = false
            BoostState.status = "Error: ${e.message}"
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
        private const val TAG = "GWD-BoostVPN"
        const val NOTIF_ID = 42
        const val CHANNEL_ID = "gwd_boost"
        const val ACTION_STOP = "com.whitebooster.app.BOOST_STOP"
        const val ACTION_UPDATE_NOTIF = "com.whitebooster.app.BOOST_UPDATE_NOTIF"
        const val EXTRA_DNS = "dns"
        const val EXTRA_SESSION = "session"
        const val EXTRA_CONFIG = "config"

        fun buildNotification(ctx: Context): Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "GWD Gaming",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live path monitor"
                    setShowBadge(false)
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
            val title = when {
                BoostState.coreRunning -> "GWD · Xray"
                BoostState.connected -> "GWD · VPN"
                else -> "GWD"
            }
            val ping = BoostState.livePing?.let { "${it}ms" } ?: "—"
            val loss = BoostState.liveLoss?.let { "$it%" } ?: "—"
            val jit = BoostState.liveJitter?.let { "±${it}" } ?: "—"
            val mark = when (BoostState.liveOk) {
                true -> "OK"
                false -> "HIGH"
                null -> "…"
            }
            val profile = BoostState.liveProfile.ifBlank { "Casual" }
            val node = BoostState.activeConfig.ifBlank { "—" }
            val text = "$ping  loss $loss  $jit  $mark · $profile"
            val big = buildString {
                appendLine(text)
                appendLine("Node  $node")
                appendLine("Core  ${if (BoostState.coreRunning) "ON" else "OFF"}")
                if (BoostState.activeDns.isNotBlank()) appendLine("DNS   ${BoostState.activeDns} (manual)")
                append(BoostState.status)
            }
            val b = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(ctx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            return b
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(big))
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "Stop", stop)
                .build()
        }

        fun pushNotification(ctx: Context) {
            if (!BoostState.connected) return
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
    @Volatile var status: String = "Off"
    @Volatile var coreStatus: String = "Xray OFF"
    @Volatile var activeDns: String = ""
    @Volatile var activeConfig: String = ""
    @Volatile var activeConfigRaw: String = ""
    @Volatile var activeGamePkg: String = ""
    @Volatile var lastCoreJson: String = ""
    @Volatile var livePing: Long? = null
    @Volatile var liveLoss: Int? = null
    @Volatile var liveJitter: Long? = null
    @Volatile var liveOk: Boolean? = null
    @Volatile var liveProfile: String = "Casual"
}
