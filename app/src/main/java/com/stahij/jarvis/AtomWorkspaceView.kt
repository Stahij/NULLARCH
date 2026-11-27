package com.stahij.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
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
 * Transparent interaction layer above the WebGL core.
 * It owns app icons, placement, paging and touch input; the WebView below
 * owns only the 3D background/core rendering.
 */
class AtomWorkspaceView(
    context: Context,
    private val installedApps: List<LauncherApp>,
    private val onOpenChat: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenDrawer: () -> Unit,
    private val onOpenLab: () -> Unit,
    private val onAppOptions: (LauncherApp) -> Unit,
    private val onRotateCore: (Float, Float) -> Unit
) : View(context) {
    private enum class TouchOwner { NONE, CORE, ICON, BACKGROUND }

    private data class OrbitalItem(
        val app: LauncherApp,
        var angle: Float,
        var orbit: Float,
        var page: Int,
        var rect: RectF = RectF()
    )

    private val prefs = context.getSharedPreferences("jarvis_atom_workspace", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val items = mutableListOf<OrbitalItem>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private var page = 0
    private var pageCount = 1
    private var running = true
    private var motionEnabled = true
    private var motionPhase = 0f
    private var lastMotionFrameMs = SystemClock.uptimeMillis()
    private var coreRotationOffset = 0f
    private var targetCoreRotationOffset = 0f
    private var coreTiltOffset = 0f
    private var targetCoreTiltOffset = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var selected: OrbitalItem? = null
    private var touchOwner = TouchOwner.NONE
    private var dragging = false
    private var downAt = 0L
    private var labGestureTriggered = false
    private var iconOptionsOpened = false
    private var iconScale = prefs.getFloat("icon_scale", 1f).coerceIn(.65f, 1.5f)
    private var orbitScale = prefs.getFloat("orbit_scale", 1f).coerceIn(.65f, 1.45f)
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
            val appItem = items.firstOrNull { it.page == page && it.rect.contains(e.x, e.y) }
            if (appItem != null) {
                iconOptionsOpened = true
                onAppOptions(appItem.app)
            } else if (isCoreAt(e.x, e.y)) onOpenSettings()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dy) > abs(dx) && dy < -42f * density && e1.y > height - 112f * density && e1.x < width * .38f) {
                onOpenDrawer()
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
            items.add(OrbitalItem(
                app,
                position.first,
                position.second.coerceIn(.20f, .44f),
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
        val orbit = (.25f + (pageItems % 3) * .075f).coerceIn(.20f, .44f)
        items.add(OrbitalItem(app, angle, orbit, page))
        saveLayout()
        invalidate()
        return true
    }

    fun removeApp(app: LauncherApp) { items.removeAll { it.app.packageName == app.packageName && it.app.className == app.className }; saveLayout(); invalidate() }

    fun rotateWithCore(dx: Float, dy: Float) {
        targetCoreRotationOffset += dx * 0.0065f
        targetCoreTiltOffset = (targetCoreTiltOffset + dy * 0.0018f).coerceIn(-0.42f, 0.42f)
        invalidate()
    }

    fun setLabIconScale(value: Float) { iconScale = value.coerceIn(.65f, 1.5f); prefs.edit().putFloat("icon_scale", iconScale).apply(); invalidate() }
    fun setLabOrbitScale(value: Float) { orbitScale = value.coerceIn(.65f, 1.45f); prefs.edit().putFloat("orbit_scale", orbitScale).apply(); invalidate() }

    fun changePage(delta: Int) {
        if (delta > 0 && page == pageCount - 1) pageCount++
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page_v2", page).putInt("page_count_v2", pageCount).apply()
        invalidate()
    }

    // A tap on the center freezes orbital motion at the saved base positions.
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * .5f
        val cy = h * .49f
        val base = min(w, h)
        val now = SystemClock.uptimeMillis()
        if (running) motionPhase += ((now - lastMotionFrameMs).coerceAtLeast(0L) / 1000f) * .14f
        lastMotionFrameMs = now
        val follow = if (running) .34f else .55f
        coreRotationOffset += (targetCoreRotationOffset - coreRotationOffset) * follow
        coreTiltOffset += (targetCoreTiltOffset - coreTiltOffset) * follow

        val projected = items.filter { it.page == page }.map { item ->
            val angle = item.angle + motionPhase + coreRotationOffset
            val radius = base * item.orbit * orbitScale
            val c = cos(angle.toDouble()).toFloat()
            val sn = sin(angle.toDouble()).toFloat()
            val x = cx + c * radius
            val y = cy + (sn * .57f - c * coreTiltOffset * .35f) * radius
            val depth = (sn + 1f) * .5f
            Triple(item, x, y) to depth
        }.sortedBy { it.second }

        projected.forEach { (entry, depth) ->
            val (item, x, y) = entry
            val radius = base * item.orbit * orbitScale
            val scale = .72f + depth * .38f
            val iconSize = 34f * density * scale * iconScale
            // App icons are drawn by the native overlay, not the WebGL depth buffer.
            // Mask back-side icons across the core silhouette (including their badge)
            // so they cannot appear pasted over the sphere.
            val coreRx = base * .22f
            val coreRy = base * .22f
            val iconPad = iconSize * .72f
            val maskRx = coreRx + iconPad
            val maskRy = coreRy + iconPad
            val dxCore = x - cx
            val dyCore = y - cy
            val overlapsCore = (dxCore * dxCore) / (maskRx * maskRx) +
                (dyCore * dyCore) / (maskRy * maskRy) < 1f
            val behindCore = depth < .5f && overlapsCore
            if (behindCore) {
                item.rect.setEmpty()
                return@forEach
            }
            val alpha = (155 + depth * 100).toInt()
            paint.color = Color.argb(alpha, 12, 30, 48)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, iconSize * .67f, paint)
            paint.color = Color.argb(90 + (depth * 90).toInt(), 80, 203, 239)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.1f * density
            canvas.drawCircle(x, y, iconSize * .67f, paint)
            paint.style = Paint.Style.FILL
            val rect = RectF(x - iconSize / 2f, y - iconSize / 2f, x + iconSize / 2f, y + iconSize / 2f)
            item.rect.set(rect)
            val oldAlpha = item.app.icon.alpha
            item.app.icon.alpha = alpha
            item.app.icon.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            item.app.icon.draw(canvas)
            item.app.icon.alpha = oldAlpha
            textPaint.alpha = alpha
            canvas.drawText(item.app.label.take(16), x, y + iconSize * .78f + 12f * density, textPaint)
        }
        if (running) postInvalidateOnAnimation()
    }

    private fun isCoreAt(x: Float, y: Float): Boolean {
        val cx = width * .5f
        val cy = height * .49f
        val radius = min(width, height) * .205f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= radius * radius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!labGestureTriggered && event.pointerCount >= 5 && (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)) {
            labGestureTriggered = true
            selected = null
            touchOwner = TouchOwner.NONE
            dragging = false
            onOpenLab()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                labGestureTriggered = false
                downAt = SystemClock.uptimeMillis()
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                selected = null
                touchOwner = when {
                    isCoreAt(event.x, event.y) -> TouchOwner.CORE
                    items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) } != null -> TouchOwner.ICON
                    else -> TouchOwner.BACKGROUND
                }
                if (touchOwner == TouchOwner.ICON) selected = items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) }
                dragging = false
                iconOptionsOpened = false
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected == null) {
                    if (touchOwner == TouchOwner.CORE) onRotateCore(event.x - lastTouchX, event.y - lastTouchY)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    gestureDetector.onTouchEvent(event)
                    return true
                }
                if (!dragging) gestureDetector.onTouchEvent(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) + abs(dy) > 24f * density) {
                    dragging = true
                    val item = selected!!
                    val cx = width * .5f
                    val cy = height * .49f
                    val x = event.x - cx
                    val y = (event.y - cy) / .57f
                    item.angle = atan2(y, x)
                    item.orbit = (sqrt(x * x + y * y) / min(width, height)).coerceIn(.20f, .44f)
                    item.rect.offsetTo(event.x - item.rect.width() / 2f, event.y - item.rect.height() / 2f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (labGestureTriggered) { labGestureTriggered = false; selected = null; dragging = false; return true }
                if (selected == null) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val inPageGestureZone = downY >= height - 68f * density
                    val horizontalSwipe = touchOwner == TouchOwner.BACKGROUND &&
                        inPageGestureZone && abs(dx) > 42f * density && abs(dx) > abs(dy)
                    val verticalSwipe = touchOwner == TouchOwner.BACKGROUND &&
                        dy < -38f * density &&
                        abs(dy) > abs(dx) &&
                        downY > height - 112f * density &&
                        downX < width * .38f
                    when {
                        touchOwner == TouchOwner.CORE -> gestureDetector.onTouchEvent(event)
                        horizontalSwipe -> changePage(if (dx < 0f) 1 else -1)
                        verticalSwipe -> onOpenDrawer()
                        else -> gestureDetector.onTouchEvent(event)
                    }
                } else {
                    if (!dragging && !iconOptionsOpened) selected?.let { if (SystemClock.uptimeMillis() - downAt >= 450L) onAppOptions(it.app) else launch(it.app) }
                    if (!dragging) gestureDetector.onTouchEvent(event)
                    if (dragging) saveLayout()
                }
                selected = null
                touchOwner = TouchOwner.NONE
                dragging = false
                iconOptionsOpened = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                labGestureTriggered = false
                if (selected == null) gestureDetector.onTouchEvent(event)
                selected = null
                touchOwner = TouchOwner.NONE
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
