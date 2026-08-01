package com.whitebooster.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.system.measureTimeMillis

private val Bg = Color(0xFF0B0F14)
private val Surface = Color(0xFF141A22)
private val Surface2 = Color(0xFF1C2430)
private val Accent = Color(0xFF6C5CE7)
private val AccentSoft = Color(0xFFA29BFE)
private val Green = Color(0xFF00E676)
private val Yellow = Color(0xFFFFD600)
private val Red = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE8EAED)
private val TextSecondary = Color(0xFF8B95A5)
private val TextMuted = Color(0xFF5A6577)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = Surface
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    App()
                }
            }
        }
    }
}

data class DnsEntry(
    val name: String,
    val primary: String,
    val secondary: String,
    val region: String,
    var latency: Long? = null,
    var loss: Int = 0,
    var isBest: Boolean = false
)

data class GameEntry(
    val name: String,
    val packageName: String,
    var installed: Boolean = false
)

data class SpeedState(
    val downKbps: Float = 0f,
    val upKbps: Float = 0f
)

private val dnsServers = listOf(
    // Iran optimized
    DnsEntry("Shecan", "178.22.122.100", "185.51.200.2", "IR"),
    DnsEntry("Electro", "78.157.42.100", "78.157.42.101", "IR"),
    DnsEntry("403.online", "10.202.10.10", "10.202.10.11", "IR"),
    DnsEntry("Begzar", "185.55.226.26", "185.55.225.25", "IR"),
    DnsEntry("Radar Game", "10.10.34.35", "10.10.34.36", "IR"),
    DnsEntry("Shelter", "94.103.125.157", "94.103.125.158", "IR"),
    DnsEntry("Pishgaman", "5.202.100.100", "5.202.100.101", "IR"),
    // Public
    DnsEntry("Cloudflare", "1.1.1.1", "1.0.0.1", "Global"),
    DnsEntry("Cloudflare Malware", "1.1.1.2", "1.0.0.2", "Global"),
    DnsEntry("Google", "8.8.8.8", "8.8.4.4", "Global"),
    DnsEntry("Quad9", "9.9.9.9", "149.112.112.112", "Global"),
    DnsEntry("Quad9 ECS", "9.9.9.11", "149.112.112.11", "Global"),
    DnsEntry("AdGuard", "94.140.14.14", "94.140.15.15", "Global"),
    DnsEntry("AdGuard Family", "94.140.14.15", "94.140.15.16", "Global"),
    DnsEntry("OpenDNS", "208.67.222.222", "208.67.220.220", "Global"),
    DnsEntry("OpenDNS Family", "208.67.222.123", "208.67.220.123", "Global"),
    DnsEntry("Control D", "76.76.2.0", "76.76.10.0", "Global"),
    DnsEntry("Control D Ads", "76.76.2.2", "76.76.10.2", "Global"),
    DnsEntry("CleanBrowsing", "185.228.168.9", "185.228.169.9", "Global"),
    DnsEntry("CleanBrowsing Adult", "185.228.168.10", "185.228.169.11", "Global"),
    DnsEntry("Level3", "4.2.2.4", "4.2.2.1", "Global"),
    DnsEntry("Verisign", "64.6.64.6", "64.6.65.6", "Global"),
    DnsEntry("Comodo", "8.26.56.26", "8.20.247.20", "Global"),
    DnsEntry("Yandex", "77.88.8.8", "77.88.8.1", "Global"),
    DnsEntry("DNS.SB", "185.222.222.222", "185.184.185.185", "Global"),
    DnsEntry("Mullvad", "194.242.2.2", "194.242.2.3", "Global"),
    DnsEntry("Alternate DNS", "76.76.19.19", "76.223.100.101", "Global"),
    DnsEntry("Neustar", "156.154.70.1", "156.154.71.1", "Global"),
    DnsEntry("SafeDNS", "195.46.39.39", "195.46.39.40", "Global")
)

private val knownGames = listOf(
    GameEntry("Call of Duty Mobile", "com.activision.callofduty.shooter"),
    GameEntry("PUBG Mobile", "com.tencent.ig"),
    GameEntry("PUBG Mobile Global", "com.pubg.imobile"),
    GameEntry("Free Fire", "com.dts.freefireth"),
    GameEntry("Free Fire Max", "com.dts.freefiremax"),
    GameEntry("Genshin Impact", "com.miHoYo.GenshinImpact"),
    GameEntry("Mobile Legends", "com.mobile.legends"),
    GameEntry("Clash of Clans", "com.supercell.clashofclans"),
    GameEntry("Clash Royale", "com.supercell.clashroyale"),
    GameEntry("Brawl Stars", "com.supercell.brawlstars"),
    GameEntry("Roblox", "com.roblox.client"),
    GameEntry("Minecraft", "com.mojang.minecraftpe"),
    GameEntry("Among Us", "com.innersloth.spacemafia"),
    GameEntry("Wild Rift", "com.riotgames.league.wildrift"),
    GameEntry("Arena of Valor", "com.ngame.allstar.eu"),
    GameEntry("Fortnite", "com.epicgames.fortnite"),
    GameEntry("Apex Legends", "com.ea.gp.apexlegendsmobilefps"),
    GameEntry("eFootball", "jp.konami.pesam"),
    GameEntry("FIFA Mobile", "com.ea.gp.fifamobile"),
    GameEntry("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM"),
    GameEntry("Subway Surfers", "com.kiloo.subwaysurf"),
    GameEntry("Shadow Fight 3", "com.nekki.shadowfight3"),
    GameEntry("War Robots", "com.pixonic.wwr"),
    GameEntry("Candy Crush", "com.king.candycrushsaga")
)

// ── Speed monitor ───────────────────────────────────────
object SpeedMonitor {
    var downKbps = 0f
    var upKbps = 0f
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    fun tick() {
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (lastTime > 0 && rx != TrafficStats.UNSUPPORTED.toLong()) {
            val dt = max((now - lastTime) / 1000f, 0.1f)
            downKbps = ((rx - lastRx) * 8f / 1000f) / dt
            upKbps = ((tx - lastTx) * 8f / 1000f) / dt
        }
        lastRx = rx
        lastTx = tx
        lastTime = now
    }

    fun format(kbps: Float): String {
        return when {
            kbps >= 1000 -> String.format("%.1f Mbps", kbps / 1000)
            else -> String.format("%.0f Kbps", kbps)
        }
    }
}

// ── VPN + Notification Service ──────────────────────────
class DnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var speedThread: Thread? = null

    companion object {
        const val ACTION_CONNECT = "CONNECT"
        const val ACTION_DISCONNECT = "DISCONNECT"
        const val EXTRA_DNS1 = "dns1"
        const val EXTRA_DNS2 = "dns2"
        const val CHANNEL_ID = "wb_speed"
        const val NOTIF_ID = 1001
        var isActive = false
        var activeDns = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val d1 = intent.getStringExtra(EXTRA_DNS1) ?: "1.1.1.1"
                val d2 = intent.getStringExtra(EXTRA_DNS2) ?: "1.0.0.1"
                startVpn(d1, d2)
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "White Booster", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Speed & DNS status"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, DnsVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("White Booster · $activeDns")
            .setContentText("↓ ${SpeedMonitor.format(SpeedMonitor.downKbps)}  ↑ ${SpeedMonitor.format(SpeedMonitor.upKbps)}")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Disconnect", stop)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun startVpn(d1: String, d2: String) {
        if (running.get()) return
        try {
            ensureChannel()
            val builder = Builder()
                .setSession("White Booster")
                .addAddress("10.0.0.2", 32)
                .addDnsServer(d1)
                .addDnsServer(d2)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(true)

            vpnInterface = builder.establish() ?: return
            running.set(true)
            isActive = true
            activeDns = d1

            startForeground(NOTIF_ID, buildNotification())

            worker = Thread {
                try {
                    val fd = vpnInterface!!.fileDescriptor
                    val input = FileInputStream(fd)
                    val output = FileOutputStream(fd)
                    val buf = ByteArray(32767)
                    while (running.get()) {
                        val n = input.read(buf)
                        if (n > 0) output.write(buf, 0, n)
                    }
                } catch (_: Exception) {}
            }.also { it.start() }

            speedThread = Thread {
                val nm = getSystemService(NotificationManager::class.java)
                while (running.get()) {
                    try {
                        SpeedMonitor.tick()
                        nm.notify(NOTIF_ID, buildNotification())
                        Thread.sleep(1000)
                    } catch (_: Exception) { break }
                }
            }.also { it.start() }
        } catch (_: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        running.set(false)
        isActive = false
        activeDns = ""
        try {
            worker?.interrupt()
            speedThread?.interrupt()
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

// ── Utils ───────────────────────────────────────────────
data class PingResult(val latency: Long?, val success: Boolean)

suspend fun singlePing(host: String, timeoutMs: Int = 2000): PingResult = withContext(Dispatchers.IO) {
    try {
        val t = measureTimeMillis {
            val addr = InetAddress.getByName(host.trim())
            addr.isReachable(timeoutMs)
        }
        if (t < timeoutMs) PingResult(t.coerceIn(1, 999), true)
        else PingResult(null, false)
    } catch (_: Exception) {
        PingResult(null, false)
    }
}

suspend fun measureLatency(host: String): Long? = singlePing(host).latency

fun detectGames(context: Context): List<GameEntry> {
    val pm = context.packageManager
    return knownGames.map { game ->
        val found = try {
            pm.getPackageInfo(game.packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
        game.copy(installed = found)
    }
}

// ── Root ────────────────────────────────────────────────
@Composable
fun App() {
    var selected by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf(SpeedState()) }

    // Live speed in UI
    LaunchedEffect(Unit) {
        while (isActive) {
            SpeedMonitor.tick()
            speed = SpeedState(SpeedMonitor.downKbps, SpeedMonitor.upKbps)
            delay(1000)
        }
    }

    val tabs = listOf(
        Triple("DNS", Icons.Outlined.Dns, Icons.Filled.Dns),
        Triple("Boost", Icons.Outlined.Bolt, Icons.Filled.Bolt),
        Triple("Games", Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
        Triple("Ping", Icons.Outlined.NetworkCheck, Icons.Filled.NetworkCheck)
    )

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // Speed bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, Modifier.size(14.dp), tint = Green)
                        Spacer(Modifier.width(4.dp))
                        Text(SpeedMonitor.format(speed.downKbps), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, null, Modifier.size(14.dp), tint = AccentSoft)
                        Spacer(Modifier.width(4.dp))
                        Text(SpeedMonitor.format(speed.upKbps), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (DnsVpnService.isActive) {
                        Text("DNS ${DnsVpnService.activeDns}", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                NavigationBar(containerColor = Surface, tonalElevation = 0.dp, windowInsets = WindowInsets.navigationBars) {
                    tabs.forEachIndexed { i, (label, outlined, filled) ->
                        NavigationBarItem(
                            selected = selected == i,
                            onClick = { selected = i },
                            icon = {
                                Icon(if (selected == i) filled else outlined, label, Modifier.size(22.dp))
                            },
                            label = {
                                Text(label, fontSize = 11.sp, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = Accent.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            when (selected) {
                0 -> DnsScreen()
                1 -> BoostScreen()
                2 -> GamesScreen()
                3 -> PingScreen()
            }
        }
    }
}

// ── DNS ─────────────────────────────────────────────────
@Composable
fun DnsScreen() {
    var servers by remember { mutableStateOf(dnsServers) }
    var testing by remember { mutableStateOf(false) }
    var customDns by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("DNS Servers", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${servers.size} resolvers · IR + Public", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        // Custom DNS
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customDns,
                onValueChange = { customDns = it },
                label = { Text("Custom resolver IP", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Surface2,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val ip = customDns.trim()
                    if (ip.isNotEmpty()) {
                        servers = listOf(DnsEntry("Custom", ip, ip, "Custom")) + servers.filter { it.name != "Custom" }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("Add", color = Color.White, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                testing = true
                scope.launch {
                    val results = servers.map { s ->
                        var success = 0
                        var totalLatency = 0L
                        repeat(3) {
                            val r = singlePing(s.primary, 1500)
                            if (r.success && r.latency != null) {
                                success++
                                totalLatency += r.latency
                            }
                        }
                        val avg = if (success > 0) totalLatency / success else null
                        val loss = ((3 - success) * 100) / 3
                        s.copy(latency = avg, loss = loss)
                    }.sortedBy { it.latency ?: 9999L }
                    val bestIp = results.firstOrNull { it.latency != null }?.primary
                    servers = results.map { it.copy(isBest = it.primary == bestIp) }
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            enabled = !testing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Scanning...", color = Color.White, fontWeight = FontWeight.Medium)
            } else {
                Icon(Icons.Default.Speed, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Scan All (${servers.size})", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(servers) { server ->
                DnsCard(server) {
                    // Apply this DNS
                    val prepare = VpnService.prepare(context)
                    if (prepare == null) {
                        context.startService(Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_CONNECT
                            putExtra(DnsVpnService.EXTRA_DNS1, server.primary)
                            putExtra(DnsVpnService.EXTRA_DNS2, server.secondary)
                        })
                        DnsVpnService.isActive = true
                        DnsVpnService.activeDns = server.primary
                    }
                }
            }
        }
    }
}

@Composable
fun DnsCard(server: DnsEntry, onApply: () -> Unit) {
    val borderColor = if (server.isBest) Accent else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onApply() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (server.region) {
                            "IR" -> Accent.copy(0.15f)
                            "Custom" -> Green.copy(0.15f)
                            else -> Surface2
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    server.region.take(3),
                    color = when (server.region) {
                        "IR" -> AccentSoft
                        "Custom" -> Green
                        else -> TextMuted
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (server.isBest) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "BEST",
                            color = Green,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Green.copy(0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    "${server.primary}  ·  ${server.secondary}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (server.loss > 0) {
                    Text("Loss ${server.loss}%", color = Red, fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (server.latency != null) {
                    val c = when {
                        server.latency!! < 40 -> Green
                        server.latency!! < 80 -> Yellow
                        else -> Red
                    }
                    Text("${server.latency} ms", color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Text("—", color = TextMuted, fontSize = 14.sp)
                }
                Text("tap to apply", color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

// ── Boost ───────────────────────────────────────────────
@Composable
fun BoostScreen() {
    val context = LocalContext.current
    var selectedDns by remember { mutableStateOf(dnsServers[0]) }
    var active by remember { mutableStateOf(DnsVpnService.isActive) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpn(context, selectedDns)
            active = true
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        val ringColor by animateColorAsState(if (active) Green else TextMuted.copy(0.3f), label = "ring")
        val fillColor by animateColorAsState(if (active) Green.copy(0.12f) else Surface2, label = "fill")

        Box(
            Modifier
                .size(140.dp)
                .border(3.dp, ringColor, CircleShape)
                .background(fillColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (active) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                    null, Modifier.size(34.dp),
                    tint = if (active) Green else TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (active) "ACTIVE" else "OFF",
                    color = if (active) Green else TextMuted,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                if (active) {
                    Text(DnsVpnService.activeDns, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Select DNS then Connect", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(dnsServers) { dns ->
                val sel = selectedDns.primary == dns.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Accent.copy(0.12f) else Surface)
                        .clickable { selectedDns = dns }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dns.name, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(dns.primary, color = TextMuted, fontSize = 11.sp)
                    if (sel) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (active) {
                    context.startService(Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_DISCONNECT
                    })
                    active = false
                    DnsVpnService.isActive = false
                } else {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) permissionLauncher.launch(prepare)
                    else {
                        startVpn(context, selectedDns)
                        active = true
                    }
                }
            },
            Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (active) Red else Accent)
        ) {
            Icon(if (active) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (active) "Disconnect" else "Connect", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun startVpn(context: Context, dns: DnsEntry) {
    context.startService(Intent(context, DnsVpnService::class.java).apply {
        action = DnsVpnService.ACTION_CONNECT
        putExtra(DnsVpnService.EXTRA_DNS1, dns.primary)
        putExtra(DnsVpnService.EXTRA_DNS2, dns.secondary)
    })
    DnsVpnService.isActive = true
    DnsVpnService.activeDns = dns.primary
}

// ── Games ───────────────────────────────────────────────
@Composable
fun GamesScreen() {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<GameEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun rescan() {
        loading = true
        games = detectGames(context)
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    val installed = games.filter { it.installed }
    val rest = games.filter { !it.installed }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Games", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) "Scanning..." else "${installed.size} installed · ${rest.size} supported",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
            IconButton(onClick = { rescan() }) {
                Icon(Icons.Default.Refresh, "Rescan", tint = Accent)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                if (installed.isNotEmpty()) {
                    item {
                        Text("INSTALLED", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(installed) { g -> GameCard(g) }
                } else {
                    item {
                        Text("No supported games found on device", color = TextMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                if (rest.isNotEmpty()) {
                    item {
                        Text("SUPPORTED", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, top = 12.dp))
                    }
                    items(rest) { g -> GameCard(g) }
                }
            }
        }
    }
}

@Composable
fun GameCard(game: GameEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (game.installed) Green.copy(0.12f) else Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SportsEsports, null, Modifier.size(18.dp),
                tint = if (game.installed) Green else TextMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(game.packageName, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (game.installed) {
            Text(
                "ON DEVICE",
                color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Green.copy(0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Ping ────────────────────────────────────────────────
@Composable
fun PingScreen() {
    var host by remember { mutableStateOf("1.1.1.1") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var packetCount by remember { mutableIntStateOf(10) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Ping Test", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Latency + packet loss", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host / IP / Resolver", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Surface2,
                focusedLabelColor = Accent,
                cursorColor = Accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1.1.1.1", "8.8.8.8", "178.22.122.100", "78.157.42.100").forEach { h ->
                AssistChip(
                    onClick = { host = h },
                    label = { Text(h, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = Surface, labelColor = AccentSoft)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Packets:", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            listOf(5, 10, 20).forEach { n ->
                FilterChip(
                    selected = packetCount == n,
                    onClick = { packetCount = n },
                    label = { Text("$n", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(0.2f),
                        selectedLabelColor = Accent
                    )
                )
                Spacer(Modifier.width(4.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                running = true
                results = emptyList()
                scope.launch {
                    val list = mutableListOf<String>()
                    var ok = 0
                    val latencies = mutableListOf<Long>()
                    repeat(packetCount) { i ->
                        val r = singlePing(host, 2000)
                        if (r.success && r.latency != null) {
                            ok++
                            latencies.add(r.latency)
                            list.add("#${i + 1}  →  ${r.latency} ms")
                        } else {
                            list.add("#${i + 1}  →  timeout")
                        }
                        results = list.toList()
                        delay(200)
                    }
                    val loss = ((packetCount - ok) * 100) / packetCount
                    list.add("")
                    list.add("sent $packetCount  received $ok  loss $loss%")
                    if (latencies.isNotEmpty()) {
                        list.add("avg ${latencies.average().toInt()} ms")
                        list.add("min ${latencies.min()} ms   max ${latencies.max()} ms")
                    }
                    results = list
                    running = false
                }
            },
            Modifier.fillMaxWidth().height(46.dp),
            enabled = !running,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (running) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Running...", color = Color.White)
            } else {
                Icon(Icons.Default.NetworkCheck, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Start Test", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        // Apply this host as DNS
        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val prepare = VpnService.prepare(context)
                    if (prepare == null) {
                        context.startService(Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_CONNECT
                            putExtra(DnsVpnService.EXTRA_DNS1, host)
                            putExtra(DnsVpnService.EXTRA_DNS2, host)
                        })
                        DnsVpnService.isActive = true
                        DnsVpnService.activeDns = host
                    }
                },
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
            ) {
                Icon(Icons.Default.Bolt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply as DNS & Connect")
            }
        }

        Spacer(Modifier.height(14.dp))

        if (results.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    results.forEach { line ->
                        val highlight = line.contains("avg") || line.contains("loss") || line.contains("min")
                        Text(
                            line.ifEmpty { " " },
                            color = if (highlight) AccentSoft else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
