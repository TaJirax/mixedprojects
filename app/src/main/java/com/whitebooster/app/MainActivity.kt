package com.whitebooster.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

// ── Colors ──────────────────────────────────────────────
private val Bg          = Color(0xFF0B0F14)
private val Surface     = Color(0xFF141A22)
private val Surface2    = Color(0xFF1C2430)
private val Accent      = Color(0xFF6C5CE7)
private val AccentSoft  = Color(0xFFA29BFE)
private val Green       = Color(0xFF00E676)
private val Yellow      = Color(0xFFFFD600)
private val Red         = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE8EAED)
private val TextSecondary = Color(0xFF8B95A5)
private val TextMuted   = Color(0xFF5A6577)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

// ── Data ────────────────────────────────────────────────
data class DnsEntry(
    val name: String,
    val primary: String,
    val secondary: String,
    val region: String,
    var latency: Long? = null,
    var isBest: Boolean = false
)

data class GameEntry(
    val name: String,
    val packageName: String,
    var installed: Boolean = false
)

private val dnsServers = listOf(
    DnsEntry("Shecan", "178.22.122.100", "185.51.200.2", "IR"),
    DnsEntry("Electro", "78.157.42.100", "78.157.42.101", "IR"),
    DnsEntry("403.online", "10.202.10.10", "10.202.10.11", "IR"),
    DnsEntry("Begzar", "185.55.226.26", "185.55.225.25", "IR"),
    DnsEntry("Radar Game", "10.10.34.35", "10.10.34.36", "IR"),
    DnsEntry("Shelter", "94.103.125.157", "94.103.125.158", "IR"),
    DnsEntry("Cloudflare", "1.1.1.1", "1.0.0.1", "Global"),
    DnsEntry("Google", "8.8.8.8", "8.8.4.4", "Global"),
    DnsEntry("Quad9", "9.9.9.9", "149.112.112.112", "Global"),
    DnsEntry("AdGuard", "94.140.14.14", "94.140.15.15", "Global"),
    DnsEntry("OpenDNS", "208.67.222.222", "208.67.220.220", "Global"),
    DnsEntry("Level3", "4.2.2.4", "4.2.2.1", "Global")
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

// ── VPN Service ─────────────────────────────────────────
class DnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    companion object {
        const val ACTION_CONNECT = "CONNECT"
        const val ACTION_DISCONNECT = "DISCONNECT"
        const val EXTRA_DNS1 = "dns1"
        const val EXTRA_DNS2 = "dns2"
        var isActive = false
        var activeDns = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val d1 = intent.getStringExtra(EXTRA_DNS1) ?: "1.1.1.1"
                val d2 = intent.getStringExtra(EXTRA_DNS2) ?: "1.0.0.1"
                start(d1, d2)
            }
            ACTION_DISCONNECT -> stop()
        }
        return START_STICKY
    }

    private fun start(d1: String, d2: String) {
        if (running.get()) return
        try {
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
            activeDns = "$d1"

            thread = Thread {
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
        } catch (_: Exception) {
            stop()
        }
    }

    private fun stop() {
        running.set(false)
        isActive = false
        activeDns = ""
        try {
            thread?.interrupt()
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }
}

// ── Utils ───────────────────────────────────────────────
suspend fun measureLatency(host: String): Long? = withContext(Dispatchers.IO) {
    try {
        val t = measureTimeMillis { InetAddress.getByName(host.trim()) }
        if (t < 2500) t.coerceIn(1, 999) else null
    } catch (_: Exception) { null }
}

fun detectGames(context: Context): List<GameEntry> {
    val pm = context.packageManager
    return knownGames.map { game ->
        val found = try {
            pm.getPackageInfo(game.packageName, 0)
            true
        } catch (_: Exception) { false }
        game.copy(installed = found)
    }
}

// ── Root UI ─────────────────────────────────────────────
@Composable
fun App() {
    var selected by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Triple("DNS", Icons.Outlined.Dns, Icons.Filled.Dns),
        Triple("Boost", Icons.Outlined.Bolt, Icons.Filled.Bolt),
        Triple("Games", Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
        Triple("Ping", Icons.Outlined.NetworkCheck, Icons.Filled.NetworkCheck)
    )

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { i, (label, outlined, filled) ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = {
                            Icon(
                                if (selected == i) filled else outlined,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> DnsScreen()
                1 -> BoostScreen()
                2 -> GamesScreen()
                3 -> PingScreen()
            }
        }
    }
}

// ── DNS Screen ──────────────────────────────────────────
@Composable
fun DnsScreen() {
    var servers by remember { mutableStateOf(dnsServers) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("DNS Servers", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Tap test to find the fastest", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                testing = true
                scope.launch {
                    val results = servers.map { s ->
                        s.copy(latency = measureLatency(s.primary))
                    }.sortedBy { it.latency ?: 9999L }
                    val bestIp = results.firstOrNull()?.primary
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
                Text("Testing...", color = Color.White, fontWeight = FontWeight.Medium)
            } else {
                Icon(Icons.Default.Speed, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Test All Servers", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(servers) { server ->
                DnsCard(server)
            }
        }
    }
}

@Composable
fun DnsCard(server: DnsEntry) {
    val borderColor = if (server.isBest) Accent else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (server.region == "IR") Accent.copy(0.15f) else Surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    server.region,
                    color = if (server.region == "IR") AccentSoft else TextMuted,
                    fontSize = 11.sp,
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
            }

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
        }
    }
}

// ── Boost (VPN) Screen ──────────────────────────────────
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
        Spacer(Modifier.height(24.dp))

        // Status circle
        val circleColor by animateColorAsState(
            if (active) Green.copy(0.15f) else Surface2,
            label = "circle"
        )
        val ringColor by animateColorAsState(
            if (active) Green else TextMuted.copy(0.3f),
            label = "ring"
        )

        Box(
            Modifier
                .size(150.dp)
                .border(3.dp, ringColor, CircleShape)
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (active) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                    null,
                    Modifier.size(36.dp),
                    tint = if (active) Green else TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (active) "ACTIVE" else "OFF",
                    color = if (active) Green else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (active) {
                    Text(DnsVpnService.activeDns, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Select DNS", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(dnsServers) { dns ->
                val selected = selectedDns.primary == dns.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Accent.copy(0.12f) else Surface)
                        .clickable { selectedDns = dns }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dns.name, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(dns.primary, color = TextMuted, fontSize = 11.sp)
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Accent)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                    if (prepare != null) {
                        permissionLauncher.launch(prepare)
                    } else {
                        startVpn(context, selectedDns)
                        active = true
                    }
                }
            },
            Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Red else Accent
            )
        ) {
            Icon(
                if (active) Icons.Default.Stop else Icons.Default.PlayArrow,
                null,
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (active) "Disconnect" else "Connect",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))
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

// ── Games Screen ────────────────────────────────────────
@Composable
fun GamesScreen() {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<GameEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        games = detectGames(context)
        loading = false
    }

    val installed = games.filter { it.installed }
    val rest = games.filter { !it.installed }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Games", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
        } else {
            Text(
                "${installed.size} installed · ${rest.size} supported",
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (installed.isNotEmpty()) {
                    item {
                        Text("INSTALLED", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, top = 2.dp))
                    }
                    items(installed) { g -> GameCard(g) }
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
                Icons.Default.SportsEsports,
                null,
                Modifier.size(18.dp),
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
                color = Green,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Green.copy(0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Ping Screen ─────────────────────────────────────────
@Composable
fun PingScreen() {
    var host by remember { mutableStateOf("1.1.1.1") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Ping Test", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Run a 5-packet latency check", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host or IP", fontSize = 13.sp) },
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
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Surface,
                        labelColor = AccentSoft
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                running = true
                results = emptyList()
                scope.launch {
                    val list = mutableListOf<String>()
                    repeat(5) { i ->
                        val ms = measureLatency(host)
                        list.add(if (ms != null) "#${i + 1}  →  $ms ms" else "#${i + 1}  →  timeout")
                        results = list.toList()
                        delay(250)
                    }
                    val nums = list.mapNotNull {
                        Regex("""(\d+) ms""").find(it)?.groupValues?.get(1)?.toLongOrNull()
                    }
                    if (nums.isNotEmpty()) {
                        list.add("")
                        list.add("avg  ${nums.average().toInt()} ms")
                        list.add("min  ${nums.min()} ms   max  ${nums.max()} ms")
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

        Spacer(Modifier.height(16.dp))

        if (results.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    results.forEach { line ->
                        Text(
                            line.ifEmpty { " " },
                            color = if (line.startsWith("avg") || line.startsWith("min")) AccentSoft else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (line.startsWith("avg") || line.startsWith("min")) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
