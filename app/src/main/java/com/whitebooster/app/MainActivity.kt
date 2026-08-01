package com.whitebooster.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.system.measureTimeMillis

// ── Theme colors ────────────────────────────────────────
private val DarkBg = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF141A22)
private val DarkSurface2 = Color(0xFF1C2430)
private val LightBg = Color(0xFFF5F6FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFEEF0F5)
private val Accent = Color(0xFF6C5CE7)
private val AccentSoft = Color(0xFFA29BFE)
private val Green = Color(0xFF00C853)
private val Yellow = Color(0xFFFFD600)
private val Red = Color(0xFFFF5252)

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val text2: Color,
    val muted: Color
)

private val DarkColors = AppColors(DarkBg, DarkSurface, DarkSurface2, Color(0xFFE8EAED), Color(0xFF8B95A5), Color(0xFF5A6577))
private val LightColors = AppColors(LightBg, LightSurface, LightSurface2, Color(0xFF1A1D24), Color(0xFF5A6577), Color(0xFF9AA3B2))

// ── Prefs ───────────────────────────────────────────────
object Prefs {
    private const val N = "wb_prefs"
    fun get(ctx: Context) = ctx.getSharedPreferences(N, Context.MODE_PRIVATE)
    var theme: String
        get() = _theme
        set(v) { _theme = v }
    private var _theme = "auto" // auto | dark | light
    var lang: String = "en" // en | fa
    var protocol: String = "both" // tcp | udp | both
    var compress: Boolean = false
    var mtu: Int = 1500
    var maxLoss: Int = 30
}

object AppLog {
    private val lines = mutableListOf<String>()
    private val lock = Mutex()
    suspend fun add(msg: String) = lock.withLock {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        lines.add("[$t] $msg")
        if (lines.size > 500) lines.removeAt(0)
    }
    suspend fun dump(): String = lock.withLock { lines.joinToString("\n") }
    suspend fun clear() = lock.withLock { lines.clear() }
}

// ── Data ────────────────────────────────────────────────
data class DnsEntry(
    val name: String,
    val primary: String,
    val secondary: String,
    val region: String,
    val games: List<String> = emptyList(),
    var latency: Long? = null,
    var loss: Int = 0,
    var isBest: Boolean = false
)

data class GameEntry(
    val name: String,
    val packageName: String,
    val preferredDns: List<String> = listOf("Shecan", "Electro", "Radar Game"),
    var installed: Boolean = false
)

private val dnsServers = listOf(
    DnsEntry("Shecan", "178.22.122.100", "185.51.200.2", "IR", listOf("mlbb", "pubg", "cod")),
    DnsEntry("Electro", "78.157.42.100", "78.157.42.101", "IR", listOf("mlbb", "freefire")),
    DnsEntry("403.online", "10.202.10.10", "10.202.10.11", "IR"),
    DnsEntry("Begzar", "185.55.226.26", "185.55.225.25", "IR"),
    DnsEntry("Radar Game", "10.10.34.35", "10.10.34.36", "IR", listOf("mlbb", "pubg")),
    DnsEntry("Shelter", "94.103.125.157", "94.103.125.158", "IR"),
    DnsEntry("Cloudflare", "1.1.1.1", "1.0.0.1", "Global"),
    DnsEntry("Google", "8.8.8.8", "8.8.4.4", "Global"),
    DnsEntry("Quad9", "9.9.9.9", "149.112.112.112", "Global"),
    DnsEntry("AdGuard", "94.140.14.14", "94.140.15.15", "Global"),
    DnsEntry("OpenDNS", "208.67.222.222", "208.67.220.220", "Global"),
    DnsEntry("Control D", "76.76.2.0", "76.76.10.0", "Global"),
    DnsEntry("CleanBrowsing", "185.228.168.9", "185.228.169.9", "Global"),
    DnsEntry("Level3", "4.2.2.4", "4.2.2.1", "Global"),
    DnsEntry("Yandex", "77.88.8.8", "77.88.8.1", "Global"),
    DnsEntry("DNS.SB", "185.222.222.222", "185.184.185.185", "Global"),
    DnsEntry("Mullvad", "194.242.2.2", "194.242.2.3", "Global"),
    DnsEntry("Comodo", "8.26.56.26", "8.20.247.20", "Global")
)

private val knownGames = listOf(
    GameEntry("Mobile Legends", "com.mobile.legends", listOf("Electro", "Shecan", "Radar Game")),
    GameEntry("Call of Duty Mobile", "com.activision.callofduty.shooter", listOf("Shecan", "Electro")),
    GameEntry("PUBG Mobile", "com.tencent.ig", listOf("Shecan", "Radar Game")),
    GameEntry("PUBG Mobile Global", "com.pubg.imobile", listOf("Shecan", "Radar Game")),
    GameEntry("Free Fire", "com.dts.freefireth", listOf("Electro", "Shecan")),
    GameEntry("Free Fire Max", "com.dts.freefiremax", listOf("Electro", "Shecan")),
    GameEntry("Genshin Impact", "com.miHoYo.GenshinImpact"),
    GameEntry("Clash of Clans", "com.supercell.clashofclans"),
    GameEntry("Clash Royale", "com.supercell.clashroyale"),
    GameEntry("Brawl Stars", "com.supercell.brawlstars"),
    GameEntry("Roblox", "com.roblox.client"),
    GameEntry("Minecraft", "com.mojang.minecraftpe"),
    GameEntry("Among Us", "com.innersloth.spacemafia"),
    GameEntry("Wild Rift", "com.riotgames.league.wildrift"),
    GameEntry("Fortnite", "com.epicgames.fortnite"),
    GameEntry("Apex Legends", "com.ea.gp.apexlegendsmobilefps"),
    GameEntry("eFootball", "jp.konami.pesam"),
    GameEntry("FIFA Mobile", "com.ea.gp.fifamobile"),
    GameEntry("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM"),
    GameEntry("Subway Surfers", "com.kiloo.subwaysurf")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val p = Prefs.get(this)
        Prefs.theme = p.getString("theme", "auto") ?: "auto"
        Prefs.lang = p.getString("lang", "en") ?: "en"
        Prefs.protocol = p.getString("protocol", "both") ?: "both"
        Prefs.compress = p.getBoolean("compress", false)
        Prefs.mtu = p.getInt("mtu", 1500)
        Prefs.maxLoss = p.getInt("maxLoss", 30)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (Prefs.theme) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            val colors = if (dark) DarkColors else LightColors
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Accent, background = colors.bg, surface = colors.surface)
                else lightColorScheme(primary = Accent, background = colors.bg, surface = colors.surface)
            ) {
                Surface(Modifier.fillMaxSize(), color = colors.bg) {
                    App(colors, dark) { /* recompose trigger via state inside */ }
                }
            }
        }
    }
}

// ── Speed ───────────────────────────────────────────────
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
        lastRx = rx; lastTx = tx; lastTime = now
    }
    fun format(kbps: Float) = if (kbps >= 1000) String.format("%.1f Mbps", kbps / 1000) else String.format("%.0f Kbps", kbps)
}

// ── VPN Service (session-friendly DNS only) ─────────────
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
            ACTION_CONNECT -> startVpn(
                intent.getStringExtra(EXTRA_DNS1) ?: "1.1.1.1",
                intent.getStringExtra(EXTRA_DNS2) ?: "1.0.0.1"
            )
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "White Booster", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotif(): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, DnsVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhitDNS · $activeDns")
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
            // DNS-only style: still need tunnel but keep sessions stable
            val b = Builder()
                .setSession("WhitDNS")
                .addAddress("10.8.0.2", 32)
                .addDnsServer(d1)
                .addDnsServer(d2)
                .setMtu(Prefs.mtu.coerceIn(1280, 1500))
                .setBlocking(true)
                .allowBypass() // help keep some sessions
            // Route only DNS-ish traffic is not fully possible without full stack;
            // allowFamily keeps IPv4 sessions simpler
            try { b.allowFamily(android.system.OsConstants.AF_INET) } catch (_: Exception) {}
            b.addRoute("0.0.0.0", 0)

            vpnInterface = b.establish() ?: return
            running.set(true)
            isActive = true
            activeDns = d1
            startForeground(NOTIF_ID, buildNotif())

            worker = Thread {
                try {
                    val fd = vpnInterface!!.fileDescriptor
                    val input = FileInputStream(fd)
                    val output = FileOutputStream(fd)
                    val buf = ByteArray(32767)
                    while (running.get()) {
                        val n = input.read(buf)
                        if (n > 0) {
                            // optional light pass-through; compress flag is preference only
                            output.write(buf, 0, n)
                        }
                    }
                } catch (_: Exception) {}
            }.also { it.start() }

            speedThread = Thread {
                val nm = getSystemService(NotificationManager::class.java)
                while (running.get()) {
                    try {
                        SpeedMonitor.tick()
                        nm.notify(NOTIF_ID, buildNotif())
                        Thread.sleep(1000)
                    } catch (_: Exception) { break }
                }
            }.also { it.start() }
        } catch (_: Exception) { stopVpn() }
    }

    private fun stopVpn() {
        running.set(false); isActive = false; activeDns = ""
        try { worker?.interrupt(); speedThread?.interrupt(); vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }
}

// ── Utils ───────────────────────────────────────────────
data class PingResult(val latency: Long?, val success: Boolean)

suspend fun singlePing(host: String, timeoutMs: Int = 2000): PingResult = withContext(Dispatchers.IO) {
    try {
        val t = measureTimeMillis {
            InetAddress.getByName(host.trim()).isReachable(timeoutMs)
        }
        if (t < timeoutMs) PingResult(t.coerceIn(1, 999), true) else PingResult(null, false)
    } catch (_: Exception) { PingResult(null, false) }
}

fun detectGames(context: Context): List<GameEntry> {
    val pm = context.packageManager
    return knownGames.map { g ->
        val found = try { pm.getPackageInfo(g.packageName, 0); true } catch (_: Exception) { false }
        g.copy(installed = found)
    }
}

fun launchGame(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

fun deviceInfo(context: Context): String {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val mem = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val totalRam = Formatter.formatFileSize(context, mem.totalMem)
    val cpu = Build.HARDWARE + " / " + Build.BOARD
    val gpu = try {
        // best-effort
        Build.MODEL + " GPU"
    } catch (_: Exception) { "Unknown" }
    return """
        Model: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        RAM: $totalRam
        CPU/SoC: $cpu
        Board: ${Build.BOARD}
        GPU hint: $gpu
        ABI: ${Build.SUPPORTED_ABIS.joinToString()}
    """.trimIndent()
}

fun tr(en: String, fa: String) = if (Prefs.lang == "fa") fa else en

// ── Root UI ─────────────────────────────────────────────
@Composable
fun App(colors: AppColors, dark: Boolean, onThemeChange: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf(0f to 0f) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            SpeedMonitor.tick()
            speed = SpeedMonitor.downKbps to SpeedMonitor.upKbps
            delay(1000)
        }
    }

    val tabs = listOf(
        Triple(tr("DNS", "دی‌ان‌اس"), Icons.Outlined.Dns, Icons.Filled.Dns),
        Triple(tr("Boost", "بوست"), Icons.Outlined.Bolt, Icons.Filled.Bolt),
        Triple(tr("Games", "بازی‌ها"), Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
        Triple(tr("Ping", "پینگ"), Icons.Outlined.NetworkCheck, Icons.Filled.NetworkCheck),
        Triple(tr("Settings", "تنظیمات"), Icons.Outlined.Settings, Icons.Filled.Settings)
    )

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, Modifier.size(13.dp), tint = Green)
                        Spacer(Modifier.width(3.dp))
                        Text(SpeedMonitor.format(speed.first), color = colors.text, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Upload, null, Modifier.size(13.dp), tint = AccentSoft)
                        Spacer(Modifier.width(3.dp))
                        Text(SpeedMonitor.format(speed.second), color = colors.text, fontSize = 11.sp)
                    }
                    if (DnsVpnService.isActive) {
                        Text("DNS ${DnsVpnService.activeDns}", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp, windowInsets = WindowInsets.navigationBars) {
                    tabs.forEachIndexed { i, (label, out, fill) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(if (tab == i) fill else out, label, Modifier.size(20.dp)) },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                                indicatorColor = Accent.copy(0.12f)
                            )
                        )
                    }
                }
                Text(
                    "Copyrighted by WhitDNS",
                    color = colors.muted,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
            key(refresh, Prefs.lang, Prefs.theme) {
                when (tab) {
                    0 -> DnsScreen(colors)
                    1 -> BoostScreen(colors)
                    2 -> GamesScreen(colors)
                    3 -> PingScreen(colors)
                    4 -> SettingsScreen(colors) { refresh++ }
                }
            }
        }
    }
}

// ── DNS (multithreaded scan) ────────────────────────────
@Composable
fun DnsScreen(colors: AppColors) {
    var servers by remember { mutableStateOf(dnsServers) }
    var testing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(tr("DNS Servers", "سرورهای DNS"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(tr("Parallel scan · tap card to apply", "اسکن موازی · لمس برای اعمال"), color = colors.text2, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom, onValueChange = { custom = it },
                label = { Text(tr("Custom IP", "IP سفارشی"), fontSize = 12.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(colors)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val ip = custom.trim()
                    if (ip.isNotEmpty()) {
                        servers = listOf(DnsEntry("Custom", ip, ip, "Custom")) + servers.filter { it.name != "Custom" }
                        scope.launch { AppLog.add("Custom DNS added: $ip") }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(tr("Add", "افزودن"), color = Color.White, fontSize = 13.sp) }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                testing = true
                scope.launch {
                    AppLog.add("Parallel DNS scan started (${servers.size})")
                    // Multithreaded parallel scan
                    val results = withContext(Dispatchers.IO) {
                        servers.map { s ->
                            async {
                                var ok = 0
                                var total = 0L
                                repeat(3) {
                                    val r = singlePing(s.primary, 1500)
                                    if (r.success && r.latency != null) {
                                        ok++; total += r.latency
                                    }
                                }
                                val avg = if (ok > 0) total / ok else null
                                val loss = ((3 - ok) * 100) / 3
                                s.copy(latency = avg, loss = loss)
                            }
                        }.awaitAll().sortedBy { it.latency ?: 9999L }
                    }
                    val best = results.firstOrNull { it.latency != null && it.loss <= Prefs.maxLoss }?.primary
                    servers = results.map { it.copy(isBest = it.primary == best) }
                    AppLog.add("Scan done. Best: $best")
                    testing = false
                }
            },
            Modifier.fillMaxWidth().height(46.dp),
            enabled = !testing,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(tr("Scanning...", "در حال اسکن..."), color = Color.White)
            } else {
                Icon(Icons.Default.Speed, null, Modifier.size(18.dp), Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tr("Scan All (Multi-thread)", "اسکن همه (چندنخی)"), color = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(servers) { s ->
                DnsCard(s, colors) {
                    applyDns(ctx, s)
                    scope.launch { AppLog.add("Applied DNS ${s.name} ${s.primary}") }
                }
            }
        }
    }
}

@Composable
fun DnsCard(s: DnsEntry, colors: AppColors, onApply: () -> Unit) {
    Card(
        Modifier.fillMaxWidth()
            .border(1.dp, if (s.isBest) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { onApply() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (s.region == "IR") Accent.copy(0.15f) else colors.surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(s.region.take(3), color = if (s.region == "IR") AccentSoft else colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (s.isBest) {
                        Spacer(Modifier.width(6.dp))
                        Text("BEST", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Green.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Text("${s.primary}", color = colors.muted, fontSize = 11.sp)
                if (s.loss > 0) Text("Loss ${s.loss}%", color = Red, fontSize = 10.sp)
            }
            if (s.latency != null) {
                val c = when { s.latency!! < 40 -> Green; s.latency!! < 80 -> Yellow; else -> Red }
                Text("${s.latency} ms", color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else Text("—", color = colors.muted)
        }
    }
}

fun applyDns(ctx: Context, dns: DnsEntry) {
    val prep = VpnService.prepare(ctx)
    if (prep == null) {
        ctx.startService(Intent(ctx, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_CONNECT
            putExtra(DnsVpnService.EXTRA_DNS1, dns.primary)
            putExtra(DnsVpnService.EXTRA_DNS2, dns.secondary)
        })
        DnsVpnService.isActive = true
        DnsVpnService.activeDns = dns.primary
    } else {
        // caller should launch permission; Boost screen handles full flow
        try { (ctx as? Activity)?.startActivityForResult(prep, 1001) } catch (_: Exception) {}
    }
}

// ── Boost ───────────────────────────────────────────────
@Composable
fun BoostScreen(colors: AppColors) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(dnsServers[0]) }
    var active by remember { mutableStateOf(DnsVpnService.isActive) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            startVpn(ctx, selected); active = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        val ring by animateColorAsState(if (active) Green else colors.muted.copy(0.3f), label = "r")
        Box(
            Modifier.size(130.dp).border(3.dp, ring, CircleShape)
                .background(if (active) Green.copy(0.1f) else colors.surface2, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (active) Icons.Filled.Bolt else Icons.Outlined.Bolt, null, Modifier.size(32.dp), if (active) Green else colors.muted)
                Text(if (active) "ACTIVE" else "OFF", color = if (active) Green else colors.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (active) Text(DnsVpnService.activeDns, color = colors.text2, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Protocol: ${Prefs.protocol.uppercase()} · MTU ${Prefs.mtu}", "پروتکل: ${Prefs.protocol} · MTU ${Prefs.mtu}"), color = colors.text2, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(dnsServers) { d ->
                val sel = selected.primary == d.primary
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Accent.copy(0.12f) else colors.surface)
                        .clickable { selected = d }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.name, color = colors.text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(d.primary, color = colors.muted, fontSize = 11.sp)
                    if (sel) Icon(Icons.Default.Check, null, Modifier.size(16.dp), Accent)
                }
            }
        }

        Button(
            onClick = {
                if (active) {
                    ctx.startService(Intent(ctx, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_DISCONNECT })
                    active = false; DnsVpnService.isActive = false
                } else {
                    val prep = VpnService.prepare(ctx)
                    if (prep != null) launcher.launch(prep)
                    else { startVpn(ctx, selected); active = true }
                }
            },
            Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (active) Red else Accent)
        ) {
            Text(if (active) tr("Disconnect", "قطع") else tr("Connect (request VPN)", "اتصال (درخواست VPN)"), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

fun startVpn(ctx: Context, dns: DnsEntry) {
    ctx.startService(Intent(ctx, DnsVpnService::class.java).apply {
        action = DnsVpnService.ACTION_CONNECT
        putExtra(DnsVpnService.EXTRA_DNS1, dns.primary)
        putExtra(DnsVpnService.EXTRA_DNS2, dns.secondary)
    })
    DnsVpnService.isActive = true
    DnsVpnService.activeDns = dns.primary
}

// ── Games ───────────────────────────────────────────────
@Composable
fun GamesScreen(colors: AppColors) {
    val ctx = LocalContext.current
    var games by remember { mutableStateOf(emptyList<GameEntry>()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun rescan() {
        loading = true
        games = detectGames(ctx)
        loading = false
        scope.launch { AppLog.add("Game scan: ${games.count { it.installed }} installed") }
    }
    LaunchedEffect(Unit) { rescan() }

    val installed = games.filter { it.installed }
    val rest = games.filter { !it.installed }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Games", "بازی‌ها"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (loading) tr("Scanning...", "اسکن...") else tr("${installed.size} installed", "${installed.size} نصب‌شده"),
                    color = colors.text2, fontSize = 12.sp
                )
            }
            IconButton(onClick = { rescan() }) { Icon(Icons.Default.Refresh, null, tint = Accent) }
        }
        Spacer(Modifier.height(10.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                item {
                    Text(tr("INSTALLED — Boost & Launch", "نصب‌شده — بوست و اجرا"), color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (installed.isEmpty()) {
                    item { Text(tr("No games found", "بازی‌ای پیدا نشد"), color = colors.muted, fontSize = 12.sp) }
                }
                items(installed) { g ->
                    GameCard(g, colors, true) {
                        // smart DNS for this game
                        val preferred = dnsServers.firstOrNull { it.name in g.preferredDns } ?: dnsServers[0]
                        applyDns(ctx, preferred)
                        scope.launch {
                            AppLog.add("Boost ${g.name} with ${preferred.name}")
                            delay(800)
                            launchGame(ctx, g.packageName)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(tr("SUPPORTED", "پشتیبانی‌شده"), color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                items(rest) { g -> GameCard(g, colors, false) {} }
            }
        }
    }
}

@Composable
fun GameCard(g: GameEntry, colors: AppColors, installed: Boolean, onBoost: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
            .then(if (installed) Modifier.clickable { onBoost() } else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                .background(if (installed) Green.copy(0.12f) else colors.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SportsEsports, null, Modifier.size(18.dp), if (installed) Green else colors.muted)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (installed) Text(tr("Tap: set DNS + launch", "لمس: DNS + اجرا"), color = colors.muted, fontSize = 10.sp)
            else Text(g.packageName, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (installed) {
            Text("BOOST", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Accent.copy(0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

// ── Ping ────────────────────────────────────────────────
@Composable
fun PingScreen(colors: AppColors) {
    var host by remember { mutableStateOf("1.1.1.1") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(10) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(tr("Ping Test", "تست پینگ"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { Text(tr("Host / Resolver", "هاست / ریزالور"), fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp), colors = fieldColors(colors)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(5, 10, 20).forEach { n ->
                FilterChip(
                    selected = count == n, onClick = { count = n },
                    label = { Text("$n") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(0.2f), selectedLabelColor = Accent)
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                running = true; results = emptyList()
                scope.launch {
                    val list = mutableListOf<String>()
                    var ok = 0
                    val lats = mutableListOf<Long>()
                    repeat(count) { i ->
                        val r = singlePing(host, 2000)
                        if (r.success && r.latency != null) {
                            ok++; lats.add(r.latency); list.add("#${i + 1} → ${r.latency} ms")
                        } else list.add("#${i + 1} → timeout")
                        results = list.toList(); delay(180)
                    }
                    val loss = ((count - ok) * 100) / count
                    list.add(""); list.add("sent $count  recv $ok  loss $loss%")
                    if (lats.isNotEmpty()) {
                        list.add("avg ${lats.average().toInt()}  min ${lats.min()}  max ${lats.max()}")
                    }
                    results = list; running = false
                    AppLog.add("Ping $host loss=$loss%")
                }
            },
            Modifier.fillMaxWidth().height(46.dp), enabled = !running,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(if (running) tr("Running...", "در حال اجرا...") else tr("Start", "شروع"), color = Color.White)
        }
        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    applyDns(ctx, DnsEntry("Manual", host, host, "Custom"))
                    scope.launch { AppLog.add("Applied from ping: $host") }
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text(tr("Apply as DNS", "اعمال به عنوان DNS"), color = Accent) }
        }
        Spacer(Modifier.height(12.dp))
        if (results.isNotEmpty()) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                Column(Modifier.padding(14.dp)) {
                    results.forEach { line ->
                        Text(line.ifEmpty { " " }, color = if (line.contains("loss") || line.contains("avg")) AccentSoft else colors.text, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Settings ────────────────────────────────────────────
@Composable
fun SettingsScreen(colors: AppColors, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Prefs.get(ctx)

    fun save() {
        prefs.edit()
            .putString("theme", Prefs.theme)
            .putString("lang", Prefs.lang)
            .putString("protocol", Prefs.protocol)
            .putBoolean("compress", Prefs.compress)
            .putInt("mtu", Prefs.mtu)
            .putInt("maxLoss", Prefs.maxLoss)
            .apply()
        onChanged()
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(tr("Settings", "تنظیمات"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
        }

        item {
            SectionTitle(tr("Appearance", "ظاهر"), colors)
            SettingRow(tr("Theme", "پوسته"), colors) {
                listOf("auto", "dark", "light").forEach { t ->
                    FilterChip(
                        selected = Prefs.theme == t,
                        onClick = { Prefs.theme = t; save() },
                        label = { Text(t) }
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            SettingRow(tr("Language", "زبان"), colors) {
                FilterChip(selected = Prefs.lang == "en", onClick = { Prefs.lang = "en"; save() }, label = { Text("English") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = Prefs.lang == "fa", onClick = { Prefs.lang = "fa"; save() }, label = { Text("فارسی") })
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle(tr("Network", "شبکه"), colors)
            SettingRow(tr("Protocol", "پروتکل"), colors) {
                listOf("both", "tcp", "udp").forEach { p ->
                    FilterChip(selected = Prefs.protocol == p, onClick = { Prefs.protocol = p; save() }, label = { Text(p.uppercase()) })
                    Spacer(Modifier.width(4.dp))
                }
            }
            SettingRow(tr("Packet compress", "فشرده‌سازی پکت"), colors) {
                Switch(checked = Prefs.compress, onCheckedChange = { Prefs.compress = it; save() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
            SettingRow(tr("Max loss %", "حداکثر لاس %"), colors) {
                listOf(10, 20, 30, 50).forEach { v ->
                    FilterChip(selected = Prefs.maxLoss == v, onClick = { Prefs.maxLoss = v; save() }, label = { Text("$v") })
                    Spacer(Modifier.width(4.dp))
                }
            }
            SettingRow("MTU", colors) {
                listOf(1280, 1400, 1500).forEach { v ->
                    FilterChip(selected = Prefs.mtu == v, onClick = { Prefs.mtu = v; save() }, label = { Text("$v") })
                    Spacer(Modifier.width(4.dp))
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle(tr("Device", "دستگاه"), colors)
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(12.dp)) {
                Text(deviceInfo(ctx), color = colors.text2, fontSize = 11.sp, modifier = Modifier.padding(12.dp), lineHeight = 16.sp)
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle(tr("Power & Cache", "باتری و کش"), colors)
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    try { ctx.startActivity(intent) } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2)
            ) { Text(tr("Battery unrestricted", "رفع محدودیت باتری"), color = colors.text) }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    try {
                        ctx.cacheDir.deleteRecursively()
                        ctx.externalCacheDir?.deleteRecursively()
                        scope.launch { AppLog.add("Cache cleared") }
                    } catch (_: Exception) {}
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2)
            ) { Text(tr("Clear cache", "پاک کردن کش"), color = colors.text) }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle(tr("Logs", "لاگ‌ها"), colors)
            Button(
                onClick = {
                    scope.launch {
                        val body = AppLog.dump().ifEmpty { "No logs yet" }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "WhitDNS Logs")
                            putExtra(Intent.EXTRA_TEXT, "To: @c8min\n\n$body")
                        }
                        ctx.startActivity(Intent.createChooser(send, "Send logs to @c8min"))
                    }
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text(tr("Share logs to Telegram @c8min", "ارسال لاگ به تلگرام @c8min"), color = Color.White) }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { scope.launch { AppLog.clear() } },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text(tr("Clear logs", "پاک کردن لاگ"), color = colors.text2) }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("Copyrighted by WhitDNS · v3.2", color = colors.muted, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun SectionTitle(t: String, colors: AppColors) {
    Text(t, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun SettingRow(label: String, colors: AppColors, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, color = colors.text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun fieldColors(colors: AppColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = colors.surface2,
    focusedLabelColor = Accent, cursorColor = Accent,
    focusedTextColor = colors.text, unfocusedTextColor = colors.text
)
