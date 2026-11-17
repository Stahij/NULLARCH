package com.stahij.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LauncherApp(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable
)

/**
 * Transparent interaction layer above the WebGL Plasma Core.
 * Icons are rendered as custom Plasma Glass modules and move on independent
 * 3D orbital planes. The WebGL core remains responsible for the central sphere.
 */
class AtomWorkspaceView(
    context: Context,
    private val installedApps: List<LauncherApp>,
    private val onOpenChat: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenDrawer: () -> Unit,
    private val onForwardTouch: (MotionEvent) -> Unit,
    private val onAppsChanged: (List<LauncherApp>) -> Unit
) : View(context) {
    private data class OrbitalItem(
        val app: LauncherApp,
        var angle: Float,
        var orbit: Float,
        var page: Int,
        val tiltX: Float,
        val tiltY: Float,
        val angularSpeed: Float,
        var rect: RectF = RectF()
    )

    private data class OrbitalPoint(val x: Float, val y: Float, val z: Float)

    private val prefs = context.getSharedPreferences("jarvis_atom_workspace", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val items = mutableListOf<OrbitalItem>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val iconPath = Path()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setShadowLayer(4f * density, 0f, 1f * density, Color.BLACK)
    }
    private var page = 0
    private var pageCount = 1
    private var running = true
    private var motionEnabled = true
    private var animationTime = 0f
    private var lastFrameUptime = SystemClock.uptimeMillis()
    private var downX = 0f
    private var downY = 0f
    private var selected: OrbitalItem? = null
    private var dragging = false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isCoreAt(e.x, e.y)) toggleMotion()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isCoreAt(e.x, e.y)) onOpenChat()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (isCoreAt(e.x, e.y)) onOpenSettings()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dy) > abs(dx) && dy < -70f * density && e1.y > 56f * density && e1.y < height - 64f * density) {
                onOpenDrawer()
                return true
            }
            if (abs(dx) > 70f * density && abs(dx) > abs(dy)) {
                changePage(if (dx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    init {
        isFocusable = true
        isClickable = true
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        loadLayout()
    }

    private fun orbitalItem(app: LauncherApp, angle: Float, orbit: Float, page: Int): OrbitalItem {
        val seed = app.packageName.hashCode().toLong().and(0xffffffffL)
        fun unit(shift: Int): Float = ((seed shr shift) and 0xffffL).toFloat() / 65535f
        // Four intentional orbital families instead of a random plane per icon.
        // This creates a readable atom-like structure: flat, vertical and two tilted planes.
        val plane = (seed % 4L).toInt()
        val tilt = when (plane) {
            0 -> 0f to 0f
            1 -> 1.12f to 0.08f
            2 -> 0.62f to 0.72f
            else -> -0.62f to -0.72f
        }
        return OrbitalItem(
            app = app,
            angle = angle,
            orbit = orbit,
            page = page,
            tiltX = tilt.first,
            tiltY = tilt.second,
            angularSpeed = .115f + unit(16) * .025f
        )
    }

    private fun loadLayout() {
        val saved = prefs.getString("layout_v2", null)
        val savedMap = HashMap<String, Triple<Float, Float, Int>>()
        if (saved != null) {
            try {
                val array = org.json.JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    savedMap[item.getString("id")] = Triple(
                        item.optDouble("angle", 0.0).toFloat(),
                        item.optDouble("orbit", 0.30).toFloat(),
                        item.optInt("page", 0)
                    )
                }
            } catch (_: Exception) { }
        }

        val appsById = installedApps.associateBy { it.packageName + "/" + it.className }
        savedMap.forEach { (id, position) ->
            val app = appsById[id] ?: return@forEach
            items.add(orbitalItem(
                app,
                position.first,
                position.second.coerceIn(.22f, .46f),
                position.third.coerceAtLeast(0)
            ))
        }
        pageCount = maxOf(1, prefs.getInt("page_count_v2", 1), (items.maxOfOrNull { it.page } ?: 0) + 1)
        page = prefs.getInt("current_page_v2", 0).coerceIn(0, pageCount - 1)
    }

    fun getCoreApps(): List<LauncherApp> = items.filter { it.page == page }.map { it.app }

    fun addApp(app: LauncherApp): Boolean {
        val id = app.packageName + "/" + app.className
        if (items.any { it.app.packageName + "/" + it.app.className == id }) return false
        val pageItems = items.count { it.page == page }
        val angle = ((pageItems % 8).toFloat() / 8f * (Math.PI * 2.0)).toFloat()
        val orbit = (.27f + (pageItems % 3) * .065f).coerceIn(.22f, .46f)
        items.add(orbitalItem(app, angle, orbit, page))
        saveLayout()
        onAppsChanged(getCoreApps())
        invalidate()
        return true
    }

    fun changePage(delta: Int) {
        if (delta > 0 && page == pageCount - 1) pageCount++
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page_v2", page).putInt("page_count_v2", pageCount).apply()
        onAppsChanged(getCoreApps())
        invalidate()
    }

    fun toggleMotion() {
        motionEnabled = !motionEnabled
        running = motionEnabled
        invalidate()
    }

    fun resumeMotion() {
        lastFrameUptime = SystemClock.uptimeMillis()
        running = motionEnabled
        invalidate()
    }

    fun pauseMotion() {
        running = false
        lastFrameUptime = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun orbitalPoint(item: OrbitalItem, angle: Float, radius: Float, elapsed: Float, phaseOffset: Float = 0f): OrbitalPoint {
        val a = angle + phaseOffset
        val x = cos(a) * radius
        val y = sin(a) * radius * .78f
        val z = 0f

        // Local orbital plane: each app belongs to one stable 3D plane.
        val cx = cos(item.tiltX); val sx = sin(item.tiltX)
        val cy = cos(item.tiltY); val sy = sin(item.tiltY)
        val y1 = y * cx - z * sx
        val z1 = y * sx + z * cx
        val x1 = x * cy + z1 * sy
        val z2 = -x * sy + z1 * cy

        // Slow global precession prevents the whole orbital system from reading as a flat drawing.
        val globalY = elapsed * .14f
        val gy = cos(globalY); val gs = sin(globalY)
        val x2 = x1 * gy + z2 * gs
        val z3 = -x1 * gs + z2 * gy
        val globalX = sin(elapsed * .35f) * .05f
        val gx = cos(globalX); val gxs = sin(globalX)
        val y2 = y1 * gx - z3 * gxs
        val z4 = y1 * gxs + z3 * gx
        return OrbitalPoint(x2, y2, z4)
    }

    private fun project(point: OrbitalPoint, cx: Float, cy: Float, base: Float): OrbitalPoint {
        // Camera sits in front of the scene (+Z), matching the WebGL Plasma Core.
        val cameraDistance = 1.65f
        val perspective = (cameraDistance / (cameraDistance - point.z)).coerceIn(.68f, 1.48f)
        return OrbitalPoint(
            cx + point.x * base * perspective,
            cy - point.y * base * perspective,
            point.z
        )
    }

    private fun isOccludedByCore(point: OrbitalPoint, cx: Float, cy: Float, coreRadius: Float): Boolean {
        if (point.z >= 0f) return false
        val dx = point.x - cx
        val dy = point.y - cy
        return dx * dx + dy * dy < coreRadius * coreRadius
    }

    private fun drawOrbit(canvas: Canvas, item: OrbitalItem, cx: Float, cy: Float, base: Float, coreRadius: Float, time: Float) {
        val backPath = Path()
        val frontPath = Path()
        var activePath: Path? = null
        val segments = 120
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2.0).toFloat()
            val p = project(orbitalPoint(item, a, item.orbit, time), cx, cy, base)
            val hidden = isOccludedByCore(p, cx, cy, coreRadius)
            val target = if (p.z < 0f) backPath else frontPath
            if (hidden) {
                activePath = null
            } else {
                if (activePath !== target) {
                    target.moveTo(p.x, p.y)
                    activePath = target
                } else {
                    target.lineTo(p.x, p.y)
                }
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = .85f * density
        paint.color = Color.argb(15, 65, 205, 255)
        canvas.drawPath(backPath, paint)
        paint.strokeWidth = 1.05f * density
        paint.color = Color.argb(48, 83, 218, 255)
        paint.setShadowLayer(3f * density, 0f, 0f, Color.argb(28, 0, 177, 255))
        canvas.drawPath(frontPath, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
    }

    private fun drawPlasmaGlassIcon(canvas: Canvas, item: OrbitalItem, x: Float, y: Float, size: Float, alpha: Int, depth: Float, time: Float) {
        val radius = size * .48f
        val outerRadius = radius + 3.5f * density

        // A restrained swarm of charged particles makes each app node feel embedded in the 3D system.
        for (i in 0 until 7) {
            val a = time * (1.15f + i * .035f) + i * (Math.PI.toFloat() * 2f / 7f)
            val orbitX = cos(a) * outerRadius
            val orbitY = sin(a * 1.08f) * outerRadius * .72f
            val pulse = .55f + .45f * sin(a * 1.7f)
            paint.color = Color.argb((alpha * (.22f + pulse * .35f)).toInt().coerceIn(0, alpha), 82, 220, 255)
            paint.setShadowLayer(3f * density, 0f, 0f, Color.argb((alpha * .35f).toInt(), 0, 190, 255))
            canvas.drawCircle(x + orbitX, y + orbitY, (1.1f + pulse * 1.1f) * density, paint)
            paint.clearShadowLayer()
        }

        paint.style = Paint.Style.FILL
        paint.setShadowLayer((7f + depth * 5f) * density, 0f, 0f, Color.argb((alpha * .48f).toInt(), 0, 177, 255))
        paint.color = Color.argb((alpha * .18f).toInt(), 28, 164, 235)
        canvas.drawCircle(x, y, outerRadius, paint)
        paint.clearShadowLayer()

        val glass = RadialGradient(
            x - radius * .28f, y - radius * .34f, radius * 1.55f,
            intArrayOf(
                Color.argb((alpha * .72f).toInt(), 42, 142, 190),
                Color.argb((alpha * .58f).toInt(), 8, 43, 70),
                Color.argb((alpha * .82f).toInt(), 3, 17, 34)
            ),
            floatArrayOf(0f, .52f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = glass
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null

        iconPath.reset()
        iconPath.addCircle(x, y, radius * .76f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(iconPath)
        val iconInset = radius * .31f
        val oldAlpha = item.app.icon.alpha
        item.app.icon.alpha = alpha
        item.app.icon.setBounds(
            (x - radius + iconInset).toInt(),
            (y - radius + iconInset).toInt(),
            (x + radius - iconInset).toInt(),
            (y + radius - iconInset).toInt()
        )
        item.app.icon.draw(canvas)
        item.app.icon.alpha = oldAlpha
        canvas.restoreToCount(save)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f * density
        paint.color = Color.argb(alpha, 102, 226, 255)
        canvas.drawCircle(x, y, radius, paint)
        paint.strokeWidth = .65f * density
        paint.color = Color.argb((alpha * .58f).toInt(), 224, 250, 255)
        canvas.drawCircle(x, y, radius * .88f, paint)

        paint.strokeWidth = 1.2f * density
        paint.color = Color.argb((alpha * .68f).toInt(), 232, 252, 255)
        val highlight = RectF(x - radius * .68f, y - radius * .68f, x + radius * .68f, y + radius * .68f)
        canvas.drawArc(highlight, 205f, 72f, false, paint)
        paint.style = Paint.Style.FILL
    }

    // Rendering is owned exclusively by the Gemini WebGL scene.
    // This view remains a transparent gesture layer for launcher navigation.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun isCoreAt(x: Float, y: Float): Boolean {
        val cx = width * .5f
        val cy = height * .49f
        val radius = min(width, height) * .14f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= radius * radius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onForwardTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                selected = items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) }
                dragging = false
                if (selected == null) gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected == null) {
                    gestureDetector.onTouchEvent(event)
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) + abs(dy) > 14f * density) {
                    dragging = true
                    val item = selected!!
                    val cx = width * .5f
                    val cy = height * .49f
                    val x = event.x - cx
                    val y = (event.y - cy) / .82f
                    item.angle = kotlin.math.atan2(y, x)
                    item.orbit = (sqrt(x * x + y * y) / min(width, height)).coerceIn(.22f, .46f)
                    item.rect.offsetTo(event.x - item.rect.width() / 2f, event.y - item.rect.height() / 2f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selected == null) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val horizontalSwipe = abs(dx) > 55f * density && abs(dx) > abs(dy)
                    val verticalSwipe = dy < -55f * density &&
                        abs(dy) > abs(dx) &&
                        downY > 56f * density &&
                        downY < height - 64f * density
                    when {
                        horizontalSwipe -> changePage(if (dx < 0f) 1 else -1)
                        verticalSwipe -> onOpenDrawer()
                        else -> gestureDetector.onTouchEvent(event)
                    }
                } else {
                    if (!dragging) selected?.let { launch(it.app) }
                    if (dragging) saveLayout()
                }
                selected = null
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (selected == null) gestureDetector.onTouchEvent(event)
                selected = null
                dragging = false
                return true
            }
        }
        return true
    }

    private fun launch(app: LauncherApp) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    private fun saveLayout() {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.app.packageName + "/" + item.app.className)
                put("angle", item.angle.toDouble())
                put("orbit", item.orbit.toDouble())
                put("page", item.page)
            })
        }
        prefs.edit().putString("layout_v2", array.toString()).putInt("page_count_v2", pageCount).apply()
    }
}
