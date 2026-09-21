package com.voutselas.skopos

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.*

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
@Composable fun GlobeScreen(rows: List<Record>) {
    var longitude by rememberSaveable { mutableDoubleStateOf(15.0) }; var latitude by rememberSaveable { mutableDoubleStateOf(18.0) }; var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var outlines by remember { mutableStateOf<List<List<Geo>>>(emptyList()) }
    var geographyError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) {
            val raw = JSONObject(context.assets.open("world.json").bufferedReader().use { it.readText() }).getJSONArray("outlines")
            (0 until raw.length()).map { i -> raw.getJSONArray(i).let { ring -> (0 until ring.length()).map { j -> ring.getJSONArray(j).let { Geo(it.getDouble(0),it.getDouble(1)) } } } }
        } }.onSuccess { outlines=it }.onFailure { geographyError=true }
    }
    val nodes = remember(rows) { rows.filter { it.latitude != null && it.longitude != null }.take(95) }
    val visible = nodes.filter { val p=project(Geo(it.longitude!!,it.latitude!!),longitude,latitude); p.depth>0 && abs(p.x*.4*zoom)<=.5 && abs(p.y*.4*zoom)<=.5 }
    val transition = rememberInfiniteTransition(label="Collection arcs")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(4000,easing=LinearEasing)),label="Arc motion")
    Page {
        item { ScreenTitle("Global Threat Infrastructure") }
        item {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()
                .semantics { contentDescription="Threat infrastructure globe, ${visible.size} visible nodes. Use rotate and zoom buttons below." }
                .pointerInput(Unit) { detectTransformGestures { _, pan, scale, _ ->
                    longitude = (longitude-pan.x*.4/zoom)%360; latitude=(latitude+pan.y*.3/zoom).coerceIn(-75.0,75.0); zoom=(zoom*scale).coerceIn(1f,3f)
                } }) {
                val radius = size.minDimension*.4f*zoom
                fun position(p: Projected, height: Float=1f)=Offset(center.x+p.x*radius*height,center.y+p.y*radius*height)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3A111A),Surface,Background),center,radius),radius)
                drawCircle(Accent.copy(alpha=.5f),radius,style=Stroke(1.2f))
                fun line(points: List<Geo>,color: Color,width: Float) {
                    var last: Projected?=null
                    points.forEach { geo -> val p=project(geo,longitude,latitude); val old=last
                        if(old != null && old.depth>0 && p.depth>0) drawLine(color,position(old),position(p),width)
                        last=p
                    }
                }
                for(lat in -60..60 step 30) line((-180..180 step 3).map { Geo(it.toDouble(),lat.toDouble()) },Muted.copy(alpha=.13f),.6f)
                for(lon in -180..180 step 30) line((-90..90 step 3).map { Geo(lon.toDouble(),it.toDouble()) },Muted.copy(alpha=.13f),.6f)
                outlines.forEach { line(it,Color(0xFFB27680).copy(alpha=.65f),1f) }
                val hubs = listOf(Geo(17.1077,48.1486),Geo(-.1276,51.5072),Geo(-74.006,40.7128),Geo(103.8198,1.3521),Geo(139.6503,35.6762))
                nodes.forEachIndexed { index,row ->
                    val start=Geo(row.longitude!!,row.latitude!!); val end=hubs[index%hubs.size]
                    val color=when(row.category) { "IP / C2" -> Accent; "Domains" -> Color(0xFF66D9A6); "URLs" -> Orange; else -> Color(0xFFAA99FF) }
                    val p=project(start,longitude,latitude)
                    if(p.depth>0) { drawCircle(color.copy(alpha=.14f),7f,position(p)); drawCircle(color,2.5f,position(p)) }
                    var previous: Projected?=null; var previousHeight=1f
                    for(step in 0..30) {
                        val fraction=step/30.0
                        val geo=interpolate(start,end,fraction); val point=project(geo,longitude,latitude)
                        val height=(1+.16*sin(fraction*PI)).toFloat()
                        val old=previous
                        if(old != null && old.depth>0 && point.depth>0) drawLine(color.copy(alpha=.26f),position(old,previousHeight),position(point,height),1.2f)
                        previous=point; previousHeight=height
                    }
                    val dot=project(interpolate(start,end,phase.toDouble()),longitude,latitude)
                    if(dot.depth>0) drawCircle(color.copy(alpha=.8f),2f,position(dot,(1+.16*sin(phase*PI)).toFloat()))
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick={zoom=(zoom-.5f).coerceAtLeast(1f)},enabled=zoom>1) { Text("− Zoom") }; Text("${String.format(java.util.Locale.getDefault(),"%.1f",zoom)}×",color=Muted)
            TextButton(onClick={zoom=(zoom+.5f).coerceAtMost(3f)},enabled=zoom<3) { Text("+ Zoom") }; TextButton(onClick={zoom=1f;longitude=15.0;latitude=18.0}) { Text("Reset") }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { TextButton(onClick={longitude-=35}) { Text("← Rotate") }; TextButton(onClick={longitude+=35}) { Text("Rotate →") } } }
        item { Panel {
            fun known(value: String)=value.lowercase() !in listOf("unknown","unattributed","n/a","-","")
            Text("${visible.size} visible nodes   ·   ${visible.map { it.text("country") }.filter(::known).distinct().size} countries",fontSize=15.sp)
            Text("${visible.map { it.text("malware") }.filter(::known).distinct().size} malware families",color=Muted,fontSize=13.sp)
            Text("● IP / C2   ● Domains   ● URLs   ● Other",color=Accent,fontSize=12.sp)
        } }
        item { Text("Live IOC locations from up to 95 loaded records. Arcs connect them to display hubs to visualize collection; they are not measured traffic or attacker-to-victim paths. Unknown geography is omitted. Drag to rotate and pinch to zoom.",color=Muted,fontSize=12.sp) }
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
