package com.stahij.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page = 0
    private var pageCount = 1
    private var running = true
    private var motionEnabled = true
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
    private val stars = List(64) { i ->
        val x = ((i * 73 + 19) % 997) / 997f
        val y = ((i * 137 + 41) % 991) / 991f
        Triple(x, y, 0.35f + ((i * 17) % 60) / 100f)
    }

    init {
        isFocusable = true
        isClickable = true
        loadLayout()
    }

    // A fresh v2 layout intentionally starts empty; installed apps are never
    // placed on the user's workspace without an explicit choice.
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

    fun changePage(delta: Int) {
        if (delta > 0 && page == pageCount - 1) pageCount++
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page_v2", page).putInt("page_count_v2", pageCount).apply()

        invalidate()
    }

    // The core itself is the only motion control: tap to freeze/resume.
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
        canvas.drawColor(Color.rgb(3, 8, 20))
        drawStars(canvas, w, h)
        val cx = w * .5f
        val cy = h * .49f
        val base = min(w, h)
        val now = SystemClock.uptimeMillis()
        val elapsed = if (running) now / 1000f else 0f

        drawCore(canvas, cx, cy, base * .115f, elapsed)

        items.filter { it.page == page }.forEachIndexed { index, item ->
            val angle = item.angle + if (running) elapsed * (.11f + (index % 4) * .012f) else 0f
            val radius = base * item.orbit
            val x = cx + cos(angle.toDouble()).toFloat() * radius
            val y = cy + sin(angle.toDouble()).toFloat() * radius * .57f
            val depth = (sin(angle.toDouble()).toFloat() + 1f) * .5f
            val scale = .72f + depth * .38f
            val iconSize = 34f * density * scale
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

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        stars.forEachIndexed { i, star ->
            starPaint.color = Color.argb((star.third * 95).toInt(), 123, 185, 221)
            val radius = if (i % 7 == 0) 1.5f else .75f
            canvas.drawCircle(star.first * w, star.second * h, radius * density, starPaint)
        }
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float) {
        val pulse = if (running) 1f + .035f * sin(time * 1.4f) else 1f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius * 1.9f * pulse,
            intArrayOf(Color.argb(0, 31, 195, 255), Color.argb(48, 20, 175, 237), Color.argb(0, 4, 12, 28)),
            floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.9f * pulse, paint)
        paint.shader = null
        paint.color = Color.argb(210, 16, 85, 119)
        canvas.drawCircle(cx, cy, radius * pulse, paint)
        paint.color = Color.argb(180, 78, 220, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        canvas.drawCircle(cx, cy, radius * .82f, paint)
        paint.color = Color.argb(225, 140, 239, 255)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - radius * .22f, cy - radius * .24f, radius * .24f, paint)
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
                    val y = (event.y - cy) / .57f
                    item.angle = atan2(y, x)
                    item.orbit = (sqrt(x * x + y * y) / min(width, height)).coerceIn(.20f, .44f)
                    item.rect.offsetTo(event.x - item.rect.width() / 2f, event.y - item.rect.height() / 2f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selected == null) {
                    gestureDetector.onTouchEvent(event)
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
