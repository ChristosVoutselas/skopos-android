package com.voutselas.skopos

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.*

fun globeCategoryColor(category: String): Color = when (category) {
    "IP / C2" -> Accent
    "Domains" -> Color(0xFFF4D35E)
    "URLs" -> Color(0xFF40C9BE)
    else -> Color(0xFFB58AFF)
}

data class Geo(val lon: Double, val lat: Double)
data class Projected(val x: Float, val y: Float, val depth: Double)
fun project(point: Geo, longitude: Double, latitude: Double): Projected {
    val lon = Math.toRadians(point.lon-longitude); val lat = Math.toRadians(point.lat); val tilt = Math.toRadians(latitude)
    val x = cos(lat)*sin(lon); val y = cos(tilt)*sin(lat)-sin(tilt)*cos(lat)*cos(lon)
    val depth = sin(tilt)*sin(lat)+cos(tilt)*cos(lat)*cos(lon)
    return Projected(x.toFloat(), -y.toFloat(), depth)
}
@Composable fun RadarBackdrop(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "Decorative radar")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "Sweep")
    Canvas(modifier) {
        val center = Offset(size.width*.5f, size.height*.26f); val radius = max(size.width,size.height)*.65f
        drawRect(Brush.radialGradient(listOf(Accent.copy(alpha=.10f), Color.Transparent), center, radius))
        listOf(.22f,.42f,.62f,.82f,1f).forEach { drawCircle(Accent.copy(alpha=.13f), radius*it, center, style=Stroke(.8f)) }
        rotate(angle, center) { drawLine(Accent.copy(alpha=.2f), center, Offset(center.x+radius, center.y), 1f) }
    }
}
@Composable fun GlobeEmblem(modifier: Modifier) {
    Canvas(modifier) {
        val c = center; val r = size.minDimension*.32f
        drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha=.25f), Color.Transparent), c, r*1.5f), r*1.5f, c)
        drawCircle(Surface,r,c); drawCircle(Accent.copy(alpha=.7f),r,c,style=Stroke(1.5f))
        drawOval(Accent.copy(alpha=.6f), Offset(c.x-r*.45f,c.y-r), androidx.compose.ui.geometry.Size(r*.9f,r*2),style=Stroke(1.5f))
        drawOval(Accent.copy(alpha=.6f), Offset(c.x-r,c.y-r*.35f), androidx.compose.ui.geometry.Size(r*2,r*.7f),style=Stroke(1.5f))
        rotate(-28f,c) { drawOval(Color.White.copy(alpha=.6f), Offset(c.x-r*1.4f,c.y-r*.25f), androidx.compose.ui.geometry.Size(r*2.8f,r*.5f),style=Stroke(1.5f)) }
    }
}
@Composable fun ArrivalsChart(rows: List<Record>) {
    val bins = remember(rows) {
        IntArray(24).also { result -> val now=System.currentTimeMillis(); rows.forEach { row ->
            val t=timestamp(row.raw.text("first_seen")); val age = if(t == 0L) -1 else floor((now-t)/3600000.0).toInt()
            if(age in 0..23) result[23-age]++
        } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("IOC arrivals · Last 24 hours", color=Muted, fontSize=12.sp)
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment=androidx.compose.ui.Alignment.Bottom, horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            bins.forEachIndexed { index, count -> Canvas(Modifier.weight(1f).height(max(3f,44f*count/max(1,bins.maxOrNull()?:1)).dp).semantics { contentDescription="${23-index} hours ago: $count arrivals" }) { drawRect(Accent.copy(alpha=if(count==0).15f else .85f)) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text("24h ago", fontSize=10.sp,color=Muted); Text("Now", fontSize=10.sp,color=Muted) }
    }
}
@Composable fun GlobeScreen(rows: List<Record>, fullScreen: Boolean = false, setFullScreen: (Boolean) -> Unit = {}) {
    var longitude by rememberSaveable { mutableDoubleStateOf(15.0) }; var latitude by rememberSaveable { mutableDoubleStateOf(18.0) }; var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L && autoRotate && !touching &&
                        android.os.SystemClock.uptimeMillis() - lastInteraction > 2500 &&
                        android.animation.ValueAnimator.areAnimatorsEnabled()) {
                        longitude = (longitude + ((now - previous) / 1_000_000_000.0).coerceAtMost(.05) * 4.0) % 360
                    }
                    previous = now
                }
            }
        }
    }
    fun interacted() { lastInteraction = android.os.SystemClock.uptimeMillis() }
    var outlines by remember { mutableStateOf<List<List<Geo>>>(emptyList()) }
    var geographyError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) {
            val raw = JSONObject(context.assets.open("world.json").bufferedReader().use { it.readText() }).getJSONArray("outlines")
            (0 until raw.length()).map { i -> raw.getJSONArray(i).let { ring -> (0 until ring.length()).map { j -> ring.getJSONArray(j).let { Geo(it.getDouble(0),it.getDouble(1)) } } } }
        } }.onSuccess { outlines=it }.onFailure { geographyError=true }
    }
    val earthRenderer = produceState<EarthRenderer?>(null, outlines) {
        value = if(outlines.isNotEmpty()) withContext(Dispatchers.Default) { EarthRenderer(outlines) } else null
    }.value
    val gpu = remember(earthRenderer) {
        if (android.os.Build.VERSION.SDK_INT >= 33) earthRenderer?.let { GpuEarthRenderer(it) } else null
    }
    // Older devices use a bounded worker: gestures never wait for rasterization.
    val earthImage = produceState<ImageBitmap?>(null, earthRenderer) {
        if (android.os.Build.VERSION.SDK_INT < 33) earthRenderer?.let { renderer ->
            snapshotFlow { longitude to latitude }.conflate().collect { (lon, lat) ->
                value = withContext(Dispatchers.Default) { renderer.render(lon, lat) }
                delay(50)
            }
        }
    }.value
    val nodes = remember(rows) { globeNodes(rows) }
    val markerGroups = remember(nodes) { nodes.groupBy { it.longitude to it.latitude }.values.toList() }
    val arcs = remember(nodes) {
        val hubs = listOf(Geo(17.1077,48.1486),Geo(-.1276,51.5072),Geo(-74.006,40.7128),Geo(103.8198,1.3521),Geo(139.6503,35.6762))
        nodes.mapIndexed { index, row -> (0..30).map { step ->
            interpolate(Geo(row.longitude!!,row.latitude!!),hubs[index%hubs.size],step/30.0)
        } }
    }
    val grid = remember {
        (-60..60 step 30).map { lat -> (-180..180 step 6).map { Geo(it.toDouble(),lat.toDouble()) } } +
        (-180..180 step 30).map { lon -> (-90..90 step 6).map { Geo(lon.toDouble(),it.toDouble()) } }
    }
    // Camera motion invalidates drawing only; summary text updates four times a second.
    val visible by produceState<List<Record>>(emptyList(), nodes) {
        while (true) {
            value = nodes.filter {
                val p=project(Geo(it.longitude!!,it.latitude!!),longitude,latitude)
                p.depth>0 && abs(p.x*.4*zoom)<=.5 && abs(p.y*.4*zoom)<=.5
            }
            delay(250)
        }
    }
    val locatedCounts = remember(rows) { rows.filter { it.latitude != null && it.longitude != null }.groupingBy { it.category }.eachCount() }
    val transition = rememberInfiniteTransition(label="Collection arcs")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(4000,easing=LinearEasing)),label="Arc motion")
    val globeCanvas: @Composable (Modifier) -> Unit = { canvasModifier ->
            Canvas(canvasModifier.clipToBounds()
                .semantics { contentDescription="Threat infrastructure globe, ${visible.size} visible nodes. Use rotate and zoom buttons below." }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            touching = event.changes.any { it.pressed }
                            interacted()
                        }
                    }
                }
                .pointerInput(Unit) { detectTransformGestures { _, pan, scale, _ ->
                    longitude = (longitude-pan.x*.4/zoom)%360; latitude=(latitude+pan.y*.3/zoom).coerceIn(-75.0,75.0); zoom=(zoom*scale).coerceIn(1f,3f)
                } }) {
                val radius = size.minDimension*.4f*zoom
                fun position(p: Projected, height: Float=1f)=Offset(center.x+p.x*radius*height,center.y+p.y*radius*height)
                // Soft atmospheric halo surrounds a shaded, opaque land/ocean sphere.
                drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF418CAD).copy(alpha=.14f), Color.Transparent), center, radius*1.10f), radius*1.10f)
                drawCircle(Color(0xFF0B2032), radius)
                if (android.os.Build.VERSION.SDK_INT >= 33) gpu?.let { renderer ->
                    drawIntoCanvas { renderer.draw(it.nativeCanvas,size.width,size.height,radius,longitude,latitude) }
                }
                earthImage?.let { earth ->
                    drawImage(earth, dstOffset=IntOffset((center.x-radius).roundToInt(), (center.y-radius).roundToInt()), dstSize=IntSize((radius*2).roundToInt(), (radius*2).roundToInt()), filterQuality=FilterQuality.Medium)
                }
                drawCircle(Color(0xFF8ACFE4).copy(alpha=.28f),radius,style=Stroke(1.5f))
                fun line(points: List<Geo>,color: Color,width: Float) {
                    val path = Path()
                    var connected = false
                    points.forEach { geo ->
                        val p=project(geo,longitude,latitude)
                        if(p.depth>0) {
                            val at=position(p)
                            if(connected) path.lineTo(at.x,at.y) else path.moveTo(at.x,at.y)
                        }
                        connected=p.depth>0
                    }
                    drawPath(path,color,style=Stroke(width))
                }
                grid.forEach { line(it,Color(0xFF97C1CD).copy(alpha=.075f),.6f) }
                nodes.forEachIndexed { index,row ->
                    val color=globeCategoryColor(row.category)
                    val path=Path()
                    var connected=false
                    for(step in 0..30) {
                        val fraction=step/30.0
                        val point=project(arcs[index][step],longitude,latitude)
                        val height=(1+.16*sin(fraction*PI)).toFloat()
                        if(point.depth>0) {
                            val at=position(point,height)
                            if(connected) path.lineTo(at.x,at.y) else path.moveTo(at.x,at.y)
                        }
                        connected=point.depth>0
                    }
                    drawPath(path,color.copy(alpha=.15f),style=Stroke(1.2f))
                    val dot=project(interpolate(arcs[index].first(),arcs[index].last(),phase.toDouble()),longitude,latitude)
                    if(dot.depth>0) drawCircle(color.copy(alpha=.8f),2f,position(dot,(1+.16*sin(phase*PI)).toFloat()))
                }
                // Draw markers last. Concentric categories remain visible at shared locations.
                markerGroups.forEach { colocated ->
                    val point = project(Geo(colocated.first().longitude!!,colocated.first().latitude!!),longitude,latitude)
                    if (point.depth > .015) {
                        val categories = colocated.map { it.category }.distinct()
                        categories.forEachIndexed { index, category ->
                            val r = (3.5f + (categories.size-1-index)*3f).dp.toPx()
                            drawCircle(Color(0xFF07131D),r+1.dp.toPx(),position(point))
                            drawCircle(globeCategoryColor(category),r,position(point))
                        }
                    }
                }
            }
        }
    if(fullScreen) {
        Box(Modifier.fillMaxSize().background(Background).displayCutoutPadding()) {
            globeCanvas(Modifier.fillMaxSize())
            TextButton(onClick={setFullScreen(false)}, modifier=Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).background(Surface.copy(alpha=.9f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))) {
                Text("↙ Exit full screen")
            }
            Row(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).background(Surface.copy(alpha=.9f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).horizontalScroll(rememberScrollState()), verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={autoRotate=!autoRotate}) { Text(if(autoRotate) "Pause rotation" else "Auto rotate") }
                TextButton(onClick={interacted();longitude-=35}) { Text("← Rotate") }
                TextButton(onClick={interacted();zoom=(zoom-.5f).coerceAtLeast(1f)},enabled=zoom>1) { Text("− Zoom") }
                TextButton(onClick={interacted();zoom=(zoom+.5f).coerceAtMost(3f)},enabled=zoom<3) { Text("+ Zoom") }
                TextButton(onClick={interacted();zoom=1f;longitude=15.0;latitude=18.0}) { Text("Reset") }
                TextButton(onClick={interacted();longitude+=35}) { Text("Rotate →") }
            }
        }
    } else Page {
        item { ScreenTitle("Global Threat Infrastructure") }
        item { OutlinedButton(onClick={setFullScreen(true)},modifier=Modifier.fillMaxWidth()) { Text("⛶ Full screen") } }
        item { TextButton(onClick={autoRotate=!autoRotate},modifier=Modifier.fillMaxWidth()) { Text(if(autoRotate) "Pause rotation" else "Auto rotate") } }
        item { globeCanvas(Modifier.fillMaxWidth().aspectRatio(1f)) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick={interacted();zoom=(zoom-.5f).coerceAtLeast(1f)},enabled=zoom>1) { Text("− Zoom") }; Text("${String.format(java.util.Locale.getDefault(),"%.1f",zoom)}×",color=Muted)
            TextButton(onClick={interacted();zoom=(zoom+.5f).coerceAtMost(3f)},enabled=zoom<3) { Text("+ Zoom") }; TextButton(onClick={interacted();zoom=1f;longitude=15.0;latitude=18.0}) { Text("Reset") }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { TextButton(onClick={interacted();longitude-=35}) { Text("← Rotate") }; TextButton(onClick={interacted();longitude+=35}) { Text("Rotate →") } } }
        item { Panel {
            fun known(value: String)=value.lowercase() !in listOf("unknown","unattributed","n/a","-","")
            Text("${visible.size} visible nodes   ·   ${visible.map { it.text("country") }.filter(::known).distinct().size} countries",fontSize=15.sp)
            Text("${visible.map { it.text("malware") }.filter(::known).distinct().size} malware families",color=Muted,fontSize=13.sp)
            listOf("IP / C2", "Domains", "URLs", "Hash / Other").chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach { category -> Text("● $category · ${locatedCounts[if(category == "Hash / Other") "Other" else category] ?: 0} located", color=globeCategoryColor(category), fontSize=12.sp, modifier=Modifier.weight(1f)) }
                }
            }
        } }
        item { Text("Up to 95 distinct IOC locations, balanced across available categories. Yellow: domains; teal: URLs; purple: hashes/other; red: IP/C2. Domains or hashes without coordinates cannot be placed on the globe. Arcs connect them to display hubs to visualize collection; they are not measured traffic or attacker-to-victim paths. Unknown geography is omitted. Drag to rotate and pinch to zoom.",color=Muted,fontSize=12.sp) }
        if(geographyError) item { Text("Bundled geography could not be loaded.",color=Orange) }
    }
}
fun interpolate(a: Geo,b: Geo,t: Double): Geo {
    fun vector(p: Geo): DoubleArray { val lat=Math.toRadians(p.lat);val lon=Math.toRadians(p.lon);return doubleArrayOf(cos(lat)*cos(lon),cos(lat)*sin(lon),sin(lat)) }
    val u=vector(a); val v=vector(b); val angle=acos((u.indices.sumOf { u[it]*v[it] }).coerceIn(-1.0,1.0)); val s=sin(angle)
    if(abs(s)<1e-8) return a
    val w=DoubleArray(3) { (sin((1-t)*angle)*u[it]+sin(t*angle)*v[it])/s }
    return Geo(Math.toDegrees(atan2(w[1],w[0])),Math.toDegrees(atan2(w[2],sqrt(w[0]*w[0]+w[1]*w[1]))))
}

/** Round-robin sampling prevents the dominant IP feed from hiding other categories. */
fun globeNodes(rows: List<Record>, limit: Int = 95): List<Record> {
    val groups = rows.filter { it.latitude != null && it.longitude != null }
        .distinctBy { Triple(it.longitude, it.latitude, it.category) }
        .groupBy { it.category }.values.map { it.iterator() }
    return buildList {
        while (size < limit && groups.any { it.hasNext() }) {
            groups.forEach { if (size < limit && it.hasNext()) add(it.next()) }
        }
    }
}
