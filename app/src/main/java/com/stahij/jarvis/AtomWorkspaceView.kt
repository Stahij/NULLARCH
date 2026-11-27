package com.stahij.jarvis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.io.ByteArrayOutputStream
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
    private val onRotateCore: (Float, Float) -> Unit,
    private val onWorkspaceChanged: () -> Unit,
    private val onWorkspaceNodeDragged: (String, Float, Float, Int) -> Unit
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
    val currentPage: Int get() = page
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
    private var dragStartAngle = 0f
    private var dragStartOrbit = .30f
    private var movedSinceDown = false
    private var autoPlacement = prefs.getBoolean("auto_placement_v1", true)
    private var labGestureTriggered = false
    private var iconOptionsOpened = false
    private var longPressConsumed = false
    private var iconScale = prefs.getFloat("icon_scale", 1f).coerceIn(.65f, 1.5f)
    private var orbitScale = prefs.getFloat("orbit_scale", 1f).coerceIn(.65f, 1.45f)
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val onIcon = items.any { it.page == page && it.rect.contains(e.x, e.y) }
            if (!onIcon && isCoreAt(e.x, e.y)) toggleMotion()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val onIcon = items.any { it.page == page && it.rect.contains(e.x, e.y) }
            if (!onIcon && isCoreAt(e.x, e.y)) onOpenChat()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val appItem = items.firstOrNull { it.page == page && it.rect.contains(e.x, e.y) }
            if (appItem != null) {
                longPressConsumed = true
                iconOptionsOpened = true
                onAppOptions(appItem.app)
            } else if (isCoreAt(e.x, e.y)) {
                longPressConsumed = true
                onOpenSettings()
            }
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
        // Auto mode distributes new icons around a golden-angle spiral.
        // Sprite plane assignment uses index % 4, alternating through the four axes.
        // Manual mode adds the icon at a neutral slot for the user to drag into place.
        val angle = if (autoPlacement) (pageItems * 2.3999632f) % (Math.PI.toFloat() * 2f) else 0f
        val orbit = if (autoPlacement) (.20f + (pageItems % 5) * .06f).coerceIn(.20f, .44f) else .30f
        items.add(OrbitalItem(app, angle, orbit, page))
        saveLayout()
        onWorkspaceChanged()
        invalidate()
        return true
    }

    fun removeApp(app: LauncherApp) {
        items.removeAll { it.app.packageName == app.packageName && it.app.className == app.className }
        saveLayout(); onWorkspaceChanged(); invalidate()
    }

    fun isAutoPlacementEnabled(): Boolean = autoPlacement

    fun togglePlacementMode(): Boolean {
        autoPlacement = !autoPlacement
        prefs.edit().putBoolean("auto_placement_v1", autoPlacement).apply()
        return autoPlacement
    }

    fun rotateWithCore(dx: Float, dy: Float) = Unit

    fun setLabIconScale(value: Float) { iconScale = value.coerceIn(.65f, 1.5f); prefs.edit().putFloat("icon_scale", iconScale).apply() }
    fun setLabOrbitScale(value: Float) { orbitScale = value.coerceIn(.65f, 1.45f); prefs.edit().putFloat("orbit_scale", orbitScale).apply() }

    fun changePage(delta: Int) {
        if (delta > 0 && page == pageCount - 1) pageCount++
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page_v2", page).putInt("page_count_v2", pageCount).apply()
        onWorkspaceChanged()
        invalidate()
    }

    fun workspaceAppsJson(): String {
        val array = org.json.JSONArray()
        items.forEach { item ->
            val drawable = item.app.icon
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val oldBounds = Rect(drawable.bounds)
            val oldAlpha = drawable.alpha
            drawable.setBounds(0, 0, 128, 128)
            drawable.alpha = 255
            drawable.draw(canvas)
            drawable.bounds = oldBounds
            drawable.alpha = oldAlpha
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            array.put(org.json.JSONObject().apply {
                put("id", item.app.packageName + "/" + item.app.className)
                put("label", item.app.label)
                put("packageName", item.app.packageName)
                put("className", item.app.className)
                put("angle", item.angle.toDouble())
                put("orbit", item.orbit.toDouble())
                put("page", item.page)
                put("icon", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
            })
        }
        return array.toString()
    }

    fun updateWebGlNodePositions(json: String) {
        post {
            try {
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val node = array.optJSONObject(i) ?: continue
                    val id = node.optString("id")
                    val item = items.firstOrNull { it.app.packageName + "/" + it.app.className == id } ?: continue
                    if (!node.optBoolean("visible", false)) {
                        item.rect.setEmpty()
                    } else {
                        // WebView reports CSS pixels; MotionEvent uses Android physical pixels.
                        val x = node.optDouble("x", -1000.0).toFloat() * density
                        val y = node.optDouble("y", -1000.0).toFloat() * density
                        val w = (node.optDouble("w", 0.0).toFloat() * density).coerceIn(24f * density, 140f * density)
                        val h = (node.optDouble("h", 0.0).toFloat() * density).coerceIn(28f * density, 170f * density)
                        item.rect.set(x - w * .5f, y - h * .5f, x + w * .5f, y + h * .5f)
                    }
                }
            } catch (_: Exception) { }
        }
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

    // Android now owns interaction only; app icons are rendered in the WebGL scene.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun isCoreAt(x: Float, y: Float): Boolean {
        val cx = width * .5f
        val cy = height * .49f
        val radius = height * .24f
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
                movedSinceDown = false
                val hitIcon = items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) }
                // Core owns drags inside its visible hit area; nearby/overlapping
                // orbital labels must not steal a rotation gesture.
                touchOwner = when {
                    isCoreAt(event.x, event.y) -> TouchOwner.CORE
                    hitIcon != null -> TouchOwner.ICON
                    else -> TouchOwner.BACKGROUND
                }
                if (touchOwner == TouchOwner.ICON) selected = hitIcon
                dragStartAngle = selected?.angle ?: 0f
                dragStartOrbit = selected?.orbit ?: .30f
                dragging = false
                iconOptionsOpened = false
                longPressConsumed = false
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected == null) {
                    val moveDx = event.x - lastTouchX
                    val moveDy = event.y - lastTouchY
                    if (abs(event.x - downX) + abs(event.y - downY) > 8f * density) movedSinceDown = true
                    if (touchOwner == TouchOwner.CORE) onRotateCore(moveDx, moveDy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    gestureDetector.onTouchEvent(event)
                    return true
                }
                if (!dragging) gestureDetector.onTouchEvent(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) + abs(dy) > 8f * density) {
                    dragging = true
                    movedSinceDown = true
                    val item = selected!!
                    val dimension = min(width, height).coerceAtLeast(1).toFloat()
                    // Tangential drag sensitivity is intentionally damped; vertical
                    // motion adjusts orbit radius instead of snapping to finger coordinates.
                    item.angle = dragStartAngle + (dx / dimension) * (Math.PI.toFloat() * 2f) * .45f
                    item.orbit = (dragStartOrbit - (dy / dimension) * .30f).coerceIn(.20f, .44f)
                    onWorkspaceNodeDragged(item.app.packageName + "/" + item.app.className, item.angle, item.orbit, item.page)
                    item.rect.offsetTo(event.x - item.rect.width() / 2f, event.y - item.rect.height() / 2f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (labGestureTriggered) { labGestureTriggered = false; selected = null; dragging = false; return true }
                // A long press opens the options dialog only. The ACTION_UP must
                // not also trigger a tap/launch, especially when an icon overlaps
                // the core hit region and is owned by the core gesture handler.
                if (longPressConsumed) {
                    longPressConsumed = false
                    selected = null
                    touchOwner = TouchOwner.NONE
                    dragging = false
                    iconOptionsOpened = false
                    return true
                }
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
                        touchOwner == TouchOwner.CORE -> {
                            if (!movedSinceDown) {
                                val tapIcon = items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) }
                                if (tapIcon != null) launch(tapIcon.app) else gestureDetector.onTouchEvent(event)
                            }
                        }
                        horizontalSwipe -> changePage(if (dx < 0f) 1 else -1)
                        verticalSwipe -> onOpenDrawer()
                        else -> gestureDetector.onTouchEvent(event)
                    }
                } else {
                    if (!dragging && !iconOptionsOpened) selected?.let { if (SystemClock.uptimeMillis() - downAt >= 450L) onAppOptions(it.app) else launch(it.app) }
                    if (!dragging) gestureDetector.onTouchEvent(event)
                    if (dragging) { saveLayout(); onWorkspaceChanged() }
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
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setClassName(app.packageName, app.className)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (error: Exception) {
            android.util.Log.e("NULLARCH", "Cannot launch ${app.packageName}/${app.className}", error)
            android.widget.Toast.makeText(context, "Не удалось открыть ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
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
