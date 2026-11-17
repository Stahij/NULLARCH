package com.stahij.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val onOpenDrawer: () -> Unit
) : View(context) {
    private data class OrbitalItem(
        val app: LauncherApp,
        var angle: Float,
        var orbit: Float,
        var page: Int,
        val tiltX: Float,
        val tiltY: Float,
        val tiltZ: Float,
        val phase: Float,
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
        return OrbitalItem(
            app = app,
            angle = angle,
            orbit = orbit,
            page = page,
            tiltX = (unit(0) - .5f) * 1.18f,
            tiltY = (unit(8) - .5f) * 1.30f,
            tiltZ = (unit(16) - .5f) * .72f,
            phase = unit(24) * (Math.PI * 2.0).toFloat()
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

    fun addApp(app: LauncherApp): Boolean {
        val id = app.packageName + "/" + app.className
        if (items.any { it.app.packageName + "/" + it.app.className == id }) return false
        val pageItems = items.count { it.page == page }
        val angle = ((pageItems % 8).toFloat() / 8f * (Math.PI * 2.0)).toFloat()
        val orbit = (.27f + (pageItems % 3) * .065f).coerceIn(.22f, .46f)
        items.add(orbitalItem(app, angle, orbit, page))
        saveLayout()
        invalidate()
        return true
    }

    fun changePage(delta: Int) {
        if (delta > 0 && page == pageCount - 1) pageCount++
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page_v2", page).putInt("page_count_v2", pageCount).apply()
        invalidate()
    }

    fun toggleMotion() {
        motionEnabled = !motionEnabled
        running = motionEnabled
        invalidate()
    }

    fun resumeMotion() {
        running = motionEnabled
        invalidate()
    }

    fun pauseMotion() {
        running = false
        invalidate()
    }

    private fun orbitalPoint(item: OrbitalItem, angle: Float, radius: Float, phaseOffset: Float = 0f): OrbitalPoint {
        val a = angle + phaseOffset
        var x = cos(a) * radius
        var y = sin(a) * radius * .82f
        var z = sin(a + item.phase) * radius * .16f

        val cx = cos(item.tiltX); val sx = sin(item.tiltX)
        val cy = cos(item.tiltY); val sy = sin(item.tiltY)
        val cz = cos(item.tiltZ); val sz = sin(item.tiltZ)

        val y1 = y * cx - z * sx
        val z1 = y * sx + z * cx
        val x2 = x * cy + z1 * sy
        val z2 = -x * sy + z1 * cy
        val x3 = x2 * cz - y1 * sz
        val y3 = x2 * sz + y1 * cz
        return OrbitalPoint(x3, y3, z2)
    }

    private fun project(point: OrbitalPoint, cx: Float, cy: Float, base: Float): OrbitalPoint {
        // Camera sits in front of the scene (+Z), matching the WebGL Plasma Core.
        val cameraDistance = base * 1.65f
        val perspective = (cameraDistance / (cameraDistance - point.z * base)).coerceIn(.68f, 1.48f)
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
        val radius = base * item.orbit
        val path = Path()
        var drawing = false
        val segments = 72
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2.0).toFloat()
            val p = project(orbitalPoint(item, a, radius), cx, cy, base)
            val hidden = isOccludedByCore(p, cx, cy, coreRadius)
            if (hidden) {
                drawing = false
            } else if (!drawing) {
                path.moveTo(p.x, p.y)
                drawing = true
            } else {
                path.lineTo(p.x, p.y)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .8f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(26, 65, 205, 255)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPlasmaGlassIcon(canvas: Canvas, item: OrbitalItem, x: Float, y: Float, size: Float, alpha: Int, depth: Float) {
        val rect = RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        val radius = size * .25f
        val outer = RectF(rect).apply { inset(-1.8f * density, -1.8f * density) }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((alpha * .16f).toInt(), 28, 164, 235)
        paint.setShadowLayer((5f + depth * 5f) * density, 0f, 0f, Color.argb((alpha * .42f).toInt(), 0, 177, 255))
        canvas.drawRoundRect(outer, radius, radius, paint)
        paint.clearShadowLayer()

        paint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(Color.argb(alpha * 3 / 5, 19, 75, 112), Color.argb(alpha * 2 / 5, 5, 22, 43), Color.argb(alpha * 3 / 5, 24, 91, 133)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        iconPath.reset()
        iconPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(iconPath)
        val iconInset = size * .105f
        val iconRect = RectF(rect).apply { inset(iconInset, iconInset) }
        val oldAlpha = item.app.icon.alpha
        item.app.icon.alpha = alpha
        item.app.icon.setBounds(iconRect.left.toInt(), iconRect.top.toInt(), iconRect.right.toInt(), iconRect.bottom.toInt())
        item.app.icon.draw(canvas)
        item.app.icon.alpha = oldAlpha

        // A restrained glass reflection gives the tile a material, not a flat sticker.
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(Color.argb((alpha * .30f).toInt(), 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        canvas.restoreToCount(save)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.15f * density
        paint.color = Color.argb(alpha, 92, 218, 255)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.strokeWidth = .65f * density
        paint.color = Color.argb(alpha / 2, 220, 249, 255)
        canvas.drawRoundRect(RectF(rect).apply { inset(2f * density, 2f * density) }, radius * .82f, radius * .82f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * .5f
        val cy = h * .49f
        val base = min(w, h)
        val coreRadius = base * .185f
        val elapsed = if (running) SystemClock.uptimeMillis() / 1000f else 0f
        val visibleItems = items.filter { it.page == page }

        visibleItems.forEach { drawOrbit(canvas, it, cx, cy, base, coreRadius, elapsed) }

        val projected = visibleItems.mapNotNull { item ->
            val angle = item.angle + if (running) elapsed * .14f else 0f
            val point = project(orbitalPoint(item, angle, base * item.orbit), cx, cy, base)
            if (isOccludedByCore(point, cx, cy, coreRadius + base * .13f)) {
                item.rect.setEmpty()
                null
            } else {
                Triple(item, point, angle)
            }
        }.sortedBy { it.second.z }

        projected.forEach { (item, point, _) ->
            val depth = ((point.z / (base * .55f)) + 1f).coerceIn(0f, 1f)
            val scale = .78f + depth * .30f
            val iconSize = 38f * density * scale
            val alpha = (178 + depth * 77).toInt().coerceIn(0, 255)
            item.rect.set(point.x - iconSize / 2f, point.y - iconSize / 2f, point.x + iconSize / 2f, point.y + iconSize / 2f)
            drawPlasmaGlassIcon(canvas, item, point.x, point.y, iconSize, alpha, depth)
            textPaint.alpha = alpha
            canvas.drawText(item.app.label.take(16), point.x, point.y + iconSize * .78f + 12f * density, textPaint)
        }
        if (running) postInvalidateOnAnimation()
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
