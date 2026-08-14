package com.whitebooster.app

import android.os.Build
import android.os.Bundle
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
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

object GwdPrefs {
    private const val NAME = "gwd_prefs"
    private const val KEY_CONFIG = "saved_config"
    fun loadConfig(ctx: Context): String =
        try { ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_CONFIG, "") ?: "" } catch (_: Exception) { "" }
    fun saveConfig(ctx: Context, value: String) {
        try {
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_CONFIG, value).apply()
        } catch (_: Exception) {}
    }
}

private val Green = Color(0xFF00C853)
private val Yellow = Color(0xFFFFD600)
private val Red = Color(0xFFFF5252)

private val _Accent = androidx.compose.runtime.mutableStateOf(Color(0xFF0D9B9B))
private val _Bg = androidx.compose.runtime.mutableStateOf(Color(0xFF0A0F0F))
private val _Surface = androidx.compose.runtime.mutableStateOf(Color(0xFF121A1A))
private val _Surface2 = androidx.compose.runtime.mutableStateOf(Color(0xFF1A2626))
private val _TextP = androidx.compose.runtime.mutableStateOf(Color(0xFFE8EAED))
private val _TextS = androidx.compose.runtime.mutableStateOf(Color(0xFF8B95A5))
private val _TextM = androidx.compose.runtime.mutableStateOf(Color(0xFF5A6577))

private val Accent: Color get() = _Accent.value
private val Bg: Color get() = _Bg.value
private val Surface: Color get() = _Surface.value
private val Surface2: Color get() = _Surface2.value
private val TextP: Color get() = _TextP.value
private val TextS: Color get() = _TextS.value
private val TextM: Color get() = _TextM.value

private fun applyDayNight(dark: Boolean) {
    if (dark) {
        _Accent.value = Color(0xFF0D9B9B)
        _Bg.value = Color(0xFF0A0F0F)
        _Surface.value = Color(0xFF121A1A)
        _Surface2.value = Color(0xFF1A2626)
        _TextP.value = Color(0xFFE8EAED)
        _TextS.value = Color(0xFF8B95A5)
        _TextM.value = Color(0xFF5A6577)
    } else {
        _Accent.value = Color(0xFF0B7F7F)
        _Bg.value = Color(0xFFF4F7F7)
        _Surface.value = Color(0xFFFFFFFF)
        _Surface2.value = Color(0xFFE6EEEE)
        _TextP.value = Color(0xFF101418)
        _TextS.value = Color(0xFF5A6577)
        _TextM.value = Color(0xFF8B95A5)
    }
}

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

/** Survives tab switches — long-running scan jobs outside composable scope */
object ScanHub {
    private val supervisor = SupervisorJob()
    val scope = CoroutineScope(supervisor + Dispatchers.Main.immediate)

    var dnsRows by mutableStateOf(emptyList<DnsItem>())
    var dnsScanning by mutableStateOf(false)
    var dnsProgress by mutableStateOf("")
    private var dnsJob: Job? = null
    @Volatile private var dnsStop = false

    var v2Text by mutableStateOf("")
    var v2Nodes by mutableStateOf<List<V2Node>>(emptyList())
    var v2Busy by mutableStateOf(false)
    var v2Status by mutableStateOf("")
    private var v2Job: Job? = null
    @Volatile private var v2Stop = false

    /** Pipeline caption for DNS tab: what the app is doing */
    var flowStep by mutableStateOf(0) // 0 idle · 1 config tested · 2 dns scanning · 3 best ready
    var flowCaption by mutableStateOf("Paste a config in Test · then scan DNS here to find the best resolver.")
    var bestDnsIp by mutableStateOf<String?>(null)
    var bestDnsDetail by mutableStateOf("")

    fun ensureDns() {
        if (dnsRows.isEmpty()) dnsRows = DNS_LIST.map { it.copy() }
    }

    fun stopDns() {
        dnsStop = true
        dnsJob?.cancel()
        dnsScanning = false
        dnsProgress = "stopped"
    }

    fun startDns(filter: Int) {
        if (dnsScanning) return
        ensureDns()
        dnsStop = false
        dnsScanning = true
        dnsProgress = "starting…"
        dnsJob = scope.launch {
            try {
                val rowsSnap = dnsRows
                val target = when (filter) {
                    1 -> rowsSnap.filter { it.tier == 1 }
                    2 -> rowsSnap.filter { it.tier == 2 }
                    3 -> rowsSnap.filter { it.tier == 3 }
                    else -> rowsSnap
                }
                val updated = rowsSnap.map { it.copy() }.toMutableList()
                for ((bi, batch) in target.chunked(8).withIndex()) {
                    if (dnsStop || !isActive) break
                    dnsProgress = "batch ${bi + 1} · ${batch.size} hosts"
                    val results = withContext(Dispatchers.IO) {
                        batch.map { d ->
                            async {
                                if (dnsStop) return@async d.ip to listOf<Any?>(null, 100, null, "STOPPED", "", "")
                                val (avg, loss, jitter) = probeDns(d.ip, 5)
                                val st = when {
                                    avg == null -> "DOWN / filtered"
                                    loss >= 40 -> "HIGH LOSS"
                                    loss >= 15 -> "UNSTABLE"
                                    avg < 40 -> "GOOD"
                                    avg < 80 -> "OK"
                                    else -> "SLOW"
                                }
                                val (gl, ga) = gamingScoreDns(avg, loss, jitter, d.tier)
                                d.ip to listOf(avg, loss, jitter, st, gl, ga)
                            }
                        }.awaitAll()
                    }
                    if (dnsStop || !isActive) break
                    results.forEach { (ip, data) ->
                        val ix = updated.indexOfFirst { it.ip == ip }
                        if (ix >= 0) {
                            updated[ix] = updated[ix].copy(
                                pingMs = data[0] as Long?,
                                lossPct = data[1] as Int,
                                jitterMs = data[2] as Long?,
                                status = data[3] as String,
                                gameLabel = data[4] as String,
                                gameAdvice = data[5] as String
                            )
                        }
                    }
                    dnsRows = updated.toList()
                }
                dnsProgress = if (dnsStop) "stopped" else "done · ${target.size} scanned"
                if (!dnsStop) {
                    val best = dnsRows
                        .filter { it.pingMs != null }
                        .sortedWith(compareBy({ it.lossPct ?: 999 }, { it.pingMs ?: 9999L }, { it.jitterMs ?: 9999L }))
                        .firstOrNull()
                    if (best != null) {
                        bestDnsIp = best.ip
                        bestDnsDetail = "${best.provider} · ${best.pingMs}ms · loss ${best.lossPct}% · ${best.gameLabel}"
                        flowStep = 3
                        flowCaption = "Best DNS: ${best.ip} (${best.provider}) · ${best.pingMs}ms loss ${best.lossPct}%. Set yourself (Private DNS). DNS helps resolve; it does not set in-match ping."
                    } else {
                        flowStep = 2
                        flowCaption = "DNS scan finished — no strong resolver. Keep system DNS or try Tier-1 again."
                    }
                }
            } finally {
                dnsScanning = false
            }
        }
    }

    fun markConfigTested() {
        val best = v2Nodes.filter { it.reachable == true }
            .sortedWith(compareBy({ it.tcpLoss ?: 100 }, { it.tcpMs ?: 9999L }))
            .firstOrNull()
        flowStep = 1
        flowCaption = if (best != null) {
            "Config OK: ${best.protocol} ${best.host}:${best.port} · ${best.gameLabel}. Next: scan DNS (Tier-1) to pick the best resolver."
        } else {
            "Config tested — no reachable node. Fix the link, then scan DNS for resolvers."
        }
    }

    fun beginDnsFlow() {
        flowStep = 2
        flowCaption = "Scanning DNS list (ping · loss · jitter)… wait for best resolver."
        startDns(1)
    }

    fun stopV2() {
        v2Stop = true
        v2Job?.cancel()
        v2Busy = false
        v2Status = "stopped"
    }

    fun startV2() {
        if (v2Busy) return
        val text = v2Text
        if (text.isBlank()) {
            v2Status = "paste a config first"
            return
        }
        v2Stop = false
        v2Busy = true
        v2Job = scope.launch {
            try {
                var list = v2Nodes
                if (list.isEmpty()) {
                    list = parseAllConfigs(text)
                    v2Nodes = list
                }
                if (list.isEmpty()) {
                    v2Status = "no valid config found"
                    return@launch
                }
                val out = list.toMutableList()
                for ((idx, n) in out.withIndex()) {
                    if (v2Stop || !isActive) break
                    v2Status = "testing ${idx + 1}/${out.size} · full TCP/UDP suite · ${n.host}"
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
                    v2Nodes = out.toList()
                }
                if (v2Stop) {
                    v2Status = "stopped"
                } else {
                    val best = out.filter { it.reachable == true }.sortedBy { it.tcpMs ?: it.tlsMs ?: 9999 }
                    v2Status = "done · ${out.count { it.reachable == true }}/${out.size} reachable"
                    if (best.isNotEmpty()) {
                        v2Status += " · best: ${best.first().protocol} ${best.first().host} (${best.first().gameLabel})"
                    }
                    markConfigTested()
                }
            } finally {
                v2Busy = false
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = GwdPrefs.loadConfig(this)
        if (saved.isNotBlank() && ScanHub.v2Text.isBlank()) ScanHub.v2Text = saved
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            applyDayNight(dark)
            val scheme = if (dark) {
                darkColorScheme(primary = Accent, background = Bg, surface = Surface, onBackground = TextP, onSurface = TextP)
            } else {
                lightColorScheme(primary = Accent, background = Bg, surface = Surface, onBackground = TextP, onSurface = TextP)
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                )
            }
            MaterialTheme(
                colorScheme = scheme,
                typography = Typography(
                    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
                    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
                    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
                    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                )
            ) {
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
                    .createSocket(socket, host, port, true) as SSLSocket
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
        score >= 80 -> "Excellent path (DNS)"
        score >= 65 -> "Good path (DNS)"
        score >= 45 -> "Acceptable / casual"
        score >= 25 -> "Poor for competitive"
        else -> "Not suitable for games"
    }
    val advice = when {
        tier == 1 && score >= 60 -> "Tier-1 resolver for launchers/stores. Does not lower in-match ping."
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
        score >= 80 -> "Excellent path score"
        score >= 65 -> "Good path score"
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

private fun shareParts(link: String): Triple<String, Int, Map<String, String>> {
    val body = link.substringAfter("://").substringBefore("#")
    val endpoint = body.substringAfterLast("@").substringBefore("?")
    val host = endpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
    val port = endpoint.substringAfterLast(":", "443").toIntOrNull() ?: 443
    val params = body.substringAfter("?", "").split("&").mapNotNull {
        val p = it.indexOf('=')
        if (p > 0) it.substring(0, p) to it.substring(p + 1) else null
    }.toMap()
    return Triple(host, port, params)
}

private fun parseShareNode(link: String, i: Int, protocol: String): V2Node {
    val (host, port, params) = shareParts(link)
    val name = link.substringAfter("#", "$protocol-$i").ifBlank { "$protocol-$i" }
    val security = params["security"] ?: if (protocol == "Trojan") "tls" else "none"
    val network = params["type"] ?: if (protocol == "Hysteria2" || protocol == "TUIC") "udp" else "tcp"
    return V2Node(i, protocol, host, port, name, security, network, link.take(80))
}

private fun parseVless(link: String, i: Int) = parseShareNode(link, i, "VLESS")
private fun parseTrojan(link: String, i: Int) = parseShareNode(link, i, "Trojan")
private fun parseHy2(link: String, i: Int) = parseShareNode(link, i, "Hysteria2")
private fun parseTuic(link: String, i: Int) = parseShareNode(link, i, "TUIC")

private fun parseSs(link: String, i: Int): V2Node {
    val body = link.substringAfter("://").substringBefore("#")
    val decoded = if ('@' in body) body else try {
        String(Base64.getUrlDecoder().decode(body.substringBefore("?")))
    } catch (_: Exception) { body }
    val endpoint = decoded.substringAfterLast("@").substringBefore("?")
    val host = endpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
    val port = endpoint.substringAfterLast(":", "8388").toIntOrNull() ?: 8388
    return V2Node(i, "Shadowsocks", host, port, link.substringAfter("#", "ss-$i"), "AEAD", "tcp/udp", link.take(80))
}

private fun parseVmess(link: String, i: Int): V2Node {
    val encoded = link.substringAfter("://").substringBefore("#")
    val json = try {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        String(Base64.getUrlDecoder().decode(padded))
    } catch (_: Exception) { "" }
    fun field(name: String) = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
    val host = field("add") ?: field("address") ?: return parseShareNode(link, i, "VMess")
    val port = (field("port") ?: "443").toIntOrNull() ?: 443
    return V2Node(i, "VMess", host, port, field("ps") ?: "vmess-$i", field("tls") ?: "none", field("net") ?: "tcp", link.take(80))
}

private fun parseJsonNode(json: String, i: Int): V2Node {
    fun field(name: String) = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
    fun number(name: String) = Regex("\\\"$name\\\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
    val protocol = field("protocol") ?: "JSON"
    val host = field("address") ?: field("server") ?: "unknown"
    val port = number("port") ?: field("port")?.toIntOrNull() ?: 443
    return V2Node(i, protocol, host, port, "json-$i", field("security") ?: "none", field("network") ?: field("type") ?: "tcp", json.take(80))
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

/** Expand paste into share-link / JSON / WG blocks */
fun parseAllConfigs(raw: String): List<V2Node> {
    val blocks = splitConfigBlocks(raw)
    val out = mutableListOf<V2Node>()
    var idx = 1
    for (b in blocks) {
        parseOneConfig(b, idx)?.let {
            out.add(it)
            idx++
        }
    }
    return out.distinctBy { "${it.protocol}:${it.host}:${it.port}" }
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
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Accent,
        selectedTextColor = Accent,
        unselectedIconColor = TextM,
        unselectedTextColor = TextM,
        indicatorColor = Accent.copy(0.18f)
    )
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("white launcher", color = TextP, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.3.sp)
                    Text("Bypass · DNS · Games", color = TextM, fontSize = 12.sp, letterSpacing = 0.2.sp)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Terminal, null) },
                    label = { Text("Bypass", fontSize = 11.sp) },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Dns, null) },
                    label = { Text("DNS", fontSize = 11.sp) },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.SportsEsports, null) },
                    label = { Text("Games", fontSize = 11.sp) },
                    colors = navColors
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // Shared short tweens — less jank, consistent feel
                    val enter = tween<IntOffset>(durationMillis = 160, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    val exit = tween<IntOffset>(durationMillis = 120, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    val forward = targetState > initialState
                    val slideIn = if (forward) {
                        slideInHorizontally(animationSpec = enter, initialOffsetX = { it / 6 })
                    } else {
                        slideInHorizontally(animationSpec = enter, initialOffsetX = { -it / 6 })
                    }
                    val slideOut = if (forward) {
                        slideOutHorizontally(animationSpec = exit, targetOffsetX = { -it / 6 })
                    } else {
                        slideOutHorizontally(animationSpec = exit, targetOffsetX = { it / 6 })
                    }
                    (slideIn + fadeIn(animationSpec = tween(160)))
                        .togetherWith(slideOut + fadeOut(animationSpec = tween(120)))
                        .using(SizeTransform(clip = false))
                },
                label = "tabs"
            ) { page ->
                when (page) {
                    0 -> BypassTab()
                    1 -> DnsCheckerTab()
                    2 -> GamesOptimizeTab()
                }
            }
        }
    }
}

@Composable
fun DnsCheckerTab() {
    ScanHub.ensureDns()
    val scanning = ScanHub.dnsScanning
    val progress = ScanHub.dnsProgress
    val caption = ScanHub.flowCaption
    val bestIp = ScanHub.bestDnsIp
    val bestDetail = ScanHub.bestDnsDetail
    // Top optimized only (hide full list)
    val top = ScanHub.dnsRows
        .filter { it.pingMs != null && (it.lossPct ?: 100) <= 20 }
        .sortedWith(compareBy({ it.lossPct ?: 999 }, { it.pingMs ?: 9999L }, { it.jitterMs ?: 9999L }))
        .take(5)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("DNS", color = TextP, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        Text("Public & private resolvers · relay quality for games", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Relay check", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(caption, color = TextS, fontSize = 12.sp, lineHeight = 17.sp)
                if (progress.isNotBlank()) Text(progress, color = TextM, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    ScanHub.flowStep = 2
                    ScanHub.flowCaption = "Scanning resolvers… measuring relay delay, loss, jitter."
                    ScanHub.startDns(0)
                },
                enabled = !scanning,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(if (scanning) "Scanning…" else "Scan", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            Button(
                onClick = { ScanHub.stopDns() },
                enabled = scanning,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("Stop", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(14.dp))
        if (bestIp != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Best relay", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(bestIp, color = TextP, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    if (bestDetail.isNotBlank()) Text(bestDetail, color = TextS, fontSize = 12.sp)
                    Text("Set this DNS yourself (Private DNS / Wi-Fi). WGB only suggests.", color = TextM, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (top.isEmpty() && !scanning) {
                item {
                    Text("Tap Scan. Only the best gaming resolvers will appear here.", color = TextM, fontSize = 13.sp)
                }
            }
            items(top, key = { it.ip }) { d ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.provider, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(d.gameLabel.ifBlank { d.status }, color = when {
                                d.gameLabel.startsWith("Excellent") || d.gameLabel.startsWith("Good") -> Green
                                d.gameLabel.startsWith("OK") -> Yellow
                                else -> TextS
                            }, fontSize = 12.sp)
                        }
                        Text(d.ip, color = TextP, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text(
                            "Relay ${d.pingMs ?: "—"}ms · loss ${d.lossPct ?: "—"}% · jitter ±${d.jitterMs ?: "—"}",
                            color = TextS, fontSize = 12.sp
                        )
                        if (d.gameAdvice.isNotBlank()) Text(d.gameAdvice, color = TextM, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BypassTab() {
    val ctx = LocalContext.current
    val text = ScanHub.v2Text
    val nodes = ScanHub.v2Nodes
    val busy = ScanHub.v2Busy
    val status = ScanHub.v2Status
    var connMsg by remember { mutableStateOf("") }
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var launchPkg by remember { mutableStateOf<String?>(null) }
    var splitMode by remember { mutableStateOf(BoostState.splitMode.ifBlank { "games" }) } // full | games
    val scope = rememberCoroutineScope()
    val pm = ctx.packageManager
    val gamePkgs = remember {
        mapOf(
            "Mobile Legends: Bang Bang" to "com.mobile.legends",
            "PUBG Mobile" to "com.tencent.ig",
            "BGMI" to "com.pubg.imobile",
            "Free Fire" to "com.dts.freefireth",
            "Free Fire MAX" to "com.dts.freefiremax",
            "Call of Duty: Mobile" to "com.activision.callofduty.shooter",
            "Genshin Impact" to "com.miHoYo.GenshinImpact",
            "Roblox" to "com.roblox.client",
            "Minecraft" to "com.mojang.minecraftpe",
            "Clash of Clans" to "com.supercell.clashofclans",
            "Clash Royale" to "com.supercell.clashroyale",
            "Brawl Stars" to "com.supercell.brawlstars",
            "eFootball" to "jp.konami.pesam",
            "EA Sports FC Mobile" to "com.ea.gp.fifamobile",
            "Standoff 2" to "com.axlebolt.standoff2",
            "Arena of Valor" to "com.garena.game.kgvn",
            "League of Legends: Wild Rift" to "com.riotgames.mobile.leagueconnect"
        )
    }
    val installedGames = remember {
        ONLINE_GAMES.mapNotNull { g ->
            val pkg = g.packageName ?: gamePkgs[g.name] ?: return@mapNotNull null
            try {
                pm.getPackageInfo(pkg, 0)
                g to pkg
            } catch (_: Exception) { null }
        }
    }

    fun ranked(): List<V2Node> = nodes
        .filter { it.reachable != false }
        .sortedWith(
            compareBy<V2Node>(
                { if (it.reachable == true) 0 else 1 },
                { it.tcpLoss ?: 100 },
                { predictLoss(it) },
                { it.tcpMs ?: 9999L },
                { it.tcpJitter ?: 9999L }
            )
        )

    fun pickRaw(n: V2Node? = null): String {
        val best = n ?: ranked().firstOrNull { it.reachable == true }
        if (best != null) {
            val hit = text.lines().map { it.trim() }.firstOrNull { it.contains(best.host) && "://" in it }
            if (hit != null) return hit
            return XrayConfigBuilder.matchRaw(text, best)
        }
        return text.lines().map { it.trim() }.firstOrNull {
            it.startsWith("vless://", true) || it.startsWith("vmess://", true) ||
                it.startsWith("trojan://", true) || it.startsWith("ss://", true) ||
                it.startsWith("hy2://", true) || it.startsWith("hysteria2://", true) ||
                it.startsWith("tuic://", true) || it.startsWith("{")
        } ?: text.trim()
    }

    fun startVpnWith(raw: String) {
        BoostState.activeConfigRaw = raw
        val dns = ScanHub.bestDnsIp ?: BoostState.activeDns.ifBlank { "8.8.8.8" }
        val pkgs = if (splitMode == "games") {
            installedGames.map { it.second }.distinct().joinToString(",")
        } else ""
        BoostState.splitMode = splitMode
        BoostState.splitPackages = pkgs
        val i = Intent(ctx, BoostVpnService::class.java).apply {
            putExtra(BoostVpnService.EXTRA_CONFIG, raw)
            putExtra(BoostVpnService.EXTRA_DNS, dns)
            putExtra(BoostVpnService.EXTRA_SESSION, "white launcher · ${BoostState.activeConfig.ifBlank { "Xray" }}")
            putExtra(BoostVpnService.EXTRA_SPLIT_MODE, splitMode)
            putExtra(BoostVpnService.EXTRA_PACKAGES, pkgs)
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        connMsg = "Connecting TUN…"
        scope.launch {
            kotlinx.coroutines.delay(900)
            connMsg = when {
                BoostState.coreRunning && BoostState.splitMode == "games" ->
                    "Path live · split games · check in-match ping"
                BoostState.coreRunning -> "Path live · full tunnel · check in-match ping"
                BoostState.connected -> "TUN up · core off · path limited"
                else -> BoostState.status
            }
            // after connect, optional DNS scan tip
            if (ScanHub.bestDnsIp == null && !ScanHub.dnsScanning) {
                ScanHub.flowCaption = "Connected. Front path is up. Measure in-game. High loss/jitter → disconnect and pick next ranked node."
            }
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val list = ranked()
            val n = list.getOrNull(selectedIdx) ?: list.firstOrNull { it.reachable == true }
            if (n != null) BoostState.activeConfig = "${n.protocol} ${n.host}:${n.port}"
            startVpnWith(pickRaw(n))
        } else connMsg = "VPN permission denied"
    }

    fun doConnect() {
        val list = ranked()
        val n = list.getOrNull(selectedIdx) ?: list.firstOrNull { it.reachable == true }
        val raw = pickRaw(n)
        if (raw.isBlank()) {
            connMsg = "Paste config list first"
            return
        }
        if (n != null) BoostState.activeConfig = "${n.protocol} ${n.host}:${n.port}"
        val prep = VpnService.prepare(ctx)
        if (prep != null) vpnLauncher.launch(prep) else startVpnWith(raw)
    }

    fun doDisconnect() {
        ctx.startService(Intent(ctx, BoostVpnService::class.java).setAction(BoostVpnService.ACTION_STOP))
        connMsg = "Disconnected"
    }

    fun launchGame(pkg: String) {
        try {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launch)
                connMsg = "Launching $pkg"
            } else connMsg = "Cannot launch $pkg"
        } catch (e: Exception) {
            connMsg = "Launch failed: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Bypass", color = TextP, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        Text("Rank path · Games only · in-match is the real test", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                ScanHub.v2Text = it
                GwdPrefs.saveConfig(ctx, it)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 120.dp),
            placeholder = {
                Text("Paste full config list (one per line)…", color = TextM, fontSize = 12.sp)
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Surface2,
                focusedTextColor = TextP, unfocusedTextColor = TextP, cursorColor = Accent
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    ScanHub.v2Nodes = parseAllConfigs(ScanHub.v2Text)
                    GwdPrefs.saveConfig(ctx, ScanHub.v2Text)
                    ScanHub.startV2()
                },
                enabled = !busy && text.isNotBlank(),
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (busy) "Scanning…" else "Scan & Rank", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Button(
                onClick = { ScanHub.stopV2() },
                enabled = busy,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("Stop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tunnel mode", color = TextM, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = splitMode == "games",
                onClick = { if (!BoostState.connected) splitMode = "games" },
                enabled = !BoostState.connected,
                label = { Text("Games only", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(0.25f),
                    selectedLabelColor = Accent
                )
            )
            FilterChip(
                selected = splitMode == "full",
                onClick = { if (!BoostState.connected) splitMode = "full" },
                enabled = !BoostState.connected,
                label = { Text("Full device", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(0.25f),
                    selectedLabelColor = Accent
                )
            )
        }
        if (splitMode == "games") {
            Text(
                if (installedGames.isEmpty()) "No known games installed — install a title or use Full device."
                else "Split: ${installedGames.size} installed game(s) will use the tunnel.",
                color = TextS, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { if (BoostState.connected) doDisconnect() else doConnect() },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (BoostState.connected) Red else Accent)
        ) {
            Text(
                if (BoostState.connected) "Disconnect"
                else if (splitMode == "games") "Connect · Games only"
                else "Connect · Full device",
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
        val phase = BoostState.phase
        val showStatus = connMsg.isNotBlank() || status.isNotBlank() || BoostState.connected || phase == "connecting" || phase == "binding"
        androidx.compose.animation.AnimatedVisibility(
            visible = showStatus,
            enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + androidx.compose.animation.shrinkVertically(tween(150))
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dot = when {
                        phase == "connecting" || phase == "binding" -> Yellow
                        BoostState.coreRunning -> Green
                        BoostState.connected -> Accent
                        phase == "error" -> Red
                        else -> TextM
                    }
                    Box(Modifier.size(10.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                phase == "connecting" -> "Connecting tunnel…"
                                phase == "binding" -> "Starting engine…"
                                BoostState.coreRunning && BoostState.splitMode == "games" -> "Connected · Xray · Split"
                                BoostState.coreRunning -> "Connected · Xray"
                                BoostState.connected && BoostState.splitMode == "games" -> "Connected · Split"
                                BoostState.connected -> "Connected · TUN"
                                phase == "error" -> "Connection error"
                                else -> "Status"
                            },
                            color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        val sub = listOf(connMsg, status).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(sub, color = TextS, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (BoostState.livePing != null) {
                        Text(
                            "${BoostState.livePing}ms",
                            color = if (BoostState.liveOk == true) Green else if (BoostState.liveOk == false) Red else TextS,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        // Launch installed games after connect
        if (BoostState.connected && installedGames.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Launch game", color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(installedGames, key = { it.second }) { (g, pkg) ->
                    FilterChip(
                        selected = launchPkg == pkg,
                        onClick = {
                            launchPkg = pkg
                            launchGame(pkg)
                        },
                        label = { Text(g.name.take(18), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(0.25f),
                            selectedLabelColor = Accent
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val list = ranked()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            if (list.isEmpty()) {
                item {
                    Text("Paste many configs → Scan & Rank → pick one → Connect → launch game.", color = TextM, fontSize = 13.sp)
                }
            }
            items(list.size) { i ->
                val n = list[i]
                val pred = predictLoss(n)
                val selected = selectedIdx == i
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Accent.copy(0.15f) else Surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selectedIdx = i }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${i + 1}", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(n.protocol, color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                when (n.reachable) { true -> "OK"; false -> "FAIL"; null -> "—" },
                                color = when (n.reachable) { true -> Green; false -> Red; null -> TextM },
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }
                        Text("${n.host}:${n.port}", color = TextS, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(
                            "Front ${n.tcpMs ?: "—"}ms · loss ${n.tcpLoss ?: "—"}% · pred ~$pred% · ±${n.tcpJitter ?: "—"}",
                            color = TextP, fontSize = 11.sp
                        )
                        Text(
                            "Path score · not in-match ping. High loss → pick another node.",
                            color = TextM, fontSize = 10.sp
                        )
                        if (n.gameLabel.isNotEmpty()) {
                            Text(n.gameLabel, color = when {
                                n.gameLabel.startsWith("Excellent") || n.gameLabel.startsWith("Good") -> Green
                                n.gameLabel.startsWith("OK") || n.gameLabel.startsWith("Acceptable") -> Yellow
                                else -> Red
                            }, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        if (n.gameAdvice.isNotEmpty()) {
                            Text(n.gameAdvice, color = TextS, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Predicted extra loss % from jitter + measured loss (heuristic for gaming stability). */
fun predictLoss(n: V2Node): Int {
    val base = n.tcpLoss ?: if (n.reachable == true) 0 else 100
    val jit = (n.tcpJitter ?: 0L).toInt()
    val extra = when {
        jit > 80 -> 15
        jit > 40 -> 8
        jit > 20 -> 4
        else -> 0
    }
    val udpPen = when (n.udpStatus) {
        "BLOCKED", "FAIL" -> 10
        "NO_REPLY" -> 5
        else -> 0
    }
    return (base + extra + udpPen).coerceIn(0, 100)
}

data class GameInfo(
    val name: String,
    val genre: String,
    val pingNeed: String,
    val dnsHint: String,
    val tip: String,
    val hosts: List<String>,
    val packageName: String? = null
)

data class GamePingResult(
    val host: String,
    val resolveMs: Long?,
    val tcpMs: Long?,
    val udpStatus: String
)

private val GAME_PACKAGES = mapOf(
    "com.mobile.legends" to "Mobile Legends: Bang Bang",
    "com.tencent.ig" to "PUBG Mobile",
    "com.pubg.imobile" to "BGMI",
    "com.pubg.newstate" to "PUBG New State",
    "com.dts.freefireth" to "Free Fire",
    "com.dts.freefiremax" to "Free Fire MAX",
    "com.garena.game.kgvn" to "Free Fire",
    "com.activision.callofduty.shooter" to "Call of Duty: Mobile",
    "com.roblox.client" to "Roblox",
    "com.miHoYo.GenshinImpact" to "Genshin Impact",
    "com.riotgames.league.wildrift" to "Wild Rift",
    "com.supercell.clashofclans" to "Clash of Clans",
    "com.supercell.clashroyale" to "Clash Royale",
    "com.supercell.brawlstars" to "Brawl Stars",
    "jp.konami.pesam" to "eFootball",
    "com.ea.gp.fifamobile" to "EA Sports FC Mobile",
    "com.axlebolt.standoff2" to "Standoff 2",
    "com.criticalforceentertainment.criticalops" to "Critical Ops",
    "com.innersloth.spacemafia" to "Among Us",
    "com.mojang.minecraftpe" to "Minecraft",
    "com.nianticlabs.pokemongo" to "Pokémon GO",
    "com.proximabeta.mf.uamo" to "Arena Breakout"
)

fun scanInstalledGames(pm: PackageManager): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for ((pkg, name) in GAME_PACKAGES) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            out.add(pkg to name)
        } catch (_: Exception) {
        }
    }
    return out.sortedBy { it.second }
}

val ONLINE_GAMES = listOf(
    // ——— MOBA ———
    GameInfo("Mobile Legends: Bang Bang", "MOBA", "Critical <60ms", "Electro", "Ranked 5v5", listOf("ml.youngjoygame.com", "api.mobilelegends.com")),
    GameInfo("Honor of Kings / HoK", "MOBA", "Critical <60ms", "Shecan", "Tencent", listOf("sgame.qq.com")),
    GameInfo("Arena of Valor", "MOBA", "Critical <70ms", "Shecan", "Garena/Tencent", listOf("aov.garena.com")),
    GameInfo("League of Legends: Wild Rift", "MOBA", "Critical <80ms", "Shecan", "Riot", listOf("wildrift.leagueoflegends.com", "riotgames.com")),
    GameInfo("League of Legends (PC)", "MOBA", "Critical <50ms", "Shecan", "Riot", listOf("riotgames.com", "leagueoflegends.com")),
    GameInfo("Dota 2", "MOBA", "Critical <60ms", "Shecan", "Valve", listOf("dota2.com", "steampowered.com")),
    GameInfo("Pokemon Unite", "MOBA", "High <90ms", "Shecan", "Tencent", listOf("pokemonunite.jp")),
    GameInfo("Vainglory", "MOBA", "Critical <80ms", "Cloudflare", "Super Evil Megacorp", listOf("vainglorygame.com")),
    GameInfo("Onmyoji Arena", "MOBA", "High <90ms", "Shecan", "NetEase", listOf("onmyojigame.com")),
    GameInfo("SMITE", "MOBA", "Critical <80ms", "Shecan", "Hi-Rez", listOf("smitegame.com")),
    GameInfo("Heroes of the Storm", "MOBA", "Critical <80ms", "Shecan", "Blizzard", listOf("heroesofthestorm.com")),
    // ——— Battle Royale ———
    GameInfo("PUBG Mobile", "BR", "Critical <80ms", "Shecan", "Krafton", listOf("api.pubg.com", "prod-live-front.playbattlegrounds.com")),
    GameInfo("BGMI", "BR", "Critical <80ms", "Shecan", "India", listOf("api.bgmi.com")),
    GameInfo("PUBG: New State", "BR", "Critical <80ms", "Shecan", "Krafton", listOf("newstate.pubg.com")),
    GameInfo("PUBG PC / Steam", "BR", "Critical <60ms", "Shecan", "Krafton", listOf("pubg.com")),
    GameInfo("Free Fire", "BR", "High <90ms", "Electro", "Garena", listOf("ff.garena.com", "loginbp.common.ggbluefox.com")),
    GameInfo("Free Fire MAX", "BR", "High <90ms", "Electro", "Garena", listOf("ff.garena.com")),
    GameInfo("Call of Duty: Mobile", "FPS/BR", "Critical <70ms", "Shecan", "Activision", listOf("codm.activision.com")),
    GameInfo("Call of Duty: Warzone", "BR", "Critical <60ms", "Shecan", "Activision", listOf("callofduty.com")),
    GameInfo("Fortnite", "BR", "Critical <70ms", "Shecan", "Epic", listOf("fortnite.com", "epicgames.com")),
    GameInfo("Apex Legends", "BR", "Critical <70ms", "Shecan", "EA/Respawn", listOf("ea.com", "easports.com")),
    GameInfo("Apex Legends Mobile", "BR", "Critical <80ms", "Shecan", "EA", listOf("ea.com")),
    GameInfo("Blood Strike", "BR", "High <90ms", "Shecan", "NetEase", listOf("bloodstrike.com")),
    GameInfo("Farlight 84", "BR", "High <90ms", "Shecan", "Lilith", listOf("farlight84.com")),
    GameInfo("Garena Undawn", "BR", "High <100ms", "Shecan", "Garena", listOf("undawn.garena.com")),
    GameInfo("Knives Out / Identity V BR modes", "BR", "High <100ms", "Shecan", "NetEase", listOf("identityv.game")),
    GameInfo("Rules of Survival", "BR", "High <100ms", "Shecan", "NetEase", listOf("rulesofsurvivalgame.com")),
    GameInfo("Knives Out Mobile", "BR", "High <100ms", "Shecan", "NetEase", listOf("knivesout.com")),
    GameInfo("The Cycle: Frontier", "BR", "Critical <80ms", "Shecan", "Yager", listOf("thecycle.game")),
    GameInfo("Naraka: Bladepoint", "BR", "Critical <80ms", "Shecan", "NetEase", listOf("narakathegame.com")),
    GameInfo("Super People", "BR", "Critical <80ms", "Shecan", "Wonder Games", listOf("superpeople.com")),
    GameInfo("Realm Royale", "BR", "High <100ms", "Shecan", "Hi-Rez", listOf("realmroyale.com")),
    // ——— Tactical / Extraction ———
    GameInfo("Delta Force Hawk Ops", "Tactical", "Critical <70ms", "Shecan", "TiMi", listOf("deltaforce.com")),
    GameInfo("Arena Breakout", "Tactical", "Critical <80ms", "Shecan", "MoreFun", listOf("arenabreakout.com")),
    GameInfo("Arena Breakout: Infinite", "Tactical", "Critical <80ms", "Shecan", "MoreFun", listOf("arenabreakoutinfinite.com")),
    GameInfo("Escape from Tarkov", "Tactical", "Critical <60ms", "Shecan", "BSG", listOf("escapefromtarkov.com")),
    GameInfo("Hunt: Showdown", "Tactical", "Critical <70ms", "Shecan", "Crytek", listOf("huntshowdown.com")),
    GameInfo("Marauders", "Tactical", "Critical <80ms", "Shecan", "Small Impact", listOf("playmarauders.com")),
    GameInfo("Dark and Darker", "Tactical", "Critical <80ms", "Shecan", "IRONMACE", listOf("darkanddarker.com")),
    // ——— FPS Competitive ———
    GameInfo("Valorant", "FPS", "Critical <50ms", "Shecan", "Riot", listOf("playvalorant.com", "riotgames.com")),
    GameInfo("CS2 / Counter-Strike 2", "FPS", "Critical <40ms", "Shecan", "Valve", listOf("counter-strike.net", "steampowered.com")),
    GameInfo("CS:GO", "FPS", "Critical <40ms", "Shecan", "Valve", listOf("counter-strike.net")),
    GameInfo("Overwatch 2", "FPS", "Critical <60ms", "Shecan", "Blizzard", listOf("overwatch.blizzard.com")),
    GameInfo("Rainbow Six Siege", "FPS", "Critical <50ms", "Shecan", "Ubisoft", listOf("ubisoft.com", "rainbow6.ubisoft.com")),
    GameInfo("Team Fortress 2", "FPS", "High <90ms", "Cloudflare", "Valve", listOf("teamfortress.com")),
    GameInfo("Quake Champions", "FPS", "Critical <50ms", "Shecan", "id/Bethesda", listOf("quake.com")),
    GameInfo("Splitgate", "FPS", "Critical <70ms", "Shecan", "1047 Games", listOf("splitgate.com")),
    GameInfo("XDefiant", "FPS", "Critical <70ms", "Shecan", "Ubisoft", listOf("xdefiant.ubisoft.com")),
    GameInfo("The Finals", "FPS", "Critical <70ms", "Shecan", "Embark", listOf("reachthefinals.com")),
    GameInfo("Battlefield 2042", "FPS", "Critical <70ms", "Shecan", "EA", listOf("ea.com")),
    GameInfo("Halo Infinite", "FPS", "Critical <60ms", "Shecan", "Xbox", listOf("halowaypoint.com")),
    GameInfo("Destiny 2", "FPS/RPG", "Critical <80ms", "Shecan", "Bungie", listOf("bungie.net", "destinythegame.com")),
    GameInfo("Destiny: Rising", "FPS/RPG", "Critical <80ms", "Shecan", "NetEase", listOf("destiny.com")),
    GameInfo("Standoff 2", "FPS", "Critical <70ms", "Electro", "Axlebolt", listOf("standoff2.com")),
    GameInfo("Critical Ops", "FPS", "Critical <70ms", "Shecan", "Critical Force", listOf("criticalops.com")),
    GameInfo("Modern Warships", "FPS", "High <90ms", "Shecan", "Artstorm", listOf("modernwarships.com")),
    GameInfo("World War Heroes", "FPS", "High <100ms", "Shecan", "Tap4Fun", listOf("worldwarheroes.com")),
    GameInfo("Shadowgun War Games", "FPS", "High <90ms", "Shecan", "Madfinger", listOf("shadowgun.com")),
    GameInfo("Bullet Echo", "FPS", "High <100ms", "Shecan", "Magmatic", listOf("bulletecho.com")),
    GameInfo("Pixel Gun 3D", "FPS", "High <100ms", "Cloudflare", "Lightmap", listOf("pixelgun3d.com")),
    GameInfo("Guns of Boom", "FPS", "High <100ms", "Cloudflare", "Game Insight", listOf("gunsofboom.com")),
    GameInfo("Modern Combat Versus", "FPS", "High <100ms", "Shecan", "Gameloft", listOf("moderncombat.com")),
    GameInfo("Sniper 3D Assassin", "FPS", "Medium <120ms", "Cloudflare", "Fun Games", listOf("sniper3d.com")),
    GameInfo("CrossFire", "FPS", "Critical <60ms", "Shecan", "Smilegate", listOf("crossfire.z8games.com")),
    GameInfo("Point Blank", "FPS", "Critical <70ms", "Shecan", "Zepetto", listOf("pointblank.zepetto.com")),
    GameInfo("Warface", "FPS", "Critical <80ms", "Shecan", "My.com", listOf("warface.com")),
    GameInfo("Paladins", "FPS", "Critical <80ms", "Shecan", "Hi-Rez", listOf("paladins.com")),
    GameInfo("RoboQuest", "FPS", "High <100ms", "Cloudflare", "RyseUp", listOf("roboquestgame.com")),
    // ——— Sports ———
    GameInfo("eFootball", "Sports", "High <90ms", "Shecan", "Konami", listOf("efootball.konami.net")),
    GameInfo("EA Sports FC Mobile", "Sports", "High <90ms", "Shecan", "EA", listOf("easports.com")),
    GameInfo("EA Sports FC (PC/Console)", "Sports", "High <90ms", "Shecan", "EA", listOf("ea.com")),
    GameInfo("NBA 2K Mobile", "Sports", "Medium <110ms", "Shecan", "2K", listOf("nba.2k.com")),
    GameInfo("NBA 2K Online", "Sports", "High <90ms", "Shecan", "2K", listOf("nba.2k.com")),
    GameInfo("Rocket League", "Sports", "Critical <70ms", "Shecan", "Epic/Psyonix", listOf("rocketleague.com")),
    GameInfo("Dream League Soccer", "Sports", "Medium <120ms", "Cloudflare", "First Touch", listOf("dreamleaguesoccer.com")),
    GameInfo("Football Manager Mobile", "Sports", "Low <150ms", "Cloudflare", "SEGA", listOf("footballmanager.com")),
    GameInfo("F1 24 / F1 Online", "Sports", "Critical <80ms", "Shecan", "EA/Codemasters", listOf("ea.com")),
    GameInfo("F1 Clash", "Sports", "Medium <120ms", "Cloudflare", "Hutch", listOf("f1clash.com")),
    GameInfo("MLB The Show / Perfect Inning", "Sports", "Medium <120ms", "Cloudflare", "Com2uS/Sony", listOf("mlbpi.com")),
    GameInfo("WWE Champions", "Sports", "Medium <130ms", "Cloudflare", "Scopely", listOf("wwechampions.com")),
    GameInfo("Tennis Clash", "Sports", "High <100ms", "Cloudflare", "Wildlife", listOf("tennisclash.com")),
    GameInfo("Golf Battle", "Sports", "Medium <120ms", "Cloudflare", "Miniclip", listOf("golfbattle.com")),
    GameInfo("8 Ball Pool", "Sports", "Medium <120ms", "Cloudflare", "Miniclip", listOf("8ballpool.com")),
    // ——— Fighting ———
    GameInfo("Street Fighter 6", "Fighting", "Critical <60ms", "Shecan", "Capcom", listOf("streetfighter.com")),
    GameInfo("Tekken 8", "Fighting", "Critical <60ms", "Shecan", "Bandai Namco", listOf("tekken.com")),
    GameInfo("Mortal Kombat 1", "Fighting", "Critical <70ms", "Shecan", "WB/NRS", listOf("mortalkombat.com")),
    GameInfo("MultiVersus", "Fighting", "Critical <80ms", "Shecan", "WB", listOf("multiversus.com")),
    GameInfo("Brawlhalla", "Fighting", "High <90ms", "Cloudflare", "Ubisoft", listOf("brawlhalla.com")),
    GameInfo("Injustice 2 Mobile", "Fighting", "Medium <120ms", "Cloudflare", "WB", listOf("injustice.com")),
    GameInfo("Shadow Fight 3/4 Arena", "Fighting", "High <100ms", "Cloudflare", "Nekki", listOf("shadowfight.com")),
    // ——— Racing ———
    GameInfo("Asphalt 9", "Racing", "High <100ms", "Shecan", "Gameloft", listOf("asphalt9.com")),
    GameInfo("Asphalt Legends Unite", "Racing", "High <100ms", "Shecan", "Gameloft", listOf("asphalt.com")),
    GameInfo("Real Racing 3", "Racing", "Medium <120ms", "Cloudflare", "EA", listOf("realracing3.com")),
    GameInfo("Need for Speed No Limits", "Racing", "Medium <120ms", "Cloudflare", "EA", listOf("ea.com")),
    GameInfo("Mario Kart Tour", "Racing", "Medium <120ms", "Cloudflare", "Nintendo", listOf("mariokarttour.com")),
    GameInfo("Forza Horizon / Motorsport Online", "Racing", "Critical <80ms", "Shecan", "Xbox", listOf("forzamotorsport.net")),
    GameInfo("Gran Turismo Sport/7 Sport Mode", "Racing", "Critical <70ms", "Shecan", "Sony", listOf("gran-turismo.com")),
    GameInfo("Trackmania", "Racing", "Critical <80ms", "Shecan", "Ubisoft", listOf("trackmania.com")),
    // ——— Vehicles / Naval / Tank ———
    GameInfo("World of Tanks", "Vehicles", "Critical <80ms", "Shecan", "Wargaming", listOf("worldoftanks.eu")),
    GameInfo("World of Tanks Blitz", "Vehicles", "High <90ms", "Shecan", "Wargaming", listOf("wotblitz.com")),
    GameInfo("World of Warships", "Naval", "High <100ms", "Shecan", "Wargaming", listOf("worldofwarships.com")),
    GameInfo("World of Warships Blitz", "Naval", "High <100ms", "Shecan", "Wargaming", listOf("wowblitz.com")),
    GameInfo("War Thunder", "Vehicles", "Critical <80ms", "Shecan", "Gaijin", listOf("warthunder.com")),
    GameInfo("Force of Warships", "Naval", "High <100ms", "Shecan", "Artstorm", listOf("forceofwarships.com")),
    GameInfo("Armored Warfare", "Vehicles", "High <100ms", "Shecan", "My.com", listOf("armoredwarfare.com")),
    // ——— Survival / Sandbox multiplayer ———
    GameInfo("Minecraft", "Sandbox", "Medium <100ms", "Cloudflare", "Mojang", listOf("minecraft.net", "mojang.com")),
    GameInfo("Roblox", "Sandbox", "Medium <120ms", "Cloudflare", "Roblox", listOf("www.roblox.com", "clientsettingscdn.roblox.com")),
    GameInfo("Rust", "Survival", "Critical <80ms", "Shecan", "Facepunch", listOf("rust.facepunch.com")),
    GameInfo("ARK: Survival Ascended/Evolved", "Survival", "Critical <90ms", "Shecan", "Studio Wildcard", listOf("playark.com")),
    GameInfo("Valheim", "Survival", "High <100ms", "Cloudflare", "Iron Gate", listOf("valheimgame.com")),
    GameInfo("Once Human", "Survival", "High <100ms", "Shecan", "NetEase", listOf("oncehuman.game")),
    GameInfo("DayZ", "Survival", "Critical <90ms", "Shecan", "Bohemia", listOf("dayz.com")),
    GameInfo("7 Days to Die", "Survival", "High <100ms", "Cloudflare", "TFP", listOf("7daystodie.com")),
    GameInfo("Terraria", "Sandbox", "Medium <120ms", "Cloudflare", "Re-Logic", listOf("terraria.org")),
    GameInfo("Don't Starve Together", "Survival", "Medium <120ms", "Cloudflare", "Klei", listOf("dontstarvegame.com")),
    // ——— Party / Social online ———
    GameInfo("Among Us", "Party", "Low <150ms", "Cloudflare", "Innersloth", listOf("among.us")),
    GameInfo("Fall Guys", "Party", "Medium <120ms", "Shecan", "Epic", listOf("fallguys.com")),
    GameInfo("Stumble Guys", "Party", "Medium <120ms", "Cloudflare", "Scopely", listOf("stumbleguys.com")),
    GameInfo("Gartic Phone", "Party", "Low <150ms", "Cloudflare", "Gartic", listOf("garticphone.com")),
    GameInfo("Jackbox Party Pack", "Party", "Low <150ms", "Cloudflare", "Jackbox", listOf("jackbox.tv")),
    GameInfo("Krunker.io", "FPS", "Critical <80ms", "Cloudflare", "Browser", listOf("krunker.io")),
    GameInfo("Shell Shockers", "FPS", "High <100ms", "Cloudflare", "Browser", listOf("shellshock.io")),
    // ——— Card / Board competitive ———
    GameInfo("Hearthstone", "Card", "Medium <120ms", "Shecan", "Blizzard", listOf("hearthstone.blizzard.com")),
    GameInfo("Yu-Gi-Oh! Master Duel", "Card", "Medium <120ms", "Shecan", "Konami", listOf("masterduel.com")),
    GameInfo("Marvel Snap", "Card", "Medium <120ms", "Cloudflare", "Second Dinner", listOf("marvelsnap.com")),
    GameInfo("Legends of Runeterra", "Card", "Medium <120ms", "Shecan", "Riot", listOf("playruneterra.com")),
    GameInfo("MTG Arena", "Card", "Medium <120ms", "Shecan", "Wizards", listOf("magic.wizards.com")),
    GameInfo("Chess.com", "Board", "Low <150ms", "Cloudflare", "Chess.com", listOf("chess.com")),
    GameInfo("Lichess", "Board", "Low <150ms", "Cloudflare", "Lichess", listOf("lichess.org")),
    GameInfo("Clash Royale", "Strategy", "Medium <100ms", "Google DNS", "Supercell", listOf("game.clashroyale.com")),
    // ——— Strategy real-time ———
    GameInfo("Clash of Clans", "Strategy", "Medium <100ms", "Google DNS", "Supercell", listOf("game.clashofclans.com")),
    GameInfo("Brawl Stars", "Action", "High <90ms", "Shecan", "Supercell", listOf("game.brawlstarsgame.com")),
    GameInfo("Boom Beach", "Strategy", "Medium <120ms", "Google DNS", "Supercell", listOf("game.boombeachgame.com")),
    GameInfo("Hay Day", "Farm", "Low <150ms", "Google DNS", "Supercell", listOf("game.haydaygame.com")),
    GameInfo("State of Survival", "Strategy", "Medium <130ms", "Shecan", "KingsGroup", listOf("stateofsurvival.com")),
    GameInfo("Rise of Kingdoms", "Strategy", "Medium <130ms", "Shecan", "Lilith", listOf("riseofkingdoms.com")),
    GameInfo("Lords Mobile", "Strategy", "Medium <130ms", "Cloudflare", "IGG", listOf("lordsmobile.com")),
    GameInfo("Last War: Survival", "Strategy", "Medium <130ms", "Shecan", "FirstFun", listOf("lastwar.com")),
    GameInfo("Whiteout Survival", "Strategy", "Medium <130ms", "Shecan", "Century Games", listOf("whiteoutsurvival.com")),
    GameInfo("Age of Empires IV Multiplayer", "Strategy", "Critical <80ms", "Shecan", "Xbox", listOf("ageofempires.com")),
    GameInfo("StarCraft II", "Strategy", "Critical <50ms", "Shecan", "Blizzard", listOf("starcraft2.com")),
    GameInfo("Warcraft III Reforged", "Strategy", "Critical <60ms", "Shecan", "Blizzard", listOf("warcraft3.com")),
    GameInfo("Command & Conquer Remastered Online", "Strategy", "Critical <80ms", "Shecan", "EA", listOf("ea.com")),
    // ——— MMO / Action RPG online ———
    GameInfo("Genshin Impact", "RPG", "Medium <120ms", "Shecan", "Hoyoverse", listOf("hk4e-api-os.hoyoverse.com")),
    GameInfo("Honkai: Star Rail", "RPG", "Medium <120ms", "Shecan", "Hoyoverse", listOf("hkrpg-api-os.hoyoverse.com")),
    GameInfo("Zenless Zone Zero", "Action", "Medium <120ms", "Shecan", "Hoyoverse", listOf("nap-api-os.hoyoverse.com")),
    GameInfo("Wuthering Waves", "Action", "Medium <120ms", "Shecan", "Kuro", listOf("wutheringwaves.kurogame.com")),
    GameInfo("Tower of Fantasy", "RPG", "Medium <120ms", "Shecan", "Level Infinite", listOf("toweroffantasy-global.com")),
    GameInfo("Diablo Immortal", "RPG", "High <100ms", "Shecan", "Blizzard", listOf("diabloimmortal.com")),
    GameInfo("Diablo IV", "RPG", "High <100ms", "Shecan", "Blizzard", listOf("diablo4.blizzard.com")),
    GameInfo("Path of Exile", "ARPG", "High <100ms", "Shecan", "GGG", listOf("pathofexile.com")),
    GameInfo("Path of Exile 2", "ARPG", "High <100ms", "Shecan", "GGG", listOf("pathofexile.com")),
    GameInfo("Lost Ark", "MMORPG", "Critical <80ms", "Shecan", "Smilegate/Amazon", listOf("playlostark.com")),
    GameInfo("Final Fantasy XIV", "MMORPG", "High <100ms", "Shecan", "Square Enix", listOf("finalfantasyxiv.com")),
    GameInfo("World of Warcraft", "MMORPG", "High <100ms", "Shecan", "Blizzard", listOf("worldofwarcraft.com")),
    GameInfo("Black Desert Online", "MMORPG", "High <100ms", "Shecan", "Pearl Abyss", listOf("blackdesertonline.com")),
    GameInfo("Black Desert Mobile", "MMORPG", "High <100ms", "Shecan", "Pearl Abyss", listOf("blackdesertm.com")),
    GameInfo("Albion Online", "MMORPG", "High <100ms", "Shecan", "Sandbox", listOf("albiononline.com")),
    GameInfo("Old School RuneScape", "MMORPG", "Medium <120ms", "Cloudflare", "Jagex", listOf("oldschool.runescape.com")),
    GameInfo("RuneScape 3", "MMORPG", "Medium <120ms", "Cloudflare", "Jagex", listOf("runescape.com")),
    GameInfo("New World", "MMORPG", "High <100ms", "Shecan", "Amazon", listOf("newworld.com")),
    GameInfo("Throne and Liberty", "MMORPG", "High <100ms", "Shecan", "NCSoft/Amazon", listOf("playthroneandliberty.com")),
    GameInfo("Blade & Soul", "MMORPG", "Critical <80ms", "Shecan", "NCSoft", listOf("bladeandsoul.com")),
    GameInfo("Guild Wars 2", "MMORPG", "High <100ms", "Shecan", "ArenaNet", listOf("guildwars2.com")),
    GameInfo("Elder Scrolls Online", "MMORPG", "High <100ms", "Shecan", "Zenimax", listOf("elderscrollsonline.com")),
    GameInfo("Warframe", "Action", "High <100ms", "Shecan", "Digital Extremes", listOf("warframe.com")),
    GameInfo("Warframe Mobile", "Action", "High <100ms", "Shecan", "DE", listOf("warframe.com")),
    GameInfo("Summoners War", "RPG", "Medium <120ms", "Shecan", "Com2uS", listOf("summonerswar.com")),
    GameInfo("Epic Seven", "RPG", "Medium <120ms", "Shecan", "Smilegate", listOf("epicseven.com")),
    GameInfo("Raid: Shadow Legends", "RPG", "Low <150ms", "Cloudflare", "Plarium", listOf("raidshadowlegends.com")),
    GameInfo("AFK Arena", "RPG", "Low <150ms", "Cloudflare", "Lilith", listOf("afkarena.com")),
    GameInfo("Fate/Grand Order", "RPG", "Medium <130ms", "Shecan", "Aniplex", listOf("fate-go.us")),
    GameInfo("Mobile Legends: Adventure", "RPG", "Medium <120ms", "Electro", "moonton", listOf("mladventure.com")),
    // ——— Asymmetric / Horror online ———
    GameInfo("Dead by Daylight", "Asym", "High <100ms", "Shecan", "Behaviour", listOf("deadbydaylight.com")),
    GameInfo("Dead by Daylight Mobile", "Asym", "High <100ms", "Shecan", "Behaviour", listOf("deadbydaylight.com")),
    GameInfo("Identity V", "Asym", "High <100ms", "Shecan", "NetEase", listOf("identityv.game")),
    GameInfo("Evil Dead: The Game", "Asym", "High <100ms", "Shecan", "Saber", listOf("evildeadthegame.com")),
    GameInfo("Predator: Hunting Grounds", "Asym", "High <100ms", "Shecan", "IllFonic", listOf("predatorhuntinggrounds.com")),
    // ——— AR / Location ———
    GameInfo("Pokémon GO", "AR", "Medium <120ms", "Google DNS", "Niantic", listOf("pgorelease.nianticlabs.com")),
    GameInfo("Monster Hunter Now", "AR", "Medium <120ms", "Google DNS", "Niantic", listOf("monsterhunternow.com")),
    GameInfo("Pikmin Bloom", "AR", "Low <150ms", "Google DNS", "Niantic", listOf("pikminbloom.com")),
    GameInfo("Ingress", "AR", "Medium <120ms", "Google DNS", "Niantic", listOf("ingress.com")),
    // ——— Path baselines ———
    GameInfo("Cloudflare edge", "Baseline", "Low", "1.1.1.1", "Path check", listOf("1.1.1.1", "cloudflare.com")),
    GameInfo("Google edge", "Baseline", "Low", "8.8.8.8", "Path check", listOf("dns.google", "google.com")),
    GameInfo("Riot edge", "Baseline", "Low", "Shecan", "Path check", listOf("riotgames.com")),
    GameInfo("Garena edge", "Baseline", "Low", "Electro", "Path check", listOf("garena.com")),
    GameInfo("Hoyoverse edge", "Baseline", "Low", "Shecan", "Path check", listOf("hoyoverse.com")),
    GameInfo("Valve / Steam edge", "Baseline", "Low", "Shecan", "Path check", listOf("steampowered.com", "steamcommunity.com")),
    GameInfo("Epic Games edge", "Baseline", "Low", "Shecan", "Path check", listOf("epicgames.com")),
    GameInfo("Activision edge", "Baseline", "Low", "Shecan", "Path check", listOf("activision.com")),
    GameInfo("Wargaming edge", "Baseline", "Low", "Shecan", "Path check", listOf("wargaming.net"))
)

suspend fun testGamePing(g: GameInfo): List<GamePingResult> {
    return g.hosts.map { host ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var resolveMs: Long? = null
            var tcpMs: Long? = null
            var udpStatus = "—"
            try {
                val t0 = System.currentTimeMillis()
                val addrs = java.net.InetAddress.getAllByName(host)
                resolveMs = System.currentTimeMillis() - t0
                val ip = addrs.firstOrNull()?.hostAddress
                if (ip != null) {
                    val t1 = System.currentTimeMillis()
                    try {
                        java.net.Socket().use { s ->
                            s.connect(java.net.InetSocketAddress(ip, 443), 2500)
                            tcpMs = System.currentTimeMillis() - t1
                        }
                    } catch (_: Exception) {
                        try {
                            java.net.Socket().use { s ->
                                s.connect(java.net.InetSocketAddress(ip, 80), 2500)
                                tcpMs = System.currentTimeMillis() - t1
                            }
                        } catch (_: Exception) {
                            tcpMs = null
                        }
                    }
                    try {
                        val ds = java.net.DatagramSocket()
                        ds.soTimeout = 1200
                        val data = ByteArray(8)
                        val packet = java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName(ip), 443)
                        val u0 = System.currentTimeMillis()
                        ds.send(packet)
                        try {
                            ds.receive(packet)
                            udpStatus = "REPLIES ${System.currentTimeMillis() - u0}ms"
                        } catch (_: Exception) {
                            udpStatus = "NO_REPLY"
                        }
                        ds.close()
                    } catch (_: Exception) {
                        udpStatus = "FAIL"
                    }
                }
            } catch (_: Exception) {
                resolveMs = null
            }
            GamePingResult(host, resolveMs, tcpMs, udpStatus)
        }
    }
}

fun summarizeGamePing(g: GameInfo, list: List<GamePingResult>): Pair<String, String> {
    val ok = list.mapNotNull { it.tcpMs }
    if (ok.isEmpty()) return "Weak path" to "No TCP reach on public hosts"
    val avg = ok.average()
    val label = when {
        avg < 60 -> "Live path: Excellent"
        avg < 100 -> "Live path: Good"
        avg < 150 -> "Live path: Playable"
        else -> "Live path: High ping"
    }
    val detail = "avg TCP ${avg.toInt()}ms · hosts ${ok.size}/${list.size} · need ${g.pingNeed}"
    return label to detail
}



data class NearbyEndpoint(
    val host: String,
    val games: List<String>,
    val resolveMs: Long?,
    val tcpMs: Long?,
    val score: Long // lower better
)

/**
 * Probe known public game endpoints (API/CDN/login — not always the match room IP).
 * Ranks by TCP latency so user can see nearest reachable gaming edges.
 * Optional extraHosts: user-supplied IPs/domains (one per line).
 */
suspend fun scanNearbyGameServers(
    extraHosts: List<String> = emptyList(),
    limit: Int = 24
): List<NearbyEndpoint> = withContext(Dispatchers.IO) {
    val hostToGames = linkedMapOf<String, MutableSet<String>>()
    for (g in ONLINE_GAMES) {
        for (h in g.hosts) {
            val key = h.trim().lowercase()
            if (key.isEmpty()) continue
            hostToGames.getOrPut(key) { mutableSetOf() }.add(g.name)
        }
    }
    for (h in extraHosts) {
        val key = h.trim().lowercase()
        if (key.isEmpty() || key.startsWith("#")) continue
        hostToGames.getOrPut(key) { mutableSetOf() }.add("Custom")
    }
    val hosts = hostToGames.keys.take(80) // cap work
    val results = mutableListOf<NearbyEndpoint>()
    // parallel chunks
    for (chunk in hosts.chunked(8)) {
        val part = kotlinx.coroutines.coroutineScope {
            chunk.map { host ->
                async {
                    val resolve = try {
                        val t0 = System.currentTimeMillis()
                        InetAddress.getByName(host)
                        (System.currentTimeMillis() - t0).coerceIn(1, 9999)
                    } catch (_: Exception) { null }
                    val tcp = tcpConnect(host, 443, 1800) ?: tcpConnect(host, 80, 1500)
                    val score = when {
                        tcp != null -> tcp
                        resolve != null -> resolve + 5000
                        else -> 99999L
                    }
                    NearbyEndpoint(
                        host = host,
                        games = hostToGames[host]?.toList()?.take(4) ?: emptyList(),
                        resolveMs = resolve,
                        tcpMs = tcp,
                        score = score
                    )
                }
            }.map { it.await() }
        }
        results.addAll(part)
    }
    results
        .filter { it.score < 99999L }
        .sortedBy { it.score }
        .take(limit)
}


@Composable
fun GamesOptimizeTab() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var q by remember { mutableStateOf("") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Map<String, Pair<List<GamePingResult>, Pair<String, String>>>>(emptyMap()) }
    var showInstalledOnly by remember { mutableStateOf(false) }
    var nearbyBusy by remember { mutableStateOf(false) }
    var nearbyRows by remember { mutableStateOf<List<NearbyEndpoint>>(emptyList()) }
    var extraIps by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val pkgFor = remember {
        mapOf(
            "Mobile Legends: Bang Bang" to "com.mobile.legends",
            "PUBG Mobile" to "com.tencent.ig",
            "BGMI" to "com.pubg.imobile",
            "Free Fire" to "com.dts.freefireth",
            "Free Fire MAX" to "com.dts.freefiremax",
            "Call of Duty: Mobile" to "com.activision.callofduty.shooter",
            "Genshin Impact" to "com.miHoYo.GenshinImpact",
            "Honkai: Star Rail" to "com.HoYoverse.hkrpgoversea",
            "Zenless Zone Zero" to "com.HoYoverse.Nap",
            "Roblox" to "com.roblox.client",
            "Minecraft" to "com.mojang.minecraftpe",
            "Clash of Clans" to "com.supercell.clashofclans",
            "Clash Royale" to "com.supercell.clashroyale",
            "Brawl Stars" to "com.supercell.brawlstars",
            "eFootball" to "jp.konami.pesam",
            "EA Sports FC Mobile" to "com.ea.gp.fifamobile",
            "Arena of Valor" to "com.garena.game.kgvn",
            "League of Legends: Wild Rift" to "com.riotgames.mobile.leagueconnect",
            "Pokemon Unite" to "com.tencent.pokemonunite",
            "Standoff 2" to "com.axlebolt.standoff2",
            "Asphalt 9" to "com.gameloft.android.ANMP.GloftA9HM",
            "Delta Force Hawk Ops" to "com.garena.game.df",
            "Arena Breakout" to "com.more.abmobile",
            "Identity V" to "com.netease.dwrg.google",
            "Dead by Daylight Mobile" to "com.bhvr.dbd.mobile",
            "Shadow Fight 4" to "com.nekki.shadowfightarena",
            "Rocket League Sideswipe" to "com.psyonix.rl_sideswipe",
            "PUBG New State" to "com.pubg.newstate",
            "Farlight 84" to "com.miraclegames.farlight84",
            "Blood Strike" to "com.netease.newsnap"
        )
    }

    fun isInstalled(name: String, explicit: String?): Boolean {
        val pkg = explicit ?: pkgFor[name] ?: return false
        return try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }

    val filtered = ONLINE_GAMES.filter {
        val match = q.isBlank() || it.name.contains(q, true) || it.genre.contains(q, true)
        val inst = isInstalled(it.name, it.packageName)
        match && (!showInstalledOnly || inst)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Games", color = TextP, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        Text("Nearby edges · path test · installed", color = TextS, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("Search", color = TextM) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent, unfocusedBorderColor = Surface2,
                focusedTextColor = TextP, unfocusedTextColor = TextP, cursorColor = Accent
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showInstalledOnly,
                onClick = { showInstalledOnly = !showInstalledOnly },
                label = { Text("Installed only", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(0.25f),
                    selectedLabelColor = Accent
                )
            )
            Spacer(Modifier.width(8.dp))
            Text("${filtered.size} titles", color = TextM, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Nearby gaming endpoints
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nearby gaming servers", color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Front/edge latency only (API/CDN). Not match-room IP. Optional custom IPs.",
                    color = TextS, fontSize = 11.sp
                )
                OutlinedTextField(
                    value = extraIps,
                    onValueChange = { extraIps = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 72.dp),
                    placeholder = { Text("Optional IP/host list…", color = TextM, fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Surface2,
                        focusedTextColor = TextP, unfocusedTextColor = TextP, cursorColor = Accent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (nearbyBusy) return@Button
                            nearbyBusy = true
                            scope.launch {
                                nearbyRows = scanNearbyGameServers(
                                    extraHosts = extraIps.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                )
                                nearbyBusy = false
                            }
                        },
                        enabled = !nearbyBusy,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(if (nearbyBusy) "Scanning…" else "Scan nearby", color = Color.White, fontSize = 12.sp)
                    }
                }
                nearbyRows.take(8).forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n.host, color = TextP, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                            Text(n.games.joinToString(" · ").ifBlank { "—" }, color = TextM, fontSize = 10.sp, maxLines = 1)
                        }
                        Text(
                            n.tcpMs?.let { "${it}ms" } ?: n.resolveMs?.let { "DNS ${it}ms" } ?: "—",
                            color = when {
                                (n.tcpMs ?: 9999) < 60 -> Green
                                (n.tcpMs ?: 9999) < 120 -> Yellow
                                else -> TextS
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.name }) { g ->
                val installed = isInstalled(g.name, g.packageName)
                val pkg = g.packageName ?: pkgFor[g.name]
                val r = results[g.name]
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, color = TextP, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (installed) Text("ON DEVICE", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${g.genre} · ${g.pingNeed}", color = when {
                            g.pingNeed.startsWith("Critical") -> Red
                            g.pingNeed.startsWith("High") -> Yellow
                            else -> TextS
                        }, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(if (busyId == g.name) "…" else "Path test", color = Color.White, fontSize = 11.sp)
                            }
                            if (installed && pkg != null) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            pm.getLaunchIntentForPackage(pkg)?.let {
                                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(it)
                                            }
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) { Text("Launch", color = Accent, fontSize = 11.sp) }
                            }
                        }
                        r?.let { (list, sum) ->
                            Text(sum.first, color = when {
                                sum.first.contains("Excellent") || sum.first.contains("Good") -> Green
                                sum.first.contains("Playable") -> Yellow
                                else -> Red
                            }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(sum.second, color = TextS, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
