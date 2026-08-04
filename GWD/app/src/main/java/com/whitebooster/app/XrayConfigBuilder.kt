package com.whitebooster.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/**
 * Builds a minimal Xray JSON config with local SOCKS inbound
 * and one outbound from a share-link or host:port node.
 */
object XrayConfigBuilder {

    fun fromShareLink(link: String, socksPort: Int = 10808, dns: String = "8.8.8.8"): String {
        val trimmed = link.trim()
        val outbound = when {
            trimmed.startsWith("vless://", true) -> vlessOutbound(trimmed)
            trimmed.startsWith("vmess://", true) -> vmessOutbound(trimmed)
            trimmed.startsWith("trojan://", true) -> trojanOutbound(trimmed)
            trimmed.startsWith("ss://", true) -> ssOutbound(trimmed)
            trimmed.startsWith("hy2://", true) || trimmed.startsWith("hysteria2://", true) -> hy2Outbound(trimmed)
            trimmed.startsWith("tuic://", true) -> tuicOutbound(trimmed)
            else -> {
                // host:port fallback as freedom via socks test node
                val m = Regex("""([A-Za-z0-9.\-\[\]]+):(\d{2,5})""").find(trimmed)
                if (m != null) {
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "freedom")
                        .put("settings", JSONObject())
                } else {
                    JSONObject().put("tag", "proxy").put("protocol", "freedom").put("settings", JSONObject())
                }
            }
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put(
            "dns",
            JSONObject().put(
                "servers",
                JSONArray().put(dns).put("localhost")
            )
        )
        root.put(
            "inbounds",
            JSONArray()
                .put(
                    JSONObject()
                        .put("tag", "socks-in")
                        .put("port", socksPort)
                        .put("listen", "127.0.0.1")
                        .put("protocol", "socks")
                        .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
                        .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))
                )
                .put(
                    JSONObject()
                        .put("tag", "http-in")
                        .put("port", socksPort + 1)
                        .put("listen", "127.0.0.1")
                        .put("protocol", "http")
                        .put("settings", JSONObject())
                )
        )
        root.put(
            "outbounds",
            JSONArray()
                .put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject()))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", JSONObject()))
        )
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put("outboundTag", "proxy")
                            .put("network", "tcp,udp")
                    )
                )
        )
        return root.toString(2)
    }

    fun fromNode(node: V2Node, rawLink: String, socksPort: Int, dns: String): String {
        val link = rawLink.ifBlank {
            // rebuild minimal custom
            "${node.host}:${node.port}"
        }
        return fromShareLink(link, socksPort, dns)
    }

    private fun query(s: String): Map<String, String> =
        s.split("&").mapNotNull {
            val i = it.indexOf("=")
            if (i > 0) it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8") else null
        }.toMap()

    private fun vlessOutbound(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val id = body.substringBefore("@")
        val rest = body.substringAfter("@", "")
        val host = rest.substringBefore(":").substringBefore("?").removePrefix("[").removeSuffix("]")
        val after = rest.substringAfter(":", "")
        val port = after.substringBefore("?").toIntOrNull() ?: 443
        val q = query(after.substringAfter("?", ""))
        val security = q["security"] ?: "none"
        val network = q["type"] ?: "tcp"
        val sni = q["sni"] ?: q["host"] ?: host
        val fp = q["fp"] ?: "chrome"
        val pbk = q["pbk"]
        val sid = q["sid"]
        val spx = q["spx"]
        val flow = q["flow"] ?: ""
        val path = q["path"] ?: "/"
        val hostHeader = q["host"] ?: sni

        val user = JSONObject().put("id", id).put("encryption", "none")
        if (flow.isNotBlank()) user.put("flow", flow)

        val vnext = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        val stream = JSONObject().put("network", network)
        when (security) {
            "tls" -> stream.put(
                "security", "tls"
            ).put("tlsSettings", JSONObject().put("serverName", sni).put("fingerprint", fp).put("allowInsecure", q["allowInsecure"] == "1"))
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp)
                if (!pbk.isNullOrBlank()) reality.put("publicKey", pbk)
                if (!sid.isNullOrBlank()) reality.put("shortId", sid)
                if (!spx.isNullOrBlank()) reality.put("spiderX", spx)
                stream.put("security", "reality").put("realitySettings", reality)
            }
            else -> stream.put("security", "none")
        }
        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().put("path", path).put("headers", JSONObject().put("Host", hostHeader)))
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"] ?: path))
            "h2", "http" -> stream.put("httpSettings", JSONObject().put("path", path).put("host", JSONArray().put(hostHeader)))
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
    }

    private fun vmessOutbound(link: String): JSONObject {
        val raw = link.substringAfter("://").substringBefore("#")
        val json = try {
            String(Base64.getDecoder().decode(raw.replace('-', '+').replace('_', '/')))
        } catch (_: Exception) {
            try {
                String(Base64.getUrlDecoder().decode(raw))
            } catch (_: Exception) {
                "{}"
            }
        }
        fun f(k: String) = Regex(""""$k"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1) ?: ""
        fun n(k: String, d: Int) = Regex(""""$k"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: d
        val host = f("add").ifBlank { f("host") }
        val port = n("port", 443)
        val id = f("id")
        val aid = n("aid", 0)
        val net = f("net").ifBlank { "tcp" }
        val tls = f("tls")
        val path = f("path").ifBlank { "/" }
        val hostH = f("host").ifBlank { host }
        val scy = f("scy").ifBlank { "auto" }

        val user = JSONObject().put("id", id).put("alterId", aid).put("security", scy)
        val vnext = JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))
        val stream = JSONObject().put("network", net)
        if (tls == "tls") {
            stream.put("security", "tls").put("tlsSettings", JSONObject().put("serverName", hostH))
        }
        if (net == "ws") {
            stream.put("wsSettings", JSONObject().put("path", path).put("headers", JSONObject().put("Host", hostH)))
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
    }

    private fun trojanOutbound(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val password = body.substringBefore("@")
        val rest = body.substringAfter("@", "")
        val host = rest.substringBefore(":").substringBefore("?").removePrefix("[").removeSuffix("]")
        val after = rest.substringAfter(":", "")
        val port = after.substringBefore("?").toIntOrNull() ?: 443
        val q = query(after.substringAfter("?", ""))
        val sni = q["sni"] ?: q["peer"] ?: host
        val network = q["type"] ?: "tcp"
        val stream = JSONObject().put("network", network).put("security", "tls")
            .put("tlsSettings", JSONObject().put("serverName", sni).put("allowInsecure", q["allowInsecure"] == "1"))
        if (network == "ws") {
            stream.put("wsSettings", JSONObject().put("path", q["path"] ?: "/").put("headers", JSONObject().put("Host", q["host"] ?: sni)))
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("password", password)
            )))
            .put("streamSettings", stream)
    }

    private fun ssOutbound(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val decoded = try {
            if ("@" in body) body else String(Base64.getDecoder().decode(body.replace('-', '+').replace('_', '/')))
        } catch (_: Exception) {
            body
        }
        val methodPass = decoded.substringBefore("@")
        val method = methodPass.substringBefore(":")
        val password = methodPass.substringAfter(":")
        val rest = decoded.substringAfter("@", "")
        val host = rest.substringBefore(":").removePrefix("[").removeSuffix("]")
        val port = rest.substringAfter(":").substringBefore("/").toIntOrNull() ?: 443
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("method", method.ifBlank { "aes-128-gcm" })
                            .put("password", password)
                    )
                )
            )
    }

    private fun hy2Outbound(link: String): JSONObject {
        // Xray may not support hy2 natively in all builds — use freedom fallback tag
        val body = link.substringAfter("://").substringBefore("#")
        val rest = body.substringAfter("@", body)
        val host = rest.substringBefore(":").substringBefore("?").removePrefix("[").removeSuffix("]")
        val port = rest.substringAfter(":").substringBefore("?").toIntOrNull() ?: 443
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put("settings", JSONObject())
            .put("streamSettings", JSONObject())
            .put("_gwd_note", "hy2:$host:$port")
    }

    private fun tuicOutbound(link: String): JSONObject {
        val body = link.substringAfter("://").substringBefore("#")
        val rest = body.substringAfter("@", body)
        val host = rest.substringBefore(":").substringBefore("?").removePrefix("[").removeSuffix("]")
        val port = rest.substringAfter(":").substringBefore("?").toIntOrNull() ?: 443
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put("settings", JSONObject())
            .put("_gwd_note", "tuic:$host:$port")
    }

    fun matchRaw(paste: String, node: V2Node): String {
        val lines = paste.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val hit = lines.firstOrNull {
            it.contains(node.host) && (
                it.startsWith("vless://", true) || it.startsWith("vmess://", true) ||
                    it.startsWith("trojan://", true) || it.startsWith("ss://", true) ||
                    it.startsWith("hy2://", true) || it.startsWith("hysteria2://", true) ||
                    it.startsWith("tuic://", true) || it.startsWith("{")
                )
        }
        return hit ?: node.rawPreview
    }
}
