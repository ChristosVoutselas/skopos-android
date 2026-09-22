package com.voutselas.skopos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Local Natural Earth geometry, shaded for depth; no satellite imagery or map requests. */
class EarthRenderer(outlines: List<List<Geo>>) {
    private val width = 1440
    private val height = 720
    val texture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val land = IntArray(width * height)
    init {
        val mask = texture
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        outlines.forEach { ring ->
            if (ring.size >= 3) {
                val path = Path()
                ring.forEachIndexed { i, p ->
                    val x = ((p.lon + 180) / 360 * width).toFloat()
                    val y = ((90 - p.lat) / 180 * height).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); canvas.drawPath(path, paint)
            }
        }
        mask.getPixels(land, 0, width, 0, 0, width, height)

    }
    suspend fun render(longitude: Double, latitude: Double, size: Int = 256): ImageBitmap {
        val pixels = IntArray(size * size)
        val tilt = Math.toRadians(latitude)
        val origin = Math.toRadians(longitude)
        val sinTilt = sin(tilt); val cosTilt = cos(tilt)
        for (y in 0 until size) {
            currentCoroutineContext().ensureActive()
            val north = 1.0 - 2.0 * (y + .5) / size
            for (x in 0 until size) {
                val east = 2.0 * (x + .5) / size - 1.0
                val squared = east * east + north * north
                if (squared >= 1.0) continue
                val depth = sqrt(1.0 - squared)
                val lat = asin((north * cosTilt + depth * sinTilt).coerceIn(-1.0, 1.0))
                val lon = origin + atan2(east, depth * cosTilt - north * sinTilt)
                val u = (((Math.toDegrees(lon) + 180) % 360 + 360) % 360 / 360 * width).toInt().coerceIn(0, width - 1)
                val v = ((90 - Math.toDegrees(lat)) / 180 * height).toInt().coerceIn(0, height - 1)
                val coverage = ((land[v * width + u] ushr 24) and 255) / 255.0
                // Fixed presentation lighting, not an astronomical day/night forecast.
                val light = max(0.0, -.42 * east + .48 * north + .77 * depth)
                val shade = .19 + .81 * light
                val grain = sin(u * .17) * cos(v * .21) * 2.0 * coverage
                val oceanGlint = (1 - coverage) * max(0.0, -.24 * east + .25 * north + .937 * depth).pow(55) * 23
                val rim = (1 - depth).pow(4) * 15
                fun channel(ocean: Int, ground: Int, haze: Double): Int = (((ocean * (1 - coverage) + ground * coverage + grain) * shade) + oceanGlint + rim * haze).toInt().coerceIn(0, 255)
                val alpha = ((1 - squared) * size * .5).coerceIn(0.0, 1.0) * 255
                pixels[y * size + x] = android.graphics.Color.argb(alpha.toInt(), channel(15, 75, .3), channel(51, 112, .8), channel(79, 98, 1.4))
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Android 13+: inverse orthographic projection runs per pixel on the GPU. */
@androidx.annotation.RequiresApi(33)
class GpuEarthRenderer(renderer: EarthRenderer) {
    private val shader = android.graphics.RuntimeShader("""
        uniform shader earth;
        uniform float2 viewport;
        uniform float2 camera;
        uniform float radius;
        half4 main(float2 pixel) {
            float2 p = (pixel - viewport * 0.5) / radius;
            float east = p.x;
            float north = -p.y;
            float squared = dot(p, p);
            if (squared >= 1.0) return half4(0);
            float depth = sqrt(1.0 - squared);
            float lat = asin(clamp(north*cos(camera.y) + depth*sin(camera.y), -1.0, 1.0));
            float lon = camera.x + atan(east, depth*cos(camera.y) - north*sin(camera.y));
            float2 uv = float2(fract((lon + 3.14159265) / 6.2831853), (1.57079633-lat)/3.14159265);
            float coverage = earth.eval(uv * float2(1440,720)).a;
            float light = max(0.0, -.42*east + .48*north + .77*depth);
            float shade = .19 + .81*light;
            float glint = (1.0-coverage)*pow(max(0.0,-.24*east+.25*north+.937*depth),55.0)*23.0;
            float rim = pow(1.0-depth,4.0)*15.0;
            float3 rgb = (mix(float3(15,51,79),float3(75,112,98),coverage)*shade + glint + rim*float3(.3,.8,1.4))/255.0;
            float alpha = clamp((1.0-squared)*radius,0.0,1.0);
            return half4(rgb*alpha,alpha);
        }
    """.trimIndent())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@GpuEarthRenderer.shader }
    init {
        shader.setInputShader("earth", android.graphics.BitmapShader(renderer.texture,
            android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.CLAMP).apply {
                setFilterMode(android.graphics.BitmapShader.FILTER_MODE_LINEAR)
            })
    }
    fun draw(canvas: Canvas, width: Float, height: Float, radius: Float, longitude: Double, latitude: Double) {
        shader.setFloatUniform("viewport",width,height)
        shader.setFloatUniform("camera",Math.toRadians(longitude).toFloat(),Math.toRadians(latitude).toFloat())
        shader.setFloatUniform("radius",radius)
        canvas.drawRect(0f,0f,width,height,paint)
    }
}
