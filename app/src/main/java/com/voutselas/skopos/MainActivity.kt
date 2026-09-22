@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.voutselas.skopos

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.pullrefresh.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

val Background = Color(0xFF0C0A0D)
val Surface = Color(0xFF181418)
val Accent = Color(0xFFFF6673)
val Muted = Color(0xFFA8A1AB)
val Orange = Color(0xFFFFA857)
val Border = Color(0xFF302A30)
val sections = listOf("Overview", "News", "Vulnerabilities", "Ransomware", "IOC Hunt", "APT Campaigns", "Watchlist Setup", "Product & Legal")
fun dateLabel(time: Long) = if(time == 0L) "Unknown" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
fun numberLabel(value: Double?) = value?.let { String.format(Locale.getDefault(), "%,.0f", it) } ?: "—"
fun severityColor(value: String) = when(value.lowercase()) { "critical" -> Accent; "high" -> Orange; "medium" -> Color(0xFFF7D56C); "low" -> Color(0xFF66D9A6); else -> Muted }
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val store = ViewModelProvider(this)[Store::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Surface, onPrimary = Background, onSurface = Color.White, onBackground = Color.White, outline = Border, secondary = Orange)) {
                SkoposApp(store)
            }
        }
    }
}
@Composable fun SkoposApp(store: Store) {
    var section by rememberSaveable { mutableStateOf("Overview") }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Record?>(null) }
    var globeFullScreen by rememberSaveable { mutableStateOf(false) }
    val immersive = globeFullScreen && route == "Global view"
    val holder = rememberSaveableStateHolder()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    DisposableEffect(context, immersive) {
        val activity = context as? ComponentActivity
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if(immersive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        var disposed = false
        fun update() { main.post { if(!disposed) store.connectivity(manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) } }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
        }
        manager.registerDefaultNetworkCallback(callback); update()
        onDispose { disposed = true; manager.unregisterNetworkCallback(callback) }
    }
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, section, store.base) {
        if(active) while(true) { store.tick(section); delay(1000) }
    }
    BackHandler(detail != null || route != null || section != "Overview") {
        if(immersive) globeFullScreen = false else if(detail != null) detail = null else if(route != null) route = null else section = "Overview"
    }
    Scaffold(containerColor = Background,
        contentWindowInsets = if(immersive) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if(!immersive && (detail != null || route != null)) TopAppBar(title = { Text(if(detail != null) "${detail!!.kind.label} details" else route!!) }, navigationIcon = { IconButton(onClick = { if(detail != null) detail = null else route = null }) { Icon(Icons.Default.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) },
        bottomBar = { if(!immersive) Column(Modifier.fillMaxWidth().background(Surface).navigationBarsPadding()) {
            Text("‹  Swipe for more tabs  ›", Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp), color = Muted, fontSize = 10.sp)
            LazyRow(Modifier.fillMaxWidth()) {
                items(sections) { item ->
                    val selected = section == item
                    TextButton(onClick = { section = item; route = null; detail = null }, modifier = Modifier.width(104.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(sectionIcon(item), item, tint = if(selected) Accent else Muted, modifier = Modifier.size(23.dp))
                            Text(item, fontSize = 10.sp, color = if(selected) Accent else Muted, modifier = Modifier.padding(top = 5.dp).heightIn(min = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        } }
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize()) {
            store.storageError?.let { Text(it, color = Orange, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
            val pull = rememberPullRefreshState(store.loading || store.huntLoading || store.aptLoading, { store.refreshCurrent(section) })
            Box(Modifier.fillMaxSize().pullRefresh(pull, enabled = route != "Global view")) {
                val selected = detail
                if(selected != null) DetailScreen(selected, store)
                else when(route) {
                    "Global view" -> GlobeScreen(store.snapshot?.rows("iocs", Kind.IOC) ?: emptyList(), immersive) { globeFullScreen = it }
                    "Settings" -> SettingsScreen(store) { route = null; section = "Overview" }
                    "Investigate" -> InvestigationScreen(store) { detail = it }
                    else -> holder.SaveableStateProvider(section) {
                        when(section) {
                            "Overview" -> OverviewScreen(store) { globeFullScreen = false; route = "Global view" }
                            "News" -> NewsScreen(store) { detail = it }
                            "Vulnerabilities" -> VulnerabilitiesScreen(store) { detail = it }
                            "Ransomware" -> RansomwareScreen(store) { detail = it }
                            "IOC Hunt" -> HuntScreen(store, { route = "Settings" }, { route = "Investigate" }) { detail = it }
                            "APT Campaigns" -> CampaignScreen(store) { detail = it }
                            "Watchlist Setup" -> WatchlistScreen(store) { detail = it }
                            else -> LegalScreen { route = "Settings" }
                        }
                    }
                }
                if(route != "Global view") PullRefreshIndicator(store.loading || store.huntLoading || store.aptLoading, pull, Modifier.align(Alignment.TopCenter), backgroundColor = Surface, contentColor = Accent)
            }
        }
    }
}
fun sectionIcon(section: String): ImageVector = when(section) {
    "Overview" -> Icons.Default.Home; "News" -> Icons.Default.List; "Vulnerabilities" -> Icons.Default.Warning; "Ransomware" -> Icons.Default.Lock
    "IOC Hunt" -> Icons.Default.Search; "APT Campaigns" -> Icons.Default.Person; "Watchlist Setup" -> Icons.Default.Star; else -> Icons.Default.Info
}
@Composable fun Page(content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 850.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable fun Badge(text: String, color: Color = Accent) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 6.dp))
}
@Composable fun Status(data: Dataset?, loading: Boolean, error: String?, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when { error != null -> "●  Connection unavailable"; data?.cached == true -> "●  Cached Intelligence"; data != null -> "●  Live Intelligence"; else -> "○  Connecting to SKOPOS" }, color = if(data != null && !data.cached && error == null) Color(0xFF66D9A6) else Orange, fontSize = 12.sp)
        Text("Last updated: ${data?.let { dateLabel(it.updated) } ?: "—"}", color = Muted, fontSize = 11.sp)
        data?.notice?.let { Text(it, color = Orange, fontSize = 12.sp) }
        error?.let { Text(it, color = Orange, fontSize = 12.sp); TextButton(onClick = retry) { Text("Retry connection") } }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
    }
}
@Composable fun ScreenTitle(title: String) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
@Composable fun Search(value: String, label: String, update: (String) -> Unit) {
    OutlinedTextField(value, update, label = { Text(label, fontSize = 13.sp) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if(value.isNotEmpty()) IconButton(onClick = { update("") }) { Icon(Icons.Default.Close, "Clear search") } })
}
@Composable fun Choice(label: String, choices: List<String>, selected: String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("$label$selected  ▾", fontSize = 12.sp) }
        DropdownMenu(open, { open = false }) { choices.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { change(item); open = false }) } }
    }
}
@Composable fun Filters(values: List<String>, selected: String, change: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { value -> FilterChip(selected == value, { change(value) }, label = { Text(value, fontSize = 12.sp) }) } }
}
@Composable fun Empty(message: String) { Panel { Text(message, color = Muted, modifier = Modifier.padding(vertical = 24.dp)) } }
@Composable fun OverviewScreen(store: Store, globe: () -> Unit) {
    val summary = store.snapshot?.json?.optJSONObject("summary")
    Box {
        RadarBackdrop(Modifier.fillMaxSize())
        Page {
            item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Image(painterResource(R.drawable.skopos_logo), "SKOPOS owl logo", Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
                Text("SKOPOS", fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = 4.sp)
                Status(store.snapshot, store.loading, store.error) { store.refresh() }
            } }
            item { TextButton(onClick = globe, modifier = Modifier.fillMaxWidth()) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlobeEmblem(Modifier.size(110.dp)); Text("GLOBAL VIEW  ↗", letterSpacing = 2.sp, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White)
            } } }
            val metrics = listOf(Triple("Fresh IOCs", "iocs_window", "LAST 24 HOURS"), Triple("CISA KEV", "kev_total", "KNOWN EXPLOITED"), Triple("New & updated CVEs", "new_updated_cves_24h", "LAST 24 HOURS"), Triple("Critical CVEs", "critical_recent", "RECENT CRITICAL"), Triple("Ransomware claims", "ransomware_24h", "LAST 24 HOURS"), Triple("Headlines", "news_24h", "LAST 24 HOURS"))
            items(metrics.chunked(2)) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) { pair.forEach { (title, key, note) ->
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Icon(if(key == "news_24h") Icons.Default.List else if(key == "ransomware_24h") Icons.Default.Lock else Icons.Default.Info, null, tint = Accent, modifier = Modifier.size(23.dp))
                    Text(numberLabel(summary?.number(key)), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.heightIn(min = 36.dp))
                    Text(note, color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            } } }
        }
    }
}
@Composable fun NewsScreen(store: Store, select: (Record) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }; var source by rememberSaveable { mutableStateOf("All sources") }
    var sort by rememberSaveable { mutableStateOf("Newest first") }; var size by rememberSaveable { mutableIntStateOf(10) }; var page by rememberSaveable { mutableIntStateOf(1) }
    val all = store.snapshot?.rows("news", Kind.NEWS) ?: emptyList()
    val rows = all.filter { it.matches(search) && (source == "All sources" || it.text("source") == source) }.let { rows -> when(sort) { "Oldest first" -> rows.sortedBy { it.date }; "Source A–Z" -> rows.sortedBy { it.text("source").lowercase() }; "Title A–Z" -> rows.sortedBy { it.title.lowercase() }; else -> rows.sortedByDescending { it.date } } }
    val pages = maxOf(1, (rows.size + size - 1)/size); val current = page.coerceIn(1, pages)
    Page {
        item { ScreenTitle("Security news") }; item { Status(store.snapshot, store.loading, store.error) { store.refresh() } }
        item { Search(search, "Search news or sources") { search = it; page = 1 } }
        item { Text("Sources  ·  Swipe to browse", color = Muted, fontSize = 12.sp); Filters(listOf("All sources") + all.map { it.text("source") }.distinct().sorted(), source) { source = it; page = 1 } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("", listOf("Newest first", "Oldest first", "Source A–Z", "Title A–Z"), sort) { sort = it; page = 1 }
            Choice("Per page: ", listOf("10", "20", "50"), size.toString()) { size = it.toInt(); page = 1 }
        } }
        item { Text("Showing ${if(rows.isEmpty()) 0 else (current-1)*size+1}–${minOf(current*size, rows.size)} of ${rows.size} · Latest ${all.size} articles", color = Muted, fontSize = 12.sp) }
        if(rows.isEmpty()) item { Empty("No matching articles. Try another search or source, or refresh.") }
        items(rows.drop((current-1)*size).take(size), key = { it.id }) { RecordCard(it, select) }
        item { Pager(current, pages) { page = it } }
    }
}
@Composable fun Pager(page: Int, count: Int, change: (Int) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
    OutlinedButton(onClick = { change(page-1) }, enabled = page > 1) { Text("Previous") }; Text("$page / $count", color = Muted, fontSize = 12.sp)
    OutlinedButton(onClick = { change(page+1) }, enabled = page < count) { Text("Next") }
} }
@Composable fun VulnerabilitiesScreen(store: Store, select: (Record) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("All") }; var sort by rememberSaveable { mutableStateOf("Last modified") }
    val all = store.snapshot?.rows("vulnerabilities", Kind.CVE) ?: emptyList()
    val rows = all.filter { it.matches(search) && when(filter) { "CISA KEV" -> it.raw.optBoolean("in_kev"); "EPSS ≥90%" -> (it.raw.number("epss") ?: -1.0) >= .9; else -> true } }.let { r -> when(sort) {
        "CVSS highest" -> r.sortedByDescending { it.raw.number("cvss") ?: -1.0 }; "EPSS highest" -> r.sortedByDescending { it.raw.number("epss") ?: -1.0 }
        "Last modified" -> r.sortedByDescending { timestamp(it.raw.text("nvd_last_modified", it.raw.text("modified"))) }; "KEV first" -> r.sortedWith(compareByDescending<Record> { it.raw.optBoolean("in_kev") }.thenByDescending { timestamp(it.raw.text("nvd_last_modified", it.raw.text("modified"))) }); "CVE ID" -> r.sortedBy { it.title }; else -> r.sortedByDescending { it.date }
    } }
    Page {
        item { ScreenTitle("Vulnerabilities") }; item { Status(store.snapshot, store.loading, store.error) { store.refresh() } }
        item { Search(search, "CVE, vendor, product or title") { search = it } }
        item { Choice("", listOf("Newest published", "Last modified", "CVSS highest", "EPSS highest", "KEV first", "CVE ID"), sort) { sort = it }; Filters(listOf("All", "CISA KEV", "EPSS ≥90%"), filter) { filter = it } }
        item { Text("${rows.size} matching · Up to 1,000 loaded CVEs. The backend does not provide full-database pagination.", color = Muted, fontSize = 12.sp) }
        if(rows.isEmpty()) item { Empty("No matching vulnerabilities. Adjust the filters or refresh.") }
        items(rows, key = { it.id }) { RecordCard(it, select) }
    }
}
@Composable fun RansomwareScreen(store: Store, select: (Record) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val rows = (store.snapshot?.rows("ransomware", Kind.CLAIM) ?: emptyList()).filter { it.matches(search) }.sortedByDescending { it.date }
    Page {
        item { ScreenTitle("Ransomware") }; item { Status(store.snapshot, store.loading, store.error) { store.refresh() } }
        item { Search(search, "Victim, group, country or sector") { search = it } }
        item { Badge("${numberLabel(store.snapshot?.json?.optJSONObject("summary")?.number("ransomware_24h"))} · Last 24h") }
        if(rows.isEmpty()) item { Empty("No matching claims. Try another search or refresh.") }
        items(rows, key = { it.id }) { RecordCard(it, select) }
        item { Text("Latest 200 reported claims. Claims require independent verification.", color = Muted, fontSize = 12.sp) }
    }
}
@Composable fun HuntScreen(store: Store, settings: () -> Unit, investigate: () -> Unit, select: (Record) -> Unit) {
    var search by rememberSaveable { mutableStateOf(store.query.text) }; var hours by rememberSaveable { mutableIntStateOf(store.query.hours) }; var type by rememberSaveable { mutableStateOf(store.query.type) }
    var categories by rememberSaveable { mutableStateOf(listOf("IP / C2", "Domains", "URLs", "Other")) }
    val all = store.hunt?.rows("iocs", Kind.IOC) ?: emptyList(); val rows = all.filter { it.category in categories }
    Page {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.width(48.dp)); Text("IOC Hunt", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)); IconButton(onClick = settings, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Settings, "Settings") } } }
        item { Status(store.hunt, store.huntLoading, store.huntError) { store.refreshHunt() } }
        item { Search(search, "IP, domain, URL, hash, malware, ASN or tag") { search = it } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Window: ", listOf("1", "6", "24"), hours.toString()) { hours = it.toInt() }
            Choice("Type: ", listOf("All", "ip", "domain", "url", "md5", "sha1", "sha256"), type.ifEmpty { "All" }) { type = if(it == "All") "" else it }
        } }
        item { Button(onClick = { store.submitHunt(HuntQuery(search, hours, type)) }, modifier = Modifier.fillMaxWidth()) { Text("Search") } }
        item { Column { listOf("IP / C2", "Domains", "URLs", "Other").chunked(2).forEach { pair -> Row { pair.forEach { c -> Row(Modifier.weight(1f).clickable { categories = if(c in categories) categories-c else categories+c }, verticalAlignment = Alignment.CenterVertically) { Checkbox(c in categories, { categories = if(c in categories) categories-c else categories+c }); Text(c, fontSize = 12.sp) } } } } } }
        item { ArrivalsChart(all) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Limit 1,000", color = Muted, fontSize = 12.sp); TextButton(onClick = investigate) { Text("Quick investigation") } } }
        if(rows.isEmpty()) item { Empty("No matching indicators. Submit a search or adjust the categories.") }
        items(rows, key = { it.id }) { RecordCard(it, select) }
    }
}
@Composable fun CampaignScreen(store: Store, select: (Record) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val rows = (store.apt?.rows("campaigns", Kind.APT) ?: emptyList()).filter { it.matches(search) }.sortedWith(compareByDescending<Record> { it.date }.thenBy { it.id })
    Page {
        item { ScreenTitle("APT Campaigns") }; item { Status(store.apt, store.aptLoading, store.aptError) { store.refreshAPT() } }
        item { Search(search, "Campaign, country, industry, malware or MITRE ID") { search = it } }
        item { Text("NAME  ·  MITRE LAST MODIFIED  ·  SEVERITY", fontFamily = FontFamily.Monospace, color = Muted, fontSize = 10.sp) }
        if(rows.isEmpty()) item { Empty("No APT campaigns match your search.") }
        items(rows, key = { it.id }) { RecordCard(it, select) }
    }
}
@Composable fun RecordCard(record: Record, select: (Record) -> Unit) {
    Panel {
        Column(Modifier.fillMaxWidth().clickable { select(record) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when(record.kind) {
                Kind.IOC -> { Badge(record.text("ioc_type")); Text(record.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold); Text("${record.text("malware", "Unattributed")} · ${record.text("source")}", color = Muted, fontSize = 12.sp); Text("Confidence: ${record.raw.number("confidence")?.let { "$it%" } ?: "Unknown"} · ${record.text("country_code", record.text("country"))}", color = Muted, fontSize = 12.sp) }
                Kind.CVE -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Badge(record.severity, severityColor(record.severity)); if(record.raw.optBoolean("in_kev")) Badge("KEV", Orange); if(record.raw.optBoolean("exploit_available")) Badge("Exploit") }
                    Text(record.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(record.text("title", record.text("product", record.title)), color = Muted)
                    Text("${record.text("vendor")} · ${record.text("product")}", color = Muted, fontSize = 12.sp)
                    Text("NVD Published: ${dateLabel(record.date)}\nNVD Last Modified: ${dateLabel(timestamp(record.raw.text("nvd_last_modified", record.raw.text("modified"))))}", color = Muted, fontSize = 11.sp)
                    Text("CVSS ${record.raw.number("cvss") ?: "—"}   ·   EPSS ${record.raw.number("epss")?.let { String.format(Locale.getDefault(), "%.1f%%", it*100) } ?: "—"}", fontFamily = FontFamily.Monospace, color = Accent, fontSize = 12.sp)
                }
                Kind.CLAIM -> { Badge("Group: ${record.text("group_name")}"); Text(record.title, fontWeight = FontWeight.Bold); Text("Discovered: ${dateLabel(record.date)}", color = Muted, fontSize = 12.sp); Text("${record.text("country")} · ${record.text("sector").replace("Not Found", "Unknown")}", color = Muted, fontSize = 12.sp) }
                Kind.NEWS -> { Text(record.title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Badge(record.text("source")); Text(dateLabel(record.date), color = Muted, fontSize = 11.sp); Text(record.description.take(320), color = Muted, fontSize = 14.sp, lineHeight = 21.sp) }
                Kind.APT -> { Text(record.title, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(record.text("mitre_id", "—"), fontFamily = FontFamily.Monospace); Badge(record.severity, severityColor(record.severity)) }; Text("MITRE Last Modified: ${dateLabel(timestamp(record.raw.text("mitre_last_modified")))}", color = Muted, fontSize = 11.sp) }
            }
            Text("View details  ›", color = Accent, fontSize = 12.sp)
        }
        if(record.kind in listOf(Kind.NEWS, Kind.CVE, Kind.CLAIM)) record.link?.let { SourceButton(it, if(record.kind == Kind.CVE) "Open NVD details" else "Read more") }
    }
}
@Composable fun SourceButton(url: String, title: String = "Open source") {
    val context = LocalContext.current
    TextButton(onClick = {
        sourceURL(url)?.let { safe -> runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(safe)) }.onFailure { Toast.makeText(context, "No browser available to open this source.", Toast.LENGTH_SHORT).show() } }
    }) { Icon(Icons.Default.ExitToApp, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(title) }
}
@Composable fun Field(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label.uppercase(), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
    Text(value, fontSize = 14.sp, lineHeight = 21.sp)
} }
@Composable fun DetailScreen(record: Record, store: Store) {
    Page {
        item { SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Badge(if(record.kind == Kind.CLAIM) "Reported claim" else if(record.kind == Kind.IOC) record.text("ioc_type") else record.severity, severityColor(record.severity))
            Text(record.title, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontFamily = if(record.kind in listOf(Kind.IOC, Kind.CVE)) FontFamily.Monospace else FontFamily.Default)
            if(record.kind == Kind.APT && record.raw.isNull("severity")) Text("Website default; severity not supplied by backend.", color = Muted, fontSize = 12.sp)
        } } }
        item { record.link?.let { SourceButton(it, if(record.kind == Kind.APT) "Open MITRE details" else "Open source") }
            OutlinedButton(onClick = { store.toggleSave(record) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp)); Text(if(store.isSaved(record)) "Saved to watchlist" else "Save to watchlist") }
        }
        item { SelectionContainer { Panel {
            Field(if(record.kind == Kind.CLAIM) "Context" else "Overview", record.description)
            when(record.kind) {
                Kind.IOC -> {
                    Field("Malware", record.text("malware", "Unattributed")); Field("Confidence", record.raw.number("confidence")?.let { "$it%" } ?: "Unknown")
                    Field("Location", "${record.text("country")} · ${record.text("country_code")}"); Field("ASN", record.text("asn")); Field("Source", record.text("source"))
                    Field("First recorded", dateLabel(timestamp(record.raw.text("first_seen", record.raw.text("effective_seen"))))); Field("Last recorded", dateLabel(record.date))
                    Field("Tags", record.text("tags", "None")); Field("Watchlist matches", record.raw.optJSONArray("watchlist_matches")?.let { a -> (0 until a.length()).joinToString(" · ") { a.getString(it) } }?.ifEmpty { "None" } ?: "None")
                    Field("Sector", record.text("sector")); Field("Category", record.text("category"))
                }
                Kind.CVE -> {
                    Field("CVSS severity", record.raw.number("cvss")?.let { "$it / 10" } ?: "Unavailable")
                    Field("EPSS probability", record.raw.number("epss")?.let { String.format(Locale.getDefault(), "%.1f%%", it*100) } ?: "Unavailable")
                    Field("Known exploited", if(record.raw.optBoolean("in_kev")) "CISA KEV" else "Not in KEV")
                    Field("Exploit available", if(record.raw.isNull("exploit_available")) "Unknown" else record.raw.optBoolean("exploit_available").toString())
                    Field("Vendor", record.text("vendor")); Field("Product", record.text("product")); Field("Published", dateLabel(record.date))
                    Field("NVD Last Modified", dateLabel(timestamp(record.raw.text("nvd_last_modified", record.raw.text("modified")))))
                    Field("Recommended action", record.text("required_action", "No remediation guidance supplied. Consult the vendor advisory."))
                }
                Kind.NEWS -> { Field("Source", record.text("source")); Field("Published", dateLabel(record.date)) }
                Kind.CLAIM -> { Field("Ransomware group", record.text("group_name")); Field("Country", record.text("country")); Field("Sector", record.text("sector").replace("Not Found", "Unknown")); Field("Discovered", dateLabel(record.date)) }
                Kind.APT -> {
                    Field("MITRE ID", record.text("mitre_id", "—")); Field("Status", record.text("status"))
                    listOf("first_seen", "last_seen", "mitre_last_modified").forEach { Field(it.replace('_', ' '), dateLabel(timestamp(record.raw.text(it)))) }
                    Field("Threat actors", record.raw.optJSONArray("actors")?.objects()?.joinToString(" · ") { it.text("name") }?.ifEmpty { "Not supplied" } ?: "Not supplied")
                    listOf("target_country", "target_sector", "malware", "techniques", "countries", "industries").forEach { if(!record.raw.isNull(it)) Field(it.replace('_', ' '), record.text(it)) }
                }
            }
        } } }
        if(record.kind == Kind.IOC) item { Text("Indicators are defanged for safe review. SKOPOS does not connect to indicator addresses.", fontSize = 12.sp, color = Muted) }
        if(record.kind == Kind.CVE) item { Text("EPSS estimates exploitation probability over the next 30 days. Combine it with known exploitation and your asset exposure when prioritizing.", color = Muted, fontSize = 12.sp) }
        if(record.kind == Kind.APT) {
            val links = Regex("\\[([^]]+)\\]\\((https?://[^)]+)\\)").findAll(record.description).map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
            items(links) { (label, url) -> if(sourceURL(url) != null) SourceButton(url, label) }
        }
    }
}
@Composable fun WatchlistScreen(store: Store, select: (Record) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }; var editing by rememberSaveable { mutableStateOf<String?>(null) }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = listOf("IOCs" to (store.snapshot?.rows("iocs", Kind.IOC) ?: emptyList()), "Vulnerabilities" to (store.snapshot?.rows("vulnerabilities", Kind.CVE) ?: emptyList()), "Ransomware" to (store.snapshot?.rows("ransomware", Kind.CLAIM) ?: emptyList()), "APT campaigns" to (store.apt?.rows("campaigns", Kind.APT) ?: emptyList()))
    Page {
        item { ScreenTitle("Watchlist Setup") }; item { Status(store.snapshot, store.loading, store.error) { store.refresh() } }
        item { Panel {
            Text("Monitor what matters", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Add an IOC, CVE, ransomware group, victim, APT campaign, actor or keyword.", color = Muted)
            OutlinedTextField(input, { input = it }, label = { Text("Add a watchlist term") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row { Button(onClick = { error = store.saveTerm(input, editing); if(error == null) { input = ""; editing = null } }) { Text(if(editing == null) "Add term" else "Save changes") }; if(editing != null) TextButton(onClick = { input = ""; editing = null; error = null }) { Text("Cancel") } }
            error?.let { Text(it, color = Orange) }
            store.terms.forEach { term -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(defang(term), Modifier.weight(1f)); IconButton(onClick = { input = term; editing = term }) { Icon(Icons.Default.Edit, "Edit $term") }; IconButton(onClick = { store.removeTerm(term); if(editing == term) { input = ""; editing = null } }) { Icon(Icons.Default.Delete, "Delete $term") }
            } }
            Text("Saved on this device. Matches use intelligence currently loaded in the app; no background alerts or cross-device sync.", color = Muted, fontSize = 12.sp)
        } }
        if(store.terms.isNotEmpty()) {
            item { Text("Watchlist matches", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            groups.forEach { (label, all) ->
                val rows = all.filter { row -> store.terms.any { row.matches(it) } }
                item { Text("$label · ${rows.size}", color = Accent) }
                if(label == "APT campaigns") item { Status(store.apt, store.aptLoading, store.aptError) { store.refreshAPT() } }
                items(rows, key = { "match-${it.id}" }) { RecordCard(it, select) }
            }
        }
        if(store.saved.isNotEmpty()) item { Text("Saved items · ${store.saved.size}", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        items(store.saved, key = { it.text("id") }) { item -> Panel { Text(item.text("title"), fontWeight = FontWeight.Bold); Badge(item.text("kind")); Text(item.text("detail"), color = Muted); TextButton(onClick = { store.removeSaved(item) }) { Text("Remove saved item") } } }
    }
}
@Composable fun SettingsScreen(store: Store, done: () -> Unit) {
    var url by rememberSaveable(store.base) { mutableStateOf(store.base) }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    Page { item { Panel {
        Text("Connect to SKOPOS", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        OutlinedTextField(url, { url = it }, label = { Text("SKOPOS backend URL") }, modifier = Modifier.fillMaxWidth())
        Text("Use the final HTTPS base URL. Provider credentials stay on the server.", color = Muted, fontSize = 13.sp)
        error?.let { Text(it, color = Orange) }
        Button(onClick = { error = store.configure(url); if(error == null) done() }, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
        TextButton(onClick = { url = DEFAULT_BACKEND }) { Text("Use default SKOPOS backend") }
    } }; item { Panel { Text("Refresh & offline access", fontWeight = FontWeight.Bold); Text("Intelligence refreshes every 60 seconds while the app is open. Pull down on a screen to refresh. Offline results retain their original retrieval time. Changing backends loads only that backend’s saved intelligence.", color = Muted) } } }
}
@Composable fun InvestigationScreen(store: Store, select: (Record) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Page {
        item { ScreenTitle("Investigate an indicator") }
        item { Search(query, "IP, domain, URL or hash") { query = it } }
        item { Button(onClick = { store.investigate(query) }, enabled = !store.investigating, modifier = Modifier.fillMaxWidth()) { Text("Investigate") }; if(store.investigating) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("Lookup is sent only to your SKOPOS backend. Queries and results are not cached locally.", color = Muted, fontSize = 12.sp) }
        store.investigationMessage?.let { item { Text(it, color = Orange) } }
        items(store.investigation, key = { it.id }) { RecordCard(it, select) }
    }
}
@Composable fun LegalScreen(settings: () -> Unit) {
    val context = LocalContext.current
    Page {
        item { ScreenTitle("Product & Legal") }
        item { Panel {
            Text("SKOPOS", color = Accent, fontWeight = FontWeight.Bold, fontSize = 27.sp); Text("Threat Intelligence Platform", fontWeight = FontWeight.Bold)
            Text("Threat intelligence aggregation and visualization by Voutselas Group.")
            Field("App version", "1.0 · Android")
            SourceButton("https://voutselasgroup.com", "Voutselas Group")
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:cybersecurity@voutselasgroup.com"))) }.onFailure { Toast.makeText(context, "No email app installed.", Toast.LENGTH_SHORT).show() } }) { Text("Contact support") }
            OutlinedButton(onClick = settings) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Connection settings") }
        } }
        item { Panel {
            Text("Legal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            listOf("This platform aggregates publicly available threat intelligence.", "Data is provided “as is” without warranties.", "Threat intelligence should be independently validated before operational use.", "Users are responsible for compliance with local laws and regulations.", "Third-party feeds remain the property of their respective owners.", "Skopos and Voutselas Group are not liable for damages arising from the use of this platform.").forEach { Text(it, fontSize = 14.sp) }
        } }
        item { Panel { Text("Data & storage", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("Intelligence is retrieved through the SKOPOS backend over HTTPS. External feed API keys stay on the server. Cached intelligence, watchlist terms and saved items are stored on this device. Opening source articles loads the third-party website in a browser tab.", fontSize = 14.sp) } }
        item { Panel { Text("Credits", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("ThreatFox · CISA KEV · NVD · FIRST EPSS · ransomware.live · MITRE ATT&CK · The Hacker News · BleepingComputer", fontSize = 14.sp); Text("Globe geography: Natural Earth, public domain.", color = Muted); SourceButton("https://www.naturalearthdata.com/about/terms-of-use/", "Natural Earth") } }
    }
}
