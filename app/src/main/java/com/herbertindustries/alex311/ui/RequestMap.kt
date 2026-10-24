package com.herbertindustries.alex311.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.herbertindustries.alex311.data.MapPoint
import com.herbertindustries.alex311.domain.Coordinate
import com.herbertindustries.alex311.domain.Geo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.File
import kotlin.math.*

@Composable fun RequestMap(
    points: List<MapPoint>, modifier: Modifier = Modifier, center: Coordinate = Geo.city,
    zoom: Int = 13, pin: Coordinate? = null, colorBy: String = "Status",
    onCase: (String) -> Unit = {}, onPick: ((Coordinate) -> Unit)? = null,
    onArea: ((List<Coordinate>) -> Unit)? = null
) {
    val context = LocalContext.current
    var native by remember { mutableStateOf<TileMapView?>(null) }
    var bounds by remember { mutableStateOf<List<Coordinate>?>(null) }
    val latestCase by rememberUpdatedState(onCase)
    val latestPick by rememberUpdatedState(onPick)
    Box(modifier) {
        AndroidView(factory = { TileMapView(context).also { view ->
            native = view
            view.setCenter(center, zoom)
            view.onCase = { latestCase(it) }
            view.onPick = { latestPick?.invoke(it) }
            view.onViewport = { bounds = it }
        } }, update = { view ->
            view.points = points; view.pin = pin; view.colorBy = colorBy; view.selecting = onPick != null
            view.updateCenter(center, zoom); view.invalidate()
        }, modifier = Modifier.fillMaxSize().clipToBounds())
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = { native?.zoomBy(1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            FilledTonalButton(onClick = { native?.zoomBy(-1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        }
        if (onArea != null) FilledTonalButton(onClick = { bounds?.let(onArea) }, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) { Text("Search this area") }
        TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.openstreetmap.org/copyright"))) },
            modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = .92f)), contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A small native slippy map. Only visible tiles are fetched, using a disk HTTP cache. */
class TileMapView(context: Context): View(context) {
    var points: List<MapPoint> = emptyList()
    var pin: Coordinate? = null
    var colorBy: String = "Status"
    var selecting = false
    var onCase: (String) -> Unit = {}
    var onPick: (Coordinate) -> Unit = {}
    var onViewport: (List<Coordinate>) -> Unit = {}
    private var zoom = 13
    private var center = Geo.city
    private var requestedCenter: Coordinate? = null
    private var requestedZoom = 13
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val density = resources.displayMetrics.density
    private val tiles = object: LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
    private val pending = mutableSetOf<String>()
    private val failedAt = mutableMapOf<String, Long>()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val semaphore = Semaphore(4)
    private data class Hit(val x: Float, val y: Float, val id: String, val count: Int)
    private var targets: List<Hit> = emptyList()
    private val gestures = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scale.isInProgress) {
                val c = project(center); center = unproject(c.first + dx, c.second + dy); invalidate(); viewport()
            }
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val target = targets.minByOrNull { (it.x - e.x).pow(2) + (it.y - e.y).pow(2) }
            if (target != null && hypot(target.x - e.x, target.y - e.y) < 28 * density) {
                if (target.count > 1 && zoom < 18) {
                    val c = project(center)
                    center = unproject(c.first + target.x - width / 2, c.second + target.y - height / 2)
                    zoomBy(2)
                } else onCase(target.id)
                performClick()
            }
            else if (selecting) { val c = project(center); onPick(unproject(c.first + e.x - width / 2, c.second + e.y - height / 2)); performClick() }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomBy(1); return true }
        override fun onLongPress(e: MotionEvent) {
            if (selecting) { val c = project(center); onPick(unproject(c.first + e.x - width / 2, c.second + e.y - height / 2)) }
        }
    })
    private var scaleSum = 1f
    private val scale = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaleSum = 1f; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleSum *= detector.scaleFactor
            if (scaleSum > 1.5f) { zoomBy(1); scaleSum = 1f }
            if (scaleSum < .67f) { zoomBy(-1); scaleSum = 1f }
            return true
        }
    })
    init { contentDescription = "Map of Alexandria requests. Drag to pan, pinch or use buttons to zoom. Tap a request to open it."; isClickable = true }
    fun setCenter(value: Coordinate, level: Int) { center = value; requestedCenter = value; zoom = level; requestedZoom = level; invalidate(); viewport() }
    fun updateCenter(value: Coordinate, level: Int) { if (requestedCenter != value || requestedZoom != level) setCenter(value, level) }
    fun zoomBy(delta: Int) { zoom = (zoom + delta).coerceIn(10, 18); invalidate(); viewport() }
    private fun project(c: Coordinate): Pair<Double, Double> {
        val world = 256.0 * (1 shl zoom)
        val lat = Math.toRadians(c.lat.coerceIn(-85.0511, 85.0511))
        return (c.lng + 180) / 360 * world to (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * world
    }
    private fun unproject(x: Double, y: Double): Coordinate {
        val world = 256.0 * (1 shl zoom)
        return Coordinate(Math.toDegrees(atan(sinh(PI * (1 - 2 * y / world)))), x / world * 360 - 180)
    }
    private fun viewport() {
        if (width == 0) return
        val c = project(center)
        onViewport(listOf(unproject(c.first - width / 2, c.second + height / 2), unproject(c.first + width / 2, c.second + height / 2), unproject(c.first + width / 2, c.second - height / 2), unproject(c.first - width / 2, c.second - height / 2)))
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { viewport() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scale.onTouchEvent(event); gestures.onTouchEvent(event)
        if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    override fun onDetachedFromWindow() { scope.cancel(); pending.clear(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(Color.rgb(232, 238, 232))
        val c = project(center)
        val left = c.first - width / 2; val top = c.second - height / 2
        val minX = floor(left / 256).toInt(); val minY = floor(top / 256).toInt()
        val maxX = floor((left + width) / 256).toInt(); val maxY = floor((top + height) / 256).toInt()
        for (x in minX..maxX) for (y in minY..maxY) {
            if (x !in 0 until (1 shl zoom) || y !in 0 until (1 shl zoom)) continue
            val key = "$zoom/$x/$y"
            val px = (x * 256 - left).toFloat(); val py = (y * 256 - top).toFloat()
            tiles.get(key)?.let { canvas.drawBitmap(it, null, RectF(px, py, px + 256, py + 256), tilePaint) } ?: fetch(key)
        }
        val visible = points.mapNotNull { p ->
            val position = project(Coordinate(p.lat, p.lng))
            val x = (position.first - left).toFloat(); val y = (position.second - top).toFloat()
            if (x in -20f..width + 20f && y in -20f..height + 20f) Triple(x, y, p) else null
        }
        val grouped = visible.groupBy { (it.first / (28 * density)).toInt() to (it.second / (28 * density)).toInt() }
        val hitTargets = mutableListOf<Hit>()
        grouped.values.forEach { group ->
            val x = group.map { it.first }.average().toFloat(); val y = group.map { it.second }.average().toFloat()
            val point = group.first().third
            paint.color = Color.WHITE; canvas.drawCircle(x, y, if (group.size > 1) 13 * density else 6 * density, paint)
            paint.color = when (colorBy) {
                "Age" -> when { point.age < 0 -> Color.GRAY; point.age < 30 -> Color.rgb(0, 121, 107); point.age < 90 -> Color.rgb(194, 119, 16); else -> Color.rgb(173, 58, 51) }
                "Type" -> intArrayOf(0xff006d77.toInt(), 0xff8b5e3c.toInt(), 0xff6554a4.toInt(), 0xffb24d6c.toInt(), 0xff147d92.toInt())[point.category.hashCode().and(Int.MAX_VALUE) % 5]
                else -> if (point.status.equals("closed", true)) Color.rgb(61, 126, 106) else if (point.status.equals("open", true)) Color.rgb(206, 116, 31) else Color.GRAY
            }
            canvas.drawCircle(x, y, if (group.size > 1) 11 * density else 4.5f * density, paint)
            if (group.size > 1) {
                paint.color = Color.WHITE; paint.textSize = 10 * density; paint.textAlign = Paint.Align.CENTER
                canvas.drawText(if (group.size > 99) "99+" else "${group.size}", x, y + 3 * density, paint)
            }
            hitTargets += Hit(x, y, point.id, group.size)
        }
        targets = hitTargets
        pin?.let {
            val position = project(it); val x = (position.first - left).toFloat(); val y = (position.second - top).toFloat()
            paint.color = Color.WHITE; canvas.drawCircle(x, y, 11 * density, paint)
            paint.color = Color.rgb(0, 109, 119); canvas.drawCircle(x, y, 8 * density, paint)
            paint.color = Color.WHITE; canvas.drawCircle(x, y, 3 * density, paint)
        }
        canvas.restore()
    }
    private fun fetch(key: String) {
        if (key in pending || System.currentTimeMillis() - (failedAt[key] ?: 0) < 30000) return
        pending += key
        scope.launch {
            try {
                val bitmap = semaphore.withPermit { withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url("https://tile.openstreetmap.org/$key.png")
                        .header("User-Agent", "Alex311RebornAndroid/1.0 (+https://alex311visibility.me)").build()
                    tileClient(context).newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) } else null
                    }
                } }
                if (bitmap != null) tiles.put(key, bitmap) else failedAt[key] = System.currentTimeMillis()
                invalidate()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failedAt[key] = System.currentTimeMillis() }
            finally { pending -= key }
        }
    }
    companion object {
        @Volatile private var http: OkHttpClient? = null
        @Synchronized private fun tileClient(context: Context): OkHttpClient = http ?: OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map-tiles"), 50L * 1024 * 1024))
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.header("Cache-Control") == null && response.header("Expires") == null)
                    response.newBuilder().header("Cache-Control", "public, max-age=604800").build() else response
            }.build().also { http = it }
    }
}
