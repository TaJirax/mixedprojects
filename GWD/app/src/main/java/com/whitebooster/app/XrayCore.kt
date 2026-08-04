package com.whitebooster.app

import android.content.Context
import android.util.Base64
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Xray core bridge (AndroidLibXrayLite / libv2ray).
 * Builds outbound JSON from share links and runs core against TUN fd.
 */
object XrayCore {
    private const val TAG = "GWD-Xray"
    @Volatile private var controller: CoreController? = null
    @Volatile private var envReady = false

    fun init(ctx: Context) {
        if (envReady) return
        synchronized(this) {
            if (envReady) return
            try {
                Libv2ray.touch()
                val dir = ctx.filesDir.absolutePath
                Libv2ray.initCoreEnv(dir, "")
                envReady = true
                Log.i(TAG, "core env ready · ${Libv2ray.checkVersionX()}")
            } catch (e: Throwable) {
                Log.e(TAG, "initCoreEnv failed", e)
                throw e
            }
        }
    }

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (_: Throwable) {
        "unavailable"
    }

    fun isRunning(): Boolean = try {
        controller?.isRunning == true
    } catch (_: Throwable) {
        false
    }

    /**
     * Start Xray with [configJson] bound to TUN [fd].
     */
    @Synchronized
    fun start(ctx: Context, configJson: String, fd: Int) {
        init(ctx)
        stop()
        val cb = object : CoreCallbackHandler {
            override fun onEmitStatus(p0: Long, p1: String?): Long {
                Log.i(TAG, "status $p0 $p1")
                if (p1 != null) BoostState.status = p1
                return 0
            }
            override fun shutdown(): Long = 0
            override fun startup(): Long = 0
        }
        val c = Libv2ray.newCoreController(cb)
        c.startLoop(configJson, fd)
        controller = c
        BoostState.coreRunning = true
        BoostState.status = "Xray core ON · fd=$fd"
    }

    @Synchronized
    fun stop() {
        try {
            controller?.stopLoop()
        } catch (e: Throwable) {
            Log.w(TAG, "stopLoop", e)
        }
        controller = null
        BoostState.coreRunning = false
    }

    /** Build full xray config from share-link / JSON + DNS */
    fun buildConfig(raw: String, dns: String): String {
        val trimmed = raw.trim()
        val outbound = when {
            trimmed.startsWith("{") && trimmed.contains("outbounds") -> return injectDns(trimmed, dns)
            trimmed.startsWith("{") -> outboundFromJsonSnippet(trimmed)
            trimmed.startsWith("vless://", true) -> outboundVless(trimmed)
            trimmed.startsWith("vmess://", true) -> outboundVmess(trimmed)
            trimmed.startsWith("trojan://", true) -> outboundTrojan(trimmed)
            trimmed.startsWith("ss://", true) -> outboundSs(trimmed)
            else -> throw IllegalArgumentException("Unsupported config for core")
        }
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("dns", JSONObject().put("servers", JSONArray().put(dns).put("1.1.1.1")))
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "tun-in")
                    .put("port", 0)
                    .put("protocol", "dokodemo-door")
                    .put("settings", JSONObject().put("network", "tcp,udp").put("followRedirect", true))
                    .put("sniffing", JSONObject().put("enabled", true)
                        .put("destOverride", JSONArray().put("http").put("tls").put("quic")))
            )
        )
        // Also local socks for diagnostics
        root.getJSONArray("inbounds").put(
            JSONObject()
                .put("tag", "socks")
                .put("port", 10808)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
        )
        root.put("outbounds", JSONArray().put(outbound).put(
            JSONObject().put("tag", "direct").put("protocol", "freedom")
        ).put(
            JSONObject().put("tag", "block").put("protocol", "blackhole")
        ))
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray()
                        .put(JSONObject().put("type", "field").put("outboundTag", "proxy")
                            .put("network", "tcp,udp"))
                )
        )
        return root.toString()
    }

    private fun injectDns(full: String, dns: String): String {
        return try {
            val o = JSONObject(full)
            o.put("dns", JSONObject().put("servers", JSONArray().put(dns).put("1.1.1.1")))
            o.toString()
        } catch (_: Exception) {
            full
        }
    }

    private fun outboundFromJsonSnippet(json: String): JSONObject {
        val o = JSONObject(json)
        if (o.has("protocol")) {
            if (!o.has("tag")) o.put("tag", "proxy")
            return o
        }
        // try first outbound
        val outs = o.optJSONArray("outbounds")
        if (outs != null && outs.length() > 0) {
            val first = outs.getJSONObject(0)
            first.put("tag", "proxy")
            return first
        }
        throw IllegalArgumentException("No outbound in JSON")
    }

    private fun outboundVless(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val uuid = body.substringBefore("@")
        val rest = body.substringAfter("@")
        val hostPort = rest.substringBefore("?")
        val host = hostPort.substringBefore(":").removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfter(":").toIntOrNull() ?: 443
        val q = parseQuery(rest.substringAfter("?", ""))
        val security = q["security"] ?: "none"
        val network = q["type"] ?: "tcp"
        val sni = q["sni"] ?: q["host"] ?: host
        val fp = q["fp"] ?: "chrome"
        val flow = q["flow"] ?: ""
        val pbk = q["pbk"] ?: ""
        val sid = q["sid"] ?: ""
        val spx = q["spx"] ?: ""

        val user = JSONObject().put("id", uuid).put("encryption", "none")
        if (flow.isNotBlank()) user.put("flow", flow)

        val vnext = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        val stream = JSONObject().put("network", network)
        when (security) {
            "tls" -> stream.put("security", "tls").put(
                "tlsSettings",
                JSONObject().put("serverName", sni).put("fingerprint", fp).put("allowInsecure", false)
            )
            "reality" -> stream.put("security", "reality").put(
                "realitySettings",
                JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp)
                    .put("publicKey", pbk)
                    .put("shortId", sid)
                    .put("spiderX", spx)
            )
            else -> stream.put("security", "none")
        }
        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject().put("path", q["path"] ?: "/").put(
                    "headers",
                    JSONObject().put("Host", q["host"] ?: sni)
                )
            )
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", q["serviceName"] ?: q["path"] ?: "")
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject().put("path", q["path"] ?: "/").put("host", q["host"] ?: sni)
            )
            "xhttp", "splithttp" -> stream.put(
                "xhttpSettings",
                JSONObject().put("path", q["path"] ?: "/").put("host", q["host"] ?: sni)
            )
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
    }

    private fun outboundVmess(link: String): JSONObject {
        val raw = link.substringAfter("://").substringBefore("#")
        val jsonStr = try {
            String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        } catch (_: Exception) {
            String(Base64.decode(raw, Base64.DEFAULT))
        }
        val j = JSONObject(jsonStr)
        val host = j.optString("add").ifBlank { j.optString("host") }
        val port = j.optInt("port", 443)
        val id = j.optString("id")
        val aid = j.optInt("aid", 0)
        val net = j.optString("net", "tcp")
        val tls = j.optString("tls")
        val sni = j.optString("sni").ifBlank { j.optString("host", host) }
        val path = j.optString("path", "/")
        val user = JSONObject().put("id", id).put("alterId", aid).put("security", j.optString("scy", "auto"))
        val vnext = JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))
        val stream = JSONObject().put("network", net)
        if (tls == "tls") {
            stream.put("security", "tls").put("tlsSettings", JSONObject().put("serverName", sni))
        }
        if (net == "ws") {
            stream.put("wsSettings", JSONObject().put("path", path).put("headers", JSONObject().put("Host", sni)))
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
    }

    private fun outboundTrojan(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val password = URLDecoder.decode(body.substringBefore("@"), "UTF-8")
        val rest = body.substringAfter("@")
        val hostPort = rest.substringBefore("?")
        val host = hostPort.substringBefore(":").removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfter(":").toIntOrNull() ?: 443
        val q = parseQuery(rest.substringAfter("?", ""))
        val sni = q["sni"] ?: q["peer"] ?: host
        val network = q["type"] ?: "tcp"
        val server = JSONObject().put("address", host).put("port", port).put("password", password)
        val stream = JSONObject().put("network", network).put("security", "tls")
            .put("tlsSettings", JSONObject().put("serverName", sni).put("allowInsecure", q["allowInsecure"] == "1"))
        if (network == "ws") {
            stream.put(
                "wsSettings",
                JSONObject().put("path", q["path"] ?: "/").put("headers", JSONObject().put("Host", q["host"] ?: sni))
            )
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream)
    }

    private fun outboundSs(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val decoded = if ("@" in body) body else try {
            String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        } catch (_: Exception) {
            String(Base64.decode(body, Base64.DEFAULT))
        }
        val methodPass = decoded.substringBefore("@")
        val method = methodPass.substringBefore(":")
        val password = methodPass.substringAfter(":")
        val hostPort = decoded.substringAfter("@")
        val host = hostPort.substringBefore(":").removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfter(":").substringBefore("/").toIntOrNull() ?: 443
        val server = JSONObject().put("address", host).put("port", port).put("method", method).put("password", password)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&").mapNotNull {
            val i = it.indexOf("=")
            if (i <= 0) null
            else {
                val k = URLDecoder.decode(it.substring(0, i), "UTF-8")
                val v = URLDecoder.decode(it.substring(i + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }
}
