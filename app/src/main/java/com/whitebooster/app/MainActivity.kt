package com.whitebooster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.Base64
import kotlin.system.measureTimeMillis

private val Accent = Color(0xFF6C5CE7)
private val Green = Color(0xFF00C853)
private val Yellow = Color(0xFFFFD600)
private val Red = Color(0xFFFF5252)
private val Bg = Color(0xFF0B0F14)
private val Surface = Color(0xFF141A22)
private val Surface2 = Color(0xFF1C2430)
private val TextP = Color(0xFFE8EAED)
private val TextS = Color(0xFF8B95A5)
private val TextM = Color(0xFF5A6577)

data class DnsItem(
    val n: Int, val ip: String, val provider: String, val tier: Int, val notes: String,
    var pingMs: Long? = null, var lossPct: Int? = null, var jitterMs: Long? = null, var status: String = "—", var gameLabel: String = "", var gameAdvice: String = ""
)

private val DNS_LIST: List<DnsItem> = listOf(
    DnsItem(1, "178.22.122.100", "Shecan", 1, "Primary · anti-sanction"),
    DnsItem(2, "185.51.200.2", "Shecan", 1, "Secondary"),
    DnsItem(3, "10.202.10.202", "403.online", 1, "RFC1918 · games/stores"),
    DnsItem(4, "10.202.10.102", "403.online", 1, "RFC1918 secondary"),
    DnsItem(5, "10.202.10.10", "Radar Game", 1, "RFC1918 · console"),
    DnsItem(6, "10.202.10.11", "Radar Game", 1, "RFC1918 secondary"),
    DnsItem(7, "78.157.42.100", "Electro", 1, "Gaming-focused"),
    DnsItem(8, "78.157.42.101", "Electro", 1, "Secondary"),
    DnsItem(9, "185.55.226.26", "Begzar", 1, "General bypass"),
    DnsItem(10, "185.55.225.25", "Begzar", 1, "Secondary"),
    DnsItem(11, "87.107.110.109", "DNS Pro", 1, "Arvan-adjacent"),
    DnsItem(12, "87.107.110.110", "DNS Pro", 1, "Secondary"),
    DnsItem(13, "193.24.103.1", "DynX", 1, "Public"),
    DnsItem(14, "193.24.103.2", "DynX", 1, "Secondary"),
    DnsItem(15, "10.70.95.150", "DynX", 1, "RFC1918 domestic"),
    DnsItem(16, "10.70.95.162", "DynX", 1, "RFC1918 domestic"),
    DnsItem(17, "172.29.0.100", "Hostiran", 1, "RFC1918"),
    DnsItem(18, "172.29.2.100", "Hostiran", 1, "RFC1918 secondary"),
    DnsItem(19, "10.30.72.17", "Private IP", 1, "RFC1918"),
    DnsItem(20, "10.30.72.18", "Private IP", 1, "RFC1918 secondary"),
    DnsItem(21, "5.200.200.200", "TCI", 2, "ISP · no sanction bypass"),
    DnsItem(22, "217.218.127.127", "TCI", 2, "ISP"),
    DnsItem(23, "185.98.113.113", "AsiaTech", 2, "ISP"),
    DnsItem(24, "185.98.114.114", "AsiaTech", 2, "ISP"),
    DnsItem(25, "85.15.1.14", "Shatel", 2, "ISP"),
    DnsItem(26, "85.15.1.15", "Shatel", 2, "ISP"),
    DnsItem(27, "5.202.100.100", "Pishgaman", 2, "ISP"),
    DnsItem(28, "5.202.100.101", "Pishgaman", 2, "ISP"),
    DnsItem(29, "37.10.64.1", "ParsOnline", 2, "ISP"),
    DnsItem(30, "37.10.65.1", "ParsOnline", 2, "ISP"),
    DnsItem(31, "89.40.90.100", "Sabanet", 2, "ISP"),
    DnsItem(32, "188.158.158.158", "Sabanet", 2, "ISP"),
    DnsItem(33, "185.47.48.122", "Taknet", 2, "ISP"),
    DnsItem(34, "185.142.95.10", "Taknet", 2, "ISP"),
    DnsItem(35, "172.20.11.11", "Zi-Tel", 2, "RFC1918 ISP"),
    DnsItem(36, "172.20.11.12", "Zi-Tel", 2, "RFC1918 ISP"),
    DnsItem(37, "10.104.88.8", "Mobinnet", 2, "RFC1918 ISP"),
    DnsItem(38, "1.1.1.1", "Cloudflare", 3, "International bench"),
    DnsItem(39, "1.0.0.1", "Cloudflare", 3, "Secondary"),
    DnsItem(40, "1.1.1.2", "Cloudflare", 3, "Malware block"),
    DnsItem(41, "1.0.0.2", "Cloudflare", 3, "Malware secondary"),
    DnsItem(42, "1.1.1.3", "Cloudflare", 3, "Family"),
    DnsItem(43, "1.0.0.3", "Cloudflare", 3, "Family secondary"),
    DnsItem(44, "8.8.8.8", "Google", 3, "Standard"),
    DnsItem(45, "8.8.4.4", "Google", 3, "Secondary"),
    DnsItem(46, "9.9.9.9", "Quad9", 3, "Secure"),
    DnsItem(47, "149.112.112.112", "Quad9", 3, "Secure secondary"),
    DnsItem(48, "9.9.9.10", "Quad9", 3, "Unsecured"),
    DnsItem(49, "149.112.112.10", "Quad9", 3, "Unsecured secondary"),
    DnsItem(50, "9.9.9.11", "Quad9", 3, "ECS · CDN steer"),
    DnsItem(51, "149.112.112.11", "Quad9", 3, "ECS secondary"),
    DnsItem(52, "208.67.222.222", "OpenDNS", 3, "Standard"),
    DnsItem(53, "208.67.220.220", "OpenDNS", 3, "Secondary"),
    DnsItem(54, "208.67.222.123", "OpenDNS", 3, "FamilyShield"),
    DnsItem(55, "208.67.220.123", "OpenDNS", 3, "FamilyShield secondary"),
    DnsItem(56, "94.140.14.14", "AdGuard", 3, "Default filter"),
    DnsItem(57, "94.140.15.15", "AdGuard", 3, "Secondary"),
    DnsItem(58, "94.140.14.140", "AdGuard", 3, "Non-filtering"),
    DnsItem(59, "94.140.14.141", "AdGuard", 3, "Non-filtering secondary"),
    DnsItem(60, "94.140.14.15", "AdGuard", 3, "Family"),
    DnsItem(61, "94.140.15.16", "AdGuard", 3, "Family secondary"),
    DnsItem(62, "4.2.2.1", "Level3", 3, "Legacy open"),
    DnsItem(63, "4.2.2.2", "Level3", 3, "Legacy"),
    DnsItem(64, "4.2.2.3", "Level3", 3, "Legacy"),
    DnsItem(65, "4.2.2.4", "Level3", 3, "Legacy"),
    DnsItem(66, "4.2.2.5", "Level3", 3, "Legacy"),
    DnsItem(67, "4.2.2.6", "Level3", 3, "Legacy"),
    DnsItem(68, "8.26.56.26", "Comodo", 3, ""),
    DnsItem(69, "8.20.247.20", "Comodo", 3, ""),
    DnsItem(70, "84.200.69.80", "DNS.Watch", 3, ""),
    DnsItem(71, "84.200.70.40", "DNS.Watch", 3, ""),
    DnsItem(72, "77.88.8.8", "Yandex", 3, "Basic"),
    DnsItem(73, "77.88.8.1", "Yandex", 3, "Secondary"),
    DnsItem(74, "77.88.8.88", "Yandex", 3, "Safe"),
    DnsItem(75, "77.88.8.2", "Yandex", 3, "Safe secondary"),
    DnsItem(76, "77.88.8.7", "Yandex", 3, "Family"),
    DnsItem(77, "77.88.8.3", "Yandex", 3, "Family secondary"),
    DnsItem(78, "185.228.168.9", "CleanBrowsing", 3, "Security"),
    DnsItem(79, "185.228.169.9", "CleanBrowsing", 3, "Security secondary"),
    DnsItem(80, "185.228.168.168", "CleanBrowsing", 3, "Family"),
    DnsItem(81, "185.228.169.168", "CleanBrowsing", 3, "Family secondary"),
    DnsItem(82, "185.228.168.10", "CleanBrowsing", 3, "Adult filter"),
    DnsItem(83, "185.228.169.11", "CleanBrowsing", 3, "Adult secondary"),
    DnsItem(84, "76.76.2.0", "ControlD", 3, "Unfiltered"),
    DnsItem(85, "76.76.10.0", "ControlD", 3, "Unfiltered secondary"),
    DnsItem(86, "76.76.2.1", "ControlD", 3, "Malware"),
    DnsItem(87, "76.76.10.1", "ControlD", 3, "Malware secondary"),
    DnsItem(88, "193.110.81.0", "dns0.eu", 3, "Standard"),
    DnsItem(89, "185.253.5.0", "dns0.eu", 3, "Secondary"),
    DnsItem(90, "193.110.81.9", "dns0.eu", 3, "ZERO filter"),
    DnsItem(91, "185.253.5.9", "dns0.eu", 3, "ZERO secondary"),
    DnsItem(92, "45.90.28.0", "NextDNS", 3, "Anycast bare"),
    DnsItem(93, "45.90.30.0", "NextDNS", 3, "Anycast secondary"),
    DnsItem(94, "194.242.2.2", "Mullvad", 3, "DoH-primary"),
    DnsItem(95, "223.5.5.5", "AliDNS", 3, "East path"),
    DnsItem(96, "223.6.6.6", "AliDNS", 3, "East path"),
    DnsItem(97, "119.29.29.29", "DNSPod", 3, "East path"),
    DnsItem(98, "114.114.114.114", "114DNS", 3, "East path"),
    DnsItem(99, "156.154.70.1", "UltraDNS", 3, "Vercara"),
    DnsItem(100, "156.154.71.1", "UltraDNS", 3, "Vercara secondary")
)

data class V2Node(
    val index: Int, val protocol: String, val host: String, val port: Int, val name: String,
    val security: String, val network: String, val rawPreview: String,
    var tcpMs: Long? = null, var tcpLoss: Int? = null, var tcpJitter: Long? = null,
    var resolveMs: Long? = null, var tlsMs: Long? = null,
    var udpMs: Long? = null, var udpLoss: Int? = null, var udpJitter: Long? = null, var udpStatus: String = "",
    var tcp443Ms: Long? = null, var udp443Status: String = "",
    var udp53Status: String = "", var stunMs: Long? = null, var stunStatus: String = "",
    var reachable: Boolean? = null, var detail: String = "", var gameLabel: String = "", var gameAdvice: String = ""
)

data class TcpProbeResult(val avgMs: Long?, val lossPct: Int, val jitterMs: Long?)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root() }
            }
        }
    }
}

suspend fun pingHost(host: String, timeoutMs: Int = 1200): Long? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(timeoutMs.toLong() + 300) {
        try {
            val t = measureTimeMillis { InetAddress.getByName(host).isReachable(timeoutMs) }
            if (t < timeoutMs) t.coerceIn(1, 999) else null
        } catch (_: Exception) {
            try { measureTimeMillis { InetAddress.getByName(host) }.coerceIn(1, 999) } catch (_: Exception) { null }
        }
    }
}

suspend fun tcpConnect(host: String, port: Int, timeoutMs: Int = 2500): Long? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(timeoutMs.toLong() + 400) {
        try {
            measureTimeMillis { Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) } }.coerceIn(1, 9999)
        } catch (_: Exception) { null }
    }
}

/** Multi-sample TCP for game path: avg / loss% / jitter */
suspend fun probeTcp(host: String, port: Int, probes: Int = 5, timeoutMs: Int = 2000): TcpProbeResult =
    withContext(Dispatchers.IO) {
        if (port <= 0) return@withContext TcpProbeResult(null, 100, null)
        val samples = mutableListOf<Long>()
        repeat(probes) {
            val ms = try {
                measureTimeMillis {
                    Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                }.coerceIn(1, 9999)
            } catch (_: Exception) { null }
            if (ms != null) samples.add(ms)
            try { Thread.sleep(50) } catch (_: Exception) {}
        }
        val loss = ((probes - samples.size) * 100) / probes
        val avg = if (samples.isNotEmpty()) samples.sum() / samples.size else null
        val jitter = when {
            samples.size >= 2 -> samples.maxOrNull()!! - samples.minOrNull()!!
            samples.size == 1 -> 0L
            else -> null
        }
        TcpProbeResult(avg, loss, jitter)
    }

/**
 * Full game-oriented path suite on a host (config endpoint).
 * TCP main port, UDP main port, TCP/UDP 443 (HTTPS/QUIC path), UDP 53, public STUN (UDP baseline).
 */
suspend fun probeGamePath(host: String, port: Int): Map<String, Any?> = withContext(Dispatchers.IO) {
    val resolve = try {
        measureTimeMillis { InetAddress.getByName(host) }.coerceIn(1, 999)
    } catch (_: Exception) { null }
    val tcpMain = if (port > 0) probeTcp(host, port, 5, 2000) else TcpProbeResult(null, 100, null)
    val tcp443 = probeTcp(host, 443, 3, 2000)
    val udpMain = if (port > 0) probeUdp(host, port, 5, 900) else UdpProbeResult(null, 100, null, "FAIL")
    val udp443 = probeUdp(host, 443, 3, 800)
    val udp53 = probeUdp(host, 53, 3, 800)
    // Google public STUN — baseline: can this device send/receive UDP at all?
    val stun = probeUdp("stun.l.google.com", 19302, 3, 1000)
    mapOf(
        "resolve" to resolve,
        "tcp" to tcpMain,
        "tcp443" to tcp443,
        "udp" to udpMain,
        "udp443" to udp443,
        "udp53" to udp53,
        "stun" to stun
    )
}



suspend fun tlsConnect(host: String, port: Int, timeoutMs: Int = 3000): Long? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(timeoutMs.toLong() + 500) {
        try {
            measureTimeMillis {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, host, port, true) as javax.net.ssl.SSLSocket
                ssl.soTimeout = timeoutMs
                ssl.startHandshake()
                ssl.close()
            }.coerceIn(1, 9999)
        } catch (_: Exception) { null }
    }
}


data class UdpProbeResult(
    val avgMs: Long?,
    val lossPct: Int,
    val jitterMs: Long?,
    val status: String // REPLIES | NO_REPLY | BLOCKED | FAIL
)

/**
 * UDP path probe for online games / Hysteria / TUIC / WG-style ports.
 * Most game & proxy ports do NOT echo — "NO_REPLY" with successful sends still means
 * the OS could inject UDP toward the host (path not hard-blocked). REPLIES = real RTT.
 */
suspend fun probeUdp(host: String, port: Int, probes: Int = 5, timeoutMs: Int = 900): UdpProbeResult =
    withContext(Dispatchers.IO) {
        if (port <= 0 || port > 65535) return@withContext UdpProbeResult(null, 100, null, "FAIL")
        val rtts = mutableListOf<Long>()
        var blocked = 0
        var sendFail = 0
        var noReply = 0
        val addr = try {
            InetAddress.getByName(host)
        } catch (_: Exception) {
            return@withContext UdpProbeResult(null, 100, null, "FAIL")
        }
        val payload = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, // lightweight probe marker
            0x57, 0x4F, 0x55, 0x44  // "WOUD" White Options UDP
        )
        repeat(probes) { i ->
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                sock.soTimeout = timeoutMs
                sock.connect(addr, port)
                val packet = DatagramPacket(payload, payload.size, addr, port)
                val t0 = System.nanoTime()
                sock.send(packet)
                val buf = ByteArray(128)
                try {
                    sock.receive(DatagramPacket(buf, buf.size))
                    val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceIn(1, 9999)
                    rtts.add(ms)
                } catch (e: java.net.SocketTimeoutException) {
                    noReply++
                } catch (e: java.net.PortUnreachableException) {
                    blocked++
                }
            } catch (e: java.net.PortUnreachableException) {
                blocked++
            } catch (_: Exception) {
                sendFail++
            } finally {
                try { sock?.close() } catch (_: Exception) {}
            }
            try { Thread.sleep(60) } catch (_: Exception) {}
        }
        val loss = (((probes - rtts.size) * 100) / probes).coerceIn(0, 100)
        val avg = if (rtts.isNotEmpty()) rtts.sum() / rtts.size else null
        val jitter = when {
            rtts.size >= 2 -> rtts.maxOrNull()!! - rtts.minOrNull()!!
            rtts.size == 1 -> 0L
            else -> null
        }
        val status = when {
            rtts.isNotEmpty() -> "REPLIES"
            blocked > probes / 2 -> "BLOCKED"
            sendFail == probes -> "FAIL"
            else -> "NO_REPLY" // UDP left the device; server silent (common)
        }
        UdpProbeResult(avg, loss, jitter, status)
    }

/** avgMs, loss%, jitterMs (max-min of successful samples) */
suspend fun probeDns(ip: String, probes: Int = 5): Triple<Long?, Int, Long?> = withContext(Dispatchers.IO) {
    val samples = mutableListOf<Long>()
    repeat(probes) {
        val ms = try {
            val t = measureTimeMillis { InetAddress.getByName(ip).isReachable(1000) }
            if (t < 1000) t else null
        } catch (_: Exception) {
            try { measureTimeMillis { InetAddress.getByName(ip) } } catch (_: Exception) { null }
        }
        if (ms != null) samples.add(ms)
        try { Thread.sleep(80) } catch (_: Exception) {}
    }
    val loss = ((probes - samples.size) * 100) / probes
    val avg = if (samples.isNotEmpty()) samples.sum() / samples.size else null
    val jitter = if (samples.size >= 2) samples.maxOrNull()!! - samples.minOrNull()!! else if (samples.size == 1) 0L else null
    Triple(avg, loss, jitter)
}

fun gamingScoreDns(avg: Long?, loss: Int, jitter: Long?, tier: Int): Pair<String, String> {
    // Returns label + advice
    if (avg == null) return "Not suitable" to "Unreachable from this network. RFC1918 may need Iranian ISP without VPN."
    var score = 100
    score -= (loss * 1.5).toInt()
    score -= ((jitter ?: 0) / 2).toInt()
    score -= when {
        avg > 120 -> 35; avg > 80 -> 20; avg > 50 -> 10; else -> 0
    }
    if (tier == 3) score -= 15 // international usually worse for sanctioned titles from IR
    if (tier == 1) score += 10
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 80 -> "Excellent for games"
        score >= 65 -> "Good for games"
        score >= 45 -> "Acceptable / casual"
        score >= 25 -> "Poor for competitive"
        else -> "Not suitable for games"
    }
    val advice = when {
        tier == 1 && score >= 60 -> "Tier-1 anti-sanction resolver — best for launchers/stores (Shecan, 403, Electro…). Does not lower in-match ping."
        tier == 2 && score >= 60 -> "ISP DNS — low RTT but zero sanction bypass. Fine if game already works."
        tier == 3 -> "Public international DNS — benchmark only; often a downgrade on Iranian lines for blocked titles."
        loss >= 20 -> "High packet loss — expect rubber-banding if used as system DNS under load."
        (jitter ?: 0) > 40 -> "High jitter (~${jitter}ms swing) — unstable for competitive sessions."
        else -> "Stable enough for menus/patch; in-match lag still depends on your path to game servers."
    }
    return label to advice
}

fun gamingScoreNode(
    tcp: TcpProbeResult?, tls: Long?, resolve: Long?, proto: String,
    udp: UdpProbeResult?,
    tcp443: TcpProbeResult? = null,
    udp443: UdpProbeResult? = null,
    stun: UdpProbeResult? = null
): Pair<String, String> {
    val primary = tls ?: tcp?.avgMs
    if (primary == null && resolve == null && (udp == null || udp.status == "FAIL"))
        return "Not suitable" to "Host unreachable — config will not work from this network."
    if (udp?.status == "BLOCKED" && (stun?.status == "BLOCKED" || stun?.status == "FAIL"))
        return "Not suitable for games" to "UDP blocked both to node and STUN — online games will fail on this network."
    if (primary == null && udp?.status == "BLOCKED")
        return "Not suitable for games" to "UDP path blocked to this host:port — bad for real-time games."
    if (primary == null)
        return "Weak / risky" to "DNS resolves but TCP failed — port filtered or server down."
    var score = 100
    score -= when {
        primary > 200 -> 40; primary > 120 -> 25; primary > 80 -> 12; primary > 50 -> 5; else -> 0
    }
    tcp?.let {
        score -= (it.lossPct / 3)
        score -= ((it.jitterMs ?: 0) / 4).toInt()
    }
    if (tls == null && (proto.contains("VLESS", true) || proto.contains("Trojan", true) || proto.contains("VMess", true)))
        score -= 12
    when (udp?.status) {
        "BLOCKED" -> score -= 35
        "FAIL" -> score -= 20
        "NO_REPLY" -> score -= 4
        "REPLIES" -> {
            val u = udp.avgMs
            if (u != null) {
                score -= when { u > 150 -> 18; u > 100 -> 10; u > 70 -> 5; else -> 0 }
                score -= ((udp.jitterMs ?: 0) / 3).toInt()
                score -= (udp.lossPct / 4)
            }
        }
    }
    // QUIC / HTTPS path (443)
    if (tcp443?.avgMs == null) score -= 5 else if (tcp443.avgMs!! > 150) score -= 5
    when (udp443?.status) {
        "BLOCKED" -> score -= 10
        "REPLIES" -> score += 3
    }
    // Device UDP capability via STUN
    when (stun?.status) {
        "REPLIES" -> score += 5
        "BLOCKED", "FAIL" -> score -= 20
        "NO_REPLY" -> score -= 2
    }
    val udpHeavy = proto.contains("Hysteria", true) || proto.contains("TUIC", true) ||
        proto.contains("WireGuard", true) || proto.contains("QUIC", true)
    if (udpHeavy && udp?.status == "BLOCKED") score -= 12
    score = score.coerceIn(0, 100)
    val label = when {
        score >= 80 -> "Excellent for games"
        score >= 65 -> "Good for games"
        score >= 45 -> "OK for casual / non-ranked"
        score >= 25 -> "Poor for competitive"
        else -> "Not suitable for games"
    }
    val advice = buildString {
        append("TCP ~${primary}ms")
        tcp?.jitterMs?.let { append(" ±${it}ms") }
        tcp?.lossPct?.let { append(" loss ${it}%") }
        append(". ")
        when (udp?.status) {
            "REPLIES" -> append("UDP echo ~${udp.avgMs}ms loss ${udp.lossPct}% ±${udp.jitterMs ?: 0}ms. ")
            "NO_REPLY" -> append("UDP sent OK, silent server (common). ")
            "BLOCKED" -> append("UDP BLOCKED to node. ")
            "FAIL" -> append("UDP send failed. ")
            else -> Unit
        }
        stun?.let {
            append("STUN(UDP baseline)=${it.status}")
            it.avgMs?.let { ms -> append(" ${ms}ms") }
            append(". ")
        }
        tcp443?.avgMs?.let { append("TCP443=${it}ms. ") }
        when {
            primary < 50 && udp?.status != "BLOCKED" && stun?.status == "REPLIES" ->
                append("Strong path for competitive mobile games.")
            primary < 90 && udp?.status != "BLOCKED" ->
                append("Fine for MLBB/BR casual; ranked wants lower jitter.")
            else ->
                append("Expect delay or instability in real-time modes.")
        }
    }
    return label to advice
}



fun splitConfigBlocks(raw: String): List<String> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyList()
    val blocks = mutableListOf<String>()
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        val lower = line.lowercase()
        if (lower.startsWith("vless://") || lower.startsWith("vmess://") || lower.startsWith("trojan://") ||
            lower.startsWith("ss://") || lower.startsWith("ssr://") || lower.startsWith("hy2://") ||
            lower.startsWith("hysteria2://") || lower.startsWith("tuic://") || lower.startsWith("wireguard://")
        ) blocks.add(line)
    }
    if (text.contains("{") && text.contains("}")) {
        var depth = 0
        val buf = StringBuilder()
        for (c in text) {
            if (c == '{') { if (depth == 0) buf.clear(); depth++ }
            if (depth > 0) buf.append(c)
            if (c == '}') {
                depth--
                if (depth == 0 && buf.isNotEmpty()) {
                    val j = buf.toString()
                    if (j.contains("outbounds") || j.contains("vnext") || j.contains("address") || j.contains("server"))
                        blocks.add(j)
                }
            }
        }
    }
    if (text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)) blocks.add(text)
    return blocks.distinct().ifEmpty { if (text.length > 8) listOf(text) else emptyList() }
}

fun parseOneConfig(raw: String, index: Int): V2Node? {
    val t = raw.trim(); if (t.isEmpty()) return null
    val lower = t.lowercase()
    return try {
        when {
            lower.startsWith("vless://") -> parseVless(t, index)
            lower.startsWith("vmess://") -> parseVmess(t, index)
            lower.startsWith("trojan://") -> parseTrojan(t, index)
            lower.startsWith("ss://") -> parseSs(t, index)
            lower.startsWith("hy2://") || lower.startsWith("hysteria2://") -> parseHy2(t, index)
            lower.startsWith("tuic://") -> parseTuic(t, index)
            t.startsWith("{") -> parseJsonNode(t, index)
            t.contains("[Interface]", true) -> parseWg(t, index)
            else -> {
                val m = Regex("""([A-Za-z0-9.\-\[\]]+):(\d{2,5})""").find(t) ?: return null
                V2Node(index, "Custom", m.groupValues[1].removePrefix("[").removeSuffix("]"),
                    m.groupValues[2].toInt(), "node-$index", "—", "—", t.take(80))
            }
        }
    } catch (_: Exception) { null }
}

private fun q(s: String) = s.split("&").mapNotNull {
    val i = it.indexOf("="); if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
}.toMap()

private fun parseVless(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val name = link.substringAfter("#", "vless-$i")
    val uuid = body.substringBefore("@")
    val hp = body.substringAfter("@").substringBefore("?")
    val host = hp.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
    val port = hp.substringAfterLast(":").toIntOrNull() ?: 443
    val p = q(body.substringAfter("?", ""))
    return V2Node(i, "VLESS", host, port, name, p["security"] ?: "none", p["type"] ?: "tcp", "id=${uuid.take(8)}… sni=${p["sni"] ?: host}")
}

private fun parseVmess(link: String, i: Int): V2Node {
    val b64 = link.substringAfter("://").substringBefore("#")
    val json = try { String(Base64.getUrlDecoder().decode(b64)) } catch (_: Exception) { String(Base64.getDecoder().decode(b64)) }
    fun field(k: String) = Regex(""""$k"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1) ?: ""
    fun fieldInt(k: String, d: Int) = Regex(""""$k"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: d
    val host = field("add"); val port = fieldInt("port", 443)
    return V2Node(i, "VMess", host, port, field("ps").ifBlank { "vmess-$i" }, field("tls").ifBlank { "none" }, field("net").ifBlank { "tcp" }, "id=${field("id").take(8)}… aid=${fieldInt("aid", 0)}")
}

private fun parseTrojan(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val name = link.substringAfter("#", "trojan-$i")
    val pass = body.substringBefore("@")
    val hp = body.substringAfter("@").substringBefore("?")
    val host = hp.substringBeforeLast(":"); val port = hp.substringAfterLast(":").toIntOrNull() ?: 443
    val p = q(body.substringAfter("?", ""))
    return V2Node(i, "Trojan", host, port, name, "tls", "tcp", "sni=${p["sni"] ?: host} pass=${pass.take(4)}…")
}

private fun parseSs(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val name = link.substringAfter("#", "ss-$i")
    return try {
        if (body.contains("@")) {
            val dec = String(Base64.getUrlDecoder().decode(body.substringBefore("@")))
            val method = dec.substringBefore(":")
            val hp = body.substringAfter("@")
            V2Node(i, "Shadowsocks", hp.substringBefore(":"), hp.substringAfter(":").substringBefore("/").toIntOrNull() ?: 8388, name, method, "udp/tcp", "method=$method")
        } else {
            val dec = String(Base64.getDecoder().decode(body))
            val method = dec.substringBefore(":")
            val rest = dec.substringAfter("@")
            V2Node(i, "Shadowsocks", rest.substringBefore(":"), rest.substringAfter(":").toIntOrNull() ?: 8388, name, method, "udp/tcp", "method=$method")
        }
    } catch (_: Exception) { V2Node(i, "Shadowsocks", "?", 0, name, "?", "?", body.take(40)) }
}

private fun parseHy2(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val name = link.substringAfter("#", "hy2-$i")
    val hp = body.substringAfter("@").substringBefore("?")
    val host = hp.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
    val port = hp.substringAfterLast(":").toIntOrNull() ?: 443
    val p = q(body.substringAfter("?", ""))
    return V2Node(i, "Hysteria2", host, port, name, "tls", "udp", "sni=${p["sni"] ?: host}")
}

private fun parseTuic(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val name = link.substringAfter("#", "tuic-$i")
    val hp = body.substringAfter("@").substringBefore("?")
    val host = hp.substringBeforeLast(":"); val port = hp.substringAfterLast(":").toIntOrNull() ?: 443
    val p = q(body.substringAfter("?", ""))
    return V2Node(i, "TUIC", host, port, name, "tls", "udp", "sni=${p["sni"] ?: host} cc=${p["congestion_control"] ?: "bbr"}")
}

private fun parseJsonNode(json: String, i: Int): V2Node {
    fun f(k: String) = Regex(""""$k"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
    fun n(k: String) = Regex(""""$k"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
    val host = f("address") ?: f("server") ?: f("add") ?: "?"
    val port = n("port") ?: n("server_port") ?: 443
    val proto = when {
        json.contains("vless", true) -> "VLESS"; json.contains("vmess", true) -> "VMess"
        json.contains("trojan", true) -> "Trojan"; json.contains("shadowsocks", true) -> "Shadowsocks"
        json.contains("hysteria2", true) -> "Hysteria2"; json.contains("tuic", true) -> "TUIC"
        json.contains("wireguard", true) -> "WireGuard"; else -> "JSON"
    }
    return V2Node(i, proto, host, port, "json-$i", f("security") ?: "—", f("network") ?: f("type") ?: "—", json.take(60))
}

private fun parseWg(conf: String, i: Int): V2Node {
    val ep = Regex("""(?i)Endpoint\s*=\s*([^\s#]+)""").find(conf)?.groupValues?.get(1)?.trim()
    val host = ep?.substringBeforeLast(":")?.removePrefix("[")?.removeSuffix("]") ?: "?"
    val port = ep?.substringAfterLast(":")?.toIntOrNull() ?: 51820
    return V2Node(i, "WireGuard", host, port, "wg-$i", "Noise", "udp", "Endpoint=$host:$port")
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Bg, contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_white_options),
                    contentDescription = "White Options",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("White Options", color = TextP, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("DNS · V2Ray checker", color = TextM, fontSize = 11.sp)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Surface, windowInsets = WindowInsets.navigationBars) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Dns, null) },
                    label = { Text("DNS Checker", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, unselectedIconColor = TextM, unselectedTextColor = TextM, indicatorColor = Accent.copy(0.15f))
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Terminal, null) },
                    label = { Text("V2Ray Test", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, unselectedIconColor = TextM, unselectedTextColor = TextM, indicatorColor = Accent.copy(0.15f))
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.SportsEsports, null) },
                    label = { Text("Games", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, unselectedIconColor = TextM, unselectedTextColor = TextM, indicatorColor = Accent.copy(0.15f))
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
            when (tab) { 0 -> DnsCheckerTab(); 1 -> V2RayTestTab(); 2 -> GamesOptimizeTab() }
        }
    }
}

@Composable
fun DnsCheckerTab() {
    var rows by remember { mutableStateOf(DNS_LIST.map { it.copy() }) }
    var filter by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shown = when (filter) {
        1 -> rows.filter { it.tier == 1 }; 2 -> rows.filter { it.tier == 2 }; 3 -> rows.filter { it.tier == 3 }; else -> rows
    }.sortedWith(compareBy({ it.lossPct ?: 999 }, { it.pingMs ?: 9999 }))

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("DNS Checker", color = TextP, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("100 default resolvers · ping + packet loss · operator", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "All", 1 to "Tier1", 2 to "Tier2", 3 to "Tier3").forEach { (v, l) ->
                FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(l, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(0.2f), selectedLabelColor = Accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (scanning) return@Button
                scanning = true; progress = "starting…"
                scope.launch {
                    val target = when (filter) {
                        1 -> rows.filter { it.tier == 1 }; 2 -> rows.filter { it.tier == 2 }; 3 -> rows.filter { it.tier == 3 }; else -> rows
                    }
                    val updated = rows.map { it.copy() }.toMutableList()
                    target.chunked(8).forEachIndexed { bi, batch ->
                        progress = "batch ${bi + 1} · ${batch.size} hosts"
                        val results = withContext(Dispatchers.IO) {
                            batch.map { d -> async {
                                val (avg, loss, jitter) = probeDns(d.ip, 5)
                                val st = when {
                                    avg == null -> "DOWN / filtered"; loss >= 40 -> "HIGH LOSS"
                                    loss >= 15 -> "UNSTABLE"; avg < 40 -> "GOOD"; avg < 80 -> "OK"; else -> "SLOW"
                                }
                                val (gl, ga) = gamingScoreDns(avg, loss, jitter, d.tier)
                                d.ip to listOf(avg, loss, jitter, st, gl, ga)
                            } }.awaitAll()
                        }
                        results.forEach { (ip, data) ->
                            val idx = updated.indexOfFirst { it.ip == ip }
                            if (idx >= 0) {
                                val avg = data[0] as Long?
                                val loss = data[1] as Int
                                val jitter = data[2] as Long?
                                val st = data[3] as String
                                val gl = data[4] as String
                                val ga = data[5] as String
                                updated[idx] = updated[idx].copy(
                                    pingMs = avg, lossPct = loss, jitterMs = jitter,
                                    status = st, gameLabel = gl, gameAdvice = ga
                                )
                            }
                        }
                        rows = updated.toList()
                    }
                    progress = "done · ${target.size} scanned"; scanning = false
                }
            },
            enabled = !scanning, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text(progress, color = Color.White, fontSize = 13.sp)
            } else Text("Scan DNS list", color = Color.White)
        }
        if (progress.isNotEmpty() && !scanning) Text(progress, color = TextM, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.ip }) { d ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${d.n}", color = TextM, fontSize = 11.sp, modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.provider, color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(d.ip, color = TextS, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(d.pingMs?.let { "$it ms" } ?: "—", color = when {
                                    d.pingMs == null -> TextM; d.pingMs!! < 40 -> Green; d.pingMs!! < 80 -> Yellow; else -> Red
                                }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(d.lossPct?.let { "loss $it%" } ?: "loss —", color = when {
                                    d.lossPct == null -> TextM; d.lossPct!! == 0 -> Green; d.lossPct!! < 20 -> Yellow; else -> Red
                                }, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("T${d.tier} · ${d.notes} · ${d.status}", color = TextM, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (d.jitterMs != null) {
                            Text("Jitter ±${d.jitterMs} ms · loss ${d.lossPct ?: 0}%", color = TextS, fontSize = 11.sp)
                        }
                        if (d.gameLabel.isNotEmpty()) {
                            Text(d.gameLabel, color = when {
                                d.gameLabel.startsWith("Excellent") || d.gameLabel.startsWith("Good") -> Green
                                d.gameLabel.startsWith("Acceptable") || d.gameLabel.startsWith("OK") -> Yellow
                                else -> Red
                            }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(d.gameAdvice, color = TextM, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // summary of best for games
            val good = rows.filter { it.gameLabel.startsWith("Excellent") || it.gameLabel.startsWith("Good") }
                .sortedBy { it.pingMs ?: 9999 }.take(5)
            if (good.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Best for games (this scan)", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            good.forEach { g ->
                                Text("• ${g.provider} ${g.ip} · ${g.pingMs}ms · ${g.gameLabel}", color = TextP, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("Checker only · DNS helps launchers/stores; in-match ping needs real path/tunnel", color = TextM, fontSize = 10.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun V2RayTestTab() {
    var text by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<V2Node>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("V2Ray Config Test", color = TextP, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Full game suite: TCP+UDP+TLS+443+STUN on each config", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 200.dp),
            placeholder = {
                Text("vless://…\nvmess://…\ntrojan://…\nss://…\nhy2://…\ntuic://…\n{ JSON }\n\nOne per line or a list — all parsed",
                    color = TextM, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Surface2, focusedTextColor = TextP, unfocusedTextColor = TextP, cursorColor = Accent)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val blocks = splitConfigBlocks(text)
                    nodes = blocks.mapIndexedNotNull { i, b -> parseOneConfig(b, i + 1) }
                    status = "${nodes.size} config(s) parsed from ${blocks.size} block(s)"
                },
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("Parse list", color = TextP, fontSize = 13.sp) }
            Button(
                onClick = {
                    if (text.isBlank()) return@Button
                    busy = true
                    scope.launch {
                        var list = nodes
                        if (list.isEmpty()) {
                            val blocks = splitConfigBlocks(text)
                            list = blocks.mapIndexedNotNull { i, b -> parseOneConfig(b, i + 1) }
                            nodes = list
                        }
                        if (list.isEmpty()) { status = "no valid config found"; busy = false; return@launch }
                        val out = list.toMutableList()
                        out.forEachIndexed { idx, n ->
                            status = "testing ${idx + 1}/${out.size} · full TCP/UDP suite · ${n.host}"
                            val suite = probeGamePath(n.host, n.port)
                            val resolve = suite["resolve"] as Long?
                            val tcp = suite["tcp"] as TcpProbeResult
                            val tcp443 = suite["tcp443"] as TcpProbeResult
                            val udp = suite["udp"] as UdpProbeResult
                            val udp443 = suite["udp443"] as UdpProbeResult
                            val udp53 = suite["udp53"] as UdpProbeResult
                            val stun = suite["stun"] as UdpProbeResult
                            val wantTls = n.security.contains("tls", true) || n.security.contains("reality", true) ||
                                n.port in listOf(443, 8443, 2053, 2083, 2087, 2096) || n.port == 443
                            val tls = if (wantTls && n.port > 0) tlsConnect(n.host, n.port, 3000) else null
                            val ok = tcp.avgMs != null || tls != null || udp.status == "REPLIES" || udp.status == "NO_REPLY"
                            val detail = buildString {
                                append("DNS ${resolve?.let { "$it ms" } ?: "fail"}")
                                append(" · TCP:${n.port} ${tcp.avgMs?.let { "$it ms" } ?: "fail"} loss ${tcp.lossPct}% ±${tcp.jitterMs ?: 0}")
                                if (wantTls) append(" · TLS ${tls?.let { "$it ms" } ?: "fail"}")
                                append(" · UDP:${n.port} ${udp.status}")
                                udp.avgMs?.let { append(" ${it}ms") }
                                append(" loss ${udp.lossPct}%")
                                append(" · TCP443 ${tcp443.avgMs?.let { "$it ms" } ?: "fail"}")
                                append(" · UDP443 ${udp443.status}")
                                append(" · UDP53 ${udp53.status}")
                                append(" · STUN ${stun.status}")
                                stun.avgMs?.let { append(" ${it}ms") }
                                append(" · ${n.security}/${n.network}")
                            }
                            val (gl, ga) = gamingScoreNode(tcp, tls, resolve, n.protocol, udp, tcp443, udp443, stun)
                            out[idx] = n.copy(
                                resolveMs = resolve,
                                tcpMs = tcp.avgMs, tcpLoss = tcp.lossPct, tcpJitter = tcp.jitterMs,
                                tlsMs = tls,
                                udpMs = udp.avgMs, udpLoss = udp.lossPct, udpJitter = udp.jitterMs, udpStatus = udp.status,
                                tcp443Ms = tcp443.avgMs, udp443Status = udp443.status,
                                udp53Status = udp53.status, stunMs = stun.avgMs, stunStatus = stun.status,
                                reachable = ok, detail = detail, gameLabel = gl, gameAdvice = ga
                            )
                            nodes = out.toList()
                        }
                        val best = out.filter { it.reachable == true }.sortedBy { it.tcpMs ?: it.tlsMs ?: 9999 }
                        status = "done · ${out.count { it.reachable == true }}/${out.size} reachable"
                        if (best.isNotEmpty()) {
                            status += " · best: ${best.first().protocol} ${best.first().host} (${best.first().gameLabel})"
                        }
                        busy = false
                    }
                },
                enabled = !busy && text.isNotBlank(), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                else Text("Test all", color = Color.White, fontSize = 13.sp)
            }
        }
        if (status.isNotEmpty()) Text(status, color = TextS, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(nodes, key = { "${it.index}-${it.host}-${it.port}" }) { n ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.protocol, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(n.name, color = TextP, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(when (n.reachable) { true -> "OK"; false -> "FAIL"; null -> "—" },
                                color = when (n.reachable) { true -> Green; false -> Red; null -> TextM },
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text("${n.host}:${n.port}", color = TextS, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(n.rawPreview, color = TextM, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (n.detail.isNotEmpty()) Text(n.detail, color = TextS, fontSize = 11.sp)
                        Text("TCP ${n.tcpMs?.let { "$it ms" } ?: "—"} · loss ${n.tcpLoss ?: "—"}% · jitter ±${n.tcpJitter ?: "—"}", color = TextP, fontSize = 11.sp)
                        Text("UDP ${when {
                            n.udpStatus == "REPLIES" -> "${n.udpMs}ms loss ${n.udpLoss}% ±${n.udpJitter ?: 0}"
                            n.udpStatus.isNotEmpty() -> n.udpStatus
                            else -> "—"
                        }}", color = when (n.udpStatus) {
                            "REPLIES" -> Green; "NO_REPLY" -> Yellow; "BLOCKED", "FAIL" -> Red; else -> TextM
                        }, fontSize = 11.sp)
                        Text("TLS ${n.tlsMs?.let { "$it ms" } ?: "—"} · DNS ${n.resolveMs?.let { "$it ms" } ?: "—"}", color = TextP, fontSize = 11.sp)
                        Text("TCP443 ${n.tcp443Ms?.let { "$it ms" } ?: "—"} · UDP443 ${n.udp443Status.ifEmpty { "—" }} · UDP53 ${n.udp53Status.ifEmpty { "—" }}", color = TextS, fontSize = 10.sp)
                        Text("STUN ${n.stunStatus.ifEmpty { "—" }}${n.stunMs?.let { " · $it ms" } ?: ""}  (device UDP baseline)", color = TextS, fontSize = 10.sp)
                        if (n.gameLabel.isNotEmpty()) {
                            Text(n.gameLabel, color = when {
                                n.gameLabel.startsWith("Excellent") || n.gameLabel.startsWith("Good") -> Green
                                n.gameLabel.startsWith("OK") -> Yellow
                                else -> Red
                            }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(n.gameAdvice, color = TextM, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Text("Suite: TCP×5 · UDP×5 · TLS · TCP/UDP443 · UDP53 · STUN — game path quality", color = TextM, fontSize = 10.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
}



data class GameInfo(
    val name: String,
    val genre: String,
    val pingNeed: String,
    val dnsHint: String,
    val tip: String,
    /** Public hosts used for login / API / CDN — not always the match server IP */
    val hosts: List<String> = emptyList(),
    val tcpPorts: List<Int> = listOf(443, 80),
    val udpPorts: List<Int> = listOf(443, 53)
)

data class GamePingResult(
    val host: String,
    val resolveMs: Long?,
    val tcpMs: Long?,
    val tcpLoss: Int?,
    val udpStatus: String,
    val udpMs: Long?
)

private val ONLINE_GAMES = listOf(
    GameInfo("Mobile Legends: Bang Bang", "MOBA", "Critical <60ms", "Electro / Shecan",
        "Ranked needs low jitter. Hosts are API/CDN — match nodes differ by region.",
        listOf("www.mobilelegends.com", "api.mobilelegends.com", "mlbb-formal.moba.ml.youngjoygame.com", "akm.ml.youngjoygame.com")),
    GameInfo("PUBG Mobile / BGMI", "Battle Royale", "Critical <80ms", "Shecan / 403",
        "UDP-heavy combat. Test hits lobby/API edges, not every battle server.",
        listOf("www.pubgmobile.com", "api.pubgmobile.com", "prod-live-front.igamecj.com", "cloudctrl.igamecj.com")),
    GameInfo("Call of Duty: Mobile", "FPS", "Critical <60ms", "Shecan / Electro",
        "Very sensitive. Activision/Tencent edges vary by account region.",
        listOf("www.callofduty.com", "codm.activision.com", "code.activision.com")),
    GameInfo("Free Fire", "Battle Royale", "High <90ms", "403 / Electro",
        "Garena stack; login and matchmaking domains.",
        listOf("ff.garena.com", "com.dts.freefireth", "login.garena.com", "auth.garena.com")),
    GameInfo("Roblox", "UGC / Platform", "Medium", "Shecan / Cloudflare",
        "Many endpoints; client settings + website are good path samples.",
        listOf("www.roblox.com", "clientsettingscdn.roblox.com", "gamejoin.roblox.com", "economy.roblox.com")),
    GameInfo("Genshin Impact", "Action RPG", "Medium", "Shecan",
        "Hoyoverse SDK/API; combat is partly local.",
        listOf("hk4e-sdk-os.hoyoverse.com", "sdk-os-static.hoyoverse.com", "api-os-takumi.mihoyo.com", "genshin.hoyoverse.com")),
    GameInfo("Honkai: Star Rail", "RPG", "Medium", "Shecan",
        "Same Hoyoverse family as Genshin for account/CDN.",
        listOf("hkrpg-sdk-os.hoyoverse.com", "sdk-os-static.hoyoverse.com", "hsr.hoyoverse.com")),
    GameInfo("Wuthering Waves", "Action RPG", "Medium", "Shecan",
        "Kuro Game / official site path sample.",
        listOf("wutheringwaves.kurogame.com", "www.kurogame.com")),
    GameInfo("League of Legends: Wild Rift", "MOBA", "Critical <70ms", "Shecan",
        "Riot auth/client; often needs anti-sanction DNS in IR.",
        listOf("riotgames.com", "auth.riotgames.com", "clientconfig.rpg.riotgames.com", "wildrift.leagueoflegends.com")),
    GameInfo("Clash of Clans", "Strategy", "Low", "Any stable",
        "Supercell game API — turn-based tolerant.",
        listOf("game.clashofclans.com", "api.clashofclans.com", "supercell.com")),
    GameInfo("Clash Royale", "Strategy", "Medium <100ms", "Any stable",
        "Real-time 1v1; jitter shows as rubber-band.",
        listOf("game.clashroyale.com", "api.clashroyale.com", "supercell.com")),
    GameInfo("Brawl Stars", "Action", "High <80ms", "Electro",
        "Fast sessions; Supercell stack.",
        listOf("game.brawlstarsgame.com", "api.brawlstars.com", "supercell.com")),
    GameInfo("eFootball", "Sports", "High <80ms", "Shecan",
        "Konami online services sample.",
        listOf("www.efootball.com", "efootball.konami.net")),
    GameInfo("EA Sports FC Mobile", "Sports", "High <80ms", "Shecan",
        "EA accounts / FC mobile web edges.",
        listOf("easports.com", "ea.com", "accounts.ea.com")),
    GameInfo("Roblox / Fortnite Epic path", "BR / UGC", "High", "Shecan",
        "Epic + Roblox public edges for comparison.",
        listOf("www.epicgames.com", "account-public-service-prod.ol.epicgames.com", "www.roblox.com")),
    GameInfo("Arena Breakout", "Extraction FPS", "Critical <70ms", "Shecan / Electro",
        "Tencent/Morefun style edges when public.",
        listOf("www.arenabreakout.com", "arenabreakout.morefun.com")),
    GameInfo("Standoff 2", "FPS", "Critical <60ms", "Electro / Shecan",
        "Axlebolt; sensitive competitive FPS.",
        listOf("standoff2.com", "www.standoff2.com")),
    GameInfo("Critical Ops", "FPS", "Critical <60ms", "Electro",
        "Critical Force public site/API sample.",
        listOf("criticalops.com", "www.criticalops.com")),
    GameInfo("Asphalt 9", "Racing", "High <70ms", "Electro",
        "Gameloft services sample.",
        listOf("www.gameloft.com", "asphaltlegends.com")),
    GameInfo("Among Us", "Social", "Low", "Any",
        "Innersloth — latency tolerant.",
        listOf("www.innersloth.com", "amongus.com")),
    GameInfo("Stumble Guys", "Party", "Medium", "Any stable",
        "Scopely / kitka path sample.",
        listOf("www.stumbleguys.com")),
    GameInfo("Albion Online", "MMORPG", "High <90ms", "Shecan",
        "Sandbox full-loot; live.albiononline.com style.",
        listOf("albiononline.com", "live.albiononline.com", "game.albiononline.com")),
    GameInfo("Warframe Mobile", "Loot shooter", "Medium", "Shecan",
        "Digital Extremes content API sample.",
        listOf("www.warframe.com", "content.warframe.com")),
    GameInfo("Diablo Immortal", "ARPG", "High <80ms", "Shecan",
        "Blizzard / NetEase public edges.",
        listOf("diabloimmortal.blizzard.com", "blizzard.com")),
    GameInfo("Minecraft Realms / multiplayer", "Sandbox", "Medium", "Any",
        "Mojang session/auth sample — actual realm IP varies.",
        listOf("www.minecraft.net", "session.minecraft.net", "api.minecraftservices.com")),
    GameInfo("Pokémon GO", "AR", "Medium", "Google / stable",
        "Niantic + Google stack.",
        listOf("pgorelease.nianticlabs.com", "www.nianticlabs.com", "pagoda.nianticlabs.com")),
    GameInfo("Chess.com", "Board", "Low–Medium", "Any",
        "Live blitz needs stable TCP.",
        listOf("www.chess.com", "api.chess.com")),
    GameInfo("Lichess", "Board", "Low–Medium", "Any",
        "Open platform; good baseline.",
        listOf("lichess.org", "www.lichess.org")),
    GameInfo("Hearthstone", "Card", "Low", "Shecan",
        "Blizzard card game — turn-based.",
        listOf("playhearthstone.com", "blizzard.com")),
    GameInfo("World of Tanks Blitz", "Vehicles", "High <80ms", "Shecan",
        "Wargaming mobile path sample.",
        listOf("wotblitz.com", "tanksblitz.com", "wargaming.net")),
    GameInfo("Garena Free Fire MAX", "Battle Royale", "High", "403 / Electro",
        "Same family as Free Fire.",
        listOf("ff.garena.com", "login.garena.com")),
    GameInfo("Honor of Kings / AoV path", "MOBA", "Critical <70ms", "Shecan",
        "Tencent MOBA public marketing domains.",
        listOf("www.levelinfinite.com", "www.arenaofvalor.com")),
    GameInfo("Zenless Zone Zero", "Action", "Medium", "Shecan",
        "Hoyoverse ZZZ SDK sample.",
        listOf("zzz.hoyoverse.com", "nap-sdk-os.hoyoverse.com")),
    GameInfo("Cookie Run: Kingdom", "RPG", "Low–Medium", "Any stable",
        "Devsisters public site.",
        listOf("www.cookierun-kingdom.com", "game.devsisters.com")),
    GameInfo("Call of Duty Warzone Mobile", "FPS BR", "Critical <70ms", "Shecan",
        "Activision Warzone mobile edges.",
        listOf("www.callofduty.com", "warzone.activision.com")),
    GameInfo("Delta Force Mobile", "FPS", "Critical <60ms", "Shecan",
        "Team Jade / Tencent public pages when available.",
        listOf("www.deltaforcemobile.com")),
    GameInfo("Blood Strike", "FPS BR", "High", "Shecan",
        "NetEase light BR sample.",
        listOf("www.bloodstrike.com")),
    GameInfo("Farlight 84", "Battle Royale", "High", "Shecan",
        "Lilith games public edge.",
        listOf("www.farlight84.com")),
    GameInfo("Once Human", "Survival", "Medium", "Shecan",
        "NetEase survival MMO sample.",
        listOf("www.oncehuman.game", "oncehuman.onmtacc.com")),
    GameInfo("State of Survival", "Strategy", "Low", "Any",
        "KingsGroup strategy — mostly async.",
        listOf("www.stateofsurvival.com")),
    GameInfo("Whiteout Survival", "Strategy", "Low", "Any",
        "Async + events.",
        listOf("www.whiteoutsurvival.com")),
    GameInfo("FC Mobile / FIFA path", "Sports", "High", "Shecan",
        "EA sports mobile path.",
        listOf("www.ea.com", "easports.com")),
    GameInfo("Rocket League Sideswipe", "Sports", "Critical <60ms", "Electro",
        "Psyonix / Epic path sample.",
        listOf("www.rocketleague.com", "www.epicgames.com")),
    GameInfo("Shadow Fight 4", "Fighting", "High", "Electro",
        "Nekki public site.",
        listOf("www.nekki.com", "shadowfightarena.com")),
    GameInfo("Identity V", "Asym horror", "Medium", "Shecan",
        "NetEase Identity V.",
        listOf("idv.163.com", "www.identityv.com")),
    GameInfo("Dead by Daylight Mobile", "Asym", "High", "Shecan",
        "Behaviour / NetEase mobile path.",
        listOf("deadbydaylight.com")),
    GameInfo("Genshin Impact (Asia SDK)", "Action RPG", "Medium", "Shecan",
        "Extra Asia SDK host sample for IR players.",
        listOf("hk4e-sdk-os.hoyoverse.com", "api-os-takumi.hoyoverse.com")),
    GameInfo("PUBG New State", "Battle Royale", "Critical", "Shecan / 403",
        "Krafton New State public domains.",
        listOf("www.pubgnewstate.com", "pubgnewstate.com")),
    GameInfo("Supercell trilogy baseline", "Strategy / Action", "Medium",
        "Any stable", "Clash + Brawl shared infrastructure sample.",
        listOf("supercell.com", "game.clashofclans.com", "game.brawlstarsgame.com")),
    GameInfo("Hoyoverse baseline", "RPG", "Medium", "Shecan",
        "Shared Hoyoverse account/CDN path.",
        listOf("hoyoverse.com", "sdk-os-static.hoyoverse.com", "account.hoyoverse.com")),
    GameInfo("Garena baseline", "BR / FPS", "High", "403 / Electro",
        "Garena login stack for FF and related titles.",
        listOf("login.garena.com", "auth.garena.com", "ff.garena.com")),
    GameInfo("Riot baseline", "MOBA / FPS", "Critical", "Shecan",
        "Riot auth — important for WR / Valorant-family.",
        listOf("auth.riotgames.com", "riotgames.com", "clientconfig.rpg.riotgames.com")),
    GameInfo("Google / Play Games baseline", "Platform", "Low", "Any",
        "Control sample for general mobile connectivity.",
        listOf("play.google.com", "googleapis.com", "gstatic.com")),
    GameInfo("Cloudflare edge baseline", "CDN", "Low", "Any",
        "1.1.1.1 / cloudflare.com path quality sample.",
        listOf("cloudflare.com", "1.1.1.1", "www.cloudflare.com"))
)

suspend fun testGamePing(game: GameInfo): List<GamePingResult> = withContext(Dispatchers.IO) {
    val hosts = game.hosts.ifEmpty { return@withContext emptyList() }
    hosts.take(4).map { host ->
        val resolve = try {
            measureTimeMillis { InetAddress.getByName(host) }.coerceIn(1, 999)
        } catch (_: Exception) { null }
        val port = game.tcpPorts.firstOrNull() ?: 443
        val tcp = probeTcp(host, port, 3, 2000)
        val udpPort = game.udpPorts.firstOrNull() ?: 443
        val udp = probeUdp(host, udpPort, 3, 800)
        GamePingResult(host, resolve, tcp.avgMs, tcp.lossPct, udp.status, udp.avgMs)
    }
}

fun summarizeGamePing(game: GameInfo, results: List<GamePingResult>): Pair<String, String> {
    if (results.isEmpty()) return "No hosts" to "No public hosts configured for this title."
    val tcpSamples = results.mapNotNull { it.tcpMs }
    val bestTcp = tcpSamples.minOrNull()
    val avgTcp = if (tcpSamples.isNotEmpty()) tcpSamples.sum() / tcpSamples.size else null
    val udpBlocked = results.count { it.udpStatus == "BLOCKED" }
    val udpReply = results.count { it.udpStatus == "REPLIES" }
    if (bestTcp == null && results.all { it.resolveMs == null })
        return "Unreachable" to "Cannot resolve/connect to public game hosts from this network."
    val label = when {
        bestTcp != null && bestTcp < 60 && udpBlocked == 0 -> "Live path: Excellent"
        bestTcp != null && bestTcp < 90 && udpBlocked == 0 -> "Live path: Good"
        bestTcp != null && bestTcp < 130 -> "Live path: Playable"
        bestTcp != null -> "Live path: High ping"
        else -> "Live path: Weak"
    }
    val advice = buildString {
        append("Best TCP ${bestTcp?.let { "$it ms" } ?: "—"} · avg ${avgTcp?.let { "$it ms" } ?: "—"} · ")
        append("UDP replies $udpReply/${results.size} · blocked $udpBlocked. ")
        append("Need: ${game.pingNeed}. ")
        when {
            game.pingNeed.startsWith("Critical") && (bestTcp == null || bestTcp > 80) ->
                append("Above ideal for competitive play on measured edges.")
            game.pingNeed.startsWith("Critical") && bestTcp != null && bestTcp < 60 ->
                append("Measured edges look fit for ranked if match nodes are similar.")
            else -> append("Use as guidance for login/API path — final match server may differ.")
        }
    }
    return label to advice
}

@Composable
fun GamesOptimizeTab() {
    var q by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Map<String, Pair<List<GamePingResult>, Pair<String, String>>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val filtered = ONLINE_GAMES.filter {
        q.isBlank() || it.name.contains(q, true) || it.genre.contains(q, true) || it.dnsHint.contains(q, true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Game ping", color = TextP, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Real path to public game hosts (API/CDN/login) · TCP+UDP", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            label = { Text("Search game") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Surface2, focusedTextColor = TextP, unfocusedTextColor = TextP, cursorColor = Accent, focusedLabelColor = Accent)
        )
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("What this measures", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Ping to official public hosts (login, API, CDN). Match/battle servers are often different IPs chosen after lobby — this is the closest honest test without the game client.", color = TextS, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.name }) { g ->
                val r = results[g.name]
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(g.name, color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${g.genre} · Need: ${g.pingNeed}", color = when {
                            g.pingNeed.startsWith("Critical") -> Red
                            g.pingNeed.startsWith("High") -> Yellow
                            else -> TextS
                        }, fontSize = 11.sp)
                        Text("DNS hint: ${g.dnsHint}", color = Accent, fontSize = 11.sp)
                        Text(g.tip, color = TextM, fontSize = 10.sp)
                        if (g.hosts.isNotEmpty()) {
                            Text(g.hosts.take(3).joinToString(" · "), color = TextM, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = {
                                if (busyId != null) return@Button
                                busyId = g.name
                                scope.launch {
                                    val list = testGamePing(g)
                                    val sum = summarizeGamePing(g, list)
                                    results = results + (g.name to (list to sum))
                                    busyId = null
                                }
                            },
                            enabled = busyId == null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            if (busyId == g.name) {
                                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Testing…", color = Color.White, fontSize = 12.sp)
                            } else Text("Test real path", color = Color.White, fontSize = 12.sp)
                        }
                        r?.let { (list, sum) ->
                            Text(sum.first, color = when {
                                sum.first.contains("Excellent") || sum.first.contains("Good") -> Green
                                sum.first.contains("Playable") -> Yellow
                                else -> Red
                            }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(sum.second, color = TextS, fontSize = 10.sp)
                            list.forEach { row ->
                                Text(
                                    "${row.host} · DNS ${row.resolveMs ?: "—"} · TCP ${row.tcpMs ?: "—"}ms · UDP ${row.udpStatus}${row.udpMs?.let { " ${it}ms" } ?: ""}",
                                    color = TextM, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
