package com.whitebooster.app

import android.content.Context
import android.util.Log

/** Facade over XrayCore (libv2ray AAR). */
object V2RayCoreManager {
    private const val TAG = "GWD-Core"
    @Volatile var lastError: String = ""
    @Volatile var coreReady: Boolean = false

    fun isRunning(): Boolean = XrayCore.isRunning()

    fun start(ctx: Context, shareLink: String, dns: String = "8.8.8.8", tunFd: Int = -1): Boolean {
        lastError = ""
        coreReady = false
        return try {
            XrayCore.init(ctx.applicationContext)
            val json = try {
                XrayCore.buildConfig(shareLink, dns)
            } catch (_: Exception) {
                XrayConfigBuilder.fromShareLink(shareLink, 10808, dns)
            }
            BoostState.lastCoreJson = json
            if (tunFd < 0) {
                lastError = "TUN fd required"
                return false
            }
            XrayCore.start(ctx.applicationContext, json, tunFd)
            coreReady = XrayCore.isRunning()
            BoostState.coreRunning = coreReady
            BoostState.coreStatus = if (coreReady) "Xray ON" else "Xray started"
            Log.i(TAG, BoostState.coreStatus)
            true
        } catch (e: Exception) {
            lastError = e.message ?: "core failed"
            BoostState.coreStatus = "Core error: $lastError"
            BoostState.coreRunning = false
            coreReady = false
            Log.e(TAG, "start", e)
            false
        }
    }

    fun stop() {
        try { XrayCore.stop() } catch (_: Exception) {}
        coreReady = false
        BoostState.coreRunning = false
        BoostState.coreStatus = "Xray OFF"
    }
}
