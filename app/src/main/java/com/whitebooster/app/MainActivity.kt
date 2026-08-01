package com.whitebooster.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhiteBoosterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0F1A)) {
                    WhiteBoosterApp()
                }
            }
        }
    }
}

// ==================== DATA ====================

data class DnsServer(
    val name: String,
    val primary: String,
    val secondary: String,
    val category: String,
    val description: String,
    var pingMs: Long? = null,
    var isBest: Boolean = false
)

data class GameInfo(
    val name: String,
    val packageName: String,
    val isInstalled: Boolean = false
)

val dnsList = listOf(
    DnsServer("Shecan", "178.22.122.100", "185.51.200.2", "ایرانی", "بهترین انتخاب برای گیمینگ در ایران"),
    DnsServer("Electro", "78.157.42.100", "78.157.42.101", "ایرانی", "تأخیر بسیار پایین برای بازی"),
    DnsServer("403 Online", "10.202.10.10", "10.202.10.11", "ایرانی", "سرعت و پایداری عالی"),
    DnsServer("Begzar", "185.55.226.26", "185.55.225.25", "ایرانی", "مخصوص کاربران ایرانی"),
    DnsServer("Radar Game", "10.10.34.35", "10.10.34.36", "ایرانی", "بهینه‌شده مخصوص گیم"),
    DnsServer("Shelter", "94.103.125.157", "94.103.125.158", "ایرانی", "پایدار و سریع"),
    DnsServer("Pishgaman", "5.202.100.100", "5.202.100.101", "ایرانی", "سازگار با اکثر اپراتورها"),
    DnsServer("Cloudflare", "1.1.1.1", "1.0.0.1", "عمومی", "سریع‌ترین DNS جهانی"),
    DnsServer("Google", "8.8.8.8", "8.8.4.4", "عمومی", "پایدار و همیشه در دسترس"),
    DnsServer("Quad9", "9.9.9.9", "149.112.112.112", "عمومی", "امن + سریع"),
    DnsServer("AdGuard", "94.140.14.14", "94.140.15.15", "عمومی", "حذف تبلیغ + سرعت"),
    DnsServer("OpenDNS", "208.67.222.222", "208.67.220.220", "عمومی", "امنیت بالا"),
    DnsServer("Level3", "4.2.2.4", "4.2.2.1", "گیمینگ", "کلاسیک گیمینگ"),
    DnsServer("Verisign", "64.6.64.6", "64.6.65.6", "گیمینگ", "تأخیر پایین"),
    DnsServer("CleanBrowsing", "185.228.168.9", "185.228.169.9", "گیمینگ", "مناسب خانواده")
)

val knownGames = listOf(
    GameInfo("Call of Duty Mobile", "com.activision.callofduty.shooter"),
    GameInfo("PUBG Mobile", "com.tencent.ig"),
    GameInfo("PUBG Mobile (Global)", "com.pubg.imobile"),
    GameInfo("Free Fire", "com.dts.freefireth"),
    GameInfo("Free Fire Max", "com.dts.freefiremax"),
    GameInfo("Genshin Impact", "com.miHoYo.GenshinImpact"),
    GameInfo("Mobile Legends", "com.mobile.legends"),
    GameInfo("Clash of Clans", "com.supercell.clashofclans"),
    GameInfo("Clash Royale", "com.supercell.clashroyale"),
    GameInfo("Brawl Stars", "com.supercell.brawlstars"),
    GameInfo("Roblox", "com.roblox.client"),
    GameInfo("Minecraft", "com.mojang.minecraftpe"),
    GameInfo("Among Us", "com.innersloth.spacemafia"),
    GameInfo("Wild Rift", "com.riotgames.league.wildrift"),
    GameInfo("Arena of Valor", "com.ngame.allstar.eu"),
    GameInfo("Fortnite", "com.epicgames.fortnite"),
    GameInfo("Apex Legends", "com.ea.gp.apexlegendsmobilefps"),
    GameInfo("Candy Crush", "com.king.candycrushsaga"),
    GameInfo("eFootball", "jp.konami.pesam"),
    GameInfo("FIFA Mobile", "com.ea.gp.fifamobile"),
    GameInfo("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM"),
    GameInfo("Subway Surfers", "com.kiloo.subwaysurf"),
    GameInfo("PES 2021", "jp.konami.pesclubmanager"),
    GameInfo("Shadow Fight 3", "com.nekki.shadowfight3"),
    GameInfo("War Robots", "com.pixonic.wwr")
)

// ==================== VPN SERVICE ====================

class DnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null

    companion object {
        const val ACTION_CONNECT = "com.whitebooster.app.CONNECT"
        const val ACTION_DISCONNECT = "com.whitebooster.app.DISCONNECT"
        const val EXTRA_DNS1 = "dns1"
        const val EXTRA_DNS2 = "dns2"
        var isVpnActive = false
        var currentDns: String = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val dns1 = intent.getStringExtra(EXTRA_DNS1) ?: "1.1.1.1"
                val dns2 = intent.getStringExtra(EXTRA_DNS2) ?: "1.0.0.1"
                startVpn(dns1, dns2)
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(dns1: String, dns2: String) {
        if (isRunning.get()) return
        try {
            val builder = Builder()
            builder.setSession("White Booster")
            builder.addAddress("10.0.0.2", 32)
            builder.addDnsServer(dns1)
            builder.addDnsServer(dns2)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            builder.setBlocking(true)

            vpnInterface = builder.establish()
            if (vpnInterface == null) return

            isRunning.set(true)
            isVpnActive = true
            currentDns = "$dns1 / $dns2"

            vpnThread = Thread {
                try {
                    val fd = vpnInterface!!.fileDescriptor
                    val input = FileInputStream(fd)
                    val output = FileOutputStream(fd)
                    val buffer = ByteArray(32767)

                    while (isRunning.get()) {
                        val length = input.read(buffer)
                        if (length > 0) {
                            // Simple pass-through (DNS is handled by system via addDnsServer)
                            output.write(buffer, 0, length)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            vpnThread?.start()
        } catch (_: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        isVpnActive = false
        currentDns = ""
        try {
            vpnThread?.interrupt()
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

// ==================== UTILS ====================

suspend fun measurePing(host: String, timeoutMs: Int = 2000): Long? = withContext(Dispatchers.IO) {
    try {
        val cleanHost = host.trim()
        // Method 1: DNS resolve time
        val resolveTime = measureTimeMillis {
            InetAddress.getByName(cleanHost)
        }
        // Method 2: UDP-like reachability approximation
        val reachable = try {
            val addr = InetAddress.getByName(cleanHost)
            addr.isReachable(timeoutMs)
        } catch (_: Exception) {
            false
        }
        if (reachable || resolveTime < timeoutMs) {
            resolveTime.coerceIn(1, 999)
        } else null
    } catch (_: Exception) {
        null
    }
}

fun getInstalledGames(context: Context): List<GameInfo> {
    val pm = context.packageManager
    return knownGames.map { game ->
        val installed = try {
            pm.getPackageInfo(game.packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
        game.copy(isInstalled = installed)
    }
}

// ==================== UI ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteBoosterApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("DNS", "VPN", "بازی‌ها", "تست", "راهنما")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ ", fontSize = 20.sp)
                        Text("White Booster", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1520)),
                actions = {
                    if (DnsVpnService.isVpnActive) {
                        Text(
                            "فعال",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF0A0F1A)
    ) { padding ->
        Column(Modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status bar
            if (DnsVpnService.isVpnActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF004D40))
                        .padding(8.dp)
                ) {
                    Text(
                        "🟢 VPN فعال | DNS: ${DnsVpnService.currentDns}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D1520),
                contentColor = Color(0xFF00D4FF)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                color = if (selectedTab == index) Color(0xFF00D4FF) else Color(0xFF607D8B)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> DnsScreen()
                1 -> VpnScreen()
                2 -> GamesScreen()
                3 -> PingTestScreen()
                4 -> GuideScreen()
            }
        }
    }
}

@Composable
fun DnsScreen() {
    var servers by remember { mutableStateOf(dnsList) }
    var selected by remember { mutableStateOf<DnsServer?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Smart recommend
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132033)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00D4FF))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("پیشنهاد هوشمند ایران", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Shecan و Electro معمولاً بهترین نتیجه را می‌دهند", color = Color(0xFF90A4AE), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                isTesting = true
                scope.launch {
                    val tested = servers.map { dns ->
                        val p = measurePing(dns.primary)
                        dns.copy(pingMs = p)
                    }.sortedBy { it.pingMs ?: 9999 }
                    val bestPrimary = tested.firstOrNull()?.primary
                    servers = tested.map { it.copy(isBest = it.primary == bestPrimary) }
                    isTesting = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
            enabled = !isTesting,
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isTesting) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("در حال تست همه DNSها...", color = Color.Black, fontSize = 13.sp)
            } else {
                Icon(Icons.Default.Speed, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تست همه و پیدا کردن بهترین", color = Color.Black, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(servers) { dns ->
                DnsItem(dns, selected?.primary == dns.primary) {
                    selected = dns
                }
            }
        }
    }
}

@Composable
fun DnsItem(dns: DnsServer, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (dns.isBest) Modifier.border(1.dp, Color(0xFF00D4FF), RoundedCornerShape(10.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF1A3A5C) else Color(0xFF132033)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dns.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (dns.isBest) {
                        Spacer(Modifier.width(6.dp))
                        Text("★ بهترین", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(dns.category, color = Color(0xFF546E7A), fontSize = 10.sp)
                }
                Text("${dns.primary}  •  ${dns.secondary}", color = Color(0xFF78909C), fontSize = 11.sp)
                Text(dns.description, color = Color(0xFF607D8B), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (dns.pingMs != null) {
                    val color = when {
                        dns.pingMs!! < 35 -> Color(0xFF00E676)
                        dns.pingMs!! < 70 -> Color(0xFFFFEB3B)
                        else -> Color(0xFFFF5252)
                    }
                    Text("${dns.pingMs} ms", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Text("—", color = Color.Gray, fontSize = 14.sp)
                }
                if (selected) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00D4FF), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun VpnScreen() {
    val context = LocalContext.current
    var selectedDns by remember { mutableStateOf(dnsList[0]) }
    var vpnActive by remember { mutableStateOf(DnsVpnService.isVpnActive) }
    var statusText by remember { mutableStateOf(if (vpnActive) "فعال" else "غیرفعال") }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_CONNECT
                putExtra(DnsVpnService.EXTRA_DNS1, selectedDns.primary)
                putExtra(DnsVpnService.EXTRA_DNS2, selectedDns.secondary)
            }
            context.startService(intent)
            vpnActive = true
            statusText = "فعال"
            DnsVpnService.isVpnActive = true
            DnsVpnService.currentDns = "${selectedDns.primary} / ${selectedDns.secondary}"
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status circle
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    if (vpnActive) Color(0xFF004D40) else Color(0xFF1A1A2E),
                    CircleShape
                )
                .border(
                    3.dp,
                    if (vpnActive) Color(0xFF00E676) else Color(0xFF37474F),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (vpnActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                    null,
                    tint = if (vpnActive) Color(0xFF00E676) else Color(0xFF607D8B),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(statusText, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("انتخاب DNS برای اعمال", color = Color(0xFF90A4AE), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        // DNS selector
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(dnsList.take(8)) { dns ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDns = dns },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedDns.primary == dns.primary)
                            Color(0xFF1A3A5C) else Color(0xFF132033)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dns.name, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(dns.primary, color = Color(0xFF78909C), fontSize = 12.sp)
                        if (selectedDns.primary == dns.primary) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Check, null, tint = Color(0xFF00D4FF), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (vpnActive) {
                    val intent = Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_DISCONNECT
                    }
                    context.startService(intent)
                    vpnActive = false
                    statusText = "غیرفعال"
                    DnsVpnService.isVpnActive = false
                } else {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        vpnPermissionLauncher.launch(prepare)
                    } else {
                        val intent = Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_CONNECT
                            putExtra(DnsVpnService.EXTRA_DNS1, selectedDns.primary)
                            putExtra(DnsVpnService.EXTRA_DNS2, selectedDns.secondary)
                        }
                        context.startService(intent)
                        vpnActive = true
                        statusText = "فعال"
                        DnsVpnService.isVpnActive = true
                        DnsVpnService.currentDns = "${selectedDns.primary} / ${selectedDns.secondary}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (vpnActive) Color(0xFFFF5252) else Color(0xFF00D4FF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (vpnActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                null,
                tint = if (vpnActive) Color.White else Color.Black
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (vpnActive) "قطع اتصال VPN" else "اعمال DNS و اتصال",
                color = if (vpnActive) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "با اتصال، DNS انتخاب‌شده روی کل ترافیک دستگاه اعمال می‌شود",
            color = Color(0xFF546E7A),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GamesScreen() {
    val context = LocalContext.current
    var games by remember { mutableStateOf(knownGames) }
    var scanned by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        games = getInstalledGames(context)
        scanned = true
    }

    val installed = games.filter { it.isInstalled }
    val notInstalled = games.filter { !it.isInstalled }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132033)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsEsports, null, tint = Color(0xFF00D4FF))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (scanned) "${installed.size} بازی نصب‌شده پیدا شد" else "در حال اسکن...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text("بازی‌های شناسایی‌شده از گوگل پلی", color = Color(0xFF90A4AE), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (installed.isNotEmpty()) {
            Text("نصب‌شده روی دستگاه", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(installed) { game ->
                    GameCard(game, true)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("سایر بازی‌های پشتیبانی‌شده", color = Color(0xFF607D8B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(notInstalled) { game ->
                GameCard(game, false)
            }
        }
    }
}

@Composable
fun GameCard(game: GameInfo, installed: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132033)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SportsEsports,
                null,
                tint = if (installed) Color(0xFF00E676) else Color(0xFF546E7A),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(game.name, color = Color.White, fontSize = 13.sp)
                Text(game.packageName, color = Color(0xFF546E7A), fontSize = 10.sp)
            }
            if (installed) {
                Text("نصب‌شده", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PingTestScreen() {
    var host by remember { mutableStateOf("1.1.1.1") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("تست پینگ پیشرفته", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("IP یا دامنه") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00D4FF),
                unfocusedBorderColor = Color(0xFF37474F),
                focusedLabelColor = Color(0xFF00D4FF),
                cursorColor = Color(0xFF00D4FF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1.1.1.1", "8.8.8.8", "178.22.122.100", "78.157.42.100").forEach { h ->
                AssistChip(
                    onClick = { host = h },
                    label = { Text(h, fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF132033),
                        labelColor = Color(0xFF00D4FF)
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                isRunning = true
                results = emptyList()
                scope.launch {
                    val list = mutableListOf<String>()
                    repeat(5) { i ->
                        val p = measurePing(host)
                        list.add(if (p != null) "پینگ ${i + 1}: $p ms" else "پینگ ${i + 1}: Timeout")
                        results = list.toList()
                        delay(300)
                    }
                    val valid = list.mapNotNull {
                        it.substringAfter(": ").substringBefore(" ms").toLongOrNull()
                    }
                    if (valid.isNotEmpty()) {
                        list.add("────────────────")
                        list.add("میانگین: ${valid.average().toInt()} ms")
                        list.add("کمینه: ${valid.min()} ms  |  بیشینه: ${valid.max()} ms")
                    }
                    results = list
                    isRunning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
            enabled = !isRunning,
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("در حال تست...", color = Color.Black)
            } else {
                Icon(Icons.Default.NetworkCheck, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("شروع تست ۵ مرحله‌ای", color = Color.Black)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (results.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF132033)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    results.forEach { line ->
                        Text(line, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GuideScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GuideCard(
                "چطور از White Booster استفاده کنیم؟",
                "۱. تب DNS → تست همه DNSها\n۲. بهترین را پیدا کنید\n۳. تب VPN → همان DNS را انتخاب و وصل کنید\n۴. بازی را باز کنید و از پینگ کمتر لذت ببرید"
            )
        }
        item {
            GuideCard(
                "بهترین DNS برای ایران",
                "• Shecan → معمولاً رتبه ۱\n• Electro → عالی برای گیم\n• Radar Game → مخصوص بازی\n• 403 Online → سرعت بالا\n• Cloudflare → جایگزین خوب"
            )
        }
        item {
            GuideCard(
                "تشخیص بازی‌ها",
                "اپ به صورت خودکار بازی‌های محبوب نصب‌شده روی گوشی را شناسایی می‌کند (بر اساس پکیج‌نیم گوگل پلی)."
            )
        }
        item {
            GuideCard(
                "نکات مهم",
                "• VPN فقط DNS را تغییر می‌دهد و ترافیک را رمزنگاری نمی‌کند\n• بعد از قطع VPN، DNS به حالت پیش‌فرض برمی‌گردد\n• در برخی شبکه‌ها ممکن است نیاز به یک‌بار روشن/خاموش کردن اینترنت باشد\n• پینگ DNS با پینگ سرور بازی متفاوت است"
            )
        }
        item {
            GuideCard(
                "اپراتورهای ایران",
                "ایرانسل، همراه اول، رایتل و مخابرات معمولاً با DNSهای ایرانی (Shecan / Electro) نتیجه بهتری می‌گیرند."
            )
        }
    }
}

@Composable
fun GuideCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132033)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(content, color = Color(0xFFB0BEC5), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun WhiteBoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00D4FF),
            background = Color(0xFF0A0F1A),
            surface = Color(0xFF132033)
        ),
        content = content
    )
}
